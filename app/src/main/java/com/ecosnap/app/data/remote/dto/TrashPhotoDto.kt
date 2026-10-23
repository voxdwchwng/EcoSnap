package com.ecosnap.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrashPhotoDto(
    @SerialName("id") val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("ward_id") val wardId: String,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double,
    @SerialName("captured_at") val capturedAt: String? = null,
    @SerialName("ai_label") val aiLabel: String? = null,
    @SerialName("confidence") val confidence: Float? = null,
    @SerialName("status") val status: String? = "pending",
)
