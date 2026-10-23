package com.ecosnap.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WardDto(
    @SerialName("id") val id: String? = null,
    @SerialName("ward_name") val wardName: String,
    @SerialName("total_photos") val totalPhotos: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)