package com.SICV.plurry.crewstep

import android.content.Context
import android.util.Log
import android.widget.ImageButton
import android.widget.LinearLayout
import com.SICV.plurry.R
import com.SICV.plurry.pointrecord.PointRecordDialog
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import androidx.fragment.app.FragmentManager

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
                    Log.d("CrewRecentPlaceManager", "활성화된 장소가 없음")
                    currentPlaces.clear()
                    container.removeAllViews()
                    return@addOnSuccessListener
                }

                fetchPlaceDetails(activePlaceIds, db, container, myLatitude, myLongitude, limit, isInitialLoad)

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
        var processedCount = 0

        val logPrefix = if (isInitialLoad) "초기 로드" else "업데이트"
        Log.d("CrewRecentPlaceManager", "$logPrefix - 장소 상세 정보 가져오기 시작 - 총 ${placeIds.size}개")

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

                            if (isInitialLoad) {
                                Log.d("CrewRecentPlaceManager", "장소 정보 추가: $placeName, imageTime: $imageTime")
                            }
                        }
                    } else {
                        Log.w("CrewRecentPlaceManager", "장소 문서가 존재하지 않음: $placeId")
                    }

                    if (processedCount == placeIds.size) {
                        displaySortedPlaces(placeInfoList, container, myLatitude, myLongitude, limit, isInitialLoad)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("CrewRecentPlaceManager", "Places/$placeId 문서 가져오기 실패", e)
                    processedCount++

                    if (processedCount == placeIds.size) {
                        displaySortedPlaces(placeInfoList, container, myLatitude, myLongitude, limit, isInitialLoad)
                    }
                }
        }
    }

    private fun displaySortedPlaces(
        placeInfoList: List<PlaceInfo>,
        container: LinearLayout,
        myLatitude: Double?,
        myLongitude: Double?,
        limit: Int,
        isInitialLoad: Boolean
    ) {
        val sortedPlaces = placeInfoList.sortedByDescending { it.imageTime }.take(limit)

        val logPrefix = if (isInitialLoad) "초기 로드" else "업데이트"
        Log.d("CrewRecentPlaceManager", "$logPrefix - 정렬 완료 - 총 ${sortedPlaces.size}개 장소")

        if (isInitialLoad) {
            sortedPlaces.forEachIndexed { index, place ->
                Log.d("CrewRecentPlaceManager", "$index: ${place.name} - imageTime: ${place.imageTime}")
            }
        }

        currentPlaces.clear()
        currentPlaces.addAll(sortedPlaces)
        lastUpdateTime = System.currentTimeMillis()

        container.removeAllViews()

        for (placeInfo in sortedPlaces) {
            addPlaceImageToContainer(placeInfo, container, myLatitude, myLongitude)
        }

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

        val distanceText = if (myLatitude != null && myLongitude != null) {
            val distance = calculateDistance(myLatitude, myLongitude, placeInfo.lat, placeInfo.lng)
            String.format("%.2f", distance) + "km"
        } else {
            "거리 계산 불가"
        }

        val detailInfo = "추가한 유저: ${placeInfo.addedBy}\n거리: $distanceText"
        val displayName = "장소: ${placeInfo.name}"

        Glide.with(context)
            .load(placeInfo.imageUrl)
            .placeholder(R.drawable.placeholder)
            .error(R.drawable.basiccrewprofile)
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