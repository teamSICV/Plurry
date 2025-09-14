package com.SICV.plurry.crewstep

import android.content.Context
import android.os.Looper
import android.util.Log
import android.widget.ImageButton
import android.widget.LinearLayout
import com.SICV.plurry.R
import com.SICV.plurry.pointrecord.PointRecordDialog
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import androidx.fragment.app.FragmentManager
import android.os.Handler

class CrewRecentPlaceManager(
    private val context: Context,
    private val fragmentManager: FragmentManager
) {

    data class PlaceInfo(
        val placeId: String,
        val imageUrl: String,
        val name: String,
        val addedBy: String,
        val lat: Double,
        val lng: Double,
        val imageTime: Long
    )

    interface PlaceLoadCallback {
        fun onPlacesLoaded(places: List<PlaceInfo>)
        fun onError(error: Exception)
    }

    private var currentPlaces = mutableListOf<PlaceInfo>()
    private var lastUpdateTime = 0L
    private var currentCrewId: String = ""

    fun loadCrewPlacesInOrder(
        crewId: String,
        db: FirebaseFirestore,
        container: LinearLayout,
        myLatitude: Double?,
        myLongitude: Double?,
        limit: Int = 10
    ) {
        Log.d("CrewRecentPlaceManager", "크루 장소 초기 로드 시작 - crewId: $crewId")
        currentCrewId = crewId

        loadAllPlaces(crewId, db, container, myLatitude, myLongitude, limit, true)
    }

    fun refreshCrewPlaces(
        crewId: String,
        db: FirebaseFirestore,
        container: LinearLayout,
        myLatitude: Double?,
        myLongitude: Double?,
        limit: Int = 10
    ) {
        Log.d("CrewRecentPlaceManager", "크루 장소 새로고침 확인 - crewId: $crewId")
        currentCrewId = crewId

        db.collection("Crew").document(crewId).collection("crewPlace").get()
            .addOnSuccessListener { querySnapshot ->
                val activePlaceIds = mutableSetOf<String>()

                for (doc in querySnapshot.documents) {
                    val placeId = doc.id
                    val isActive = doc.getBoolean(placeId) ?: false

                    if (isActive) {
                        activePlaceIds.add(placeId)
                    }
                }

                val currentPlaceIds = currentPlaces.map { it.placeId }.toSet()
                val newPlaceIds = activePlaceIds - currentPlaceIds
                val removedPlaceIds = currentPlaceIds - activePlaceIds

                if (newPlaceIds.isNotEmpty() || removedPlaceIds.isNotEmpty()) {
                    Log.d("CrewRecentPlaceManager", "장소 변경 감지 - 새로운 장소: ${newPlaceIds.size}개, 제거된 장소: ${removedPlaceIds.size}개")

                    if (newPlaceIds.isNotEmpty()) {
                        Log.d("CrewRecentPlaceManager", "새로운 장소들: $newPlaceIds")
                    }
                    if (removedPlaceIds.isNotEmpty()) {
                        Log.d("CrewRecentPlaceManager", "제거된 장소들: $removedPlaceIds")
                    }

                    loadAllPlaces(crewId, db, container, myLatitude, myLongitude, limit, false)
                } else {
                    Log.d("CrewRecentPlaceManager", "장소 변경사항 없음 - 업데이트 스킵")
                }
            }
            .addOnFailureListener { e ->
                Log.e("CrewRecentPlaceManager", "crewPlace 새로고침 확인 실패", e)
            }
    }

    private fun loadAllPlaces(
        crewId: String,
        db: FirebaseFirestore,
        container: LinearLayout,
        myLatitude: Double?,
        myLongitude: Double?,
        limit: Int,
        isInitialLoad: Boolean
    ) {
        val logPrefix = if (isInitialLoad) "초기 로드" else "변경사항 감지로 인한 재로드"
        Log.d("CrewRecentPlaceManager", "$logPrefix - 활성화된 장소들 가져오기")

        db.collection("Crew").document(crewId).collection("crewPlace")
            .limit(50)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val activePlaceIds = mutableListOf<String>()

                for (doc in querySnapshot.documents) {
                    val placeId = doc.id
                    val isActive = doc.getBoolean(placeId) ?: false

                    if (isActive) {
                        activePlaceIds.add(placeId)
                    }
                }

                if (activePlaceIds.isEmpty()) {
                    Log.d("CrewRecentPlaceManager", "활성화된 장소가 없음")
                    currentPlaces.clear()
                    container.removeAllViews()
                    return@addOnSuccessListener
                }

                Log.d("CrewRecentPlaceManager", "총 ${activePlaceIds.size}개 활성 장소 발견")

                //모든 활성 장소의 상세 정보를 가져온 후 정렬
                fetchAllPlaceDetailsAndSort(activePlaceIds, db, container, myLatitude, myLongitude, limit, isInitialLoad)

            }
            .addOnFailureListener { e ->
                Log.e("CrewRecentPlaceManager", "crewPlace 컬렉션 가져오기 실패", e)
            }
    }



    private fun fetchPlaceDetails(
        placeIds: List<String>,
        db: FirebaseFirestore,
        container: LinearLayout,
        myLatitude: Double?,
        myLongitude: Double?,
        limit: Int,
        isInitialLoad: Boolean
    ) {
        val placeInfoList = mutableListOf<PlaceInfo>()

        val batchSize = 8
        val batchDelay = 300L
        val batches = placeIds.chunked(batchSize)

        val logPrefix = if (isInitialLoad) "초기 로드" else "업데이트"
        Log.d("CrewRecentPlaceManager", "$logPrefix - 장소 상세 정보 가져오기 시작 - 총 ${placeIds.size}개 (${batches.size}개 배치)")

        // 메모리 상태 체크
        logMemoryUsage("배치 처리 시작 전")

        processBatchesWithMemoryCheck(batches, 0, placeInfoList, db, container, myLatitude, myLongitude, limit, isInitialLoad, batchDelay)
    }

    private fun fetchAllPlaceDetailsAndSort(
        placeIds: List<String>,
        db: FirebaseFirestore,
        container: LinearLayout,
        myLatitude: Double?,
        myLongitude: Double?,
        limit: Int,
        isInitialLoad: Boolean
    ) {
        val allPlaceInfoList = mutableListOf<PlaceInfo>()
        var processedCount = 0

        val safeLimit = minOf(placeIds.size, 20) // 메모리 부족 시 최대 20개만 처리
        val safePlaceIds = placeIds.take(safeLimit)

        for (placeId in safePlaceIds) {
            db.collection("Places").document(placeId).get()
                .addOnSuccessListener { placeDoc ->
                    processedCount++

                    if (placeDoc.exists()) {
                        val imageUrl = placeDoc.getString("myImgUrl") ?: ""
                        val placeName = placeDoc.getString("name") ?: "장소 이름 없음"
                        val addedBy = placeDoc.getString("addedBy") ?: "알 수 없음"
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

                        if (imageUrl.isNotEmpty()) {
                            val placeInfo = PlaceInfo(
                                placeId = placeId,
                                imageUrl = imageUrl,
                                name = placeName,
                                addedBy = addedBy,
                                lat = lat,
                                lng = lng,
                                imageTime = imageTime
                            )
                            allPlaceInfoList.add(placeInfo)
                        }
                    }

                    // 모든 처리 완료 시 정렬하여 표시
                    if (processedCount == safePlaceIds.size) {
                        val latestPlaces = allPlaceInfoList
                            .filter { it.imageTime > 0L }
                            .sortedByDescending { it.imageTime }
                            .take(limit)

                        displayFinalPlaces(latestPlaces, container, myLatitude, myLongitude, isInitialLoad)
                    }
                }
                .addOnFailureListener { e ->
                    processedCount++
                    if (processedCount == safePlaceIds.size) {
                        val latestPlaces = allPlaceInfoList
                            .filter { it.imageTime > 0L }
                            .sortedByDescending { it.imageTime }
                            .take(limit)
                        displayFinalPlaces(latestPlaces, container, myLatitude, myLongitude, isInitialLoad)
                    }
                }
        }
    }

    private fun displayFinalPlaces(
        latestPlaces: List<PlaceInfo>,
        container: LinearLayout,
        myLatitude: Double?,
        myLongitude: Double?,
        isInitialLoad: Boolean
    ) {
        currentPlaces.clear()
        currentPlaces.addAll(latestPlaces)
        lastUpdateTime = System.currentTimeMillis()

        val existingPlaceIds = mutableSetOf<String>()
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val placeId = child.contentDescription?.toString()?.removePrefix("visited_place_")
            if (placeId != null) {
                existingPlaceIds.add(placeId)
            }
        }

        val newPlaceIds = latestPlaces.map { it.placeId }.toSet()

        for (i in container.childCount - 1 downTo 0) {
            val child = container.getChildAt(i)
            val placeId = child.contentDescription?.toString()?.removePrefix("visited_place_")
            if (placeId != null && !newPlaceIds.contains(placeId)) {
                container.removeViewAt(i)
            }
        }

        for (placeInfo in latestPlaces) {
            if (!existingPlaceIds.contains(placeInfo.placeId)) {
                addPlaceImageToContainer(placeInfo, container, myLatitude, myLongitude)
            }
        }

        val logPrefix = if (isInitialLoad) "초기 로드" else "업데이트"
        Log.d("CrewRecentPlaceManager", "$logPrefix - UI 업데이트 완료: ${latestPlaces.size}개 장소")
    }

    private fun processBatches(
        batches: List<List<String>>,
        currentBatchIndex: Int,
        placeInfoList: MutableList<PlaceInfo>,
        db: FirebaseFirestore,
        container: LinearLayout,
        myLatitude: Double?,
        myLongitude: Double?,
        limit: Int,
        isInitialLoad: Boolean
    ) {
        if (currentBatchIndex >= batches.size) {
            displaySortedPlaces(placeInfoList, container, myLatitude, myLongitude, limit, isInitialLoad)
            return
        }

        val currentBatch = batches[currentBatchIndex]
        var processedInBatch = 0

        Log.d("CrewRecentPlaceManager", "배치 ${currentBatchIndex + 1}/${batches.size} 처리 시작 (${currentBatch.size}개)")

        for (placeId in currentBatch) {
            db.collection("Places").document(placeId).get()
                .addOnSuccessListener { placeDoc ->
                    processedInBatch++

                    if (placeDoc.exists()) {
                        val imageUrl = placeDoc.getString("myImgUrl") ?: ""
                        val placeName = placeDoc.getString("name") ?: "장소 이름 없음"
                        val addedBy = placeDoc.getString("addedBy") ?: "알 수 없음"
                        val geoPoint = placeDoc.getGeoPoint("geo")
                        val lat = geoPoint?.latitude ?: 0.0
                        val lng = geoPoint?.longitude ?: 0.0
                        val imageTime = placeDoc.getLong("imageTime") ?: 0L

                        if (imageUrl.isNotEmpty()) {
                            val placeInfo = PlaceInfo(
                                placeId = placeId,
                                imageUrl = imageUrl,
                                name = placeName,
                                addedBy = addedBy,
                                lat = lat,
                                lng = lng,
                                imageTime = imageTime
                            )
                            placeInfoList.add(placeInfo)
                        }
                    } else {
                        Log.w("CrewRecentPlaceManager", "장소 문서가 존재하지 않음: $placeId")
                    }

                    if (processedInBatch == currentBatch.size) {
                        Log.d("CrewRecentPlaceManager", "배치 ${currentBatchIndex + 1} 완료, 0.5초 후 다음 배치 처리")

                        Handler(Looper.getMainLooper()).postDelayed({
                            processBatches(
                                batches,
                                currentBatchIndex + 1,
                                placeInfoList,
                                db,
                                container,
                                myLatitude,
                                myLongitude,
                                limit,
                                isInitialLoad
                            )
                        }, 500)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CrewRecentPlaceManager", "Places/$placeId 문서 가져오기 실패", e)
                    processedInBatch++

                    if (processedInBatch == currentBatch.size) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            processBatches(
                                batches,
                                currentBatchIndex + 1,
                                placeInfoList,
                                db,
                                container,
                                myLatitude,
                                myLongitude,
                                limit,
                                isInitialLoad
                            )
                        }, 500)
                    }
                }
        }
    }

    private fun processBatchesWithMemoryCheck(
        batches: List<List<String>>,
        currentBatchIndex: Int,
        placeInfoList: MutableList<PlaceInfo>,
        db: FirebaseFirestore,
        container: LinearLayout,
        myLatitude: Double?,
        myLongitude: Double?,
        limit: Int,
        isInitialLoad: Boolean,
        batchDelay: Long
    ) {
        //메모리 사용률 체크
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val memoryUsagePercent = (usedMemory * 100 / maxMemory).toInt()

        // 메모리 사용률이 80% 넘으면 강제 가비지 컬렉션
        if (memoryUsagePercent > 80) {
            Log.w("CrewRecentPlaceManager", "메모리 사용률 높음 (${memoryUsagePercent}%) - 가비지 컬렉션 실행")
            System.gc()

            //가비지 컬렉션 후 잠시 대기
            Handler(Looper.getMainLooper()).postDelayed({
                processBatchesWithMemoryCheck(batches, currentBatchIndex, placeInfoList, db, container, myLatitude, myLongitude, limit, isInitialLoad, batchDelay)
            }, 1000)
            return
        }

        //메모리 사용률이 90% 넘으면 중단
        if (memoryUsagePercent > 90) {
            Log.e("CrewRecentPlaceManager", "메모리 사용률 위험 (${memoryUsagePercent}%) - 처리 중단")
            displaySortedPlaces(placeInfoList, container, myLatitude, myLongitude, limit, isInitialLoad)
            return
        }

        if (currentBatchIndex >= batches.size) {
            displaySortedPlaces(placeInfoList, container, myLatitude, myLongitude, limit, isInitialLoad)
            return
        }

        val currentBatch = batches[currentBatchIndex]
        var processedInBatch = 0

        Log.d("CrewRecentPlaceManager", "배치 ${currentBatchIndex + 1}/${batches.size} 처리 시작 (${currentBatch.size}개) - 메모리: ${memoryUsagePercent}%")

        for (placeId in currentBatch) {
            db.collection("Places").document(placeId).get()
                .addOnSuccessListener { placeDoc ->
                    processedInBatch++

                    if (placeDoc.exists()) {
                        val imageUrl = placeDoc.getString("myImgUrl") ?: ""
                        val placeName = placeDoc.getString("name") ?: "장소 이름 없음"
                        val addedBy = placeDoc.getString("addedBy") ?: "알 수 없음"
                        val geoPoint = placeDoc.getGeoPoint("geo")
                        val lat = geoPoint?.latitude ?: 0.0
                        val lng = geoPoint?.longitude ?: 0.0
                        val imageTime = placeDoc.getLong("imageTime") ?: 0L

                        if (imageUrl.isNotEmpty()) {
                            val placeInfo = PlaceInfo(
                                placeId = placeId,
                                imageUrl = imageUrl,
                                name = placeName,
                                addedBy = addedBy,
                                lat = lat,
                                lng = lng,
                                imageTime = imageTime
                            )
                            placeInfoList.add(placeInfo)
                        }
                    }

                    if (processedInBatch == currentBatch.size) {
                        Log.d("CrewRecentPlaceManager", "배치 ${currentBatchIndex + 1} 완료, ${batchDelay}ms 후 다음 배치 처리")

                        Handler(Looper.getMainLooper()).postDelayed({
                            processBatchesWithMemoryCheck(
                                batches,
                                currentBatchIndex + 1,
                                placeInfoList,
                                db,
                                container,
                                myLatitude,
                                myLongitude,
                                limit,
                                isInitialLoad,
                                batchDelay
                            )
                        }, batchDelay)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CrewRecentPlaceManager", "Places/$placeId 문서 가져오기 실패", e)
                    processedInBatch++

                    if (processedInBatch == currentBatch.size) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            processBatchesWithMemoryCheck(
                                batches,
                                currentBatchIndex + 1,
                                placeInfoList,
                                db,
                                container,
                                myLatitude,
                                myLongitude,
                                limit,
                                isInitialLoad,
                                batchDelay
                            )
                        }, batchDelay)
                    }
                }
        }
    }

    private fun logMemoryUsage(tag: String) {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val percentUsed = (usedMemory * 100 / maxMemory).toInt()

        Log.d("CrewRecentPlaceManager", "$tag - 메모리 사용률: $percentUsed% (${usedMemory / 1024 / 1024}MB / ${maxMemory / 1024 / 1024}MB)")
    }

    private fun displaySortedPlaces(
        placeInfoList: List<PlaceInfo>,
        container: LinearLayout,
        myLatitude: Double?,
        myLongitude: Double?,
        limit: Int,
        isInitialLoad: Boolean
    ) {
        //더 작은 limit 적용
        val actualLimit = minOf(limit, placeInfoList.size)
        val sortedPlaces = placeInfoList.sortedByDescending { it.imageTime }.take(actualLimit)

        val logPrefix = if (isInitialLoad) "초기 로드" else "업데이트"
        Log.d("CrewRecentPlaceManager", "$logPrefix - 정렬 완료 - 총 ${sortedPlaces.size}개 장소 (${placeInfoList.size}개 중 상위 $actualLimit)")

        logMemoryUsage("UI 업데이트 전")

        currentPlaces.clear()
        currentPlaces.addAll(sortedPlaces)
        lastUpdateTime = System.currentTimeMillis()

        container.removeAllViews()

        for (placeInfo in sortedPlaces) {
            addPlaceImageToContainer(placeInfo, container, myLatitude, myLongitude)
        }

        logMemoryUsage("UI 업데이트 완료")
        Log.d("CrewRecentPlaceManager", "$logPrefix - UI 업데이트 완료")
    }

    private fun addPlaceImageToContainer(
        placeInfo: PlaceInfo,
        container: LinearLayout,
        myLatitude: Double?,
        myLongitude: Double?
    ) {
        val imageButton = ImageButton(context)
        val layoutParams = LinearLayout.LayoutParams(
            (100 * context.resources.displayMetrics.density).toInt(),
            (120 * context.resources.displayMetrics.density).toInt()
        )
        layoutParams.setMargins(0, 0, 8, 0)
        imageButton.layoutParams = layoutParams
        imageButton.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        imageButton.background = null
        imageButton.contentDescription = "visited_place_${placeInfo.placeId}"

        val distanceText = if (myLatitude != null && myLongitude != null &&
            myLatitude != 0.0 && myLongitude != 0.0 &&
            placeInfo.lat != 0.0 && placeInfo.lng != 0.0) {
            val distance = calculateDistance(myLatitude, myLongitude, placeInfo.lat, placeInfo.lng)
            if (distance > 0) {
                String.format("%.2f", distance) + "km"
            } else {
                "위치 정보 확인 중"
            }
        } else {
            "위치 정보 확인 중"
        }

        val detailInfo = "추가한 유저: ${placeInfo.addedBy}\n거리: $distanceText"
        val displayName = "장소: ${placeInfo.name}"

        Glide.with(context)
            .load(placeInfo.imageUrl)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.basiccrewprofile)
            .override(150, 180)
            .into(imageButton)

        imageButton.setOnClickListener {
            showPlaceDetailDialog(
                placeInfo.imageUrl,
                displayName,
                detailInfo,
                placeInfo.placeId,
                placeInfo.lat,
                placeInfo.lng
            )
        }

        container.addView(imageButton)
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return earthRadius * c
    }

    private fun showPlaceDetailDialog(
        imageUrl: String,
        name: String,
        description: String,
        placeId: String,
        lat: Double,
        lng: Double
    ) {
        try {
            val dialog = PointRecordDialog.newInstance(imageUrl, name, description, placeId, lat, lng, currentCrewId)
            dialog.show(fragmentManager, "PlaceDetailDialog")
        } catch (e: Exception) {
            Log.e("CrewRecentPlaceManager", "팝업 다이얼로그 표시 오류", e)
        }
    }

    fun getCurrentPlaces(): List<PlaceInfo> {
        return currentPlaces.toList()
    }

    fun clearCache() {
        currentPlaces.clear()
        lastUpdateTime = 0L
        currentCrewId = ""
        Log.d("CrewRecentPlaceManager", "캐시 초기화 완료")
    }

    fun getCrewPlacesSorted(
        crewId: String,
        db: FirebaseFirestore,
        callback: PlaceLoadCallback
    ) {
        db.collection("Crew").document(crewId).collection("crewPlace").get()
            .addOnSuccessListener { querySnapshot ->
                val activePlaceIds = mutableListOf<String>()

                for (doc in querySnapshot.documents) {
                    val placeId = doc.id
                    val isActive = doc.getBoolean(placeId) ?: false

                    if (isActive) {
                        activePlaceIds.add(placeId)
                    }
                }

                if (activePlaceIds.isEmpty()) {
                    callback.onPlacesLoaded(emptyList())
                    return@addOnSuccessListener
                }

                fetchPlaceDetailsForCallback(activePlaceIds, db, callback)
            }
            .addOnFailureListener { e ->
                callback.onError(e)
            }
    }

    private fun fetchPlaceDetailsForCallback(
        placeIds: List<String>,
        db: FirebaseFirestore,
        callback: PlaceLoadCallback
    ) {
        val placeInfoList = mutableListOf<PlaceInfo>()
        var processedCount = 0

        for (placeId in placeIds) {
            db.collection("Places").document(placeId).get()
                .addOnSuccessListener { placeDoc ->
                    processedCount++

                    if (placeDoc.exists()) {
                        val imageUrl = placeDoc.getString("myImgUrl") ?: ""
                        val placeName = placeDoc.getString("name") ?: "장소 이름 없음"
                        val addedBy = placeDoc.getString("addedBy") ?: "알 수 없음"
                        val geoPoint = placeDoc.getGeoPoint("geo")
                        val lat = geoPoint?.latitude ?: 0.0
                        val lng = geoPoint?.longitude ?: 0.0
                        val imageTime = placeDoc.getLong("imageTime") ?: 0L

                        if (imageUrl.isNotEmpty()) {
                            val placeInfo = PlaceInfo(
                                placeId = placeId,
                                imageUrl = imageUrl,
                                name = placeName,
                                addedBy = addedBy,
                                lat = lat,
                                lng = lng,
                                imageTime = imageTime
                            )
                            placeInfoList.add(placeInfo)
                        }
                    }

                    if (processedCount == placeIds.size) {
                        val sortedPlaces = placeInfoList.sortedByDescending { it.imageTime }
                        callback.onPlacesLoaded(sortedPlaces)
                    }
                }
                .addOnFailureListener { e ->
                    processedCount++
                    if (processedCount == placeIds.size) {
                        val sortedPlaces = placeInfoList.sortedByDescending { it.imageTime }
                        callback.onPlacesLoaded(sortedPlaces)
                    }
                }
        }
    }
}