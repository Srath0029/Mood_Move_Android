package com.example.application

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

/**
 * OpenWeatherRetrofit
 *
 * Central Retrofit configuration for the OpenWeather API.
 *
 * Components
 * - BASE_URL: Root endpoint for OpenWeather.
 * - json: Kotlinx Serialization JSON with `ignoreUnknownKeys` and `explicitNulls=false`
 *         to tolerate schema changes and omit nulls.
 * - client: OkHttp client with a BASIC HTTP logger (request line + response status).
 * - api: Lazily-initialized [OpenWeatherApi] using Kotlinx Serialization converter.
 *
 * Notes
 * - Logging level is BASIC to avoid leaking query params in production logs.
 * - Converter media type is `application/json`.
 */
object OpenWeatherRetrofit {
    private const val BASE_URL = "https://api.openweathermap.org/"

    private val json by lazy {
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    private val client by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()
    }

    @OptIn(ExperimentalSerializationApi::class)
    val api: OpenWeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenWeatherApi::class.java)
    }
}
