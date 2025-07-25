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

class FaceMosaicHelper(private val context: Context) {

    private var faceDetector: FaceDetector? = null
    private var backupDetector: FaceDetector? = null

    init {
        initializeFaceDetector()
    }

    private fun initializeFaceDetector() {
        try {
            // 🎯 1차 검출기: 민감한 설정 (작은 얼굴, 화면 속 얼굴용)
            val sensitiveOptions = BaseOptions.builder()
                .setModelAssetPath("blaze_face_short_range.tflite")
                .build()

            val sensitiveDetectorOptions = FaceDetectorOptions.builder()
                .setBaseOptions(sensitiveOptions)
                .setMinDetectionConfidence(0.2f)  // 🔥 20%로 대폭 낮춤
                .setMinSuppressionThreshold(0.3f) // 중복 제거 임계값도 낮춤
                .setRunningMode(RunningMode.IMAGE)
                .build()

            faceDetector = FaceDetector.createFromOptions(context, sensitiveDetectorOptions)
            Log.d("FaceMosaicHelper", "✅ 민감한 얼굴 검출기 초기화 성공 (confidence: 0.2)")

            // 🎯 2차 검출기: 일반 설정 (백업용)
            try {
                val normalOptions = BaseOptions.builder()
                    .setModelAssetPath("blaze_face_short_range.tflite")
                    .build()

                val normalDetectorOptions = FaceDetectorOptions.builder()
                    .setBaseOptions(normalOptions)
                    .setMinDetectionConfidence(0.4f)
                    .setRunningMode(RunningMode.IMAGE)
                    .build()

                backupDetector = FaceDetector.createFromOptions(context, normalDetectorOptions)
                Log.d("FaceMosaicHelper", "✅ 백업 얼굴 검출기 초기화 성공 (confidence: 0.4)")
            } catch (e: Exception) {
                Log.w("FaceMosaicHelper", "백업 검출기 초기화 실패: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "❌ 얼굴 검출기 초기화 실패: ${e.message}", e)
        }
    }

    fun applyFaceMosaic(inputBitmap: Bitmap, mosaicSize: Int = 20): Bitmap? {
        return try {
            val startTime = System.currentTimeMillis()

            Log.d("FaceMosaicHelper", "🎭 개선된 모자이크 처리 시작 - 이미지 크기: ${inputBitmap.width}x${inputBitmap.height}")

            // 🎯 여러 가지 방법으로 얼굴 검출 시도
            val detectedFaces = detectFacesMultipleWays(inputBitmap)

            if (detectedFaces.isEmpty()) {
                Log.d("FaceMosaicHelper", "❌ 모든 방법으로 얼굴 검출 실패")
                return inputBitmap
            }

            Log.d("FaceMosaicHelper", "✅ ${detectedFaces.size}개 얼굴 검출됨!")

            val mutableBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            detectedFaces.forEachIndexed { index, faceRect ->
                try {
                    Log.d("FaceMosaicHelper", "🎯 얼굴 ${index + 1}: (${faceRect.left.toInt()}, ${faceRect.top.toInt()}) 크기: ${faceRect.width().toInt()}x${faceRect.height().toInt()}")

                    // 얼굴 영역 확장 (더 확실한 모자이크)
                    val expandedRect = expandFaceRect(faceRect, inputBitmap.width, inputBitmap.height)

                    val mosaicBitmap = createMosaicBitmap(
                        inputBitmap,
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

    // 🎯 다양한 방법으로 얼굴 검출 시도
    private fun detectFacesMultipleWays(inputBitmap: Bitmap): List<RectF> {
        val allFaces = mutableListOf<RectF>()

        // 🎯 방법 1: 원본 이미지로 민감한 검출
        Log.d("FaceMosaicHelper", "🔍 방법 1: 원본 이미지 민감한 검출")
        val faces1 = detectFacesWithDetector(inputBitmap, faceDetector, "민감한검출기")
        allFaces.addAll(faces1)

        // 🎯 방법 2: 이미지 향상 후 검출
        if (allFaces.isEmpty()) {
            Log.d("FaceMosaicHelper", "🔍 방법 2: 이미지 향상 후 검출")
            val enhancedBitmap = enhanceImageForFaceDetection(inputBitmap)
            val faces2 = detectFacesWithDetector(enhancedBitmap, faceDetector, "향상된이미지")
            // 좌표를 원본 크기로 변환
            val scaleFactor = inputBitmap.width.toFloat() / enhancedBitmap.width.toFloat()
            faces2.forEach { rect ->
                allFaces.add(RectF(
                    rect.left * scaleFactor,
                    rect.top * scaleFactor,
                    rect.right * scaleFactor,
                    rect.bottom * scaleFactor
                ))
            }
        }

        // 🎯 방법 3: 작은 크기로 검출
        if (allFaces.isEmpty()) {
            Log.d("FaceMosaicHelper", "🔍 방법 3: 작은 크기로 검출")
            val smallBitmap = resizeBitmapForDetection(inputBitmap, 600)
            val faces3 = detectFacesWithDetector(smallBitmap, faceDetector, "작은크기")
            // 좌표를 원본 크기로 변환
            val scaleFactor = inputBitmap.width.toFloat() / smallBitmap.width.toFloat()
            faces3.forEach { rect ->
                allFaces.add(RectF(
                    rect.left * scaleFactor,
                    rect.top * scaleFactor,
                    rect.right * scaleFactor,
                    rect.bottom * scaleFactor
                ))
            }
        }

        if (allFaces.isEmpty()) {
            Log.d("FaceMosaicHelper", "🔍 방법 3.5: 작은 얼굴 전용 확대 검출")
            val enlargedBitmap = Bitmap.createScaledBitmap(
                inputBitmap,
                (inputBitmap.width * 1.5f).toInt(),
                (inputBitmap.height * 1.5f).toInt(),
                true
            )
            val enlargedFaces = detectFacesWithDetector(enlargedBitmap, faceDetector, "확대검출")
            // 좌표를 원본 크기로 변환
            enlargedFaces.forEach { rect ->
                allFaces.add(RectF(
                    rect.left / 1.5f,
                    rect.top / 1.5f,
                    rect.right / 1.5f,
                    rect.bottom / 1.5f
                ))
            }
        }

        // 🎯 방법 4: 백업 검출기 사용
        if (allFaces.isEmpty() && backupDetector != null) {
            Log.d("FaceMosaicHelper", "🔍 방법 4: 백업 검출기 사용")
            val faces4 = detectFacesWithDetector(inputBitmap, backupDetector, "백업검출기")
            allFaces.addAll(faces4)
        }

        // 중복 제거
        val uniqueFaces = removeDuplicateFaces(allFaces)
        Log.d("FaceMosaicHelper", "🎯 최종 검출된 얼굴: ${uniqueFaces.size}개")

        return uniqueFaces
    }

    // 🎯 특정 검출기로 얼굴 검출
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
                        // 정규화된 좌표
                        RectF(
                            box.left * imageWidth,
                            box.top * imageHeight,
                            (box.left + box.width()) * imageWidth,
                            (box.top + box.height()) * imageHeight
                        )
                    } else {
                        // 픽셀 좌표
                        RectF(box.left, box.top, box.left + box.width(), box.top + box.height())
                    }

                    val faceArea = faceRect.width() * faceRect.height()
                    val imageArea = imageWidth * imageHeight
                    val minConfidence = when {
                        faceArea < imageArea * 0.02f -> 0.15f  // 작은 얼굴: 15%
                        faceArea < imageArea * 0.05f -> 0.18f  // 중간 얼굴: 18%
                        else -> 0.2f  // 큰 얼굴: 20%
                    }
                    if (confidence < minConfidence) {
                        Log.d("FaceMosaicHelper", "$detectorName: 신뢰도 부족으로 제외 (${(confidence * 100).toInt()}% < ${(minConfidence * 100).toInt()}%)")
                        return@mapNotNull null
                    }

                    // 유효성 검사
                    if (faceRect.width() > 10 && faceRect.height() > 10 &&
                        faceRect.left >= 0 && faceRect.top >= 0 &&
                        faceRect.right <= imageWidth && faceRect.bottom <= imageHeight) {
                        faceRect
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

    // 🎯 이미지 향상 (얼굴 검출률 높이기)
    private fun enhanceImageForFaceDetection(bitmap: Bitmap): Bitmap {
        val enhanced = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(enhanced)
        val paint = Paint()

        // 대비 향상
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(1.2f) // 채도 약간 증가
        colorMatrix.set(floatArrayOf(
            1.1f, 0f, 0f, 0f, 20f,    // R 채널 증가
            0f, 1.1f, 0f, 0f, 20f,    // G 채널 증가
            0f, 0f, 1.1f, 0f, 20f,    // B 채널 증가
            0f, 0f, 0f, 1f, 0f        // A 채널 유지
        ))

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        Log.d("FaceMosaicHelper", "🎨 이미지 향상 완료")
        return enhanced
    }

    // 🎯 검출용 이미지 리사이즈
    private fun resizeBitmapForDetection(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }

        val scale = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // 🎯 얼굴 영역 확장 (더 확실한 모자이크)
    private fun expandFaceRect(rect: RectF, imageWidth: Int, imageHeight: Int): RectF {
        val faceWidth = rect.width()
        val faceHeight = rect.height()
        val faceArea = faceWidth * faceHeight
        val imageArea = imageWidth * imageHeight

        // 🎯 얼굴 크기에 따른 적응형 확장 비율
        val expandRatio = when {
            faceArea < imageArea * 0.01f -> {
                // 매우 작은 얼굴 (이미지의 1% 미만): 확장 최소화
                Log.d("FaceMosaicHelper", "🔍 매우 작은 얼굴 감지 - 확장 최소화")
                0.1f  // 10%만 확장
            }
            faceArea < imageArea * 0.03f -> {
                // 작은 얼굴 (이미지의 3% 미만): 적당한 확장
                Log.d("FaceMosaicHelper", "🔍 작은 얼굴 감지 - 적당한 확장")
                0.15f  // 15% 확장
            }
            faceArea < imageArea * 0.1f -> {
                // 중간 얼굴 (이미지의 10% 미만): 일반 확장
                Log.d("FaceMosaicHelper", "🔍 중간 얼굴 감지 - 일반 확장")
                0.2f   // 20% 확장
            }
            else -> {
                // 큰 얼굴 (이미지의 10% 이상): 기존 확장
                Log.d("FaceMosaicHelper", "🔍 큰 얼굴 감지 - 기존 확장")
                0.3f   // 30% 확장
            }
        }

        val expandWidth = faceWidth * expandRatio
        val expandHeight = faceHeight * expandRatio

        Log.d("FaceMosaicHelper", "📏 얼굴 크기: ${faceWidth.toInt()}x${faceHeight.toInt()}, 확장비율: ${(expandRatio*100).toInt()}%")

        return RectF(
            maxOf(0f, rect.left - expandWidth / 2),
            maxOf(0f, rect.top - expandHeight / 2),
            minOf(imageWidth.toFloat(), rect.right + expandWidth / 2),
            minOf(imageHeight.toFloat(), rect.bottom + expandHeight / 2)
        )
    }

    // 🎯 중복 얼굴 제거
    private fun removeDuplicateFaces(faces: List<RectF>): List<RectF> {
        if (faces.size <= 1) return faces

        val uniqueFaces = mutableListOf<RectF>()

        for (face in faces) {
            var isDuplicate = false
            for (existing in uniqueFaces) {
                // 두 얼굴이 50% 이상 겹치면 중복으로 판단
                val intersection = RectF()
                if (intersection.setIntersect(face, existing)) {
                    val intersectionArea = intersection.width() * intersection.height()
                    val faceArea = face.width() * face.height()
                    val overlapRatio = intersectionArea / faceArea

                    if (overlapRatio > 0.5f) {
                        isDuplicate = true
                        break
                    }
                }
            }

            if (!isDuplicate) {
                uniqueFaces.add(face)
            }
        }

        return uniqueFaces
    }

    // 🎯 기존 함수들 유지
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

    fun detectFaces(inputBitmap: Bitmap): List<RectF> {
        return detectFacesMultipleWays(inputBitmap)
    }

    fun close() {
        try {
            faceDetector?.close()
            backupDetector?.close()
            Log.d("FaceMosaicHelper", "FaceMosaicHelper 리소스 정리 완료")
        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "리소스 정리 실패: ${e.message}")
        }
    }
}