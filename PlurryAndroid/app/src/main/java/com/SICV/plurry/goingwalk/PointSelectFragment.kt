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
    // 🚀 수정: visitedPlaceInfo를 VisitedPlaceDetails를 포함하는 Map으로 변경
    private val visitedPlaceInfo = mutableMapOf<String, VisitedPlaceDetails>() // placeId to VisitedPlaceDetails

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

        // 🚀 수정: ExploreAdapter에 PlaceData만 넘기도록 유지 (isVisitedWithImage 필드 추가로 처리)
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
                visitedPlaceInfo.clear() // 🚀 수정: visitedPlaceInfo 클리어
                Log.d("PointSelectFragment", "visitedPlaces 쿼리 결과 문서 수: ${querySnapshot.documents.size}")

                if (querySnapshot.documents.isEmpty()) {
                    Log.d("PointSelectFragment", "방문 기록이 없습니다. (새 경로 쿼리 결과 0개)")
                    Log.d("PointSelectFragment", "쿼리 대상 userId: $userId")
                }

                for (document in querySnapshot.documents) {
                    val placeIdFromDoc = document.id
                    val userIdFromDoc = document.getString("userId")
                    val imageUrlFromDoc = document.getString("imageUrl") // 🚀 추가: imageUrl 필드 가져오기
                    val caloFromDoc = document.getDouble("calo") ?: 0.0 // 🚀 NEW: calo 데이터 가져오기
                    val distanceFromDoc = document.getDouble("distance") ?: 0.0 // 🚀 NEW: distance 데이터 가져오기
                    val stepNumFromDoc = document.getLong("stepNum") ?: 0L // 🚀 NEW: stepNum 데이터 가져오기
                    val docPath = document.reference.path

                    Log.d("PointSelectFragment", "처리 중인 문서: $docPath")
                    Log.d("PointSelectFragment", "  - 문서 ID (placeId): $placeIdFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 userId: $userIdFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 imageUrl: $imageUrlFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 calo: $caloFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 distance: $distanceFromDoc")
                    Log.d("PointSelectFragment", "  - 문서 내 stepNum: $stepNumFromDoc")
                    Log.d("PointSelectFragment", "  - 현재 앱의 userId: $userId")

                    if (placeIdFromDoc != null && userIdFromDoc == userId) {
                        // 🚀 수정: imageUrl이 null이 아닌 경우에만 true로 저장 및 운동 데이터 함께 저장
                        visitedPlaceInfo[placeIdFromDoc] = VisitedPlaceDetails(
                            hasImageUrl = !imageUrlFromDoc.isNullOrEmpty(),
                            visitedImageUrl = imageUrlFromDoc, // 🚀 NEW: Store the actual image URL
                            calo = caloFromDoc,
                            distance = distanceFromDoc,
                            stepNum = stepNumFromDoc
                        )
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
                // 🚀 NEW: Check if the place has been visited and has an imageUrl, and get exercise data
                val visitedDetails = visitedPlaceInfo[placeId]
                val hasVisitedAndImageUrl = visitedDetails?.hasImageUrl ?: false
                val visitedImageUrl = visitedDetails?.visitedImageUrl // 🚀 NEW: Get visited image URL
                val calo = visitedDetails?.calo ?: 0.0
                val distance = visitedDetails?.distance ?: 0.0
                val stepNum = visitedDetails?.stepNum ?: 0L

                val geo = doc.getGeoPoint("geo") ?: continue
                val placeLocation = Location("place").apply {
                    latitude = geo.latitude
                    longitude = geo.longitude
                }

                if (userLocation.distanceTo(placeLocation) <= radiusKm * 1000) {
                    val imgUrl = doc.getString("myImgUrl") ?: continue
                    // 🚀 MODIFIED: PlaceData 생성 시 hasVisitedAndImageUrl 상태와 운동 데이터를 함께 전달
                    placeList.add(PlaceData(placeId, geo.latitude, geo.longitude, imgUrl, hasVisitedAndImageUrl, calo, distance, stepNum, visitedImageUrl))
                    Log.d("PointSelectFragment", "추가된 장소: $placeId (방문+이미지 여부: $hasVisitedAndImageUrl, 방문 이미지: $visitedImageUrl, 칼로리: $calo, 거리: $distance, 걸음수: $stepNum)")
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

    // 🚀 NEW: 방문한 장소의 상세 정보를 담을 데이터 클래스
    data class VisitedPlaceDetails(
        val hasImageUrl: Boolean = false,
        val visitedImageUrl: String? = null, // 🚀 NEW: Field to store the visited image URL
        val calo: Double = 0.0,
        val distance: Double = 0.0,
        val stepNum: Long = 0L
    )

    // 🚀 수정: isVisitedWithImage, calo, distance, stepNum, visitedImageUrl 필드 추가
    data class PlaceData(
        val placeId: String,
        val lat: Double,
        val lng: Double,
        val imageUrl: String, // This is the original image from the 'Places' collection
        val isVisitedWithImage: Boolean = false,
        val calo: Double = 0.0,
        val distance: Double = 0.0,
        val stepNum: Long = 0L,
        val visitedImageUrl: String? = null // 🚀 NEW: Field for the image URL from visitedPlaces
    )

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
            val dialogVisitedImage = view.findViewById<ImageView>(R.id.dialogVisitedImage) // 🚀 NEW: Get the new ImageView
            val btnStart = view.findViewById<Button>(R.id.btnStartExplore)
            val btnCancel = view.findViewById<Button>(R.id.btnCancelExplore)
            val tvStatusMessage = view.findViewById<TextView>(R.id.tvStatusMessage) // 🚀 NEW: 상태 메시지 TextView
            val tvExerciseData = view.findViewById<TextView>(R.id.tvExerciseData) // 🚀 NEW: 운동 데이터 TextView

            // Load the original place image
            Glide.with(view).load(place.imageUrl).into(imageView)

            // 🚀 NEW: place.isVisitedWithImage 값에 따라 버튼 활성화/비활성화 및 메시지 표시
            if (place.isVisitedWithImage) {
                btnStart.isEnabled = false
                tvStatusMessage.visibility = View.VISIBLE // 메시지 표시
                tvStatusMessage.text = "이미 탐색 완료된 장소입니다." // 메시지 설정

                tvExerciseData.visibility = View.VISIBLE // 운동 데이터 표시
                // 🚀 NEW: 운동 데이터 텍스트 설정
                tvExerciseData.text = "거리: ${String.format("%.2f", place.distance)} km | 걸음: ${place.stepNum} 걸음 | 칼로리: ${place.calo} kcal "

                // 🚀 NEW: Load the visited image if available
                if (!place.visitedImageUrl.isNullOrEmpty()) {
                    Glide.with(view).load(place.visitedImageUrl).into(dialogVisitedImage)
                    dialogVisitedImage.visibility = View.VISIBLE
                    Log.d("ExploreConfirmDialog", "방문 이미지 로드됨: ${place.visitedImageUrl}")
                } else {
                    dialogVisitedImage.visibility = View.GONE
                    Log.d("ExploreConfirmDialog", "방문 이미지 없음 또는 비어있음.")
                }
            } else {
                btnStart.isEnabled = true
                tvStatusMessage.visibility = View.GONE // 메시지 숨김
                tvExerciseData.visibility = View.GONE // 운동 데이터 숨김
                dialogVisitedImage.visibility = View.GONE // 🚀 NEW: Hide visited image if not visited with image
            }

            val dialog = android.app.AlertDialog.Builder(requireContext())
                .setView(view)
                .create()

            btnCancel.setOnClickListener {
                dismiss()
            }

            btnStart.setOnClickListener {
                if (btnStart.isEnabled) { // 🚀 추가: 버튼이 활성화된 경우에만 동작하도록 확인
                    val fragment = ExploreTrackingFragment.newInstance(place.placeId, place.lat, place.lng, place.imageUrl)
                    val activity = activity as? AppCompatActivity ?: return@setOnClickListener

                    activity.supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainerExplore, fragment)
                        .addToBackStack(null)
                        .commit()

                    parent.dismiss()
                    dismiss()
                } else {
                    Toast.makeText(context, "이미 탐색 완료된 장소입니다.", Toast.LENGTH_SHORT).show()
                }
            }

            return dialog
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
}