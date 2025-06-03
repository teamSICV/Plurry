package com.SICV.plurry.onnx

import kotlin.math.sqrt

object OnnxComparator {
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        require(a.size == b.size) { "두 벡터의 차원이 다릅니다." }

        var dotProduct = 0f
        var normA = 0f
        var normB = 0f

        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }

        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
}