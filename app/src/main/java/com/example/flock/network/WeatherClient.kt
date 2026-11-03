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
    @Json(name = "current") val current: CurrentWeather? = null,
    @Json(name = "daily") val daily: DailyBlock? = null,
    @Json(name = "hourly") val hourly: HourlyBlock? = null
)

data class HourlyBlock(
    @Json(name = "time") val time: List<String>? = null,
    @Json(name = "temperature_2m") val temp: List<Double?>? = null,
    @Json(name = "relative_humidity_2m") val rh: List<Double?>? = null,
    @Json(name = "weather_code") val code: List<Int?>? = null,
    @Json(name = "is_day") val isDay: List<Int?>? = null
)

/** One forecast hour at the farm: local time "yyyy-MM-ddTHH:mm", °C, %RH. */
data class HourPoint(val time: String, val tempC: Double, val rhPct: Double,
                     /** WMO weather code (0 clear … 95 thunderstorm), null when not known; [isDay] false at night */
                     val code: Int? = null, val isDay: Boolean = true) {
    val emoji: String get() = WeatherEmoji.of(code, isDay)
    val hour: Int get() = time.substringAfter('T').take(2).toIntOrNull() ?: 0
    val date: String get() = time.substringBefore('T')
}

data class DailyBlock(
    @Json(name = "time") val time: List<String>? = null,
    @Json(name = "temperature_2m_max") val tMax: List<Double>? = null,
    @Json(name = "temperature_2m_min") val tMin: List<Double>? = null,
    @Json(name = "precipitation_probability_max") val precipProb: List<Int?>? = null,
    @Json(name = "wind_speed_10m_max") val windMax: List<Double>? = null,
    @Json(name = "relative_humidity_2m_max") val rhMax: List<Double>? = null,
    @Json(name = "weather_code") val code: List<Int?>? = null
)

data class ForecastDay(
    val date: String,
    val tMaxC: Double?,
    val tMinC: Double?,
    val precipProbPct: Int?,
    val windKmh: Double?,
    val rhMaxPct: Double?,
    val confidencePct: Int,
    val code: Int? = null
) {
    val emoji: String get() = WeatherEmoji.of(code, true)
}

data class ForecastResult(
    val locationName: String,
    val lat: Double,
    val lon: Double,
    val days: List<ForecastDay>,
    val isLive: Boolean
)

data class CurrentWeather(
    @Json(name = "temperature_2m") val temperature2m: Double? = null,
    @Json(name = "relative_humidity_2m") val relativeHumidity2m: Double? = null,
    @Json(name = "wind_speed_10m") val windSpeed10m: Double? = null,
    @Json(name = "time") val time: String? = null,
    @Json(name = "weather_code") val weatherCode: Int? = null,
    @Json(name = "is_day") val isDay: Int? = null
)

data class WeatherResult(
    val tempC: Double,
    val rhPercent: Double,
    val windKmh: Double?,
    val locationName: String,
    val isLive: Boolean,
    /** WMO weather code now (null: not known) and whether it is day there */
    val code: Int? = null,
    val isDay: Boolean = true
) {
    val emoji: String get() = WeatherEmoji.of(code, isDay)
}

/** The sky as an emoji, from the WMO weather code Open-Meteo gives (night shows the moon when clear). */
object WeatherEmoji {
    fun of(code: Int?, isDay: Boolean = true): String = when (code) {
        null -> if (isDay) "🌡️" else "🌙"
        0 -> if (isDay) "☀️" else "🌙"
        1 -> if (isDay) "🌤️" else "🌙"
        2 -> if (isDay) "⛅" else "☁️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        51, 53, 55, 56, 57 -> "🌦️"
        61, 63, 65, 66, 67 -> "🌧️"
        71, 73, 75, 77, 85, 86 -> "🌨️"
        80, 81, 82 -> "🌦️"
        95, 96, 99 -> "⛈️"
        else -> "🌡️"
    }

    /** A few words for the code (for screen readers and the forecast). */
    fun words(code: Int?): String = when (code) {
        null -> ""; 0 -> "clear"; 1 -> "mostly clear"; 2 -> "partly cloudy"; 3 -> "overcast"; 45, 48 -> "fog"
        51, 53, 55, 56, 57 -> "drizzle"; 61, 63, 65, 66, 67 -> "rain"; 71, 73, 75, 77, 85, 86 -> "snow"
        80, 81, 82 -> "showers"; 95, 96, 99 -> "thunderstorm"; else -> ""
    }
}

interface OpenMeteoApi {
    @GET("v1/forecast")
    suspend fun getCurrentWeather(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code,is_day",
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse

    @GET("v1/forecast")
    suspend fun getForecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,relative_humidity_2m_max,weather_code",
        @Query("forecast_days") forecastDays: Int = 14,
        @Query("timezone") timezone: String = "auto"
    ): OpenMeteoResponse

    @GET("v1/forecast")
    suspend fun getHourly(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "temperature_2m,relative_humidity_2m,weather_code,is_day",
        @Query("forecast_days") forecastDays: Int = 3,
        @Query("past_days") pastDays: Int = 0,
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
                isLive = true,
                code = current?.weatherCode,
                isDay = current?.isDay?.let { it == 1 } ?: true
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

    /**
     * 14-day daily forecast. "Confidence" is a modelled estimate that decays with lead time
     * (weather APIs don't publish an accuracy figure), shown so users don't over-trust far-out days.
     */
    suspend fun fetchForecast(lat: Double, lon: Double, locationName: String): ForecastResult {
        return try {
            val res = api.getForecast(latitude = lat, longitude = lon)
            val d = res.daily
            val times = d?.time ?: emptyList()
            val days = times.indices.map { i ->
                ForecastDay(
                    date = times[i],
                    tMaxC = d?.tMax?.getOrNull(i),
                    tMinC = d?.tMin?.getOrNull(i),
                    precipProbPct = d?.precipProb?.getOrNull(i),
                    windKmh = d?.windMax?.getOrNull(i),
                    rhMaxPct = d?.rhMax?.getOrNull(i),
                    confidencePct = (95 - i * 3).coerceAtLeast(45),
                    code = d?.code?.getOrNull(i)
                )
            }
            ForecastResult(locationName, lat, lon, days, isLive = days.isNotEmpty())
        } catch (e: Exception) {
            ForecastResult(locationName, lat, lon, emptyList(), isLive = false)
        }
    }

    /** The last [pastDays] days and the next three, hourly, in the farm's local time. Empty when offline. */
    suspend fun fetchHourly(lat: Double, lon: Double, pastDays: Int = 0): List<HourPoint> = try {
        val h = api.getHourly(latitude = lat, longitude = lon, pastDays = pastDays.coerceIn(0, 92)).hourly
        val t = h?.time.orEmpty()
        t.indices.mapNotNull { i ->
            val tc = h?.temp?.getOrNull(i) ?: return@mapNotNull null
            val rh = h.rh?.getOrNull(i) ?: return@mapNotNull null
            HourPoint(t[i], tc, rh, h.code?.getOrNull(i), h.isDay?.getOrNull(i)?.let { it == 1 } ?: true)
        }
    } catch (e: Exception) { emptyList() }

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
