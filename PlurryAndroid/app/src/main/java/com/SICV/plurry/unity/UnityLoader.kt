package com.SICV.plurry.unity

import android.content.Context
import android.util.Log

/**
 * 유니티 네이티브 라이브러리를 로드하기 위한 클래스
 */
object UnityLoader {
    private var isLoaded = false

    /**
     * 유니티 네이티브 라이브러리 로드
     * Application 클래스나 MainActivity의 onCreate에서 호출해야 함
     */
    fun loadLibraries(context: Context) {
        if (isLoaded) return

        try {
            Log.d("UnityLoader", "유니티 라이브러리 로드 시작")

            // 먼저 기본 라이브러리들 로드
            System.loadLibrary("unity")
            System.loadLibrary("main")
            System.loadLibrary("unityplayer")

            // 유니티 게임에 사용되는 추가 라이브러리가 있다면 여기에 추가
            // System.loadLibrary("추가라이브러리명")

            isLoaded = true
            Log.d("UnityLoader", "유니티 라이브러리 로드 성공")
        } catch (e: UnsatisfiedLinkError) {
            Log.e("UnityLoader", "유니티 라이브러리 로드 실패: ${e.message}", e)
            throw e
        } catch (e: Exception) {
            Log.e("UnityLoader", "라이브러리 로드 중 예외 발생: ${e.message}", e)
            throw e
        }
    }

    fun isLibraryLoaded(): Boolean {
        return isLoaded
    }
}