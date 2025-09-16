package com.SICV.plurry.onnx

import android.content.Context
import android.graphics.*
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector.FaceDetectorOptions
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

class FaceMosaicHelper(private val context: Context) {

    private var faceDetector: FaceDetector? = null
    private var backupDetector: FaceDetector? = null
    private var strictDetector: FaceDetector? = null

    init { initializeFaceDetector() }

    private fun initializeFaceDetector() {
        try {
            // 1. 매우 민감한 검출기 (confidence: 0.1)
            val sensitiveOptions = BaseOptions.builder()
                .setModelAssetPath("blaze_face_short_range.tflite")
                .build()

            val sensitiveDetectorOptions = FaceDetectorOptions.builder()
                .setBaseOptions(sensitiveOptions)
                .setMinDetectionConfidence(0.1f) // 더 낮은 임계값
                .setMinSuppressionThreshold(0.2f) // 더 낮은 억제 임계값
                .setRunningMode(RunningMode.IMAGE)
                .build()

            faceDetector = FaceDetector.createFromOptions(context, sensitiveDetectorOptions)
            Log.d("FaceMosaicHelper", "✅ 매우 민감한 얼굴 검출기 초기화 성공 (confidence: 0.1)")

            // 2. 백업 검출기 (confidence: 0.3)
            try {
                val normalOptions = BaseOptions.builder()
                    .setModelAssetPath("blaze_face_short_range.tflite")
                    .build()

                val normalDetectorOptions = FaceDetectorOptions.builder()
                    .setBaseOptions(normalOptions)
                    .setMinDetectionConfidence(0.3f)
                    .setMinSuppressionThreshold(0.4f)
                    .setRunningMode(RunningMode.IMAGE)
                    .build()

                backupDetector = FaceDetector.createFromOptions(context, normalDetectorOptions)
                Log.d("FaceMosaicHelper", "✅ 백업 얼굴 검출기 초기화 성공 (confidence: 0.3)")
            } catch (e: Exception) {
                Log.w("FaceMosaicHelper", "백업 검출기 초기화 실패: ${e.message}")
            }

            // 3. 엄격한 검출기 (confidence: 0.5)
            try {
                val strictOptions = BaseOptions.builder()
                    .setModelAssetPath("blaze_face_short_range.tflite")
                    .build()

                val strictDetectorOptions = FaceDetectorOptions.builder()
                    .setBaseOptions(strictOptions)
                    .setMinDetectionConfidence(0.5f)
                    .setMinSuppressionThreshold(0.6f)
                    .setRunningMode(RunningMode.IMAGE)
                    .build()

                strictDetector = FaceDetector.createFromOptions(context, strictDetectorOptions)
                Log.d("FaceMosaicHelper", "✅ 엄격한 얼굴 검출기 초기화 성공 (confidence: 0.5)")
            } catch (e: Exception) {
                Log.w("FaceMosaicHelper", "엄격한 검출기 초기화 실패: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "❌ 얼굴 검출기 초기화 실패: ${e.message}", e)
        }
    }

    fun applyFaceMosaic(inputBitmap: Bitmap, mosaicSize: Int = 20): Bitmap? {
        return try {
            val startTime = System.currentTimeMillis()

            // MediaPipe 안정 포맷 강제
            val working = if (inputBitmap.config != Bitmap.Config.ARGB_8888)
                inputBitmap.copy(Bitmap.Config.ARGB_8888, true) else inputBitmap

            Log.d("FaceMosaicHelper", "🎭 개선된 모자이크 처리 시작 - 이미지 크기: ${working.width}x${working.height}")

            // 개선된 다단계 얼굴 탐지
            val detectedFaces = detectFacesWithImprovedStrategy(working)

            if (detectedFaces.isEmpty()) {
                Log.d("FaceMosaicHelper", "❌ 모든 방법으로 얼굴 검출 실패")
                return null
            }

            Log.d("FaceMosaicHelper", "✅ ${detectedFaces.size}개 얼굴 검출됨!")

            val mutableBitmap = working.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            detectedFaces.forEachIndexed { index, faceRect ->
                try {
                    Log.d("FaceMosaicHelper", "🎯 얼굴 ${index + 1}: (${faceRect.left.toInt()}, ${faceRect.top.toInt()}) 크기: ${faceRect.width().toInt()}x${faceRect.height().toInt()}")

                    val expandedRect = expandFaceRect(faceRect, working.width, working.height)

                    val mosaicBitmap = createMosaicBitmap(
                        working,
                        expandedRect.left.toInt(),
                        expandedRect.top.toInt(),
                        expandedRect.width().toInt(),
                        expandedRect.height().toInt(),
                        mosaicSize
                    )

                    canvas.drawBitmap(mosaicBitmap, expandedRect.left, expandedRect.top, paint)
                    Log.d("FaceMosaicHelper", "🎨 얼굴 ${index + 1} 모자이크 적용 완료")
                } catch (e: Exception) {
                    Log.e("FaceMosaicHelper", "얼굴 ${index + 1} 처리 중 오류: ${e.message}")
                }
            }

            val endTime = System.currentTimeMillis()
            Log.d("FaceMosaicHelper", "✅ 개선된 모자이크 처리 완료: ${endTime - startTime}ms, ${detectedFaces.size}개 얼굴 처리")
            mutableBitmap

        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "❌ 모자이크 처리 실패: ${e.message}", e)
            null
        }
    }

    /**
     * 🎯 개선된 다단계 얼굴 탐지 전략
     */
    private fun detectFacesWithImprovedStrategy(inputBitmap: Bitmap): List<RectF> {
        val allFaces = mutableListOf<RectF>()

        Log.d("FaceMosaicHelper", "🔍 개선된 얼굴 탐지 시작 - 원본 크기: ${inputBitmap.width}x${inputBitmap.height}")

        // 1단계: 원본 해상도에서 매우 민감한 검출
        Log.d("FaceMosaicHelper", "🔍 1단계: 원본 해상도 매우 민감한 검출")
        val faces1 = detectFacesWithDetector(inputBitmap, faceDetector, "원본_매우민감한검출")
        allFaces.addAll(faces1)

        // 2단계: 고해상도 유지하면서 이미지 향상 후 검출
        if (allFaces.isEmpty()) {
            Log.d("FaceMosaicHelper", "🔍 2단계: 고해상도 이미지 향상 후 검출")
            val enhancedBitmap = enhanceImageForFaceDetection(inputBitmap)
            val faces2 = detectFacesWithDetector(enhancedBitmap, faceDetector, "향상된이미지_민감한검출")
            allFaces.addAll(faces2)
        }

        // 3단계: 적당한 크기로 축소 후 검출 (1500px 기준)
        if (allFaces.isEmpty()) {
            Log.d("FaceMosaicHelper", "🔍 3단계: 적당한 크기로 축소 후 검출")
            val mediumBitmap = resizeBitmapForDetection(inputBitmap, 1500)
            val faces3 = detectFacesWithDetector(mediumBitmap, faceDetector, "중간크기_민감한검출")
            val scaleFactor = inputBitmap.width.toFloat() / mediumBitmap.width.toFloat()
            faces3.forEach { rect ->
                allFaces.add(RectF(rect.left * scaleFactor, rect.top * scaleFactor, rect.right * scaleFactor, rect.bottom * scaleFactor))
            }
        }

        // 4단계: 백업 검출기로 원본에서 검출
        if (allFaces.isEmpty() && backupDetector != null) {
            Log.d("FaceMosaicHelper", "🔍 4단계: 백업 검출기 사용 (원본)")
            val faces4 = detectFacesWithDetector(inputBitmap, backupDetector, "원본_백업검출기")
            allFaces.addAll(faces4)
        }

        // 5단계: 작은 크기로 축소 후 매우 민감한 검출
        if (allFaces.isEmpty()) {
            Log.d("FaceMosaicHelper", "🔍 5단계: 작은 크기 매우 민감한 검출")
            val smallBitmap = resizeBitmapForDetection(inputBitmap, 800)
            val faces5 = detectFacesWithDetector(smallBitmap, faceDetector, "작은크기_매우민감한검출")
            val scaleFactor = inputBitmap.width.toFloat() / smallBitmap.width.toFloat()
            faces5.forEach { rect ->
                allFaces.add(RectF(rect.left * scaleFactor, rect.top * scaleFactor, rect.right * scaleFactor, rect.bottom * scaleFactor))
            }
        }

        // 6단계: 확대 검출 (작은 얼굴 전용)
        if (allFaces.isEmpty()) {
            Log.d("FaceMosaicHelper", "🔍 6단계: 확대 검출 (작은 얼굴 전용)")
            val enlargedBitmap = Bitmap.createScaledBitmap(
                inputBitmap,
                (inputBitmap.width * 2.0f).toInt(),
                (inputBitmap.height * 2.0f).toInt(),
                true
            )
            val enlargedFaces = detectFacesWithDetector(enlargedBitmap, faceDetector, "확대검출")
            enlargedFaces.forEach { rect ->
                allFaces.add(RectF(rect.left / 2.0f, rect.top / 2.0f, rect.right / 2.0f, rect.bottom / 2.0f))
            }
        }

        // 7단계: 엄격한 검출기로 재시도 (노이즈가 많은 이미지의 경우)
        if (allFaces.isEmpty() && strictDetector != null) {
            Log.d("FaceMosaicHelper", "🔍 7단계: 엄격한 검출기 사용")
            val faces7 = detectFacesWithDetector(inputBitmap, strictDetector, "엄격한검출기")
            allFaces.addAll(faces7)
        }

        val uniqueFaces = removeDuplicateFaces(allFaces)
        Log.d("FaceMosaicHelper", "🎯 최종 검출된 얼굴: ${uniqueFaces.size}개")
        return uniqueFaces
    }

    private fun detectFacesWithDetector(bitmap: Bitmap, detector: FaceDetector?, detectorName: String): List<RectF> {
        return try {
            if (detector == null) {
                Log.w("FaceMosaicHelper", "$detectorName: 검출기가 null")
                return emptyList()
            }

            val mpImage = BitmapImageBuilder(bitmap).build()
            val detectionResult = detector.detect(mpImage)

            val detectedCount = detectionResult?.detections()?.size ?: 0
            Log.d("FaceMosaicHelper", "$detectorName: ${detectedCount}개 얼굴 검출")

            detectionResult?.detections()?.mapNotNull { detection ->
                try {
                    val box = detection.boundingBox()
                    val confidence = detection.categories().firstOrNull()?.score() ?: 0f
                    Log.d("FaceMosaicHelper", "$detectorName: 얼굴 신뢰도 ${(confidence * 100).toInt()}%")

                    val imageWidth = bitmap.width.toFloat()
                    val imageHeight = bitmap.height.toFloat()

                    val faceRect = if (box.left <= 1.0f && box.top <= 1.0f) {
                        RectF(
                            box.left * imageWidth,
                            box.top * imageHeight,
                            (box.left + box.width()) * imageWidth,
                            (box.top + box.height()) * imageHeight
                        )
                    } else {
                        RectF(box.left, box.top, box.left + box.width(), box.top + box.height())
                    }

                    val faceArea = faceRect.width() * faceRect.height()
                    val imageArea = imageWidth * imageHeight

                    // 더 관대한 신뢰도 임계값 적용
                    val minConfidence = when {
                        faceArea < imageArea * 0.001f -> 0.05f  // 매우 작은 얼굴
                        faceArea < imageArea * 0.01f -> 0.08f   // 작은 얼굴
                        faceArea < imageArea * 0.05f -> 0.10f   // 중간 얼굴
                        else -> 0.12f                           // 큰 얼굴
                    }

                    if (confidence < minConfidence) {
                        Log.d("FaceMosaicHelper", "$detectorName: 신뢰도 부족으로 제외 (${(confidence * 100).toInt()}% < ${(minConfidence * 100).toInt()}%)")
                        return@mapNotNull null
                    }

                    // 더 관대한 경계 체크
                    if (faceRect.width() > 5 && faceRect.height() > 5 &&
                        faceRect.left >= -10 && faceRect.top >= -10 &&
                        faceRect.right <= imageWidth + 10 && faceRect.bottom <= imageHeight + 10) {

                        // 경계 보정
                        val correctedRect = RectF(
                            max(0f, faceRect.left),
                            max(0f, faceRect.top),
                            min(imageWidth, faceRect.right),
                            min(imageHeight, faceRect.bottom)
                        )
                        correctedRect
                    } else {
                        Log.w("FaceMosaicHelper", "$detectorName: 유효하지 않은 얼굴 좌표")
                        null
                    }
                } catch (e: Exception) {
                    Log.e("FaceMosaicHelper", "$detectorName: 얼굴 처리 오류: ${e.message}")
                    null
                }
            } ?: emptyList()

        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "$detectorName: 검출 실패: ${e.message}")
            emptyList()
        }
    }

    /**
     * 🎨 개선된 이미지 향상 처리
     */
    private fun enhanceImageForFaceDetection(bitmap: Bitmap): Bitmap {
        val enhanced = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(enhanced)
        val paint = Paint()

        // 더 강한 대비와 밝기 향상
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(1.3f) // 채도 증가

        // 밝기와 대비 향상
        val brightnessMatrix = ColorMatrix(floatArrayOf(
            1.2f, 0f, 0f, 0f, 30f,    // R 채널 밝기/대비 증가
            0f, 1.2f, 0f, 0f, 30f,    // G 채널 밝기/대비 증가
            0f, 0f, 1.2f, 0f, 30f,    // B 채널 밝기/대비 증가
            0f, 0f, 0f, 1f, 0f        // 알파 채널 유지
        ))

        colorMatrix.preConcat(brightnessMatrix)
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        Log.d("FaceMosaicHelper", "🎨 이미지 향상 완료 (강화된 대비/밝기)")
        return enhanced
    }

    private fun resizeBitmapForDetection(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxSize && height <= maxSize) return bitmap

        val scale = if (width > height) maxSize.toFloat() / width else maxSize.toFloat() / height
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        // 고품질 스케일링 사용
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun expandFaceRect(rect: RectF, imageWidth: Int, imageHeight: Int): RectF {
        val faceWidth = rect.width()
        val faceHeight = rect.height()
        val faceArea = faceWidth * faceHeight
        val imageArea = imageWidth * imageHeight

        // 더 넉넉한 확장 비율 적용
        val expandRatio = when {
            faceArea < imageArea * 0.005f -> {
                Log.d("FaceMosaicHelper", "🔍 매우 작은 얼굴 감지 - 최대 확장"); 0.4f
            }
            faceArea < imageArea * 0.02f -> {
                Log.d("FaceMosaicHelper", "🔍 작은 얼굴 감지 - 큰 확장"); 0.3f
            }
            faceArea < imageArea * 0.1f  -> {
                Log.d("FaceMosaicHelper", "🔍 중간 얼굴 감지 - 일반 확장"); 0.25f
            }
            else -> {
                Log.d("FaceMosaicHelper", "🔍 큰 얼굴 감지 - 기본 확장"); 0.2f
            }
        }

        val expandWidth = faceWidth * expandRatio
        val expandHeight = faceHeight * expandRatio
        Log.d("FaceMosaicHelper", "🔍 얼굴 크기: ${faceWidth.toInt()}x${faceHeight.toInt()}, 확장비율: ${(expandRatio*100).toInt()}%")

        return RectF(
            maxOf(0f, rect.left - expandWidth / 2),
            maxOf(0f, rect.top - expandHeight / 2),
            minOf(imageWidth.toFloat(), rect.right + expandWidth / 2),
            minOf(imageHeight.toFloat(), rect.bottom + expandHeight / 2)
        )
    }

    private fun removeDuplicateFaces(faces: List<RectF>): List<RectF> {
        if (faces.size <= 1) return faces
        val uniqueFaces = mutableListOf<RectF>()

        for (face in faces) {
            var isDuplicate = false
            for (existing in uniqueFaces) {
                val intersection = RectF()
                if (intersection.setIntersect(face, existing)) {
                    val intersectionArea = intersection.width() * intersection.height()
                    val faceArea = face.width() * face.height()
                    val existingArea = existing.width() * existing.height()
                    val overlapRatio = intersectionArea / minOf(faceArea, existingArea)

                    if (overlapRatio > 0.3f) { // 더 관대한 중복 판정
                        isDuplicate = true
                        // 더 큰 영역을 선택
                        if (faceArea > existingArea) {
                            uniqueFaces.remove(existing)
                            uniqueFaces.add(face)
                        }
                        break
                    }
                }
            }
            if (!isDuplicate) uniqueFaces.add(face)
        }
        return uniqueFaces
    }

    private fun createMosaicBitmap(
        originalBitmap: Bitmap,
        x: Int, y: Int, width: Int, height: Int,
        mosaicSize: Int
    ): Bitmap {
        val safeX = maxOf(0, x)
        val safeY = maxOf(0, y)
        val safeWidth = minOf(width, originalBitmap.width - safeX)
        val safeHeight = minOf(height, originalBitmap.height - safeY)

        Log.d("FaceMosaicHelper", "🎨 모자이크 생성: 영역($safeX, $safeY) 크기(${safeWidth}x${safeHeight}) 블록크기:$mosaicSize")

        if (safeWidth <= 0 || safeHeight <= 0) {
            Log.w("FaceMosaicHelper", "⚠️ 모자이크 영역이 유효하지 않음")
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        val faceBitmap = Bitmap.createBitmap(originalBitmap, safeX, safeY, safeWidth, safeHeight)
        val smallWidth = maxOf(1, safeWidth / mosaicSize)
        val smallHeight = maxOf(1, safeHeight / mosaicSize)
        val smallBitmap = Bitmap.createScaledBitmap(faceBitmap, smallWidth, smallHeight, false)
        val mosaicBitmap = Bitmap.createScaledBitmap(smallBitmap, safeWidth, safeHeight, false)

        faceBitmap.recycle()
        smallBitmap.recycle()
        return mosaicBitmap
    }

    fun detectFaces(inputBitmap: Bitmap): List<RectF> = detectFacesWithImprovedStrategy(inputBitmap)

    fun close() {
        try {
            faceDetector?.close()
            backupDetector?.close()
            strictDetector?.close()
            Log.d("FaceMosaicHelper", "FaceMosaicHelper 리소스 정리 완료")
        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "리소스 정리 실패: ${e.message}")
        }
    }
}