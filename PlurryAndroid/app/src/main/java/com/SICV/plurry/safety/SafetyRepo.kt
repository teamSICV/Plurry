package com.SICV.plurry.safety

import com.SICV.plurry.safety.model.KakaoCategoryResponse

object SafetyRepo {
    // x = 경도(lng), y = 위도(lat)
    suspend fun fetchCounts(lat: Double, lng: Double, radius: Int = 300): Pair<Int, Int> {
        val convRes = KakaoService.api.searchCategory(
            auth = KakaoService.authHeader(),
            code = "CS2", // 편의점
            x = lng.toString(),
            y = lat.toString(),
            radius = radius
        )

        val busRes = KakaoService.api.searchCategory(
            auth = KakaoService.authHeader(),
            code = "PK6", // 버스정류장
            x = lng.toString(),
            y = lat.toString(),
            radius = radius
        )

        val conv = if (convRes.isSuccessful) (convRes.body()?.documents?.size ?: 0) else 0
        val bus = if (busRes.isSuccessful) (busRes.body()?.documents?.size ?: 0) else 0

        return conv to bus
    }
}
