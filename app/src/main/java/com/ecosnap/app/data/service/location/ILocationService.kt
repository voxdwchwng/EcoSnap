package com.ecosnap.app.data.service.location

import com.ecosnap.app.domain.model.LocationData

interface ILocationService {
    fun hasLocationPermission(): Boolean
    suspend fun getCurrentLocation(): Result<LocationData>
}
