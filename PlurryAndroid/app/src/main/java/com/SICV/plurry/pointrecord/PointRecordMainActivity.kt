package com.SICV.plurry.pointrecord

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.SICV.plurry.R
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resumeWithException
import kotlin.math.*
import android.media.MediaPlayer
import android.widget.ImageView

class PointRecordMainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var googleMap: GoogleMap
    private lateinit var showBtn: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var visitedPlacesLoader: VisitedPlacesLoader
    private var currentUserLocation: LatLng? = null

    // 배경 음악을 위한 미디어 플레이어
    private var mediaPlayer: MediaPlayer? = null

    // 크루 포인트 관련 변수
    private lateinit var crewPointLayout: View
    private lateinit var crewRecyclerView: RecyclerView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_point_record_main)

        // --- 음악 시작: 배경 음악 재생을 초기화하고 연속 재생 시작 ---
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.signrecord)
            mediaPlayer?.isLooping = true
            mediaPlayer?.setVolume(1.0f, 1.0f)
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e("PointRecord", "미디어 플레이어 초기화 또는 시작 오류: ${e.message}")
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.myPlaceMap) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        showBtn = findViewById<Button>(R.id.showCrewBottomBtn)
        showBtn.visibility = View.GONE

        // 크루 포인트 레이아웃 초기화
        initCrewPointViews()

        checkCrewMembership()
    }

    private fun initCrewPointViews() {
        crewPointLayout = findViewById(R.id.crewPointLayout)
        crewRecyclerView = findViewById(R.id.crewPointRecyclerView)
        btnDateArray = findViewById(R.id.BtnDateArray)
        btnDisArray = findViewById(R.id.BtnDisArray)

        // 초기에는 숨김
        //crewPointLayout.visibility = View.GONE

        // RecyclerView 설정
        crewRecyclerView.layoutManager = GridLayoutManager(this, 3)
        adapter = CrewPointBottomAdapter(this, displayedPlaces, crewId ?: "")
        crewRecyclerView.adapter = adapter

        // 스크롤 리스너 추가
        crewRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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

        // 정렬 버튼 설정
        setupSortButtons()
    }

    private fun setupSortButtons() {
        val BtnQuit = findViewById<ImageView>(R.id.b_quit)
        BtnQuit.setOnClickListener {
            finish()
        }

        btnDateArray.setOnClickListener {
            sortPlacesByDate()
        }

        btnDisArray.setOnClickListener {
            sortPlacesByDistance()
        }
        updateSortButtonUI()
    }

    private fun sortPlacesByDate() {
        currentSortType = when (currentSortType) {
            SortType.DATE_DESC -> SortType.DATE_ASC
            else -> SortType.DATE_DESC
        }
        updateSortButtonUI()
        applySortingAndReset()
    }

    private fun sortPlacesByDistance() {
        currentSortType = when (currentSortType) {
            SortType.DISTANCE_ASC -> SortType.DISTANCE_DESC
            else -> SortType.DISTANCE_ASC
        }
        updateSortButtonUI()
        applySortingAndReset()
    }

    private fun updateSortButtonUI() {
        btnDateArray.text = when (currentSortType) {
            SortType.DATE_DESC -> "최신순 ▼"
            SortType.DATE_ASC -> "최신순 ▲"
            else -> "최신순"
        }

        btnDisArray.text = when (currentSortType) {
            SortType.DISTANCE_ASC -> "거리순 ▲"
            SortType.DISTANCE_DESC -> "거리순 ▼"
            else -> "거리순"
        }
    }

    private fun applySortingAndReset() {
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

        currentPage = 0
        displayedPlaces.clear()
        adapter.notifyDataSetChanged()  // 이 부분을 추가!
        hasMoreData = true

        loadMoreData()
    }

    private fun loadMoreData() {
        if (isLoading || !hasMoreData) return

        isLoading = true
        val startIndex = currentPage * pageSize
        val endIndex = minOf(startIndex + pageSize, allPlacesData.size)

        if (startIndex >= allPlacesData.size) {
            hasMoreData = false
            isLoading = false
            return
        }

        lifecycleScope.launch {
            try {
                val newItems = processImageUrls(allPlacesData.subList(startIndex, endIndex))

                displayedPlaces.addAll(newItems)
                adapter.notifyDataSetChanged()  // 이 부분을 변경!

                currentPage++
                hasMoreData = endIndex < allPlacesData.size

//                LogLS.d("페이지 $currentPage 로드 완료 - ${displayedPlaces.size}/${allPlacesData.size}")
            } catch (e: Exception) {
                Log.e("PointRecord", "데이터 로드 실패", e)
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun processImageUrls(places: List<PlaceData>): List<PlaceData> = withContext(Dispatchers.Default) {
        places.map { place ->
            try {
                if (!checkMemoryAndGC()) {
                    LogLS.w("메모리 부족으로 이미지 처리 건너뛰기")
                    return@map place
                }

                place
            } catch (e: Exception) {
                LogLS.e("이미지 처리 오류 : ${e}")
                place
            }
        }
    }

    private fun startAutoUpdate() {
        updateRunnable = object : Runnable {
            override fun run() {
//                LogLS.d("자동 업데이트 실행")
                checkAndUpdateCrewPlaces(false)
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
//        LogLS.d("자동 업데이트 중지")
    }

    // --- Activity 라이프사이클 메서드에 음악 재생/정지 로직 추가 ---
    override fun onResume() {
        super.onResume()
        if (mediaPlayer?.isPlaying == false) {
            mediaPlayer?.start()
        }
        startAutoUpdate()
//        LogLS.d("화면 진입 - 자동 업데이트 시작 (20초 간격)")
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
        stopAutoUpdate()
//        LogLS.d("화면 이탈 - 자동 업데이트 중지")
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        stopAutoUpdate()
        allPlacesData.clear()
        displayedPlaces.clear()
        currentActivePlaceIds.clear()

        logMemoryUsage("Activity 종료")
    }

    private fun checkCrewMembership() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
//            LogLS.d("사용자가 로그인하지 않음")
            return
        }

        val uid = currentUser.uid
        db.collection("Users").document(uid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val crewAtValue = document.get("crewAt")
                    Log.d("PointRecord", "크루 멤버십 확인 결과 - crewAt: $crewAtValue")

                    if (crewAtValue != null) {
                        crewId = crewAtValue.toString()
                        // adapter에 crewId 업데이트
                        adapter = CrewPointBottomAdapter(this, displayedPlaces, crewId ?: "")
                        crewRecyclerView.adapter = adapter
                        showCrewFeatures()
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("PointRecord", "크루 멤버십 확인 실패", exception)
                Toast.makeText(this, "크루 정보를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCrewFeatures() {
        crewPointLayout.visibility = View.VISIBLE

        // 위치 정보 가져오기
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    myLatitude = location.latitude
                    myLongitude = location.longitude
                    Log.d("PointRecord", "현재 위치: $myLatitude, $myLongitude")

                    // 크루 장소 데이터 로드
                    loadCrewPlaces()
                }
            }
        } else {
            loadCrewPlaces()
        }
    }

    private fun loadCrewPlaces() {
        crewId?.let { crewId ->
            checkAndUpdateCrewPlaces(true)
        }
    }

    private fun checkAndUpdateCrewPlaces(isInitialLoad: Boolean) {
        crewId?.let { crewId ->
            loadVisitedPlaceInfo()
            loadCrewPlacesData(isInitialLoad)
        }
    }

    private fun loadVisitedPlaceInfo() {
        val currentUser = auth.currentUser ?: return
        val uid = currentUser.uid

        db.collection("VisitedPlaces")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { visitedDocuments ->
                visitedPlaceInfo.clear()

                for (visitedDoc in visitedDocuments) {
                    val placeId = visitedDoc.getString("placeId") ?: continue
                    val visitedImageUrl = visitedDoc.getString("visitedImageUrl")
                    val hasImageUrl = !visitedImageUrl.isNullOrEmpty()
                    val calo = visitedDoc.getDouble("calo") ?: 0.0
                    val distance = visitedDoc.getDouble("distance") ?: 0.0
                    val stepNum = visitedDoc.getLong("stepNum") ?: 0L

                    visitedPlaceInfo[placeId] = VisitedPlaceDetails(
                        hasImageUrl = hasImageUrl,
                        visitedImageUrl = visitedImageUrl,
                        calo = calo,
                        distance = distance,
                        stepNum = stepNum
                    )
                }

                Log.d("PointRecord", "방문 정보 로드 완료 - ${visitedPlaceInfo.size}개")
            }
            .addOnFailureListener { e ->
                Log.e("PointRecord", "방문 정보 로드 실패", e)
            }
    }

    private fun loadCrewPlacesData(isInitialLoad: Boolean) {
        val crewIdValue = crewId ?: return

//        LogLS.d("loadCrewPlacesData 시작 - crewId: '$crewIdValue'")

        // 크루 멤버 목록 가져오기
        db.collection("Crew").document(crewIdValue)
            .collection("member").document("members")
            .get()
            .addOnSuccessListener { membersDoc ->
                if (!membersDoc.exists()) {
                    LogLS.d("크루 멤버 문서가 없습니다")
                    return@addOnSuccessListener
                }

                // 필드명이 uid인 모든 멤버 추출
                val memberUids = membersDoc.data?.keys?.toList() ?: emptyList()

                if (memberUids.isEmpty()) {
                    LogLS.d("크루 멤버가 없습니다")
                    return@addOnSuccessListener
                }

//                LogLS.d("크루 멤버 ${memberUids.size}명 확인")

                // 크루원들이 추가한 장소 찾기
                val allCrewPlaces = mutableSetOf<String>()
                val batches = memberUids.chunked(10)

                var processedBatches = 0

                for (batch in batches) {
                    db.collection("Places")
                        .whereIn("addedBy", batch)
                        .get()
                        .addOnSuccessListener { placesSnapshot ->
                            val batchPlaceIds = placesSnapshot.documents.map { it.id }
                            allCrewPlaces.addAll(batchPlaceIds)

                            processedBatches++
//                            LogLS.d("배치 $processedBatches/${batches.size} 완료: ${batchPlaceIds.size}개 장소")

                            if (processedBatches == batches.size) {
//                                LogLS.d("크루원이 추가한 총 장소: ${allCrewPlaces.size}개")
                                checkAndSyncCrewPlace(allCrewPlaces, isInitialLoad)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("PointRecord", "Places 조회 실패", e)
                            processedBatches++
                            if (processedBatches == batches.size) {
                                checkAndSyncCrewPlace(allCrewPlaces, isInitialLoad)
                            }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("PointRecord", "크루 멤버 정보 조회 실패", e)
            }
    }

    private fun checkAndSyncCrewPlace(crewMemberPlaceIds: Set<String>, isInitialLoad: Boolean) {
        val crewIdValue = crewId ?: return

        db.collection("Crew").document(crewIdValue).collection("crewPlace")
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

//                LogLS.d("crewPlace 등록: ${registeredPlaceIds.size}개, 활성화: ${activePlaceIds.size}개")

                val newPlaces = crewMemberPlaceIds - registeredPlaceIds
                if (newPlaces.isNotEmpty()) {
//                    LogLS.d("crewPlace에 ${newPlaces.size}개 장소 추가 중")
                    for (placeId in newPlaces) {
                        db.collection("Crew").document(crewIdValue)
                            .collection("crewPlace").document(placeId)
                            .set(mapOf(placeId to true))
                        activePlaceIds.add(placeId)
                    }
                }

                if (activePlaceIds.isNotEmpty()) {
                    loadActivePlacesData(activePlaceIds, isInitialLoad)
                } else {
                    LogLS.d("활성화된 크루 장소가 없습니다")
                }
            }
            .addOnFailureListener { e ->
                Log.e("PointRecord", "crewPlace 조회 실패", e)
            }
    }

    private fun loadActivePlacesData(activePlaceIds: Set<String>, isInitialLoad: Boolean) {
        lifecycleScope.launch {
            try {
                val tempAllPlaces = mutableListOf<PlaceData>()
                val batches = activePlaceIds.chunked(10)

//                LogLS.d("활성화된 장소 ${activePlaceIds.size}개 로드 시작")

                for ((batchIndex, batch) in batches.withIndex()) {
                    try {
                        if (!checkMemoryAndGC()) {
                            Log.w("PointRecord", "메모리 부족으로 처리 중단")
                            break
                        }

                        val placesSnapshot = db.collection("Places")
                            .whereIn(com.google.firebase.firestore.FieldPath.documentId(), batch)
                            .get()
                            .await()

                        for (placeDoc in placesSnapshot.documents) {
                            try {
                                val placeId = placeDoc.id
                                val name = placeDoc.getString("name") ?: "이름 없음"
                                val imageUrl = placeDoc.getString("myImgUrl") ?: ""
                                val geoPoint = placeDoc.getGeoPoint("geo")
                                val addedBy = placeDoc.getString("addedBy") ?: "알 수 없음"

                                val lat = geoPoint?.latitude ?: 0.0
                                val lng = geoPoint?.longitude ?: 0.0

                                val imageTime = try {
                                    val imageTimeField = placeDoc.get("imageTime")
                                    when (imageTimeField) {
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
                            } catch (e: Exception) {
                                Log.e("PointRecord", "장소 처리 중 오류", e)
                            }
                        }

                        if (batchIndex < batches.size - 1) {
                            delay(batchDelay)
                        }

                    } catch (e: Exception) {
                        Log.e("PointRecord", "배치 $batchIndex 처리 중 오류", e)
                    }
                }

                withContext(Dispatchers.Main) {
                    allPlacesData = tempAllPlaces
//                    LogLS.d("데이터 로드 완료 - 총 ${allPlacesData.size}개")
                    logMemoryUsage("데이터 로드 완료")

                    if (isInitialLoad) {
                        applySortingAndReset()
                    } else {
                        updateDisplaySmoothly()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("PointRecord", "데이터 로드 중 전체 오류", e)
                }
            }
        }
    }

    private fun updateDisplaySmoothly() {
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

                Log.d("PointRecord", "부드러운 업데이트 완료 - ${displayedPlaces.size}개 표시")
            }
        }
    }

    private fun checkMemoryAndGC(): Boolean {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory * 100 / maxMemory).toInt()

        Log.d("PointRecord", "메모리 사용률: $memoryUsagePercent%")

        return when {
            memoryUsagePercent > 90 -> {
                Log.e("PointRecord", "메모리 위험 수준 (${memoryUsagePercent}%) - 처리 중단")
                false
            }
            memoryUsagePercent > 80 -> {
                Log.w("PointRecord", "메모리 사용률 높음 (${memoryUsagePercent}%) - GC 실행")
                System.gc()
                Thread.sleep(100)
                true
            }
            else -> true
        }
    }

    private fun logMemoryUsage(tag: String) {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val percentUsed = (usedMemory * 100 / maxMemory).toInt()

        Log.d("PointRecord", "$tag - 메모리: $percentUsed% (${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)")
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

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        visitedPlacesLoader = VisitedPlacesLoader(googleMap, auth, db, currentUserLocation)

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            googleMap.isMyLocationEnabled = true
            getLastKnownLocation()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }

        googleMap.setOnMarkerClickListener { marker ->
            val tag = marker.tag as? PlaceDataMap
            if (tag != null) {
                showPointRecordDialog(tag.imageUrl, tag.name, tag.description, tag.placeId, tag.lat, tag.lng, tag.isVisited)
            }
            true
        }
    }

    private fun getLastKnownLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener(this, OnSuccessListener<Location> { location ->
                    if (location != null) {
                        val userLocation = LatLng(location.latitude, location.longitude)
                        currentUserLocation = userLocation
                        myLatitude = location.latitude
                        myLongitude = location.longitude
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15f))

                        visitedPlacesLoader = VisitedPlacesLoader(googleMap, auth, db, currentUserLocation)

                        loadUserPlaces()
                        loadVisitedPlaces()
                    }
                })
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }

    private fun loadUserPlaces() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d("PointRecord", "사용자가 로그인하지 않음")
            return
        }

        val uid = currentUser.uid
        Log.d("PointRecord", "사용자 장소 로드 시작 - UID: $uid")

        db.collection("Places")
            .whereEqualTo("addedBy", uid)
            .get()
            .addOnSuccessListener { documents ->
                Log.d("PointRecord", "사용자 장소 ${documents.size()}개 로드 완료")

                for (document in documents) {
                    val name = document.getString("name") ?: "이름 없음"
                    val myImgUrl = document.getString("myImgUrl") ?: ""
                    val geoPoint = document.getGeoPoint("geo")
                    val placeId = document.id

                    if (geoPoint != null) {
                        val latitude = geoPoint.latitude
                        val longitude = geoPoint.longitude
                        val position = LatLng(latitude, longitude)

                        val distance = calculateDistance(currentUserLocation, position)

                        val description = buildString {
                            append("추가한 유저: $uid\n")
                            append("거리: ${String.format("%.2f", distance)}km")
                        }

                        val marker = googleMap.addMarker(
                            MarkerOptions()
                                .position(position)
                                .title(name)
                                .snippet(description)
                        )

                        marker?.tag = PlaceDataMap(myImgUrl, name, description, placeId, latitude, longitude)

                        Log.d("PointRecord", "마커 추가: $name at ($latitude, $longitude), 거리: ${distance}km")
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("PointRecord", "사용자 장소 로드 실패", exception)
                Toast.makeText(this, "장소 정보를 불러오는데 실패했습니다.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadVisitedPlaces() {
        visitedPlacesLoader.loadVisitedPlaces()
    }

    private fun calculateDistance(from: LatLng?, to: LatLng): Double {
        if (from == null) return 0.0

        val earthRadius = 6371.0

        val dLat = Math.toRadians(to.latitude - from.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) *
                sin(dLng / 2) * sin(dLng / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadius * c
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

    private fun showPointRecordDialog(imageUrl: String, name: String, description: String, placeId: String, lat: Double, lng: Double, isVisited: Boolean = false) {
        val dialog = PointRecordDialog.newInstance(imageUrl, name, description, placeId, lat, lng, "", isVisited)
        dialog.show(supportFragmentManager, "PointRecordDialog")
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getLastKnownLocation()
            } else {
                Toast.makeText(this, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 지도 마커용 데이터 클래스
    data class PlaceDataMap(
        val imageUrl: String,
        val name: String,
        val description: String,
        val placeId: String = "",
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val isVisited: Boolean = false
    )

    // RecyclerView용 데이터 클래스
    data class PlaceData(
        val imageUrl: String,
        val name: String,
        val description: String,
        val placeId: String = "",
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val imageTime: Long? = null,
        val isVisited: Boolean = false,
        val visitedImageUrl: String? = null,
        val calo: Double = 0.0,
        val distance: Double = 0.0,
        val stepNum: Long = 0L
    )
}