package com.example.flock.network

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class OpenMeteoResponse(
    @Json(name = "latitude") val latitude: Double? = null,
    @Json(name = "longitude") val longitude: Double? = null,
    @Json(name = "current") val current: CurrentWeather? = null
)

data class CurrentWeather(
    @Json(name = "temperature_2m") val temperature2m: Double? = null,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Double? = null,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double? = null,
    @Json(name = "time") val time: String? = null
)

data class WeatherResult(
    val tempC: Double,
    val rhPercent: Double,
    val windKmh: Double?,
    val locationName: String,
    val isLive: Boolean
)

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,wind_speed_10m",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse
}

object WeatherClient {
    private const val BASE_URL = "https://api.open-meteo.com/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val api: OpenMeteoApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenMeteoApi::class.java)
    }

    suspend fun fetchWeather(lat: Double, lon: Double, locationName: String, season: String): WeatherResult {
        return try {
            val response = api.getCurrentWeather(latitude = lat, longitude = lon)
            val current = response.current
            val temp = current?.temperature2m ?: defaultTempForSeason(season)
            val rh = current?.relativeHumidity2m ?: defaultRhForSeason(season)
            val wind = current?.windSpeed10m
            WeatherResult(
                tempC = temp,
                rhPercent = rh,
                windKmh = wind,
                locationName = locationName,
                isLive = true
            )
        } catch (e: Exception) {
            // Gracefully fallback to season defaults
            WeatherResult(
                tempC = defaultTempForSeason(season),
                rhPercent = defaultRhForSeason(season),
                windKmh = null,
                locationName = locationName,
                isLive = false
            )
        }
    }

    fun defaultTempForSeason(season: String): Double = when (season.lowercase()) {
        "summer" -> 33.0
        "monsoon" -> 29.5
        "post-monsoon" -> 27.0
        "winter" -> 22.0
        else -> 29.5
    }

    fun defaultRhForSeason(season: String): Double = when (season.lowercase()) {
        "summer" -> 57.5
        "monsoon" -> 80.0
        "post-monsoon" -> 67.5
        "winter" -> 55.0
        else -> 75.0
    }
}
