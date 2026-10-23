package com.ecosnap.app.domain.model

data class TrashPhoto(
    val id: String,
    val userId: String,
    val wardId: String,
    val imageUrl: String,
    val latitude: Double,
    val longitude: Double,
    val capturedAt: String,
    val aiLabel: String? = null,
    val confidence: Float? = null,
    val status: String = "pending",
)
