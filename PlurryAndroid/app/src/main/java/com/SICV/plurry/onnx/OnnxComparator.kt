package com.SICV.plurry.onnx

import kotlin.math.sqrt

object OnnxComparator {
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

    // 유사도 판정을 위한 헬퍼 함수
    fun isSimilar(similarity: Float, threshold: Float = 0.8f): Boolean {
        return similarity >= threshold
    }
}