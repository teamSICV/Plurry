package com.SICV.plurry.raising

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import com.SICV.plurry.R
import com.unity3d.player.UnityPlayerGameActivity

class RaisingMainActivity : UnityPlayerGameActivity() {

    private lateinit var androidUIContainer: ViewGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("MainUnityGameActivity", "Adding Android UI layer on top of Unity")

        try {
            // 루트 레이아웃 가져오기 (이것은 FrameLayout일 것입니다)
            val rootLayout = findViewById<android.widget.FrameLayout>(contentViewId)

            // 투명 레이아웃 준비
            val inflater = LayoutInflater.from(this)
            androidUIContainer = inflater.inflate(R.layout.activity_raising_main, rootLayout, false) as ViewGroup

            // 레이아웃 파라미터 설정
            val layoutParams = android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

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

    private fun setupUIElements() {
        try {
                val showPopupBtn = findViewById<Button>(R.id.b_PopUpTest)
                showPopupBtn.setOnClickListener {
                    showFloatingPopup()
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

    private fun showFloatingPopup() {
        val intent = Intent(this, RaisingStoryActivity::class.java)
        startActivity(intent)
    }
}