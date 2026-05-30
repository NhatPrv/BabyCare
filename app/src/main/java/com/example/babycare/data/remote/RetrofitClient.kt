package com.example.babycare.data.remote

import com.example.babycare.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // BASE_URL is read from BuildConfig so it can be overridden in local.properties
    // Put these lines into local.properties in project root to override:
    // SERVER_BASE_URL=https://your-ngrok-id.ngrok-free.dev/
    // DOCTOR_URL=https://your-ngrok-id.ngrok-free.dev/doctor
    private val BASE_URL: String = BuildConfig.SERVER_BASE_URL

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    val apiService: ApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()

        Retrofit.Builder()
            .client(client)
            .baseUrl(BASE_URL)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
