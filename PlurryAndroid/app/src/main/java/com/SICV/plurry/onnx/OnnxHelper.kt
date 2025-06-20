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

    // 클래스 해제시 세션 정리
    fun close() {
        session.close()
    }

}
