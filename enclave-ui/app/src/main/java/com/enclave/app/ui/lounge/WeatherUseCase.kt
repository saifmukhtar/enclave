package com.enclave.app.ui.lounge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WeatherUseCase {
    suspend fun fetchWeatherForCity(city: String): Pair<Double, String>? {
        if (city.isBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val client = okhttp3.OkHttpClient()
                val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=${java.net.URLEncoder.encode(city, "UTF-8")}&count=1&language=en&format=json"
                val geoRequest = okhttp3.Request.Builder().url(geoUrl).build()
                val geoResponse = client.newCall(geoRequest).execute()
                if (!geoResponse.isSuccessful) return@withContext null
                val geoBody = geoResponse.body?.string() ?: return@withContext null
                val geoJson = org.json.JSONObject(geoBody)
                val results = geoJson.optJSONArray("results")
                if (results == null || results.length() == 0) return@withContext null
                val firstResult = results.getJSONObject(0)
                val lat = firstResult.getDouble("latitude")
                val lon = firstResult.getDouble("longitude")

                val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
                val weatherRequest = okhttp3.Request.Builder().url(weatherUrl).build()
                val weatherResponse = client.newCall(weatherRequest).execute()
                if (!weatherResponse.isSuccessful) return@withContext null
                val weatherBody = weatherResponse.body?.string() ?: return@withContext null
                val weatherJson = org.json.JSONObject(weatherBody)
                val current = weatherJson.getJSONObject("current")
                val temp = current.getDouble("temperature_2m")
                val code = current.getInt("weather_code")

                val conditionEmoji = when (code) {
                    0 -> "☀️"
                    1, 2, 3 -> "🌤️"
                    45, 48 -> "🌫️"
                    51, 53, 55 -> "🌧️"
                    61, 63, 65 -> "🌧️"
                    71, 73, 75 -> "❄️"
                    77 -> "❄️"
                    80, 81, 82 -> "🌧️"
                    85, 86 -> "❄️"
                    95 -> "⛈️"
                    96, 99 -> "⛈️"
                    else -> "🌤️"
                }
                Pair(temp, conditionEmoji)
            } catch (e: Exception) {
                android.util.Log.e("WeatherUseCase", "Failed to fetch weather for $city", e)
                null
            }
        }
    }
}
