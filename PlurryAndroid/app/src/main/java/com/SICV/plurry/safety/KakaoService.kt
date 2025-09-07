package com.SICV.plurry.safety

import com.SICV.plurry.BuildConfig as AppBuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object KakaoService {
    private val logger = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logger)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://dapi.kakao.com")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: KakaoLocalApi = retrofit.create(KakaoLocalApi::class.java)

    // 헤더 값 생성기
    fun authHeader(): String = "KakaoAK ${AppBuildConfig.KAKAO_REST_API_KEY}"
}

