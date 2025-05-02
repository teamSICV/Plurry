package com.SICV.plurry.unity

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout
import com.unity3d.player.IUnityPlayerLifecycleEvents

/**
 * 유니티 플레이어 래퍼 클래스
 * UnityPlayerForGameActivity 클래스가 제대로 로드되지 않는 경우를 대비한 대체 클래스
 */
class UnityPlayerWrapper(
    private val context: Context,
    private val container: ViewGroup,
    private val lifecycleEvents: IUnityPlayerLifecycleEvents? = null
) {
    private var surfaceView: SurfaceView? = null

    init {
        Log.d("UnityPlayerWrapper", "UnityPlayerWrapper 초기화")

        try {
            // SurfaceView 생성
            surfaceView = SurfaceView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                // 투명 배경 설정
                holder.setFormat(PixelFormat.TRANSLUCENT)

                // 배경색 설정
                setBackgroundColor(0xFF000000.toInt())

                // 포커스 가능하도록 설정
                isFocusable = true
                isFocusableInTouchMode = true
            }

            // 컨테이너에 추가
            container.addView(surfaceView)

            Log.d("UnityPlayerWrapper", "SurfaceView 생성 완료")
        } catch (e: Exception) {
            Log.e("UnityPlayerWrapper", "UnityPlayerWrapper 초기화 실패: ${e.message}", e)
        }
    }

    fun onResume() {
        Log.d("UnityPlayerWrapper", "onResume")
        // 실제 유니티 플레이어가 로드되면 여기에 구현
    }

    fun onPause() {
        Log.d("UnityPlayerWrapper", "onPause")
        // 실제 유니티 플레이어가 로드되면 여기에 구현
    }

    fun onStart() {
        Log.d("UnityPlayerWrapper", "onStart")
        // 실제 유니티 플레이어가 로드되면 여기에 구현
    }

    fun onStop() {
        Log.d("UnityPlayerWrapper", "onStop")
        // 실제 유니티 플레이어가 로드되면 여기에 구현
    }

    fun destroy() {
        Log.d("UnityPlayerWrapper", "destroy")

        try {
            // SurfaceView 제거
            container.removeView(surfaceView)
            surfaceView = null

            Log.d("UnityPlayerWrapper", "리소스 정리 완료")
        } catch (e: Exception) {
            Log.e("UnityPlayerWrapper", "destroy 실패: ${e.message}", e)
        }
    }

    fun configurationChanged(newConfig: Configuration) {
        Log.d("UnityPlayerWrapper", "configurationChanged")
        // 실제 유니티 플레이어가 로드되면 여기에 구현
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        return false // 실제 유니티 플레이어가 로드되면 여기에 구현
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return false // 실제 유니티 플레이어가 로드되면 여기에 구현
    }

    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return false // 실제 유니티 플레이어가 로드되면 여기에 구현
    }

    fun onWindowFocusChanged(hasFocus: Boolean) {
        // 실제 유니티 플레이어가 로드되면 여기에 구현
    }

    fun newIntent(intent: android.content.Intent) {
        Log.d("UnityPlayerWrapper", "newIntent")
        // 실제 유니티 플레이어가 로드되면 여기에 구현
    }
}