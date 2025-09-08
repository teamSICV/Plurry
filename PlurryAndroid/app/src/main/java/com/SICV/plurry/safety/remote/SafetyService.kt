package com.SICV.plurry.safety.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface SafetyService {
    @GET("getTileSummary")
    suspend fun getTileSummary(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Int = 150
    ): TileSummary
}

data class TileSummary(
    val gridId: String,
    val cctvCount: Int,
    val streetLightLevel: Int?,
    val updatedAt: Long
)

