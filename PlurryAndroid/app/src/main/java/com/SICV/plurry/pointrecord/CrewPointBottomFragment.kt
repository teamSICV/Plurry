package com.SICV.plurry.pointrecord

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseAuth

import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resumeWithException

class CrewPointBottomFragment : BottomSheetDialogFragment() {

    override fun getTheme(): Int {
        return R.style.PopupTheme
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var btnDateArray: TextView
    private lateinit var btnDisArray: TextView

    private var myLatitude: Double? = null
    private var myLongitude: Double? = null
    private var crewId: String? = null

    // 전체 데이터와 현재 표시되는 데이터 분리
    private var allPlacesData = mutableListOf<PlaceData>()
    private var displayedPlaces = mutableListOf<PlaceData>()
    private lateinit var adapter: CrewPointBottomAdapter
    private val handler = Handler(Looper.getMainLooper())

    // 페이징 관련 변수
    private val pageSize = 9
    private var currentPage = 0
    private var isLoading = false
    private var hasMoreData = true

    // 배치 처리 관련
    private val batchSize = 8
    private val batchDelay = 300L
    private val maxPlacesToProcess = 50

    // 자동 업데이트 관련
    private var updateRunnable: Runnable? = null
    private val updateInterval = 20000L // 20초
    private var currentActivePlaceIds = mutableSetOf<String>()

    enum class SortType {
        DATE_DESC,
        DATE_ASC,
        DISTANCE_ASC,
        DISTANCE_DESC
    }

    data class VisitedPlaceDetails(
        val hasImageUrl: Boolean = false,
        val visitedImageUrl: String? = null,
        val calo: Double = 0.0,
        val distance: Double = 0.0,
        val stepNum: Long = 0L
    )

    private var currentSortType = SortType.DATE_DESC
    private val visitedPlaceInfo = mutableMapOf<String, VisitedPlaceDetails>()

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
        logMemoryUsage("Fragment 생성")
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
        logMemoryUsage("View 생성 완료")

        initViews(view)
        setupSortButtons()
        initLocationService()
    }

    private fun initViews(view: View) {
        recyclerView = view.findViewById(R.id.crewPointRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 3)

        btnDateArray = view.findViewById(R.id.BtnDateArray)
        btnDisArray = view.findViewById(R.id.BtnDisArray)

        adapter = CrewPointBottomAdapter(requireContext(), displayedPlaces)
        recyclerView.adapter = adapter

        // 스크롤 리스너 추가
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as GridLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && hasMoreData) {
                    if (visibleItemCount + firstVisibleItemPosition >= totalItemCount - 3) {
                        loadMoreData()
                    }
                }
            }
        })
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

    private fun startAutoUpdate() {
        updateRunnable = object : Runnable {
            override fun run() {
                Log.d("CrewPointBottom", "자동 업데이트 실행")
                checkAndUpdateCrewPlaces(false) // 자동 업데이트는 깜빡임 방지
                handler.postDelayed(this, updateInterval)
            }
        }
        handler.postDelayed(updateRunnable!!, updateInterval)
    }

    private fun stopAutoUpdate() {
        updateRunnable?.let {
            handler.removeCallbacks(it)
            updateRunnable = null
        }
        Log.d("CrewPointBottom", "자동 업데이트 중지")
    }

    private fun initLocationService() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    myLatitude = location.latitude
                    myLongitude = location.longitude
                    Log.d("CrewPointBottom", "위치 정보 획득: $myLatitude, $myLongitude")
                } else {
                    Log.d("CrewPointBottom", "위치 정보를 가져올 수 없음")
                }
                // 초기 로드
                checkAndUpdateCrewPlaces(true)
            }.addOnFailureListener { exception ->
                Log.e("CrewPointBottom", "위치 정보 가져오기 실패", exception)
                checkAndUpdateCrewPlaces(true)
            }
        } else {
            Log.d("CrewPointBottom", "위치 권한 없음")
            checkAndUpdateCrewPlaces(true)
        }
    }

    private fun sortPlacesByDate() {
        currentSortType = when (currentSortType) {
            SortType.DATE_DESC -> SortType.DATE_ASC
            SortType.DATE_ASC -> SortType.DATE_DESC
            else -> SortType.DATE_DESC
        }

        updateSortButtonUI()
        applySortingAndReset()
        Log.d("CrewPointBottom", "날짜순 정렬 완료 - ${if (currentSortType == SortType.DATE_DESC) "최신순" else "오래된순"}")
    }

    private fun sortPlacesByDistance() {
        if (myLatitude == null || myLongitude == null) {
            Toast.makeText(requireContext(), "위치 정보가 없어 거리순 정렬을 할 수 없습니다", Toast.LENGTH_SHORT).show()
            return
        }

        currentSortType = when (currentSortType) {
            SortType.DISTANCE_ASC -> SortType.DISTANCE_DESC
            SortType.DISTANCE_DESC -> SortType.DISTANCE_ASC
            else -> SortType.DISTANCE_ASC
        }

        updateSortButtonUI()
        applySortingAndReset()
        Log.d("CrewPointBottom", "거리순 정렬 완료 - ${if (currentSortType == SortType.DISTANCE_ASC) "가까운 순" else "먼 순"}")
    }

    private fun updateSortButtonUI() {
        when (currentSortType) {
            SortType.DATE_DESC -> {
                btnDateArray.isSelected = true
                btnDisArray.isSelected = false
                btnDateArray.text = "최신순"
                btnDateArray.setTextColor(resources.getColor(R.color.black, null))
                btnDisArray.text = "거리순"
                btnDisArray.setTextColor(resources.getColor(R.color.gray, null))
            }
            SortType.DATE_ASC -> {
                btnDateArray.isSelected = true
                btnDisArray.isSelected = false
                btnDateArray.text = "오래된순"
                btnDateArray.setTextColor(resources.getColor(R.color.black, null))
                btnDisArray.text = "거리순"
                btnDisArray.setTextColor(resources.getColor(R.color.gray, null))
            }
            SortType.DISTANCE_ASC -> {
                btnDateArray.isSelected = false
                btnDisArray.isSelected = true
                btnDateArray.text = "최신순"
                btnDateArray.setTextColor(resources.getColor(R.color.gray, null))
                btnDisArray.text = "가까운 순"
                btnDisArray.setTextColor(resources.getColor(R.color.black, null))
            }
            SortType.DISTANCE_DESC -> {
                btnDateArray.isSelected = false
                btnDisArray.isSelected = true
                btnDateArray.text = "최신순"
                btnDateArray.setTextColor(resources.getColor(R.color.gray, null))
                btnDisArray.text = "먼 순"
                btnDisArray.setTextColor(resources.getColor(R.color.black, null))
            }
        }
    }

    private fun applySortingAndReset() {
        // 전체 데이터 정렬
        allPlacesData = when (currentSortType) {
            SortType.DATE_DESC -> allPlacesData.sortedByDescending { it.imageTime ?: 0L }.toMutableList()
            SortType.DATE_ASC -> allPlacesData.sortedBy { it.imageTime ?: Long.MAX_VALUE }.toMutableList()
            SortType.DISTANCE_ASC -> {
                if (myLatitude != null && myLongitude != null) {
                    allPlacesData.sortedBy { place ->
                        calculateDistance(myLatitude!!, myLongitude!!, place.lat, place.lng)
                    }.toMutableList()
                } else {
                    allPlacesData.sortedByDescending { it.imageTime ?: 0L }.toMutableList()
                }
            }
            SortType.DISTANCE_DESC -> {
                if (myLatitude != null && myLongitude != null) {
                    allPlacesData.sortedByDescending { place ->
                        calculateDistance(myLatitude!!, myLongitude!!, place.lat, place.lng)
                    }.toMutableList()
                } else {
                    allPlacesData.sortedByDescending { it.imageTime ?: 0L }.toMutableList()
                }
            }
        }

        // 페이징 리셋
        currentPage = 0
        displayedPlaces.clear()
        hasMoreData = allPlacesData.isNotEmpty()
        adapter.notifyDataSetChanged()

        // 첫 페이지 로드
        loadMoreData()
        logMemoryUsage("정렬 및 리셋 완료")
    }

    private fun loadMoreData() {
        if (isLoading || !hasMoreData) return

        isLoading = true

        lifecycleScope.launch {
            try {
                val startIndex = currentPage * pageSize
                val endIndex = minOf(startIndex + pageSize, allPlacesData.size)

                if (startIndex < allPlacesData.size) {
                    val newItems = allPlacesData.subList(startIndex, endIndex)

                    // 이미지 URL 처리 (지연 로딩)
                    val processedItems = processImageUrls(newItems)

                    withContext(Dispatchers.Main) {
                        val insertPosition = displayedPlaces.size
                        displayedPlaces.addAll(processedItems)
                        adapter.notifyItemRangeInserted(insertPosition, processedItems.size)

                        currentPage++
                        hasMoreData = endIndex < allPlacesData.size

                        Log.d("CrewPointBottom", "페이지 $currentPage 로드 완료 - 추가된 아이템: ${processedItems.size}개")
                        logMemoryUsage("페이지 로드 완료")
                    }
                } else {
                    hasMoreData = false
                }
            } catch (e: Exception) {
                Log.e("CrewPointBottom", "페이지 로드 중 오류", e)
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun processImageUrls(items: List<PlaceData>): List<PlaceData> {
        return withContext(Dispatchers.IO) {
            val storage = com.google.firebase.storage.FirebaseStorage.getInstance()

            items.map { place ->
                try {
                    val finalImageUrl = if (place.imageUrl.startsWith("gs://")) {
                        try {
                            val ref = storage.getReferenceFromUrl(place.imageUrl)
                            ref.downloadUrl.await().toString()
                        } catch (e: Exception) {
                            Log.e("CrewPointBottom", "이미지 URL 변환 실패: ${place.imageUrl}", e)
                            place.imageUrl
                        }
                    } else {
                        place.imageUrl
                    }

                    place.copy(imageUrl = finalImageUrl)
                } catch (e: Exception) {
                    Log.e("CrewPointBottom", "이미지 처리 중 오류", e)
                    place
                }
            }
        }
    }

    private fun checkAndUpdateCrewPlaces(isInitialLoad: Boolean) {
        if (crewId == null) {
            Log.e("CrewPointBottom", "crewId가 null입니다")
            return
        }

        loadVisitedPlaces()

        Log.d("CrewPointBottom", "${if (isInitialLoad) "초기" else "자동"} 업데이트 시작")
        logMemoryUsage("업데이트 시작 전")

        val db = FirebaseFirestore.getInstance()

        // 크루 멤버 목록 가져오기
        db.collection("Crew").document(crewId!!)
            .collection("member").document("members")
            .get()
            .addOnSuccessListener { membersDoc ->
                if (!membersDoc.exists()) {
                    Log.d("CrewPointBottom", "크루 멤버 문서가 없습니다")
                    return@addOnSuccessListener
                }

                // 필드명이 uid인 모든 멤버 추출
                val memberUids = membersDoc.data?.keys?.toList() ?: emptyList()

                if (memberUids.isEmpty()) {
                    Log.d("CrewPointBottom", "크루 멤버가 없습니다")
                    return@addOnSuccessListener
                }

                Log.d("CrewPointBottom", "크루 멤버 ${memberUids.size}명 확인: $memberUids")

                // 크루원들이 추가한 장소 찾기 (최대 10개씩 배치 처리)
                val allCrewPlaces = mutableSetOf<String>()
                val batches = memberUids.chunked(10) // Firestore whereIn은 최대 10개

                var processedBatches = 0

                for (batch in batches) {
                    db.collection("Places")
                        .whereIn("addedBy", batch)
                        .get()
                        .addOnSuccessListener { placesSnapshot ->
                            val batchPlaceIds = placesSnapshot.documents.map { it.id }
                            allCrewPlaces.addAll(batchPlaceIds)

                            processedBatches++
                            Log.d("CrewPointBottom", "배치 $processedBatches/${batches.size} 완료: ${batchPlaceIds.size}개 장소")

                            // 모든 배치 처리 완료
                            if (processedBatches == batches.size) {
                                Log.d("CrewPointBottom", "크루원이 추가한 총 장소: ${allCrewPlaces.size}개")
                                checkAndSyncCrewPlace(allCrewPlaces, db, isInitialLoad)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("CrewPointBottom", "Places 조회 실패 (배치 $processedBatches)", e)
                            processedBatches++

                            if (processedBatches == batches.size) {
                                checkAndSyncCrewPlace(allCrewPlaces, db, isInitialLoad)
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewPointBottom", "크루 멤버 정보 조회 실패", e)
            }
    }

    // crewPlace 동기화 함수
    private fun checkAndSyncCrewPlace(crewMemberPlaceIds: Set<String>, db: FirebaseFirestore, isInitialLoad: Boolean) {
        // crewPlace에 등록된 장소 확인
        db.collection("Crew").document(crewId!!).collection("crewPlace")
            .get()
            .addOnSuccessListener { crewPlaceSnapshot ->
                val registeredPlaceIds = mutableSetOf<String>()
                val activePlaceIds = mutableSetOf<String>()

                for (doc in crewPlaceSnapshot.documents) {
                    val placeId = doc.id
                    registeredPlaceIds.add(placeId)

                    val isActive = doc.getBoolean(placeId) ?: false
                    if (isActive) {
                        activePlaceIds.add(placeId)
                    }
                }

                Log.d("CrewPointBottom", "crewPlace 등록: ${registeredPlaceIds.size}개, 활성: ${activePlaceIds.size}개")

                // 새로 추가해야 할 장소 찾기
                val newPlacesToAdd = crewMemberPlaceIds - registeredPlaceIds

                if (newPlacesToAdd.isNotEmpty()) {
                    Log.d("CrewPointBottom", "새로 추가할 장소: ${newPlacesToAdd.size}개")

                    // 새 장소 crewPlace에 추가
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val batch = db.batch()
                            val crewPlaceRef = db.collection("Crew").document(crewId!!).collection("crewPlace")

                            for (placeId in newPlacesToAdd) {
                                val placeDoc = db.collection("Places").document(placeId).get().await()

                                val imageTime = if (placeDoc.exists()) {
                                    try {
                                        when (val imageTimeField = placeDoc.get("imageTime")) {
                                            is Long -> imageTimeField
                                            is Double -> imageTimeField.toLong()
                                            is String -> imageTimeField.toLongOrNull() ?: System.currentTimeMillis()
                                            is com.google.firebase.Timestamp -> imageTimeField.toDate().time
                                            else -> System.currentTimeMillis()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("CrewPointBottom", "imageTime 파싱 실패: $placeId", e)
                                        System.currentTimeMillis()
                                    }
                                } else {
                                    Log.w("CrewPointBottom", "장소 문서 없음: $placeId")
                                    System.currentTimeMillis()
                                }

                                val docRef = crewPlaceRef.document(placeId)
                                batch.set(docRef, mapOf(
                                    placeId to true,
                                    "imageTime" to imageTime
                                ))

                                activePlaceIds.add(placeId)
                            }

                            withContext(Dispatchers.Main) {
                                batch.commit()
                                    .addOnSuccessListener {
                                        Log.d("CrewPointBottom", "crewPlace에 ${newPlacesToAdd.size}개 장소 추가 완료")
                                        processActivePlaces(activePlaceIds, db, isInitialLoad)
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("CrewPointBottom", "crewPlace 추가 실패", e)
                                        processActivePlaces(activePlaceIds, db, isInitialLoad)
                                    }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Log.e("CrewPointBottom", "장소 정보 가져오기 실패", e)
                                processActivePlaces(activePlaceIds, db, isInitialLoad)
                            }
                        }
                    }
                } else {
                    Log.d("CrewPointBottom", "새로 추가할 장소 없음")
                    processActivePlaces(activePlaceIds, db, isInitialLoad)
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewPointBottom", "crewPlace 조회 실패", e)
            }
    }

    // 활성 장소 처리 로직
    private fun processActivePlaces(activePlaceIds: Set<String>, db: FirebaseFirestore, isInitialLoad: Boolean) {
        Log.d("CrewPointBottom", "활성 장소 확인 - 이전: ${currentActivePlaceIds.size}개, 현재: ${activePlaceIds.size}개")

        val hasChanges = currentActivePlaceIds != activePlaceIds

        if (isInitialLoad || hasChanges) {
            if (hasChanges) {
                val newPlaces = activePlaceIds - currentActivePlaceIds
                val removedPlaces = currentActivePlaceIds - activePlaceIds
                Log.d("CrewPointBottom", "변경 감지 - 추가: ${newPlaces.size}개, 제거: ${removedPlaces.size}개")
            }

            currentActivePlaceIds = activePlaceIds.toMutableSet()
            loadAllCrewPlaces(activePlaceIds, db, isInitialLoad)
        } else {
            Log.d("CrewPointBottom", "변경사항 없음 - 업데이트 스킵")
        }
    }

    private fun loadVisitedPlaces() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            visitedPlaceInfo.clear()
            return
        }

        val db = FirebaseFirestore.getInstance()
        db.collection("Users").document(currentUser.uid).collection("visitedPlaces")
            .get()
            .addOnSuccessListener { querySnapshot ->
                visitedPlaceInfo.clear()
                for (document in querySnapshot.documents) {
                    val placeId = document.id
                    val imageUrlFromDoc = document.getString("imageUrl")
                    val caloFromDoc = document.getDouble("calo") ?: 0.0
                    val distanceFromDoc = document.getDouble("distance") ?: 0.0
                    val stepNumFromDoc = document.getLong("stepNum") ?: 0L

                    visitedPlaceInfo[placeId] = VisitedPlaceDetails(
                        hasImageUrl = !imageUrlFromDoc.isNullOrEmpty(),
                        visitedImageUrl = imageUrlFromDoc,
                        calo = caloFromDoc,
                        distance = distanceFromDoc,
                        stepNum = stepNumFromDoc
                    )
                }
                Log.d("CrewPointBottom", "방문한 장소 로드 완료: ${visitedPlaceInfo.size}개")
            }
            .addOnFailureListener { e ->
                Log.e("CrewPointBottom", "방문 기록 로드 실패", e)
            }
    }

    private fun loadAllCrewPlaces(activePlaceIds: Set<String>, db: FirebaseFirestore, isInitialLoad: Boolean) {
        if (activePlaceIds.isEmpty()) {
            Log.d("CrewPointBottom", "활성화된 장소가 없음")
            allPlacesData.clear()
            displayedPlaces.clear()
            if (::adapter.isInitialized) {
                adapter.notifyDataSetChanged()
            }
            return
        }

        Log.d("CrewPointBottom", "장소 상세 정보 로드 시작 - ${activePlaceIds.size}개")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val tempAllPlaces = mutableListOf<PlaceData>()
                val placeIdsList = activePlaceIds.toList()

                // 배치 처리
                val batches = placeIdsList.chunked(batchSize)

                for ((batchIndex, batch) in batches.withIndex()) {
                    // 메모리 체크
                    if (!checkMemoryAndGC()) {
                        Log.w("CrewPointBottom", "메모리 부족으로 처리 중단 - 배치 $batchIndex")
                        break
                    }

                    Log.d("CrewPointBottom", "배치 ${batchIndex + 1}/${batches.size} 처리 중")

                    try {
                        for (placeId in batch) {
                            val placeDoc = db.collection("Places").document(placeId).get().await()

                            if (placeDoc.exists()) {
                                val imageUrl = if (placeDoc.getBoolean("myImg") == true)
                                    placeDoc.getString("myImgUrl") ?: ""
                                else
                                    placeDoc.getString("baseImgUrl") ?: ""

                                val name = placeDoc.getString("name") ?: "이름 없음"
                                val addedBy = placeDoc.getString("addedBy") ?: ""
                                val geoPoint = placeDoc.getGeoPoint("geo")
                                val lat = geoPoint?.latitude ?: 0.0
                                val lng = geoPoint?.longitude ?: 0.0

                                val imageTime = try {
                                    when (val imageTimeField = placeDoc.get("imageTime")) {
                                        is Long -> imageTimeField
                                        is Double -> imageTimeField.toLong()
                                        is String -> imageTimeField.toLongOrNull() ?: 0L
                                        is com.google.firebase.Timestamp -> imageTimeField.toDate().time
                                        else -> 0L
                                    }
                                } catch (e: Exception) {
                                    0L
                                }

                                val distanceText = if (geoPoint != null && myLatitude != null && myLongitude != null) {
                                    val distance = calculateDistance(
                                        myLatitude!!, myLongitude!!,
                                        geoPoint.latitude, geoPoint.longitude
                                    )
                                    "${distance}km"
                                } else {
                                    "거리 정보 없음"
                                }

                                val description = "추가한 유저: $addedBy\n거리: $distanceText"

                                if (imageUrl.isNotEmpty()) {
                                    val visitedDetails = visitedPlaceInfo[placeId]
                                    val isVisited = visitedDetails?.hasImageUrl ?: false
                                    val visitedImageUrl = visitedDetails?.visitedImageUrl
                                    val calo = visitedDetails?.calo ?: 0.0
                                    val distance = visitedDetails?.distance ?: 0.0
                                    val stepNum = visitedDetails?.stepNum ?: 0L

                                    tempAllPlaces.add(PlaceData(
                                        imageUrl,
                                        name,
                                        description,
                                        placeId,
                                        lat,
                                        lng,
                                        imageTime,
                                        isVisited,
                                        visitedImageUrl,
                                        calo,
                                        distance,
                                        stepNum
                                    ))
                                }
                            }
                        }

                        // 배치 간 딜레이
                        if (batchIndex < batches.size - 1) {
                            delay(batchDelay)
                        }

                    } catch (e: Exception) {
                        Log.e("CrewPointBottom", "배치 $batchIndex 처리 중 오류", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    allPlacesData = tempAllPlaces
                    Log.d("CrewPointBottom", "데이터 로드 완료 - 총 ${allPlacesData.size}개")
                    logMemoryUsage("데이터 로드 완료")

                    // 화면 깜빡임 방지: 초기 로드가 아니면 부드러운 업데이트
                    if (isInitialLoad) {
                        applySortingAndReset()
                    } else {
                        updateDisplaySmoothly()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("CrewPointBottom", "데이터 로드 중 전체 오류", e)
                }
            }
        }
    }

    private fun updateDisplaySmoothly() {
        // 정렬만 적용하고 현재 스크롤 위치 유지
        allPlacesData = when (currentSortType) {
            SortType.DATE_DESC -> allPlacesData.sortedByDescending { it.imageTime ?: 0L }.toMutableList()
            SortType.DATE_ASC -> allPlacesData.sortedBy { it.imageTime ?: Long.MAX_VALUE }.toMutableList()
            SortType.DISTANCE_ASC -> {
                if (myLatitude != null && myLongitude != null) {
                    allPlacesData.sortedBy { place ->
                        calculateDistance(myLatitude!!, myLongitude!!, place.lat, place.lng)
                    }.toMutableList()
                } else {
                    allPlacesData.sortedByDescending { it.imageTime ?: 0L }.toMutableList()
                }
            }
            SortType.DISTANCE_DESC -> {
                if (myLatitude != null && myLongitude != null) {
                    allPlacesData.sortedByDescending { place ->
                        calculateDistance(myLatitude!!, myLongitude!!, place.lat, place.lng)
                    }.toMutableList()
                } else {
                    allPlacesData.sortedByDescending { it.imageTime ?: 0L }.toMutableList()
                }
            }
        }

        // 현재 표시된 아이템 수만큼 다시 로드 (스크롤 위치 유지)
        val currentDisplayCount = displayedPlaces.size
        val newDisplayCount = minOf(currentDisplayCount, allPlacesData.size)

        if (newDisplayCount > 0) {
            lifecycleScope.launch {
                val newItems = processImageUrls(allPlacesData.take(newDisplayCount))
                displayedPlaces.clear()
                displayedPlaces.addAll(newItems)
                adapter.notifyDataSetChanged()

                currentPage = (newDisplayCount + pageSize - 1) / pageSize
                hasMoreData = newDisplayCount < allPlacesData.size

                Log.d("CrewPointBottom", "부드러운 업데이트 완료 - ${displayedPlaces.size}개 표시")
            }
        }
    }

    private fun checkMemoryAndGC(): Boolean {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory * 100 / maxMemory).toInt()

        Log.d("CrewPointBottom", "메모리 사용률: $memoryUsagePercent%")

        when {
            memoryUsagePercent > 90 -> {
                Log.e("CrewPointBottom", "메모리 위험 수준 (${memoryUsagePercent}%) - 처리 중단")
                return false
            }
            memoryUsagePercent > 80 -> {
                Log.w("CrewPointBottom", "메모리 사용률 높음 (${memoryUsagePercent}%) - GC 실행")
                System.gc()
                Thread.sleep(100)
                return true
            }
            else -> return true
        }
    }

    private fun logMemoryUsage(tag: String) {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val percentUsed = (usedMemory * 100 / maxMemory).toInt()

        Log.d("CrewPointBottom", "$tag - 메모리: $percentUsed% (${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)")
    }

    // Task를 코루틴으로 변환하는 확장 함수
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            addOnCompleteListener { task ->
                if (task.exception != null) {
                    cont.resumeWithException(task.exception!!)
                } else {
                    cont.resume(task.result, null)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheetDialog = dialog as BottomSheetDialog
        bottomSheetDialog.behavior.apply {
            peekHeight = 800
            state = BottomSheetBehavior.STATE_COLLAPSED
            isDraggable = true
        }
        Log.d("CrewPointBottom", "Fragment onStart")
    }

    override fun onResume() {
        super.onResume()
        startAutoUpdate()
        Log.d("CrewPointBottom", "화면 진입 - 자동 업데이트 시작 (20초 간격)")
    }

    override fun onPause() {
        super.onPause()
        stopAutoUpdate()
        Log.d("CrewPointBottom", "화면 이탈 - 자동 업데이트 중지")
    }

    override fun onStop() {
        super.onStop()
        Log.d("CrewPointBottom", "Fragment onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopAutoUpdate()

        // 메모리 정리
        allPlacesData.clear()
        displayedPlaces.clear()
        currentActivePlaceIds.clear()

        logMemoryUsage("Fragment 종료")
        Log.d("CrewPointBottom", "Fragment onDestroyView - 메모리 정리 완료")
    }
}