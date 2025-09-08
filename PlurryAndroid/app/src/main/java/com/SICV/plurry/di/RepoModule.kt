package com.SICV.plurry.di

import com.SICV.plurry.safety.SafetyRepo

object RepoModule {
    // 실제 키로 교체: "KakaoAK xxxxxxxxxxxxxxxxxxxxx"
    private const val KAKAO_API_KEY = "KakaoAK a670ee4833315dafcd56da98e48b2a26"

    val safetyRepo: SafetyRepo by lazy {
        SafetyRepo(
            kakao = RetrofitModule.kakaoApi,
            safetyService = RetrofitModule.safetyService,
            kakaoApiKey = KAKAO_API_KEY
        )
    }
}
