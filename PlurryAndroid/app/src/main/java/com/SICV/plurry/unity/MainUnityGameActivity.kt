package com.SICV.plurry.unity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import com.SICV.plurry.R
import com.unity3d.player.UnityPlayerGameActivity

class MainUnityGameActivity : UnityPlayerGameActivity() {

    private lateinit var androidUIContainer: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainUnityGameActivity", "Adding Android UI layer on top of Unity")

        try {
            // 루트 레이아웃 가져오기 (이것은 FrameLayout일 것입니다)
            val rootLayout = findViewById<android.widget.FrameLayout>(contentViewId)

            // 투명 레이아웃 준비
            val inflater = LayoutInflater.from(this)
            androidUIContainer = inflater.inflate(R.layout.overlay_unity_ui, rootLayout, false) as ViewGroup

            // 레이아웃 파라미터 설정
            val layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // RaisingMainActivity 프래그먼트 추가
            addRaisingFragment()

            // 투명 레이아웃을 Unity 뷰 위에 추가
            rootLayout.addView(androidUIContainer, layoutParams)

            // UI 요소들의 이벤트 리스너 설정
            setupUIElements()

            Log.d("MainUnityGameActivity", "Android UI overlay added successfully")
        } catch (e: Exception) {
            Log.e("MainUnityGameActivity", "Error adding Android UI overlay", e)
        }

        // Intent에서 전달된 데이터 처리
        handleIntent(intent)
    }

    private fun addRaisingFragment() {
        try {
            // FrameLayout 직접 만들어서 ID 할당
            val fragmentContainer = FrameLayout(this)
            fragmentContainer.id = View.generateViewId() // 동적으로 ID 생성

            val containerParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            containerParams.topMargin = 56
            containerParams.bottomMargin = 100

            (androidUIContainer as FrameLayout).addView(fragmentContainer, containerParams)

            Log.d("MainUnityGameActivity", "RaisingMainActivity fragment added successfully")
        } catch (e: Exception) {
            Log.e("MainUnityGameActivity", "Error adding RaisingMainActivity fragment", e)
        }
    }

    private fun setupUIElements() {
        try {
            // 버튼 이벤트 설정
            val btnSendToUnity = androidUIContainer.findViewById<Button>(R.id.btn_send_to_unity)
            btnSendToUnity?.setOnClickListener {
                Toast.makeText(this, "유니티로 데이터 전송 기능은 아직 구현되지 않았습니다", Toast.LENGTH_SHORT).show()
            }

            val btnGoBack = androidUIContainer.findViewById<Button>(R.id.btn_go_back)
            btnGoBack?.setOnClickListener {
                finish()
            }
        } catch (e: Exception) {
            Log.e("MainUnityGameActivity", "Error setting up UI elements", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null || intent.extras == null) return

        // 종료 요청 처리
        if (intent.extras!!.containsKey("doQuit")) {
            finish()
        }
    }

    override fun onUnityPlayerUnloaded() {
        super.onUnityPlayerUnloaded()
        finish()
    }
}