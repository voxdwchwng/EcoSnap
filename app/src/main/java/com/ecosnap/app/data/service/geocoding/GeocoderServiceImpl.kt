package com.ecosnap.app.data.service.geocoding

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.util.Log
import com.ecosnap.app.data.util.AddressParserHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class GeocoderServiceImpl(
    context: Context,
) : IGeocodingService {

    private val geocoder = Geocoder(context, Locale("vi", "VN"))

    override suspend fun getWardFromCoordinates(latitude: Double, longitude: Double): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { continuation ->
                    geocoder.getFromLocation(
                        latitude,
                        longitude,
                        1,
                        object : Geocoder.GeocodeListener {
                            override fun onGeocode(addresses: MutableList<Address>) {
                                if (continuation.isActive) {
                                    val address = addresses.firstOrNull()
                                    logAddressDebug(address, latitude, longitude)
                                    val result = address?.let { AddressParserHelper.extractWard(it) }
                                    Log.d("GEO_DEBUG", "Extracted Ward Result: $result")
                                    continuation.resume(result)
                                }
                            }

                            override fun onError(errorMessage: String?) {
                                Log.e("GEO_DEBUG", "Geocoder.GeocodeListener onError: $errorMessage (lat=$latitude, lng=$longitude)")
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        },
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                    val address = addresses?.firstOrNull()
                    logAddressDebug(address, latitude, longitude)
                    val result = address?.let { AddressParserHelper.extractWard(it) }
                    Log.d("GEO_DEBUG", "Extracted Ward Result: $result")
                    result
                }
            }
        } catch (e: Exception) {
            Log.e("GEO_DEBUG", "Exception during Geocoding: ${e.message}", e)
            null
        }
    }

    private fun logAddressDebug(address: Address?, latitude: Double, longitude: Double) {
        if (address == null) {
            Log.e("GEO_DEBUG", "Geocoder trả về rỗng (Empty/Null) cho tọa độ: lat=$latitude, lng=$longitude")
        } else {
            Log.d("GEO_DEBUG", "--- RAW ADDRESS TOSTRING ---")
            Log.d("GEO_DEBUG", address.toString())
            Log.d("GEO_DEBUG", "Full Address Line: ${address.getAddressLine(0)}")
            Log.d("GEO_DEBUG", "subLocality: ${address.subLocality}")
            Log.d("GEO_DEBUG", "locality: ${address.locality}")
            Log.d("GEO_DEBUG", "subAdminArea: ${address.subAdminArea}")
        }
    }
}
