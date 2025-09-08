package com.SICV.plurry.di

import com.SICV.plurry.safety.KakaoLocalApi
import com.SICV.plurry.safety.remote.SafetyService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitModule {
    // Kakao API
    private val kakaoRetrofit = Retrofit.Builder()
        .baseUrl("https://dapi.kakao.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val kakaoApi: KakaoLocalApi = kakaoRetrofit.create(KakaoLocalApi::class.java)

    // Firebase Functions (배포된 URL)
    private val safetyRetrofit = Retrofit.Builder()
        .baseUrl("https://asia-northeast1-plurry-855a9.cloudfunctions.net/") // 꼭 끝에 '/' 필요
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val safetyService: SafetyService = safetyRetrofit.create(SafetyService::class.java)
}

