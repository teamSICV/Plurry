// 두 개 API 합쳐서 결과 반환용
package com.SICV.plurry.safety

import com.SICV.plurry.safety.model.SafetyDetail
import com.SICV.plurry.safety.remote.KakaoLocalApi
import com.SICV.plurry.safety.remote.SafetyService
import com.SICV.plurry.safety.remote.TileSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.SICV.plurry.BuildConfig
import android.util.Log

class SafetyRepo(
    private val kakao: KakaoLocalApi,
    private val safetyService: SafetyService,
) {
    private val kakaoApiKey: String by lazy { BuildConfig.KAKAO_REST_API_KEY }

    private fun authHeader() = "KakaoAK $kakaoApiKey"

    // Kakao 카테고리 코드
    private companion object {
        const val CODE_CONVENIENCE = "CS2" // 편의점
        const val CODE_PUBLIC   = "PO3"  // 공공기관
        const val CODE_SUBWAY     = "SW8"  // 지하철역
        const val RADIUS_M = 200

        // 가중치 정의 (안전도에 미치는 영향도 기준)
        const val WEIGHT_CCTV = 3.0          // CCTV: 가장 높은 가중치 (감시/억제 효과)
        const val WEIGHT_STREET_LIGHT = 2.5   // 가로등: 높은 가중치 (야간 시야 확보)
        const val WEIGHT_PUBLIC_OFFICE = 2.0  // 공공기관: 높은 가중치 (신뢰할 수 있는 피난처)
        const val WEIGHT_CONVENIENCE = 1.5    // 편의점: 중간 가중치 (24시간 운영, 사람 있음)
        const val WEIGHT_SUBWAY = 1.2        // 지하철: 낮은 가중치 (사람 많지만 혼잡)

        // 밀도 보정 계수 (200m 반경 기준)
        const val IDEAL_CCTV_COUNT = 8        // 이상적인 CCTV 개수
        const val IDEAL_LIGHT_COUNT = 15      // 이상적인 가로등 개수
        const val MAX_SCORE = 100            // 최대 점수
    }

    suspend fun getSafety(lat: Double, lon: Double): SafetyDetail = withContext(Dispatchers.IO) {
        try {
            val auth = authHeader() // ✅ 자체 메서드 사용

            // 1️⃣ 카카오 API 호출들
            val conv = runCatching {
                kakao.categorySearch(auth, CODE_CONVENIENCE, lat, lon, RADIUS_M)
                    .documents?.size ?: 0
            }.getOrElse {
                Log.e("SafetyRepo", "편의점 조회 실패: $it")
                0
            }

            val public = runCatching {
                kakao.categorySearch(auth, CODE_PUBLIC, lat, lon, RADIUS_M)
                    .documents?.size ?: 0
            }.getOrElse {
                Log.e("SafetyRepo", "공공기관 조회 실패: $it")
                0
            }

            val subway = runCatching {
                kakao.categorySearch(auth, CODE_SUBWAY, lat, lon, RADIUS_M)
                    .documents?.size ?: 0
            }.getOrElse {
                Log.e("SafetyRepo", "지하철역 조회 실패: $it")
                0
            }

            // 2️⃣ Firebase Functions 호출
            val tileSummary = runCatching {
                safetyService.getTileSummary(lat, lon, RADIUS_M)
            }.getOrElse {
                Log.e("SafetyRepo", "Firebase Functions 호출 실패: $it")
                TileSummaryDto()
            }

            // 3️⃣ 점수 계산 및 레벨 결정
            val totalScore = conv + public + subway + (tileSummary.cctvCount ?: 0) + (tileSummary.streetLightCount ?: 0)

            val finalLevel = when {
                totalScore >= 10 -> SafetyDetail.Level.SAFE
                totalScore >= 5 -> SafetyDetail.Level.CAUTION
                else -> SafetyDetail.Level.DANGER
            }

            // 4️⃣ 최종 결과 반환
            SafetyDetail(
                score = totalScore,
                level = finalLevel,
                convCount = conv,
                publicCount = public,
                subwayCount = subway,
                cctvCount = tileSummary.cctvCount ?: 0,
                streetLightCount = tileSummary.streetLightCount ?: 0,
                reasons = listOf(
                    "편의점: ${conv}개",
                    "공공기관: ${public}개",
                    "지하철역: ${subway}개",
                    "CCTV: ${tileSummary.cctvCount ?: 0}개",
                    "가로등: ${tileSummary.streetLightCount ?: 0}개"
                )
            )

        } catch (e: Exception) {
            Log.e("SafetyRepo", "전체 안전도 조회 실패", e)
            // 기본값 반환
            SafetyDetail(
                score = 0,
                level = SafetyDetail.Level.DANGER,
                convCount = 0,
                publicCount = 0,
                subwayCount = 0,
                cctvCount = 0,
                streetLightCount = 0,
                reasons = listOf("데이터를 불러올 수 없습니다")
            )
        }
    }
}