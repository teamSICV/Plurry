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
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("blaze_face_short_range.tflite")
                .build()

            val baseDetectorOptions = FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinDetectionConfidence(0.5f)
                .setMinSuppressionThreshold(0.6f)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            faceDetector = FaceDetector.createFromOptions(context, baseDetectorOptions)
            Log.d("FaceMosaicHelper", "✅ 얼굴 검출기 초기화 성공 (confidence: 0.5)")

            // 백업 검출기도 더 엄격하게
            try {
                val backupDetectorOptions = FaceDetectorOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinDetectionConfidence(0.4f) // 0.3 → 0.4로 상향
                    .setMinSuppressionThreshold(0.5f) // 0.4 → 0.5로 상향
                    .setRunningMode(RunningMode.IMAGE)
                    .build()

                backupDetector = FaceDetector.createFromOptions(context, backupDetectorOptions)
                Log.d("FaceMosaicHelper", "✅ 백업 얼굴 검출기 초기화 성공 (confidence: 0.4)")
            } catch (e: Exception) {
                Log.w("FaceMosaicHelper", "백업 검출기 초기화 실패: ${e.message}")
            }

        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "⚫ 얼굴 검출기 초기화 실패: ${e.message}", e)
        }
    }

    private fun detectFacesWithImprovedStrategy(inputBitmap: Bitmap): List<RectF> {
        val allFaces = mutableListOf<RectF>()

        Log.d("FaceMosaicHelper", "🔍 엄격한 얼굴 탐지 시작 - 원본 크기: ${inputBitmap.width}x${inputBitmap.height}")

        // 1단계: 원본 해상도에서 기본 검출 (가장 신뢰도 높은 방법만)
        Log.d("FaceMosaicHelper", "🔍 1단계: 원본 해상도 엄격한 검출")
        val faces1 = detectFacesWithDetector(inputBitmap, faceDetector, "원본_엄격한검출")
        allFaces.addAll(faces1)

        // 얼굴을 찾았으면 더 이상 시도하지 않음 (오탐 방지)
        if (allFaces.isNotEmpty()) {
            Log.d("FaceMosaicHelper", "🎯 1단계에서 ${allFaces.size}개 얼굴 발견 - 추가 탐지 중단")
            val uniqueFaces = removeDuplicateFaces(allFaces)
            return uniqueFaces
        }

        // 2단계: 백업 검출기로 한 번만 더 시도
        if (allFaces.isEmpty() && backupDetector != null) {
            Log.d("FaceMosaicHelper", "🔍 2단계: 백업 검출기 사용")
            val faces2 = detectFacesWithDetector(inputBitmap, backupDetector, "백업검출기")
            allFaces.addAll(faces2)
        }

        val uniqueFaces = removeDuplicateFaces(allFaces)
        Log.d("FaceMosaicHelper", "🎯 최종 검출된 얼굴: ${uniqueFaces.size}개")

        return uniqueFaces
    }

    fun applyFaceMosaic(inputBitmap: Bitmap, mosaicSize: Int = 20): Bitmap? {
        return try {
            val startTime = System.currentTimeMillis()

            // MediaPipe 안정 포맷 강제
            val working = if (inputBitmap.config != Bitmap.Config.ARGB_8888)
                inputBitmap.copy(Bitmap.Config.ARGB_8888, true) else inputBitmap

            Log.d("FaceMosaicHelper", "🎭 개선된 모자이크 처리 시작 - 이미지 크기: ${working.width}x${working.height}")

            // 다단계 얼굴 탐지
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


    private fun detectFacesWithDetector(bitmap: Bitmap, detector: FaceDetector?, detectorName: String): List<RectF> {
        return try {
            if (detector == null) {
                Log.w("FaceMosaicHelper", "$detectorName: 검출기가 null")
                return emptyList()
            }

            val mpImage = BitmapImageBuilder(bitmap).build()
            val detectionResult = detector.detect(mpImage)

            val detectedCount = detectionResult?.detections()?.size ?: 0
            Log.d("FaceMosaicHelper", "$detectorName: ${detectedCount}개 원시 탐지")

            detectionResult?.detections()?.mapNotNull { detection ->
                try {
                    val box = detection.boundingBox()
                    val confidence = detection.categories().firstOrNull()?.score() ?: 0f

                    // 더 엄격한 신뢰도 임계값 적용
                    val minConfidence = 0.4f // 모든 경우에 40% 이상 요구

                    if (confidence < minConfidence) {
                        Log.d("FaceMosaicHelper", "$detectorName: 신뢰도 부족으로 제외 (${(confidence * 100).toInt()}% < ${(minConfidence * 100).toInt()}%)")
                        return@mapNotNull null
                    }

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

                    // 더 엄격한 경계 체크 (오차 범위 줄임)
                    if (faceRect.width() > 10 && faceRect.height() > 10 &&  // 최소 크기 증가
                        faceRect.left >= 0 && faceRect.top >= 0 &&          // 경계 오차 제거
                        faceRect.right <= imageWidth && faceRect.bottom <= imageHeight) {

                        // 얼굴 크기가 너무 작거나 크면 제외 (오탐 방지)
                        val faceArea = faceRect.width() * faceRect.height()
                        val imageArea = imageWidth * imageHeight
                        val areaRatio = faceArea / imageArea

                        if (areaRatio < 0.001f) {  // 전체 이미지의 0.1% 미만이면 제외
                            Log.d("FaceMosaicHelper", "$detectorName: 얼굴이 너무 작아서 제외 (${(areaRatio * 100).toInt()}%)")
                            return@mapNotNull null
                        }

                        if (areaRatio > 0.8f) {    // 전체 이미지의 80% 이상이면 제외 (오탐 가능성)
                            Log.d("FaceMosaicHelper", "$detectorName: 얼굴이 너무 커서 제외 (${(areaRatio * 100).toInt()}%)")
                            return@mapNotNull null
                        }

                        Log.d("FaceMosaicHelper", "$detectorName: ✅ 유효한 얼굴 발견 - 신뢰도: ${(confidence * 100).toInt()}%, 크기: ${faceRect.width().toInt()}x${faceRect.height().toInt()}")
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

        // 확장 비율을 매우 작게 설정 (기존 0.2~0.4 → 0.05~0.1로 축소)
        val expandRatio = 0.05f  // 모든 경우에 5%만 확장
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