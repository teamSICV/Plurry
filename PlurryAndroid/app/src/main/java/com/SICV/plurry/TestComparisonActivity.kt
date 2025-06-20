package com.SICV.plurry

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.onnx.OnnxHelper
import com.SICV.plurry.onnx.OnnxComparator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class TestComparisonActivity : AppCompatActivity() {

    private lateinit var onnxHelper: OnnxHelper
    private var bitmap1: Bitmap? = null
    private var bitmap2: Bitmap? = null
    private var currentImageSelector = 0 // 1 또는 2
    private lateinit var resultText: TextView

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleImageSelection(uri)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 코드로 레이아웃 생성
        createLayout()

        // ONNX Helper 초기화
        onnxHelper = OnnxHelper(this)

        Toast.makeText(this, "이미지 비교 테스트 준비 완료!", Toast.LENGTH_SHORT).show()
    }

    private fun createLayout() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 50, 50, 50)
            gravity = Gravity.CENTER
        }

        // 제목
        val title = TextView(this).apply {
            text = "🔍 이미지 유사도 테스트"
            textSize = 24f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 50)
            setTextColor(android.graphics.Color.BLACK)
        }

        // 버튼 1: 첫 번째 이미지 선택
        val btn1 = Button(this).apply {
            text = "📷 이미지 1 선택"
            textSize = 16f
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
            setOnClickListener {
                currentImageSelector = 1
                Toast.makeText(this@TestComparisonActivity, "이미지 1 선택 모드", Toast.LENGTH_SHORT).show()
                openGallery()
            }
        }

        // 버튼 2: 두 번째 이미지 선택
        val btn2 = Button(this).apply {
            text = "📷 이미지 2 선택"
            textSize = 16f
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 20
            }
            setOnClickListener {
                currentImageSelector = 2
                Toast.makeText(this@TestComparisonActivity, "이미지 2 선택 모드", Toast.LENGTH_SHORT).show()
                openGallery()
            }
        }

        // 비교 버튼
        val compareBtn = Button(this).apply {
            text = "🔍 이미지 비교하기"
            textSize = 18f
            setPadding(20, 30, 20, 30)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 40
            }
            setBackgroundColor(android.graphics.Color.parseColor("#FF4081"))
            setTextColor(android.graphics.Color.WHITE)
            setOnClickListener {
                compareImages()
            }
        }

        // 결과 텍스트
        resultText = TextView(this).apply {
            text = "이미지를 선택하고 비교해보세요"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(20, 20, 20, 20)
            setTextColor(android.graphics.Color.GRAY)
            setBackgroundColor(android.graphics.Color.parseColor("#F5F5F5"))
        }

        // 레이아웃에 추가
        layout.addView(title)
        layout.addView(btn1)
        layout.addView(btn2)
        layout.addView(compareBtn)
        layout.addView(resultText)

        setContentView(layout)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun handleImageSelection(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // 디버깅 추가
            android.util.Log.d("TestComparison", "currentImageSelector: $currentImageSelector")

            if (currentImageSelector == 1) {
                bitmap1 = bitmap
                Toast.makeText(this, "✅ 이미지 1 선택 완료!", Toast.LENGTH_SHORT).show()
                updateResultText()
            } else if (currentImageSelector == 2) {
                bitmap2 = bitmap
                Toast.makeText(this, "✅ 이미지 2 선택 완료!", Toast.LENGTH_SHORT).show()
                updateResultText()
            } else {
                Toast.makeText(this, "❌ 오류: currentImageSelector = $currentImageSelector", Toast.LENGTH_SHORT).show()
            }

        } catch (e: IOException) {
            Toast.makeText(this, "이미지 로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateResultText() {
        val status1 = if (bitmap1 != null) "✅" else "❌"
        val status2 = if (bitmap2 != null) "✅" else "❌"
        resultText.text = "이미지 1: $status1  이미지 2: $status2\n\n두 이미지를 모두 선택하고 비교해보세요"
    }

    private fun compareImages() {
        val bmp1 = bitmap1
        val bmp2 = bitmap2

        if (bmp1 == null || bmp2 == null) {
            Toast.makeText(this, "두 이미지를 모두 선택해주세요!", Toast.LENGTH_SHORT).show()
            return
        }

        resultText.text = "⏳ 이미지 비교 중..."
        Toast.makeText(this, "이미지 비교 중...", Toast.LENGTH_SHORT).show()

        // 백그라운드에서 이미지 비교 실행
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // ONNX 추론 실행
                val features1 = onnxHelper.runInference(bmp1)
                val features2 = onnxHelper.runInference(bmp2)

                if (features1 != null && features2 != null) {
                    // 코사인 유사도 계산
                    val similarity = OnnxComparator.cosineSimilarity(features1, features2)

                    // UI 스레드에서 결과 표시
                    withContext(Dispatchers.Main) {
                        displayResult(similarity)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        resultText.text = "❌ 이미지 처리 실패"
                        Toast.makeText(this@TestComparisonActivity, "이미지 처리 실패", Toast.LENGTH_LONG).show()
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    resultText.text = "❌ 비교 실패: ${e.message}"
                    Toast.makeText(this@TestComparisonActivity, "비교 실패: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun displayResult(similarity: Float) {
        val threshold = 0.8f
        val isMatch = similarity >= threshold

        val resultMessage = if (isMatch) {
            "✅ 유사한 이미지!\n\n유사도: ${String.format("%.4f", similarity)}"
        } else {
            "❌ 다른 이미지\n\n유사도: ${String.format("%.4f", similarity)}"
        }

        resultText.text = resultMessage
        resultText.setTextColor(
            if (isMatch) android.graphics.Color.parseColor("#4CAF50")
            else android.graphics.Color.parseColor("#F44336")
        )

        Toast.makeText(this, resultMessage, Toast.LENGTH_LONG).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        onnxHelper.close()
        bitmap1?.recycle()
        bitmap2?.recycle()
    }
}