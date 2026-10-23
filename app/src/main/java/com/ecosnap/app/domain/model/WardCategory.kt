package com.ecosnap.app.domain.model

data class WardCategory(
    val id: String,
    val wardName: String,
    val totalPhotos: Int = 0
)