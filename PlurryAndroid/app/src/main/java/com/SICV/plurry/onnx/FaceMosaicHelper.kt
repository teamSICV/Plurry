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
import java.io.ByteArrayOutputStream

class FaceMosaicHelper(private val context: Context) {

    private var faceDetector: FaceDetector? = null

    init {
        initializeFaceDetector()
    }

    private fun initializeFaceDetector() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("blaze_face_short_range.tflite") // 경로 주의ㅎㅏ기!
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

            // MediaPipe 이미지 형식으로 변환
            val mpImage = BitmapImageBuilder(inputBitmap).build()

            // 얼굴 검출 실행(실행되는지 확인하는 용도 나중에는 지울 예정)
            val detectionResult = faceDetector?.detect(mpImage)

            if (detectionResult?.detections()?.isEmpty() == true) {
                Log.d("FaceMosaicHelper", "검출된 얼굴이 없습니다")
                return inputBitmap
            }

            val mutableBitmap = inputBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(mutableBitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            detectionResult?.detections()?.forEach { detection ->
                val box = detection.boundingBox()

                val imageWidth = inputBitmap.width.toFloat()
                val imageHeight = inputBitmap.height.toFloat()

                val left = (box.left * imageWidth).toInt()
                val top = (box.top * imageHeight).toInt()
                val width = (box.width() * imageWidth).toInt()
                val height = (box.height() * imageHeight).toInt()

                Log.d("FaceMosaicHelper", "얼굴 검출: ($left, $top) 크기: ${width}x${height}")

                val mosaicBitmap = createMosaicBitmap(inputBitmap, left, top, width, height, mosaicSize)
                canvas.drawBitmap(mosaicBitmap, left.toFloat(), top.toFloat(), paint)
            }

            val endTime = System.currentTimeMillis()
            Log.d("FaceMosaicHelper", "모자이크 처리 완료: ${endTime - startTime}ms")
            mutableBitmap

        } catch (e: Exception) {
            Log.e("FaceMosaicHelper", "모자이크 처리 실패: ${e.message}", e)
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

        if (safeWidth <= 0 || safeHeight <= 0) {
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
        return try {
            val mpImage = BitmapImageBuilder(inputBitmap).build()
            val detectionResult = faceDetector?.detect(mpImage)

            detectionResult?.detections()?.map { detection ->
                val box = detection.boundingBox()
                val imageWidth = inputBitmap.width.toFloat()
                val imageHeight = inputBitmap.height.toFloat()

                RectF(
                    box.left * imageWidth,
                    box.top * imageHeight,
                    (box.left + box.width()) * imageWidth,
                    (box.top + box.height()) * imageHeight
                )
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
