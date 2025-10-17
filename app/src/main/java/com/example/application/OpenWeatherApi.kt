package com.example.application

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenWeatherResponse
 *
 * Minimal model for One Call "time machine" results. Only temperature fields
 * are included because the app derives a single ambient temperature value.
 *
 * Notes
 * - Different payload shapes are supported by the API (e.g., `current`, `hourly`,
 *   legacy `data` list). We probe them in that order and use the first non-null `temp`.
 */
@Serializable
data class OpenWeatherResponse(
    val current: Current? = null,
    val hourly: List<Hourly>? = null,
    val data: List<DataItem>? = null
)

/** Current conditions block with temperature in °C when `units=metric`. */
@Serializable data class Current(val temp: Double? = null)

/** Hourly forecast/observations with temperature in °C. */
@Serializable data class Hourly(val temp: Double? = null)

/** Legacy/alternate list item containing temperature in °C. */
@Serializable data class DataItem(val temp: Double? = null)

/**
 * OpenWeatherApi
 *
 * Retrofit interface for One Call "time machine" endpoint (v3.0).
 *
 * @param lat   Latitude in decimal degrees.
 * @param lon   Longitude in decimal degrees.
 * @param dt    UNIX timestamp (seconds) for the target time.
 * @param apiKey OpenWeather API key.
 * @param units Units of measure (default `metric` ⇒ °C).
 *
 * Response
 * - See [OpenWeatherResponse]; callers should pick the first available temp.
 */
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
