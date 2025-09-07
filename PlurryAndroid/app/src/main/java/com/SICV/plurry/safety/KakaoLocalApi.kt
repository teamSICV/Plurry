package com.SICV.plurry.safety

import com.SICV.plurry.safety.model.KakaoCategoryResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface KakaoLocalApi {
    @GET("/v2/local/search/category.json")
    suspend fun searchCategory(
        @Header("Authorization") auth: String,
        @Query("category_group_code") code: String,
        @Query("x") x: String,
        @Query("y") y: String,
        @Query("radius") radius: Int
    ): Response<KakaoCategoryResponse>
}

