package com.ecosnap.app.data.service.geocoding

interface IGeocodingService {
    suspend fun getWardFromCoordinates(latitude: Double, longitude: Double): String?
}
