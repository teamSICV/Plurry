package com.SICV.plurry.goingwalk

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log // Log 임포트 추가
import android.view.*
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.DialogFragment
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.SICV.plurry.R
import com.google.firebase.auth.FirebaseAuth

class PointSelectFragment : DialogFragment() {

    private lateinit var spinner: Spinner
    private lateinit var confirmBtn: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ExploreAdapter
    private val placeList = mutableListOf<PlaceData>()
    // 🚀 수정: visitedPlaceIds를 Map으로 변경하여 imageUrl 존재 여부도 함께 저장
    private val visitedPlaceInfo = mutableMapOf<String, Boolean>() // placeId to hasImageUrl

    private val radiusValues = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0)

    private var userLat = 0.0
    private var userLng = 0.0

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.activity_goingwalk_point_select, container, false)

        spinner = view.findViewById(R.id.spinnerRadius)
        confirmBtn = view.findViewById(R.id.btnConfirmRadius)
        recyclerView = view.findViewById(R.id.recyclerViewPlaces)

        auth = FirebaseAuth.getInstance()

        val spinnerOptions = listOf("1km", "1.5km", "2km", "2.5km", "3km", "3.5km", "4km", "4.5km", "5km")
        val spinnerAdapter = object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_item, spinnerOptions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(android.graphics.Color.BLACK)
                return view
            }
        }
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = spinnerAdapter

        confirmBtn.setOnClickListener {
            val selectedRadius = radiusValues.getOrNull(spinner.selectedItemPosition) ?: 1.0
            loadVisitedPlacesThenUpdateLocation(selectedRadius)
        }

        adapter = ExploreAdapter(placeList) { place ->
            ExploreConfirmDialog(place, this).show(parentFragmentManager, "ExploreConfirmDialog")
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(context, 3)

        return view
    }

    private fun loadVisitedPlacesThenUpdateLocation(radiusKm: Double) {
        val userId = auth.currentUser?.uid
        Log.d("PointSelectFragment", "현재 로그인된 사용자 ID: $userId")

        if (userId == null) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            updateUserLocationThenLoad(radiusKm)
            return
        }

        Firebase.firestore.collection("Users")
            .document(userId)
            .collection("visitedPlaces")
            .get()
            .addOnSuccessListener { querySnapshot ->
                visitedPlaceInfo.clear() // 🚀 수정: visitedPlaceIds 대신 visitedPlaceInfo 클리어
                Log.d("PointSelectFragment", "visitedPlaces 쿼리 결과 문서 수: ${querySnapshot.documents.size}")

                if (querySnapshot.documents.isEmpty()) {
                    Log.d("PointSelectFragment", "방문 기록이 없습니다. (새 경로 쿼리 결과 0개)")
                    Log.d("PointSelectFragment", "쿼리 대상 userId: $userId")
                }

                for (document in querySnapshot.documents) {
                    val placeIdFromDoc = document.id
                    val userIdFromDoc = document.getString("userId")
                    val imageUrlFromDoc = document.getString("imageUrl") // 🚀 추가: imageUrl 필드 가져오기
                    val docPath = document.reference.path

                    Log.d("PointSelectFragment", "처리 중인 문서: $docPath")
                    Log.d("PointSelectFragment", "  - 문서 ID (placeId): $placeIdFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 userId: $userIdFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 imageUrl: $imageUrlFromDoc") // 🚀 추가 로그
                    Log.d("PointSelectFragment", "  - 현재 앱의 userId: $userId")

                    if (placeIdFromDoc != null && userIdFromDoc == userId) {
                        // 🚀 수정: imageUrl이 null이 아닌 경우에만 true로 저장
                        visitedPlaceInfo[placeIdFromDoc] = !imageUrlFromDoc.isNullOrEmpty()
                        Log.d("PointSelectFragment", "  -> 방문한 장소 ID 추가됨: $placeIdFromDoc (userId 일치, imageUrl 존재: ${!imageUrlFromDoc.isNullOrEmpty()})")
                    } else {
                        Log.d("PointSelectFragment", "  -> 문서 스킵됨:")
                        if (placeIdFromDoc == null) Log.d("PointSelectFragment", "    - placeId 없음 (문서 ID가 null)")
                        if (userIdFromDoc != userId) Log.d("PointSelectFragment", "    - userId 불일치: 문서 userId($userIdFromDoc) vs 현재 userId($userId)")
                    }
                }
                Log.d("PointSelectFragment", "최종 visitedPlaceInfo: $visitedPlaceInfo")
                updateUserLocationThenLoad(radiusKm)
            }
            .addOnFailureListener { e ->
                Log.e("PointSelectFragment", "방문한 장소 로드 오류: ${e.message}", e)
                Toast.makeText(context, "방문 기록 로드 실패: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                updateUserLocationThenLoad(radiusKm)
            }
    }

    private fun updateUserLocationThenLoad(radiusKm: Double) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                userLat = location.latitude
                userLng = location.longitude
            }
            loadNearbyPoints(radiusKm)
        }.addOnFailureListener {
            Toast.makeText(context, "위치 정보를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadNearbyPoints(radiusKm: Double) {
        Firebase.firestore.collection("Places").get().addOnSuccessListener { docs ->
            val userLocation = Location("user").apply {
                latitude = userLat
                longitude = userLng
            }

            placeList.clear()
            Log.d("PointSelectFragment", "주변 장소 로드 시작. 필터링 전 총 장소 수: ${docs.size()}")

            for (doc in docs) {
                val placeId = doc.id
                // 🚀 수정: visitedPlaceInfo 맵을 사용하여 placeId가 있고 imageUrl도 있는 경우에만 스킵
                val hasVisitedAndImageUrl = visitedPlaceInfo[placeId] ?: false
                if (hasVisitedAndImageUrl) {
                    Log.d("PointSelectFragment", "스킵된 방문 장소 (imageUrl 있음): $placeId")
                    continue // 방문한 장소이고 imageUrl이 있으므로 건너뜀
                }

                val geo = doc.getGeoPoint("geo") ?: continue
                val placeLocation = Location("place").apply {
                    latitude = geo.latitude
                    longitude = geo.longitude
                }

                if (userLocation.distanceTo(placeLocation) <= radiusKm * 1000) {
                    val imgUrl = doc.getString("myImgUrl") ?: continue
                    placeList.add(PlaceData(placeId, geo.latitude, geo.longitude, imgUrl))
                    Log.d("PointSelectFragment", "추가된 장소: $placeId")
                } else {
                    Log.d("PointSelectFragment", "거리 초과로 스킵된 장소: $placeId (거리: ${userLocation.distanceTo(placeLocation)}m)")
                }
            }

            adapter.notifyDataSetChanged()
            Log.d("PointSelectFragment", "필터링 후 최종 표시될 장소 수: ${placeList.size}")

            if (placeList.isEmpty()) {
                Toast.makeText(context, "주변에 탐색 가능한 장소가 없습니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    data class PlaceData(val placeId: String, val lat: Double, val lng: Double, val imageUrl: String)

    inner class ExploreAdapter(
        private val items: List<PlaceData>,
        private val onClick: (PlaceData) -> Unit
    ) : RecyclerView.Adapter<ExploreAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val imageView: ImageView = itemView.findViewById(R.id.imgPlace)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.activity_goingwalk_itemplaceimage, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            Glide.with(holder.imageView).load(item.imageUrl).into(holder.imageView)
            holder.itemView.setOnClickListener { onClick(item) }
        }
    }

    class ExploreConfirmDialog(private val place: PlaceData, private val parent: DialogFragment) : DialogFragment() {

        override fun onCreateDialog(savedInstanceState: Bundle?): android.app.Dialog {
            val view = LayoutInflater.from(requireContext())
                .inflate(R.layout.activity_goingwalk_exploreconfirm, null)

            val imageView = view.findViewById<ImageView>(R.id.dialogImage)
            val btnStart = view.findViewById<Button>(R.id.btnStartExplore)
            val btnCancel = view.findViewById<Button>(R.id.btnCancelExplore)

            Glide.with(view).load(place.imageUrl).into(imageView)

            val dialog = android.app.AlertDialog.Builder(requireContext())
                .setView(view)
                .create()

            btnCancel.setOnClickListener {
                dismiss()
            }

            btnStart.setOnClickListener {
                val fragment = ExploreTrackingFragment.newInstance(place.placeId, place.lat, place.lng, place.imageUrl)
                val activity = activity as? AppCompatActivity ?: return@setOnClickListener

                activity.supportFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainerExplore, fragment)
                    .addToBackStack(null)
                    .commit()

                parent.dismiss()
                dismiss()
            }

            return dialog
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}