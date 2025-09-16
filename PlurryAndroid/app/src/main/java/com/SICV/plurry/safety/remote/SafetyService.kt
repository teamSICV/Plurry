//firebase functions 호출용
package com.SICV.plurry.safety.remote
import retrofit2.http.GET
import retrofit2.http.Query

interface SafetyService {
    @GET("/")
    suspend fun getTileSummary(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius") radius: Int = 200
    ): TileSummaryDto
}

data class TileSummaryDto(
    val cctvCount: Int? = 0,
    val streetLightCount: Int? = 0,
    val score: Int? = 0,
    val level: String? = "CAUTION"
)

