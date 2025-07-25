package com.SICV.plurry.onnx

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.*
import java.nio.FloatBuffer
import kotlin.math.min

class OnnxHelper(context: Context) {
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelBytes = context.assets.open("mobilenetv2_feature512.onnx").readBytes()
        session = ortEnv.createSession(modelBytes)
    }

    // ========== 기존 함수들 (호환성 유지) ==========
    fun preprocess(bitmap: Bitmap): FloatBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, 160, 160, true)
        val floatBuffer = FloatBuffer.allocate(3 * 160 * 160)
        val pixels = IntArray(160 * 160)
        resized.getPixels(pixels, 0, 160, 0, 0, 160, 160)

        // Normalize to [0, 1] and CHW format
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            floatBuffer.put(i, r) // R 채널
            floatBuffer.put(i + 160 * 160, g) // G 채널
            floatBuffer.put(i + 160 * 160 * 2, b) // B 채널
        }

        return floatBuffer
    }

    fun runInference(bitmap: Bitmap): FloatArray? {
        return try {
            val inputName = session.inputNames.iterator().next()
            val inputBuffer = preprocess(bitmap)
            val inputShape = longArrayOf(1, 3, 160, 160)

            var inputTensor: OnnxTensor? = null
            var output: OrtSession.Result? = null

            try {
                inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, inputShape)
                output = session.run(mapOf(inputName to inputTensor))
                val result = output[0].value as Array<FloatArray>
                result[0]
            } finally {
                inputTensor?.close()
                output?.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("OnnxHelper", "추론 실패: ${e.message}")
            null
        }
    }

    // ========== 스마트 모자이크 탐지 ==========
    /**
     * 이미지에 모자이크가 있는지 빠르게 체크 (10% 샘플링)
     */
    fun hasMosaic(bitmap: Bitmap, threshold: Float = 30f, checkRatio: Float = 0.1f): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val blockSize = 8
        val blocksX = width / blockSize
        val blocksY = height / blockSize
        val totalBlocks = blocksX * blocksY

        if (totalBlocks == 0) return false

        val checkBlocks = (totalBlocks * checkRatio).toInt().coerceAtLeast(1)
        val stepSize = (totalBlocks / checkBlocks).coerceAtLeast(1)

        var checkedBlocks = 0
        var mosaicBlocks = 0

        // 샘플링해서 체크 (성능 최적화)
        for (blockIndex in 0 until totalBlocks step stepSize) {
            if (checkedBlocks >= checkBlocks) break

            val blockY = (blockIndex / blocksX) * blockSize
            val blockX = (blockIndex % blocksX) * blockSize

            if (blockY + blockSize > height || blockX + blockSize > width) continue

            var blockVariance = 0.0
            var blockMean = 0.0
            val blockPixels = blockSize * blockSize

            // 블록 내 그레이스케일 평균 계산
            for (by in blockY until blockY + blockSize) {
                for (bx in blockX until blockX + blockSize) {
                    val pixelIndex = by * width + bx
                    if (pixelIndex < pixels.size) {
                        val pixel = pixels[pixelIndex]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val gray = 0.299 * r + 0.587 * g + 0.114 * b
                        blockMean += gray
                    }
                }
            }
            blockMean /= blockPixels

            // 블록 내 분산 계산
            for (by in blockY until blockY + blockSize) {
                for (bx in blockX until blockX + blockSize) {
                    val pixelIndex = by * width + bx
                    if (pixelIndex < pixels.size) {
                        val pixel = pixels[pixelIndex]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val gray = 0.299 * r + 0.587 * g + 0.114 * b
                        val diff = gray - blockMean
                        blockVariance += diff * diff
                    }
                }
            }
            blockVariance /= blockPixels

            if (blockVariance < threshold) {
                mosaicBlocks++
            }
            checkedBlocks++
        }

        // 체크한 블록 중 일정 비율 이상이 모자이크면 모자이크 이미지로 판정
        val mosaicRatio = if (checkedBlocks > 0) mosaicBlocks.toFloat() / checkedBlocks else 0f
        return mosaicRatio > 0.1f  // 10% 이상이 모자이크 블록이면 모자이크 이미지
    }

    // ========== 모자이크 마스크 생성 및 적용 ==========
    private fun detectFullMosaicMask(bitmap: Bitmap, threshold: Float = 30f): BooleanArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val mask = BooleanArray(width * height) { true }
        val blockSize = 8

        for (y in 0 until height - blockSize step blockSize) {
            for (x in 0 until width - blockSize step blockSize) {
                var blockVariance = 0.0
                var blockMean = 0.0
                val blockPixels = blockSize * blockSize

                // 블록 내 그레이스케일 평균 계산
                for (by in y until minOf(y + blockSize, height)) {
                    for (bx in x until minOf(x + blockSize, width)) {
                        val pixelIndex = by * width + bx
                        if (pixelIndex < pixels.size) {
                            val pixel = pixels[pixelIndex]
                            val r = (pixel shr 16) and 0xFF
                            val g = (pixel shr 8) and 0xFF
                            val b = pixel and 0xFF
                            val gray = 0.299 * r + 0.587 * g + 0.114 * b
                            blockMean += gray
                        }
                    }
                }
                blockMean /= blockPixels

                // 블록 내 분산 계산
                for (by in y until minOf(y + blockSize, height)) {
                    for (bx in x until minOf(x + blockSize, width)) {
                        val pixelIndex = by * width + bx
                        if (pixelIndex < pixels.size) {
                            val pixel = pixels[pixelIndex]
                            val r = (pixel shr 16) and 0xFF
                            val g = (pixel shr 8) and 0xFF
                            val b = pixel and 0xFF
                            val gray = 0.299 * r + 0.587 * g + 0.114 * b
                            val diff = gray - blockMean
                            blockVariance += diff * diff
                        }
                    }
                }
                blockVariance /= blockPixels

                // 분산이 낮으면 모자이크로 판단
                if (blockVariance < threshold) {
                    for (by in y until minOf(y + blockSize, height)) {
                        for (bx in x until minOf(x + blockSize, width)) {
                            val pixelIndex = by * width + bx
                            if (pixelIndex < mask.size) {
                                mask[pixelIndex] = false
                            }
                        }
                    }
                }
            }
        }

        return mask
    }

    private fun preprocessWithMask(bitmap: Bitmap, mosaicThreshold: Float = 30f): FloatBuffer {
        // 1. 모자이크 마스크 생성
        val originalMask = detectFullMosaicMask(bitmap, mosaicThreshold)

        // 2. 이미지 리사이즈
        val resized = Bitmap.createScaledBitmap(bitmap, 160, 160, true)

        // 3. 마스크도 리사이즈에 맞게 조정
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val resizedMask = BooleanArray(160 * 160)

        for (y in 0 until 160) {
            for (x in 0 until 160) {
                val origX = (x * originalWidth / 160.0).toInt()
                val origY = (y * originalHeight / 160.0).toInt()
                val origIndex = origY * originalWidth + origX

                resizedMask[y * 160 + x] = if (origIndex < originalMask.size) {
                    originalMask[origIndex]
                } else {
                    true
                }
            }
        }

        // 4. 픽셀 데이터 추출 및 마스크 적용
        val floatBuffer = FloatBuffer.allocate(3 * 160 * 160)
        val pixels = IntArray(160 * 160)
        resized.getPixels(pixels, 0, 160, 0, 0, 160, 160)

        // 5. 정규화 및 CHW 포맷 변환 (마스크 적용)
        for (i in pixels.indices) {
            if (resizedMask[i]) {
                // 정상 영역: 원본 픽셀 사용
                val pixel = pixels[i]
                val r = ((pixel shr 16) and 0xFF) / 255.0f
                val g = ((pixel shr 8) and 0xFF) / 255.0f
                val b = (pixel and 0xFF) / 255.0f

                floatBuffer.put(i, r) // R 채널
                floatBuffer.put(i + 160 * 160, g) // G 채널
                floatBuffer.put(i + 160 * 160 * 2, b) // B 채널
            } else {
                // 모자이크 영역: 중립값으로 처리
                floatBuffer.put(i, 0.5f) // R 채널
                floatBuffer.put(i + 160 * 160, 0.5f) // G 채널
                floatBuffer.put(i + 160 * 160 * 2, 0.5f) // B 채널
            }
        }

        return floatBuffer
    }

    private fun runInferenceWithMask(bitmap: Bitmap, mosaicThreshold: Float = 30f): FloatArray? {
        return try {
            val inputName = session.inputNames.iterator().next()
            val inputBuffer = preprocessWithMask(bitmap, mosaicThreshold)
            val inputShape = longArrayOf(1, 3, 160, 160)

            var inputTensor: OnnxTensor? = null
            var output: OrtSession.Result? = null

            try {
                inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, inputShape)
                output = session.run(mapOf(inputName to inputTensor))
                val result = output[0].value as Array<FloatArray>
                result[0]
            } finally {
                inputTensor?.close()
                output?.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("OnnxHelper", "마스크 적용 추론 실패: ${e.message}")
            null
        }
    }

    // ========== 🎯 메인 함수들 (이것들을 사용하세요!) ==========

    /**
     * ⭐ 스마트 추론: 모자이크 자동 탐지 → 적절한 방법 선택
     * 이 함수를 기본으로 사용하세요!
     */
    fun runSmartInference(bitmap: Bitmap, mosaicThreshold: Float = 30f): FloatArray? {
        return if (hasMosaic(bitmap, mosaicThreshold)) {
            android.util.Log.d("OnnxHelper", "모자이크 탐지됨 - 마스크 적용 추론 실행")
            runInferenceWithMask(bitmap, mosaicThreshold)
        } else {
            android.util.Log.d("OnnxHelper", "모자이크 없음 - 일반 추론 실행")
            runInference(bitmap)  // 기존 빠른 방식
        }
    }

    /**
     * ⭐ 두 이미지의 스마트 비교 (가장 많이 사용할 함수)
     * 한 줄로 모든 처리 완료!
     */
    fun compareImages(bitmap1: Bitmap, bitmap2: Bitmap, mosaicThreshold: Float = 30f): Float? {
        val features1 = runSmartInference(bitmap1, mosaicThreshold) ?: return null
        val features2 = runSmartInference(bitmap2, mosaicThreshold) ?: return null

        return OnnxComparator.cosineSimilarity(features1, features2)
    }

    /**
     * ⭐ 스마트 유사도 판정 (true/false로 결과 반환)
     * 가장 간단한 사용법!
     */
    fun areImagesSimilar(
        bitmap1: Bitmap,
        bitmap2: Bitmap,
        similarityThreshold: Float = 0.8f,
        mosaicThreshold: Float = 30f
    ): Boolean {
        val similarity = compareImages(bitmap1, bitmap2, mosaicThreshold) ?: return false
        return similarity >= similarityThreshold
    }

    // ========== 고급/디버깅 함수들 ==========

    fun runInferenceWithForcedMask(bitmap: Bitmap, mosaicThreshold: Float = 30f): FloatArray? {
        android.util.Log.d("OnnxHelper", "강제 마스크 적용 추론 실행")
        return runInferenceWithMask(bitmap, mosaicThreshold)
    }

    fun runInferenceWithoutMask(bitmap: Bitmap): FloatArray? {
        android.util.Log.d("OnnxHelper", "일반 추론 실행 (마스크 없음)")
        return runInference(bitmap)
    }

    fun checkMosaicStatus(bitmap: Bitmap, threshold: Float = 30f): String {
        val hasMosaicResult = hasMosaic(bitmap, threshold)
        return if (hasMosaicResult) {
            "모자이크 탐지됨 - 마스크 적용 추론이 실행됩니다"
        } else {
            "모자이크 없음 - 일반 추론이 실행됩니다"
        }
    }

    // OnnxHelper 클래스에 추가할 개선된 모자이크 탐지 함수들

    /**
     * 🎯 개선된 모자이크 탐지 (다중 스케일 + 적응형)
     */
    fun hasMosaicImproved(bitmap: Bitmap, checkRatio: Float = 0.15f): Boolean {
        val width = bitmap.width
        val height = bitmap.height

        // 이미지 크기에 따른 적응형 블록 크기들
        val blockSizes = getAdaptiveBlockSizes(width, height)
        val thresholds = getAdaptiveThresholds(width, height)

        var totalMosaicScore = 0.0
        var totalChecks = 0

        // 다중 스케일로 검사
        blockSizes.forEachIndexed { index, blockSize ->
            val threshold = thresholds[index]
            val mosaicScore = checkMosaicAtScale(bitmap, blockSize, threshold, checkRatio)
            totalMosaicScore += mosaicScore
            totalChecks++

            android.util.Log.d("MosaicDetection", "블록크기 ${blockSize}x${blockSize}, 임계값 $threshold: 모자이크 점수 $mosaicScore")
        }

        val averageMosaicScore = totalMosaicScore / totalChecks
        val hasMosaic = averageMosaicScore > 0.1 // 10% 이상이 모자이크 패턴이면 모자이크 이미지

        android.util.Log.d("MosaicDetection", "최종 모자이크 점수: $averageMosaicScore, 판정: ${if (hasMosaic) "모자이크 있음" else "모자이크 없음"}")

        return hasMosaic
    }

    /**
     * 이미지 크기에 따른 적응형 블록 크기 결정
     */
    private fun getAdaptiveBlockSizes(width: Int, height: Int): IntArray {
        val minDimension = minOf(width, height)

        return when {
            minDimension >= 1000 -> intArrayOf(16, 12, 8, 6)  // 고해상도: 큰 블록부터
            minDimension >= 600 -> intArrayOf(12, 8, 6, 4)    // 중간해상도
            minDimension >= 300 -> intArrayOf(8, 6, 4, 3)     // 일반해상도
            else -> intArrayOf(6, 4, 3, 2)                    // 저해상도: 작은 블록까지
        }
    }

    /**
     * 이미지 크기에 따른 적응형 임계값
     */
    private fun getAdaptiveThresholds(width: Int, height: Int): FloatArray {
        val minDimension = minOf(width, height)

        return when {
            minDimension >= 1000 -> floatArrayOf(50f, 40f, 30f, 25f)  // 고해상도: 높은 임계값
            minDimension >= 600 -> floatArrayOf(40f, 30f, 25f, 20f)   // 중간해상도
            minDimension >= 300 -> floatArrayOf(30f, 25f, 20f, 15f)   // 일반해상도
            else -> floatArrayOf(25f, 20f, 15f, 10f)                  // 저해상도: 낮은 임계값
        }
    }

    /**
     * 특정 스케일에서 모자이크 검사
     */
    private fun checkMosaicAtScale(
        bitmap: Bitmap,
        blockSize: Int,
        threshold: Float,
        checkRatio: Float
    ): Double {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val blocksX = width / blockSize
        val blocksY = height / blockSize
        val totalBlocks = blocksX * blocksY

        if (totalBlocks == 0) return 0.0

        val checkBlocks = (totalBlocks * checkRatio).toInt().coerceAtLeast(1)
        val stepSize = (totalBlocks / checkBlocks).coerceAtLeast(1)

        var checkedBlocks = 0
        var mosaicBlocks = 0

        for (blockIndex in 0 until totalBlocks step stepSize) {
            if (checkedBlocks >= checkBlocks) break

            val blockY = (blockIndex / blocksX) * blockSize
            val blockX = (blockIndex % blocksX) * blockSize

            if (blockY + blockSize > height || blockX + blockSize > width) continue

            // 개선된 모자이크 패턴 검사
            if (isMosaicBlock(pixels, width, height, blockX, blockY, blockSize, threshold)) {
                mosaicBlocks++
            }
            checkedBlocks++
        }

        return if (checkedBlocks > 0) mosaicBlocks.toDouble() / checkedBlocks else 0.0
    }

    /**
     * 🎯 개선된 모자이크 블록 판정 (다중 조건)
     */
    private fun isMosaicBlock(
        pixels: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        blockSize: Int,
        threshold: Float
    ): Boolean {
        val blockPixels = blockSize * blockSize
        val grayValues = FloatArray(blockPixels)
        var index = 0

        // 1. 그레이스케일 변환
        for (y in startY until startY + blockSize) {
            for (x in startX until startX + blockSize) {
                if (y < height && x < width) {
                    val pixelIndex = y * width + x
                    if (pixelIndex < pixels.size) {
                        val pixel = pixels[pixelIndex]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        grayValues[index] = (0.299f * r + 0.587f * g + 0.114f * b)
                        index++
                    }
                }
            }
        }

        if (index == 0) return false

        // 2. 평균과 분산 계산
        val mean = grayValues.take(index).average().toFloat()
        val variance = grayValues.take(index).map { (it - mean) * (it - mean) }.average().toFloat()

        // 3. 다중 조건 검사
        val lowVariance = variance < threshold
        val uniformPattern = checkUniformPattern(grayValues, index, blockSize)
        val edgeDensity = calculateEdgeDensity(grayValues, index, blockSize)

        // 4. 모자이크 판정 (여러 조건 조합)
        val isMosaic = lowVariance && (uniformPattern || edgeDensity < 0.1f)

        return isMosaic
    }

    /**
     * 균일한 패턴 검사 (모자이크 특성)
     */
    private fun checkUniformPattern(grayValues: FloatArray, size: Int, blockSize: Int): Boolean {
        if (size < 4) return false

        // 블록을 4등분해서 각 영역의 평균이 비슷한지 확인
        val halfBlock = blockSize / 2
        val quarter1 = mutableListOf<Float>()
        val quarter2 = mutableListOf<Float>()
        val quarter3 = mutableListOf<Float>()
        val quarter4 = mutableListOf<Float>()

        for (y in 0 until blockSize) {
            for (x in 0 until blockSize) {
                val index = y * blockSize + x
                if (index < size) {
                    when {
                        y < halfBlock && x < halfBlock -> quarter1.add(grayValues[index])
                        y < halfBlock && x >= halfBlock -> quarter2.add(grayValues[index])
                        y >= halfBlock && x < halfBlock -> quarter3.add(grayValues[index])
                        else -> quarter4.add(grayValues[index])
                    }
                }
            }
        }

        if (quarter1.isEmpty() || quarter2.isEmpty() || quarter3.isEmpty() || quarter4.isEmpty()) {
            return false
        }

        val avg1 = quarter1.average()
        val avg2 = quarter2.average()
        val avg3 = quarter3.average()
        val avg4 = quarter4.average()

        // 각 분면의 평균이 비슷하면 균일한 패턴 (모자이크 특성)
        val maxDiff = maxOf(
            kotlin.math.abs(avg1 - avg2),
            kotlin.math.abs(avg1 - avg3),
            kotlin.math.abs(avg1 - avg4),
            kotlin.math.abs(avg2 - avg3),
            kotlin.math.abs(avg2 - avg4),
            kotlin.math.abs(avg3 - avg4)
        )

        return maxDiff < 15.0 // 분면간 차이가 작으면 균일한 패턴
    }

    /**
     * 엣지 밀도 계산 (모자이크는 엣지가 적음)
     */
    private fun calculateEdgeDensity(grayValues: FloatArray, size: Int, blockSize: Int): Float {
        if (size < blockSize * 2) return 0f

        var edgeCount = 0
        val edgeThreshold = 20f

        // 간단한 Sobel 엣지 검출
        for (y in 1 until blockSize - 1) {
            for (x in 1 until blockSize - 1) {
                val index = y * blockSize + x
                if (index < size) {
                    val gx = kotlin.math.abs(
                        grayValues[index - blockSize - 1] + 2 * grayValues[index - 1] + grayValues[index + blockSize - 1] -
                                grayValues[index - blockSize + 1] - 2 * grayValues[index + 1] - grayValues[index + blockSize + 1]
                    )
                    val gy = kotlin.math.abs(
                        grayValues[index - blockSize - 1] + 2 * grayValues[index - blockSize] + grayValues[index - blockSize + 1] -
                                grayValues[index + blockSize - 1] - 2 * grayValues[index + blockSize] - grayValues[index + blockSize + 1]
                    )

                    val gradient = kotlin.math.sqrt((gx * gx + gy * gy).toDouble()).toFloat()
                    if (gradient > edgeThreshold) {
                        edgeCount++
                    }
                }
            }
        }

        return edgeCount.toFloat() / size
    }

    /**
     * 🎯 스마트 추론 (개선된 모자이크 탐지 사용)
     */
    fun runSmartInferenceImproved(bitmap: Bitmap, mosaicThreshold: Float = 30f): FloatArray? {
        return if (hasMosaicImproved(bitmap)) {
            android.util.Log.d("OnnxHelper", "🎯 개선된 모자이크 탐지됨 - 마스크 적용 추론 실행")
            runInferenceWithMask(bitmap, mosaicThreshold)
        } else {
            android.util.Log.d("OnnxHelper", "✅ 모자이크 없음 - 일반 추론 실행")
            runInference(bitmap)
        }
    }

    /**
     * 🎯 개선된 이미지 비교
     */
    fun compareImagesImproved(bitmap1: Bitmap, bitmap2: Bitmap, mosaicThreshold: Float = 30f): Float? {
        val features1 = runSmartInferenceImproved(bitmap1, mosaicThreshold) ?: return null
        val features2 = runSmartInferenceImproved(bitmap2, mosaicThreshold) ?: return null

        return OnnxComparator.cosineSimilarity(features1, features2)
    }

    // 클래스 해제시 세션 정리
    fun close() {
        session.close()
    }
}