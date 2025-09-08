package com.SICV.plurry.safety

import com.SICV.plurry.safety.model.KakaoCategoryResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface KakaoLocalApi {

    // 편의점 등 카테고리 검색
    @GET("v2/local/search/category.json")
    suspend fun searchCategory(
        @Header("Authorization") auth: String,      // "KakaoAK xxx"
        @Query("category_group_code") code: String, // 예: "CS2" (편의점)
        @Query("x") x: Double,                      // lon
        @Query("y") y: Double,                      // lat
        @Query("radius") radius: Int = 150,         // m
        @Query("sort") sort: String = "distance",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 15
    ): KakaoCategoryResponse

    // 버스정류장 등 키워드 검색
    @GET("v2/local/search/keyword.json")
    suspend fun searchKeyword(
        @Header("Authorization") auth: String,      // "KakaoAK xxx"
        @Query("query") query: String,              // 예: "버스정류장"
        @Query("x") x: Double,                      // lon
        @Query("y") y: Double,                      // lat
        @Query("radius") radius: Int = 150,
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 15,
        @Query("sort") sort: String = "distance"
    ): KakaoCategoryResponse
}



