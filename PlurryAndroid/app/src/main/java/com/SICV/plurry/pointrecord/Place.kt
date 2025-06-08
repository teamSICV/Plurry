package com.SICV.plurry.pointrecord

import com.google.firebase.firestore.GeoPoint

data class Place(
    val name : String = "",
    val imageUrl : String = "",
    val description : String = "",
    val geo : GeoPoint,
    val distanceKm : Double? = null,
    val imgUrl: String = "",
    val placeId: String = ""
)
