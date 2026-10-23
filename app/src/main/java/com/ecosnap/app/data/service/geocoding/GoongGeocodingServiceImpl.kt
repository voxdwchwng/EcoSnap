package com.ecosnap.app.data.service.geocoding

import android.content.Context
import android.util.Log
import com.ecosnap.app.data.util.AddressParserHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class GoongGeocodingServiceImpl(
    private val context: Context,
) : IGeocodingService {

    private val androidGeocoderService by lazy { GeocoderServiceImpl(context) }

    companion object {
        private const val GOONG_API_KEY = "IU0V0RQzp4NwqTrWS6g2TJrf5PaBB3UuZ3sAGdOS"
    }

    override suspend fun getWardFromCoordinates(latitude: Double, longitude: Double): String? {
        return withContext(Dispatchers.IO) {
            try {
                val urlString = "https://rsapi.goong.io/Geocode?latlng=$latitude,$longitude&api_key=$GOONG_API_KEY"
                Log.d("EcoSnap_Flow", "Gọi Goong Reverse Geocoding API: $urlString")

                val url = URL(urlString)
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(responseText)
                    val status = jsonObject.optString("status")

                    if (status == "OK") {
                        val results = jsonObject.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val firstResult = results.getJSONObject(0)
                            val compound = firstResult.optJSONObject("compound")

                            val commune = compound?.optString("commune").orEmpty().trim()
                            if (commune.isNotEmpty()) {
                                val normalizedCommune = AddressParserHelper.normalizeWardName(commune)
                                Log.d("EcoSnap_Flow", "Goong Geocoding thành công: Tọa độ ($latitude, $longitude) -> Phường: '$normalizedCommune'")
                                return@withContext normalizedCommune
                            }

                            val formattedAddress = firstResult.optString("formatted_address")
                            if (formattedAddress.isNotEmpty()) {
                                val extracted = AddressParserHelper.normalizeWardName(formattedAddress)
                                Log.d("EcoSnap_Flow", "Goong Geocoding (formatted_address): Tọa độ ($latitude, $longitude) -> Phường: '$extracted'")
                                return@withContext extracted
                            }
                        }
                    } else {
                        Log.w("EcoSnap_Flow", "Goong API trả về status: $status")
                    }
                } else {
                    Log.e("EcoSnap_Flow", "Goong API thất bại - HTTP code: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("EcoSnap_Flow", "Lỗi trong GoongGeocodingServiceImpl: ${e.message}", e)
            }

            Log.d("EcoSnap_Flow", "Fallback sang Android System Geocoder cho tọa độ ($latitude, $longitude)...")
            androidGeocoderService.getWardFromCoordinates(latitude, longitude)
        }
    }
}
