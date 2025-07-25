package com.SICV.plurry.pointrecord

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore

class CrewPointBottomFragment : BottomSheetDialogFragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnDateArray: TextView
    private lateinit var btnDisArray: TextView

    private var myLatitude: Double? = null
    private var myLongitude: Double? = null
    private var crewId: String? = null
    private var allPlaces = mutableListOf<PlaceData>()
    private lateinit var adapter: CrewPointBottomAdapter

    enum class SortType {
        DATE_DESC,
        DISTANCE_ASC
    }

    private var currentSortType = SortType.DATE_DESC

    companion object {
        private const val ARG_CREW_ID = "crew_id"

        fun newInstance(crewId: String): CrewPointBottomFragment {
            val fragment = CrewPointBottomFragment()
            val args = Bundle()
            args.putString(ARG_CREW_ID, crewId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = true
        arguments?.let {
            crewId = it.getString(ARG_CREW_ID)
        }
        Log.d("CrewPointBottom", "Fragment 생성 - crewId: $crewId")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.activity_point_record_bottom_crew, container, false)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return String.format("%.2f", earthRadius * c).toDouble()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setDimAmount(0f)
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupSortButtons()
        initLocationService()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.crewPointRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)

        btnDateArray = view.findViewById(R.id.BtnDateArray)
        btnDisArray = view.findViewById(R.id.BtnDisArray)

        adapter = CrewPointBottomAdapter(requireContext(), allPlaces)
        recyclerView.adapter = adapter
    }

    private fun setupSortButtons() {
        btnDateArray.setOnClickListener {
            sortPlacesByDate()
        }

        btnDisArray.setOnClickListener {
            sortPlacesByDistance()
        }
        updateSortButtonUI()
    }

    private fun initLocationService() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        // 위치 권한 체크 후 위치 정보 가져오기
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    myLatitude = location.latitude
                    myLongitude = location.longitude
                    Log.d("CrewPointBottom", "위치 정보 획득: $myLatitude, $myLongitude")
                } else {
                    Log.d("CrewPointBottom", "위치 정보를 가져올 수 없음")
                }
                // 위치 정보 획득 후 데이터 로딩
                fetchCrewMembersAndPlaces()
            }.addOnFailureListener { exception ->
                Log.e("CrewPointBottom", "위치 정보 가져오기 실패", exception)
                // 위치 정보 없이도 데이터 로딩
                fetchCrewMembersAndPlaces()
            }
        } else {
            Log.d("CrewPointBottom", "위치 권한 없음")
            // 위치 권한 없이도 데이터 로딩
            fetchCrewMembersAndPlaces()
        }
    }

    private fun sortPlacesByDate() {
        if (currentSortType == SortType.DATE_DESC) return

        currentSortType = SortType.DATE_DESC
        updateSortButtonUI()

        val sortedList = allPlaces.sortedByDescending { it.placeId }

        updateRecyclerView(sortedList)
        Log.d("CrewPointBottom", "날짜순 정렬 완료")
    }

    private fun sortPlacesByDistance() {
        if (currentSortType == SortType.DISTANCE_ASC) return

        if (myLatitude == null || myLongitude == null) {
            Toast.makeText(requireContext(), "위치 정보가 없어 거리순 정렬을 할 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        currentSortType = SortType.DISTANCE_ASC
        updateSortButtonUI()

        val sortedList = allPlaces.sortedBy { place ->
            calculateDistance(myLatitude!!, myLongitude!!, place.lat, place.lng)
        }

        updateRecyclerView(sortedList)
        Log.d("CrewPointBottom", "거리순 정렬 완료")
    }

    private fun updateSortButtonUI() {
        when (currentSortType) {
            SortType.DATE_DESC -> {
                btnDateArray.isSelected = true
                btnDisArray.isSelected = false
                btnDateArray.setTextColor(resources.getColor(R.color.black, null))
                btnDisArray.setTextColor(resources.getColor(R.color.gray, null))
            }
            SortType.DISTANCE_ASC -> {
                btnDateArray.isSelected = false
                btnDisArray.isSelected = true
                btnDateArray.setTextColor(resources.getColor(R.color.gray, null))
                btnDisArray.setTextColor(resources.getColor(R.color.black, null))
            }
        }
    }

    private fun updateRecyclerView(newList: List<PlaceData>) {
        allPlaces.clear()
        allPlaces.addAll(newList)
        adapter.notifyDataSetChanged()
    }

    private fun fetchCrewMembersAndPlaces() {
        Log.d("CrewPointBottom", "크루 멤버와 장소 정보 가져오기 시작 - crewId: $crewId")

        crewId?.let { id ->
            val db = FirebaseFirestore.getInstance()

            db.collection("Crew").document(id).collection("member").document("members")
                .get()
                .addOnSuccessListener { memberDocument ->
                    Log.d("CrewPointBottom", "크루 멤버 문서 가져오기 성공")

                    if (memberDocument.exists()) {
                        val memberUids = mutableListOf<String>()
                        val memberData = memberDocument.data

                        Log.d("CrewPointBottom", "크루 멤버 데이터: $memberData")

                        memberData?.keys?.forEach { uid ->
                            memberUids.add(uid)
                        }

                        Log.d("CrewPointBottom", "크루 멤버 UID 목록: $memberUids")

                        if (memberUids.isNotEmpty()) {
                            fetchPlacesByMembers(memberUids)
                        } else {
                            Log.d("CrewPointBottom", "크루 멤버가 없음")
                            updateRecyclerView(emptyList())
                        }
                    } else {
                        Log.d("CrewPointBottom", "크루 멤버 문서가 존재하지 않음")
                        updateRecyclerView(emptyList())
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("CrewPointBottom", "크루 멤버 정보 불러오기 실패", exception)
                    updateRecyclerView(emptyList())
                }
        } ?: run {
            Log.e("CrewPointBottom", "crewId가 null입니다")
            updateRecyclerView(emptyList())
        }
    }

    private fun fetchPlacesByMembers(memberUids: List<String>) {
        Log.d("CrewPointBottom", "멤버들의 장소 정보 가져오기 시작 - 멤버 수: ${memberUids.size}")

        val db = FirebaseFirestore.getInstance()
        val storage = com.google.firebase.storage.FirebaseStorage.getInstance()

        // whereIn 쿼리는 최대 10개까지만 지원하므로 청크로 나누어 처리
        val chunks = memberUids.chunked(10)
        val tempAllPlaces = mutableListOf<PlaceData>()
        var processedChunks = 0

        Log.d("CrewPointBottom", "청크 개수: ${chunks.size}")

        if (chunks.isEmpty()) {
            updateRecyclerView(emptyList())
            return
        }

        for ((index, chunk) in chunks.withIndex()) {
            Log.d("CrewPointBottom", "청크 $index 처리 중 - 멤버들: $chunk")

            db.collection("Places")
                .whereIn("addedBy", chunk)
                .get()
                .addOnSuccessListener { documents ->
                    for (doc in documents) {
                        Log.d("CrewPointBottom", "장소 데이터: ${doc.data}")
                    }

                    processChunkDocuments(documents, storage, tempAllPlaces, chunks.size) { updatedAllPlaces ->
                        processedChunks++
                        Log.d("CrewPointBottom", "청크 $index 처리 완료 - 현재까지 총 장소: ${updatedAllPlaces.size}")

                        if (processedChunks == chunks.size) {
                            Log.d("CrewPointBottom", "모든 청크 처리 완료 - 최종 장소 개수: ${updatedAllPlaces.size}")
                            allPlaces.clear()
                            allPlaces.addAll(updatedAllPlaces)

                            applySorting()
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("CrewPointBottom", "청크 $index 불러오기 실패", exception)
                    processedChunks++
                    if (processedChunks == chunks.size) {
                        allPlaces.clear()
                        allPlaces.addAll(tempAllPlaces)
                        applySorting()
                    }
                }
        }
    }

    private fun applySorting() {
        when (currentSortType) {
            SortType.DATE_DESC -> {
                val sortedList = allPlaces.sortedByDescending { it.placeId }
                updateRecyclerView(sortedList)
            }
            SortType.DISTANCE_ASC -> {
                if (myLatitude != null && myLongitude != null) {
                    val sortedList = allPlaces.sortedBy { place ->
                        calculateDistance(myLatitude!!, myLongitude!!, place.lat, place.lng)
                    }
                    updateRecyclerView(sortedList)
                } else {
                    updateRecyclerView(allPlaces)
                }
            }
        }
    }

    private fun processChunkDocuments(
        documents: com.google.firebase.firestore.QuerySnapshot,
        storage: com.google.firebase.storage.FirebaseStorage,
        allPlaces: MutableList<PlaceData>,
        totalChunks: Int,
        onChunkComplete: (MutableList<PlaceData>) -> Unit
    ) {
        val tempList = mutableListOf<PlaceData>()
        val finalList = mutableListOf<PlaceData>()
        var processedCount = 0

        Log.d("CrewPointBottom", "문서 처리 시작 - 문서 개수: ${documents.size()}")

        for (doc in documents) {
            val imageUrl = if (doc.getBoolean("myImg") == true)
                doc.getString("myImgUrl") ?: ""
            else
                doc.getString("baseImgUrl") ?: ""

            val name = doc.getString("name") ?: "이름 없음"
            val addedBy = doc.getString("addedBy") ?: ""
            val geoPoint = doc.getGeoPoint("geo")
            val placeId = doc.id
            val lat = geoPoint?.latitude ?: 0.0
            val lng = geoPoint?.longitude ?: 0.0

            val distanceText = if (geoPoint != null && myLatitude != null && myLongitude != null) {
                val distance = calculateDistance(
                    myLatitude!!, myLongitude!!,
                    geoPoint.latitude, geoPoint.longitude
                )
                "$distance km"
            } else {
                "거리 정보 없음"
            }

            val description = "추가한 유저: $addedBy\n거리: $distanceText"

            tempList.add(PlaceData(imageUrl, name, description, placeId, lat, lng))

            Log.d("CrewPointBottom", "장소 추가: $name (by: $addedBy, placeId: $placeId, lat: $lat, lng: $lng)")
        }

        if (tempList.isEmpty()) {
            Log.d("CrewPointBottom", "처리할 장소가 없음")
            onChunkComplete(allPlaces)
            return
        }

        for (place in tempList) {
            if (place.imageUrl.startsWith("gs://")) {
                val ref = storage.getReferenceFromUrl(place.imageUrl)
                ref.downloadUrl
                    .addOnSuccessListener { uri ->
                        finalList.add(PlaceData(uri.toString(), place.name, place.description, place.placeId, place.lat, place.lng))
                        processedCount++

                        if (processedCount == tempList.size) {
                            allPlaces.addAll(finalList)
                            onChunkComplete(allPlaces)
                        }
                    }
                    .addOnFailureListener { exception ->
                        Log.e("CrewPointBottom", "이미지 URL 변환 실패: ${place.imageUrl}", exception)
                        processedCount++
                        if (processedCount == tempList.size) {
                            allPlaces.addAll(finalList)
                            onChunkComplete(allPlaces)
                        }
                    }
            } else {
                finalList.add(place)
                processedCount++
                if (processedCount == tempList.size) {
                    allPlaces.addAll(finalList)
                    onChunkComplete(allPlaces)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.let {
            val bottomSheet = it.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                sheet.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                behavior.peekHeight = 800
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
                behavior.isDraggable = true
            }
        }
    }
}