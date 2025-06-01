package com.SICV.plurry.unity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.SICV.plurry.R

class MainUnityActivity : AppCompatActivity() {

    private lateinit var startUnityButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_unity_main)

        // UI 요소 초기화
        setupUI()

        // 인텐트에서 데이터 확인 (유니티에서 돌아왔을 때 데이터 수신)
        handleIntent(intent)
    }

    private fun setupUI() {
        startUnityButton = findViewById(R.id.b_go_unity)

        startUnityButton.setOnClickListener {
            startUnityActivity()
        }
    }

    private fun startUnityActivity() {
        try {
            // SharedClass를 사용하여 Unity 액티비티 시작
            SharedClass.startUnityActivity(this)
        } catch (e: Exception) {
            Log.e("MainUnityActivity", "Error starting Unity activity", e)
            Toast.makeText(this, "Unity 활동 시작 오류: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.extras != null) {
            // 유니티에서 돌아왔을 때 데이터 처리
            val dataFromUnity = intent.getStringExtra("dataFromUnity")
            if (dataFromUnity != null) {
                Toast.makeText(this, "Unity에서 받은 데이터: $dataFromUnity", Toast.LENGTH_SHORT).show()
                // 필요하다면 UI 업데이트
            }
        }
    }
}