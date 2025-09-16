package com.SICV.plurry.di

import com.SICV.plurry.safety.remote.KakaoLocalApi
import com.SICV.plurry.safety.remote.SafetyService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitModule {

    // 🔧 공통 OkHttp 클라이언트
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()
    }

    // 🔧 Gson Converter (Moshi 대신)
    private val gsonConverter: GsonConverterFactory by lazy {
        GsonConverterFactory.create()
    }

    // ✅ 반드시 마지막에 슬래시(/) 포함!
    private const val KAKAO_BASE = "https://dapi.kakao.com/"
    // ⬇️ 여기에 본인 Firebase Functions 도메인(끝에 /) 입력
    private const val SAFETY_BASE = "https://gettilesummary-edx73uugqq-an.a.run.app/"

    // Kakao Local API
    val kakaoApi: KakaoLocalApi by lazy {
        Retrofit.Builder()
            .baseUrl(KAKAO_BASE)
            .client(client)
            .addConverterFactory(gsonConverter)
            .build()
            .create(KakaoLocalApi::class.java)
    }

    // Firebase Functions (getTileSummary)
    val safetyService: SafetyService by lazy {
        Retrofit.Builder()
            .baseUrl(SAFETY_BASE)
            .client(client)
            .addConverterFactory(gsonConverter)
            .build()
            .create(SafetyService::class.java)
    }
}