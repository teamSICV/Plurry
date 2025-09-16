//카카오맵 API 호츌용
package com.SICV.plurry.safety.remote

import com.SICV.plurry.safety.model.KakaoCategoryResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface KakaoLocalApi {
    @GET("v2/local/search/category.json")
    suspend fun categorySearch(
        @Header("Authorization") auth: String,
        @Query("category_group_code") code: String,
        @Query("y") lat: Double,
        @Query("x") lon: Double,
        @Query("radius") radius: Int = 200,
        @Query("sort") sort: String = "distance"
    ): KakaoCategoryResponse
}
