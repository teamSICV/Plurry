package com.SICV.plurry.pointrecord

import android.util.Log
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

class VisitedPlacesLoader(
    private val googleMap: GoogleMap,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore,
    private val currentUserLocation: LatLng?
) {

    fun loadVisitedPlaces() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d("VisitedPlacesLoader", "사용자가 로그인하지 않음")
            return
        }

        val uid = currentUser.uid
        Log.d("VisitedPlacesLoader", "탐색한 장소 로드 시작 - UID: $uid")

        db.collection("Users").document(uid).collection("visitedPlaces")
            .get()
            .addOnSuccessListener { visitedDocuments ->
                Log.d("VisitedPlacesLoader", "탐색한 장소 ${visitedDocuments.size()}개 발견")

                for (visitedDoc in visitedDocuments) {
                    val placeId = visitedDoc.id
                    val visitTimestamp = try {
                        val timestampField = visitedDoc.get("timestamp")
                        Log.d("VisitedPlacesLoader", "timestamp 필드 타입: ${timestampField?.javaClass?.simpleName}")

                        when (timestampField) {
                            is Long -> timestampField
                            is Double -> timestampField.toLong()
                            is String -> timestampField.toLongOrNull() ?: 0L
                            is com.google.firebase.Timestamp -> {
                                val timestampValue = timestampField.toDate().time
                                Log.d("VisitedPlacesLoader", "Timestamp를 Long으로 변환: $timestampValue")
                                db.collection("Users").document(uid).collection("visitedPlaces").document(placeId)
                                    .update("timestamp", timestampValue)
                                    .addOnSuccessListener {
                                        Log.d("VisitedPlacesLoader", "visitedPlaces timestamp을 Timestamp에서 Long으로 변환 완료: $timestampValue")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("VisitedPlacesLoader", "visitedPlaces timestamp 변환 업데이트 실패", e)
                                    }
                                timestampValue
                            }
                            null -> {
                                Log.d("VisitedPlacesLoader", "timestamp 필드가 null")
                                0L
                            }
                            else -> {
                                Log.w("VisitedPlacesLoader", "알 수 없는 timestamp 타입: ${timestampField.javaClass.simpleName}")
                                0L
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("VisitedPlacesLoader", "visitedPlaces timestamp 처리 중 오류: ${e.message}", e)
                        0L
                    }

                    Log.d("VisitedPlacesLoader", "탐색한 장소 ID: $placeId, 탐색 시간: $visitTimestamp")

                    db.collection("Places").document(placeId)
                        .get()
                        .addOnSuccessListener { placeDocument ->
                            if (placeDocument.exists()) {
                                val name = placeDocument.getString("name") ?: "이름 없음"
                                val myImgUrl = placeDocument.getString("myImgUrl") ?: ""
                                val geoPoint = placeDocument.getGeoPoint("geo")
                                val addedBy = placeDocument.getString("addedBy") ?: ""

                                if (geoPoint != null) {
                                    val latitude = geoPoint.latitude
                                    val longitude = geoPoint.longitude
                                    val position = LatLng(latitude, longitude)

                                    val distance = calculateDistance(currentUserLocation, position)
                                    val formattedVisitTime = formatTimestamp(visitTimestamp)

                                    getUserName(addedBy) { addedByName ->
                                        val description = buildString {
                                            append("추가한 유저: $addedByName\n")
                                            append("거리: ${String.format("%.2f", distance)}km\n")
                                            append("탐색 시간: $formattedVisitTime")
                                        }

                                        val marker = googleMap.addMarker(
                                            MarkerOptions()
                                                .position(position)
                                                .title(name)
                                                .snippet(description)
                                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                                        )

                                        marker?.tag = PointRecordMainActivity.PlaceData(
                                            imageUrl = myImgUrl,
                                            name = name,
                                            description = description,
                                            placeId = placeId,
                                            lat = latitude,
                                            lng = longitude
                                        ).copy(isVisited = true)

                                        Log.d("VisitedPlacesLoader", "탐색한 장소 마커 추가: $name at ($latitude, $longitude), 거리: ${distance}km")
                                    }
                                }
                            } else {
                                Log.d("VisitedPlacesLoader", "Places 문서가 존재하지 않음: $placeId")
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("VisitedPlacesLoader", "Places 문서 로드 실패: $placeId", exception)
                        }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("VisitedPlacesLoader", "탐색한 장소 로드 실패", exception)
            }
    }

    private fun getUserName(userId: String, callback: (String) -> Unit) {
        if (userId.isEmpty()) {
            callback("알 수 없음")
            return
        }

        db.collection("Users").document(userId)
            .get()
            .addOnSuccessListener { userDocument ->
                val userName = if (userDocument.exists()) {
                    userDocument.getString("name") ?: userId
                } else {
                    userId
                }
                callback(userName)
            }
            .addOnFailureListener { exception ->
                Log.e("VisitedPlacesLoader", "사용자 이름 가져오기 실패: $userId", exception)
                callback(userId)
            }
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

    private fun formatTimestamp(timestamp: Long): String {
        return try {
            val date = Date(timestamp)
            val format = SimpleDateFormat("yyyy년 MM월 dd일", Locale.KOREAN)
            format.format(date)
        } catch (e: Exception) {
            Log.e("VisitedPlacesLoader", "타임스탬프 포맷 실패", e)
            "시간 정보 없음"
        }
    }

    fun isPlaceVisited(placeId: String, callback: (Boolean) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            callback(false)
            return
        }

        val uid = currentUser.uid
        db.collection("Users").document(uid).collection("visitedPlaces").document(placeId)
            .get()
            .addOnSuccessListener { document ->
                callback(document.exists())
            }
            .addOnFailureListener { exception ->
                Log.e("VisitedPlacesLoader", "탐색 여부 확인 실패: $placeId", exception)
                callback(false)
            }
    }
}