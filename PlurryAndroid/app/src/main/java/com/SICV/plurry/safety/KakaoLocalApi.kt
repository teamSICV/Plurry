package com.SICV.plurry.safety

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

// 카카오 응답 모델(필요한 필드만)
data class KakaoPlace(
    val id: String,
    val place_name: String,
    val category_group_code: String?,
    val category_name: String?,
    val phone: String?,
    val x: String, // lon
    val y: String  // lat
)

data class KakaoSearchResponse(
    val documents: List<KakaoPlace>
)

interface KakaoLocalApi {
    // 카테고리로 장소 검색 (반경 m)
    // 예: 편의점 CS2, 카페 CE7, 약국 PM9, 병원 HP8, 지하철 SW8
    @GET("/v2/local/search/category.json")
    suspend fun searchByCategory(
        @Header("Authorization") auth: String, // "KakaoAK {REST_API_KEY}"
        @Query("category_group_code") categoryGroupCode: String,
        @Query("x") lon: Double,
        @Query("y") lat: Double,
        @Query("radius") radiusMeters: Int = 500,
        @Query("size") size: Int = 15
    ): KakaoSearchResponse

    // 키워드 검색 (버스정류장 등 카테고리로 안되는 항목에)
    @GET("/v2/local/search/keyword.json")
    suspend fun searchByKeyword(
        @Header("Authorization") auth: String,
        @Query("query") query: String,
        @Query("x") lon: Double,
        @Query("y") lat: Double,
        @Query("radius") radiusMeters: Int = 500,
        @Query("size") size: Int = 15
    ): KakaoSearchResponse
}
