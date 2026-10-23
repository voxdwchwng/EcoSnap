package com.ecosnap.app.domain.model

data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val wardName: String? = null
)