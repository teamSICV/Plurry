package com.SICV.plurry.onnx

import kotlin.math.sqrt

object OnnxComparator {

    // ========== 🎯 기존 핵심 함수들 (그대로 유지) ==========
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "두 벡터의 차원이 다릅니다." }
        require(a.isNotEmpty()) { "빈 벡터입니다." }

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        // Double 사용으로 정확도 향상
        for (i in a.indices) {
            val valA = a[i].toDouble()
            val valB = b[i].toDouble()

            dotProduct += valA * valB
            normA += valA * valA
            normB += valB * valB
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0.0) {
            0f
        } else {
            (dotProduct / denominator).toFloat()
        }
    }

    fun isSimilar(similarity: Float, threshold: Float = 0.7f): Boolean {
        return similarity >= threshold
    }

    // ========== 🔧 고급 마스크 기능 (선택사항) ==========
    fun cosineSimilarityWithMask(a: FloatArray, b: FloatArray, mask: BooleanArray): Float {
        require(a.size == b.size) { "두 벡터의 차원이 다릅니다." }
        require(a.size == mask.size) { "벡터와 마스크의 차원이 다릅니다." }
        require(a.isNotEmpty()) { "빈 벡터입니다." }

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        var validCount = 0

        // 마스크가 true인 위치만 계산
        for (i in a.indices) {
            if (mask[i]) {
                val valA = a[i].toDouble()
                val valB = b[i].toDouble()

                dotProduct += valA * valB
                normA += valA * valA
                normB += valB * valB
                validCount++
            }
        }

        // 유효한 요소가 없으면 0 반환
        if (validCount == 0) {
            return 0f
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator == 0.0) {
            0f
        } else {
            (dotProduct / denominator).toFloat()
        }
    }

    fun isSimilarWithMask(
        a: FloatArray,
        b: FloatArray,
        mask: BooleanArray,
        threshold: Float = 0.7f
    ): Boolean {
        val similarity = cosineSimilarityWithMask(a, b, mask)
        return similarity >= threshold
    }

    // ========== 📊 분석 및 디버깅 기능 ==========

    /**
     * 상세한 유사도 분석 결과
     */
    data class SimilarityResult(
        val similarity: Float,
        val isSimilar: Boolean,
        val vectorLength: Int,
        val validElements: Int = vectorLength,
        val processingMethod: String = "일반",
        val qualityA: String = "",
        val qualityB: String = ""
    )

    /**
     * 기본 유사도 분석
     */
    fun analyzeSimilarity(
        a: FloatArray,
        b: FloatArray,
        threshold: Float = 0.7f
    ): SimilarityResult {
        val similarity = cosineSimilarity(a, b)
        return SimilarityResult(
            similarity = similarity,
            isSimilar = isSimilar(similarity, threshold),
            vectorLength = a.size,
            processingMethod = "일반",
            qualityA = checkVectorQuality(a),
            qualityB = checkVectorQuality(b)
        )
    }

    /**
     * 마스크 적용 유사도 분석
     */
    fun analyzeSimilarityWithMask(
        a: FloatArray,
        b: FloatArray,
        mask: BooleanArray,
        threshold: Float = 0.7f
    ): SimilarityResult {
        val similarity = cosineSimilarityWithMask(a, b, mask)
        val validElements = mask.count { it }
        return SimilarityResult(
            similarity = similarity,
            isSimilar = isSimilar(similarity, threshold),
            vectorLength = a.size,
            validElements = validElements,
            processingMethod = "마스크 적용 ($validElements/${a.size} 요소 사용)",
            qualityA = checkVectorQuality(a),
            qualityB = checkVectorQuality(b)
        )
    }

    /**
     * 벡터 통계 정보
     */
    data class VectorStats(
        val mean: Float,
        val variance: Float,
        val min: Float,
        val max: Float,
        val nonZeroCount: Int,
        val zeroCount: Int
    )

    /**
     * 벡터 통계 계산
     */
    fun getVectorStats(vector: FloatArray): VectorStats {
        if (vector.isEmpty()) {
            return VectorStats(0f, 0f, 0f, 0f, 0, 0)
        }

        val mean = vector.average().toFloat()
        val variance = vector.map { (it - mean) * (it - mean) }.average().toFloat()
        val min = vector.minOrNull() ?: 0f
        val max = vector.maxOrNull() ?: 0f
        val nonZeroCount = vector.count { it != 0f }
        val zeroCount = vector.count { it == 0f }

        return VectorStats(mean, variance, min, max, nonZeroCount, zeroCount)
    }

    /**
     * 벡터 품질 검사 (특징 추출이 제대로 됐는지 확인)
     */
    fun checkVectorQuality(vector: FloatArray): String {
        val stats = getVectorStats(vector)

        return when {
            vector.isEmpty() -> "❌ 빈 벡터"
            vector.all { it == 0f } -> "❌ 모든 값이 0 (특징 추출 실패)"
            stats.variance < 0.001f -> "⚠️ 분산 매우 낮음 (${String.format("%.6f", stats.variance)})"
            stats.nonZeroCount < vector.size * 0.1 -> "⚠️ 희소 벡터 (${stats.nonZeroCount}/${vector.size})"
            stats.variance > 1.0f -> "⚠️ 분산 매우 높음 (${String.format("%.3f", stats.variance)})"
            else -> "✅ 정상 (분산: ${String.format("%.3f", stats.variance)})"
        }
    }

    /**
     * 두 벡터 비교 상세 분석
     */
    fun compareVectorsDetailed(a: FloatArray, b: FloatArray): String {
        if (a.size != b.size) {
            return "❌ 벡터 크기 불일치: ${a.size} vs ${b.size}"
        }

        val statsA = getVectorStats(a)
        val statsB = getVectorStats(b)
        val similarity = cosineSimilarity(a, b)

        return buildString {
            appendLine("📊 벡터 비교 분석")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine("🎯 유사도: ${String.format("%.4f", similarity)} (${(similarity * 100).toInt()}%)")
            appendLine()
            appendLine("📈 벡터 A 통계:")
            appendLine("   평균: ${String.format("%.4f", statsA.mean)}")
            appendLine("   분산: ${String.format("%.4f", statsA.variance)}")
            appendLine("   범위: ${String.format("%.4f", statsA.min)} ~ ${String.format("%.4f", statsA.max)}")
            appendLine("   비영 요소: ${statsA.nonZeroCount}/${a.size}")
            appendLine()
            appendLine("📈 벡터 B 통계:")
            appendLine("   평균: ${String.format("%.4f", statsB.mean)}")
            appendLine("   분산: ${String.format("%.4f", statsB.variance)}")
            appendLine("   범위: ${String.format("%.4f", statsB.min)} ~ ${String.format("%.4f", statsB.max)}")
            appendLine("   비영 요소: ${statsB.nonZeroCount}/${b.size}")
            appendLine()
            appendLine("🔍 품질 검사:")
            appendLine("   벡터 A: ${checkVectorQuality(a)}")
            appendLine("   벡터 B: ${checkVectorQuality(b)}")
        }
    }

    /**
     * 유사도 해석 도움말
     */
    fun interpretSimilarity(similarity: Float): String {
        return when {
            similarity >= 0.95f -> "🟢 거의 동일한 이미지 (${(similarity * 100).toInt()}%)"
            similarity >= 0.85f -> "🔵 매우 유사한 이미지 (${(similarity * 100).toInt()}%)"
            similarity >= 0.75f -> "🟡 상당히 유사한 이미지 (${(similarity * 100).toInt()}%)"
            similarity >= 0.60f -> "🟠 어느 정도 유사한 이미지 (${(similarity * 100).toInt()}%)"
            similarity >= 0.40f -> "🔴 약간 유사한 이미지 (${(similarity * 100).toInt()}%)"
            else -> "⚫ 다른 이미지 (${(similarity * 100).toInt()}%)"
        }
    }

    /**
     * 임계값 추천
     */
    fun recommendThreshold(useCase: String): Pair<Float, String> {
        return when (useCase.lowercase()) {
            "duplicate", "중복", "중복검사" -> 0.95f to "중복 이미지 검사용 (매우 엄격)"
            "similar", "유사", "유사검사" -> 0.8f to "일반적인 유사성 검사용 (권장)"
            "loose", "느슨한", "관대한" -> 0.6f to "느슨한 유사성 검사용"
            "strict", "엄격한", "정확한" -> 0.9f to "엄격한 유사성 검사용"
            else -> 0.8f to "기본값 (일반적인 사용)"
        }
    }

    // ========== 🎯 편의 함수들 ==========

    /**
     * 빠른 품질 체크와 함께 유사도 계산
     */
    fun safeSimilarity(a: FloatArray, b: FloatArray): Float? {
        val qualityA = checkVectorQuality(a)
        val qualityB = checkVectorQuality(b)

        if (qualityA.startsWith("❌") || qualityB.startsWith("❌")) {
            android.util.Log.w("OnnxComparator", "벡터 품질 문제: A=$qualityA, B=$qualityB")
            return null
        }

        return cosineSimilarity(a, b)
    }

    /**
     * 로그와 함께 유사도 계산
     */
    fun similarityWithLog(a: FloatArray, b: FloatArray, tag: String = "Similarity"): Float {
        val similarity = cosineSimilarity(a, b)
        android.util.Log.d(tag, "유사도: ${(similarity * 100).toInt()}% - ${interpretSimilarity(similarity)}")
        return similarity
    }
}