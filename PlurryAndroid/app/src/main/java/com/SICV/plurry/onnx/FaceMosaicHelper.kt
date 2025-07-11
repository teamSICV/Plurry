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

    init {
        initializeFaceDetector()
    }

    private fun initializeFaceDetector() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("blaze_face_short_range.tflite")
                .build()

            val options = FaceDetectorOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinDetectionConfidence(0.5f)
                .setRunningMode(RunningMode.IMAGE)
                .build()

            faceDetector = FaceDetector.createFromOptions(context, options)
            Log.d("FaceMosaicHelper", "MediaPipe 얼굴 검출기 초기화 성공")

        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "얼굴 검출기 초기화 실패: ${e.message}", e)
        }
    }

    fun applyFaceMosaic(inputBitmap: Bitmap, mosaicSize: Int = 20): Bitmap? {
        return try {
            val startTime = System.currentTimeMillis()

            Log.d("FaceMosaicHelper", "🎭 모자이크 처리 시작 - 이미지 크기: ${inputBitmap.width}x${inputBitmap.height}")

            // MediaPipe 이미지 형식으로 변환
            val mpImage = BitmapImageBuilder(inputBitmap).build()

            // 얼굴 검출 실행
            val detectionResult = faceDetector?.detect(mpImage)

            Log.d("FaceMosaicHelper", "🔍 얼굴 검출 완료")

            if (detectionResult?.detections()?.isEmpty() == true) {
                Log.d("FaceMosaicHelper", "❌ 검출된 얼굴이 없습니다")
                return inputBitmap
            }

            val detectedFaces = detectionResult?.detections()?.size ?: 0
            Log.d("FaceMosaicHelper", "✅ ${detectedFaces}개 얼굴 검출됨")

            val mutableBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            detectionResult?.detections()?.forEachIndexed { index, detection ->
                try {
                    val boundingBox = detection.boundingBox()

                    // 🔧 MediaPipe boundingBox 값 직접 로깅
                    Log.d("FaceMosaicHelper", "🔍 원본 boundingBox ${index + 1}:")
                    Log.d("FaceMosaicHelper", "   - left: ${boundingBox.left}")
                    Log.d("FaceMosaicHelper", "   - top: ${boundingBox.top}")
                    Log.d("FaceMosaicHelper", "   - width(): ${boundingBox.width()}")
                    Log.d("FaceMosaicHelper", "   - height(): ${boundingBox.height()}")
                    Log.d("FaceMosaicHelper", "   - right: ${boundingBox.right}")
                    Log.d("FaceMosaicHelper", "   - bottom: ${boundingBox.bottom}")

                    val imageWidth = inputBitmap.width
                    val imageHeight = inputBitmap.height

                    // 🔧 좌표 변환 방식 수정
                    val left: Int
                    val top: Int
                    val width: Int
                    val height: Int

                    // MediaPipe가 정규화된 좌표를 반환하는지 확인
                    if (boundingBox.left <= 1.0f && boundingBox.top <= 1.0f &&
                        boundingBox.width() <= 1.0f && boundingBox.height() <= 1.0f) {
                        // 정규화된 좌표 (0.0-1.0)
                        left = (boundingBox.left * imageWidth).toInt()
                        top = (boundingBox.top * imageHeight).toInt()
                        width = (boundingBox.width() * imageWidth).toInt()
                        height = (boundingBox.height() * imageHeight).toInt()
                        Log.d("FaceMosaicHelper", "✅ 정규화된 좌표로 처리")
                    } else {
                        // 이미 픽셀 좌표
                        left = boundingBox.left.toInt()
                        top = boundingBox.top.toInt()
                        width = boundingBox.width().toInt()
                        height = boundingBox.height().toInt()
                        Log.d("FaceMosaicHelper", "✅ 픽셀 좌표로 처리")
                    }

                    Log.d("FaceMosaicHelper", "🎯 최종 얼굴 ${index + 1}: ($left, $top) 크기: ${width}x${height}")

                    // 좌표 유효성 검사
                    if (left >= 0 && top >= 0 && width > 0 && height > 0 &&
                        left < imageWidth && top < imageHeight &&
                        left + width <= imageWidth && top + height <= imageHeight) {

                        val mosaicBitmap = createMosaicBitmap(inputBitmap, left, top, width, height, mosaicSize)
                        canvas.drawBitmap(mosaicBitmap, left.toFloat(), top.toFloat(), paint)

                        Log.d("FaceMosaicHelper", "🎨 얼굴 ${index + 1} 모자이크 적용 완료")
                    } else {
                        Log.w("FaceMosaicHelper", "⚠️ 얼굴 ${index + 1} 좌표가 유효하지 않음 - 스킵")
                        Log.w("FaceMosaicHelper", "   좌표: ($left, $top), 크기: ${width}x${height}, 이미지: ${imageWidth}x${imageHeight}")
                    }

                } catch (e: Exception) {
                    Log.e("FaceMosaicHelper", "얼굴 ${index + 1} 처리 중 오류: ${e.message}")
                }
            }

            val endTime = System.currentTimeMillis()
            Log.d("FaceMosaicHelper", "✅ 모자이크 처리 완료: ${endTime - startTime}ms, ${detectedFaces}개 얼굴 처리")
            mutableBitmap

        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "❌ 모자이크 처리 실패: ${e.message}", e)
            null
        }
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

        Log.d("FaceMosaicHelper", "🔍 축소 크기: ${smallWidth}x${smallHeight}")

        val smallBitmap = Bitmap.createScaledBitmap(faceBitmap, smallWidth, smallHeight, false)
        val mosaicBitmap = Bitmap.createScaledBitmap(smallBitmap, safeWidth, safeHeight, false)

        Log.d("FaceMosaicHelper", "✅ 모자이크 비트맵 생성 완료: ${mosaicBitmap.width}x${mosaicBitmap.height}")

        faceBitmap.recycle()
        smallBitmap.recycle()

        return mosaicBitmap
    }

    fun detectFaces(inputBitmap: Bitmap): List<RectF> {
        return try {
            val mpImage = BitmapImageBuilder(inputBitmap).build()
            val detectionResult = faceDetector?.detect(mpImage)

            detectionResult?.detections()?.map { detection ->
                val box = detection.boundingBox()
                val imageWidth = inputBitmap.width.toFloat()
                val imageHeight = inputBitmap.height.toFloat()

                if (box.left <= 1.0f && box.top <= 1.0f) {
                    // 정규화된 좌표
                    RectF(
                        box.left * imageWidth,
                        box.top * imageHeight,
                        (box.left + box.width()) * imageWidth,
                        (box.top + box.height()) * imageHeight
                    )
                } else {
                    // 픽셀 좌표
                    RectF(
                        box.left,
                        box.top,
                        box.left + box.width(),
                        box.top + box.height()
                    )
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "얼굴 검출 실패: ${e.message}", e)
            emptyList()
        }
    }

    fun close() {
        try {
            faceDetector?.close()
            Log.d("FaceMosaicHelper", "FaceMosaicHelper 리소스 정리 완료")
        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "리소스 정리 실패: ${e.message}")
        }
    }
}