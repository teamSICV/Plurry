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

        // Normalize to [0, 1] and CHW format
        for (c in 0..2) {
            for (y in 0 until 160) {
                for (x in 0 until 160) {
                    val pixel = resized.getPixel(x, y)
                    val value = when (c) {
                        0 -> ((pixel shr 16) and 0xFF) / 255.0f // R
                        1 -> ((pixel shr 8) and 0xFF) / 255.0f  // G
                        else -> (pixel and 0xFF) / 255.0f       // B
                    }
                    floatBuffer.put((c * 160 * 160) + (y * 160) + x, value)
                }
            }
        }

        return floatBuffer
    }

    fun runInference(bitmap: Bitmap): FloatArray {
        val inputName = session.inputNames.iterator().next()
        val inputBuffer = preprocess(bitmap)
        val inputShape = longArrayOf(1, 3, 160, 160)
        val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, inputShape)

        val output = session.run(mapOf(inputName to inputTensor)) // 추론 수행
        val result = output[0].value as Array<FloatArray>
        return result[0] // 512차원 벡터 하나 반환
        }

}
