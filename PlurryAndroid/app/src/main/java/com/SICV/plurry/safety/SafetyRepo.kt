package com.SICV.plurry.safety

import com.SICV.plurry.safety.remote.SafetyService
import com.SICV.plurry.safety.remote.TileSummary
import com.SICV.plurry.safety.KakaoLocalApi
import com.SICV.plurry.safety.model.KakaoCategoryResponse

class SafetyRepo(
    private val kakao: KakaoLocalApi,
    private val safetyService: SafetyService,
    private val kakaoApiKey: String
) {
    suspend fun getSafety(lat: Double, lon: Double): Detail {
        // 1) 카카오 API 호출
        val convRes = kakao.searchCategory(
            auth = kakaoApiKey,  // "KakaoAK xxxx"
            code = "CS2",
            x = lon, y = lat
        )
        val busRes = kakao.searchKeyword(
            auth = kakaoApiKey,
            query = "버스정류장",
            x = lon, y = lat
        )

// 2) 개수 계산 (nullable 안전 처리)
        val convCount = convRes.documents?.size ?: 0
        val busStopCount = busRes.documents?.size ?: 0

// 3) Functions 요약
        val summary = safetyService.getTileSummary(lat, lon)
        val cctv  = summary.cctvCount
        val light = summary.streetLightLevel ?: 0


        val score = (convCount * 5) + (busStopCount * 3) + (cctv * 10) + (light * 15)

        val level = when {
            score >= 70 -> Level.SAFE
            score >= 40 -> Level.CAUTION
            else -> Level.DANGER
        }

        return Detail(
            score = score,
            level = level,
            convCount = convCount,
            busStopCount = busStopCount,
            cctvCount = cctv,
            streetLightLevel = light,
            reasons = listOf("편의점 $convCount", "버스정류장 $busStopCount", "CCTV $cctv", "가로등 $light")
        )
    }

    // 세부 결과 담을 데이터 모델
    data class Detail(
        val score: Int,
        val level: Level,
        val convCount: Int,
        val busStopCount: Int,
        val cctvCount: Int,
        val streetLightLevel: Int?,
        val reasons: List<String>
    )

    enum class Level { SAFE, CAUTION, DANGER }
}
