package com.SICV.plurry.di

import com.SICV.plurry.safety.SafetyRepo

object RepoModule {
    val safetyRepo: SafetyRepo by lazy {
        SafetyRepo(
            kakao = RetrofitModule.kakaoApi,
            safetyService = RetrofitModule.safetyService
        )
    }

}
