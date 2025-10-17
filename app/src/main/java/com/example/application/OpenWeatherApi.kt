package com.example.application

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query


@Serializable
data class OpenWeatherResponse(
    val current: Current? = null,
    val hourly: List<Hourly>? = null,
    val data: List<DataItem>? = null
)

@Serializable data class Current(val temp: Double? = null)
@Serializable data class Hourly(val temp: Double? = null)
@Serializable data class DataItem(val temp: Double? = null)

interface OpenWeatherApi {
    @GET("data/3.0/onecall/timemachine")
    suspend fun timeMachine(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("dt") dt: Long,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): OpenWeatherResponse
}
