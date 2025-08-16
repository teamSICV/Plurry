package com.SICV.plurry.goingwalk

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth // 🚀 NEW: FirebaseAuth import 추가

// 🚀 MODIFIED: 마커 색상을 저장할 필드 추가
data class VisitedPlace(
    val placeName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val markerColor: Float = BitmapDescriptorFactory.HUE_AZURE // 기본값은 파란색
)

class VisitedPlacesRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance() // 🚀 NEW: FirebaseAuth 인스턴스 추가

    fun getVisitedPlaces(userId: String, onSuccess: (List<VisitedPlace>) -> Unit, onFailure: (Exception) -> Unit) {
        // 1. 사용자가 방문한 장소의 ID 목록을 가져옵니다.
        val visitedPlacesTask = firestore.collection("Users")
            .document(userId)
            .collection("visitedPlaces")
            .get()

        // 2. 모든 장소 목록을 가져옵니다.
        val allPlacesTask = firestore.collection("Places").get()

        // 두 개의 비동기 작업을 동시에 처리합니다.
        Tasks.whenAllSuccess<Any>(visitedPlacesTask, allPlacesTask)
            .addOnSuccessListener { results ->
                val visitedPlacesSnapshot = results[0] as com.google.firebase.firestore.QuerySnapshot
                val allPlacesSnapshot = results[1] as com.google.firebase.firestore.QuerySnapshot

                val visitedPlaceIds = visitedPlacesSnapshot.documents.map { it.id }.toSet()
                val places = mutableListOf<VisitedPlace>()

                for (placeDoc in allPlacesSnapshot.documents) {
                    val placeId = placeDoc.id
                    val geoPoint = placeDoc.getGeoPoint("geo")
                    val name = placeDoc.getString("name")
                    val addedBy = placeDoc.getString("addedBy")

                    // 🚀 MODIFIED: 방문했거나 내가 추가한 장소만 처리하도록 로직 변경
                    if (geoPoint != null && name != null) {
                        var markerColor: Float? = null

                        // 내가 추가한 장소인 경우 (빨간색)
                        if (addedBy == userId) {
                            markerColor = BitmapDescriptorFactory.HUE_RED
                        }
                        // 방문한 장소인 경우 (파란색)
                        else if (visitedPlaceIds.contains(placeId)) {
                            markerColor = BitmapDescriptorFactory.HUE_AZURE
                        }

                        // 마커를 표시해야 할 장소라면 리스트에 추가
                        if (markerColor != null) {
                            places.add(
                                VisitedPlace(
                                    placeName = name,
                                    latitude = geoPoint.latitude,
                                    longitude = geoPoint.longitude,
                                    markerColor = markerColor
                                )
                            )
                        }
                    }
                }
                onSuccess(places)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }
}
