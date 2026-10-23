package com.ecosnap.app.data.remote.mapper

import com.ecosnap.app.data.remote.dto.TrashPhotoDto
import com.ecosnap.app.domain.model.TrashPhoto

fun TrashPhotoDto.toDomain(): TrashPhoto {
    return TrashPhoto(
        id = this.id.orEmpty(),
        userId = this.userId,
        wardId = this.wardId,
        imageUrl = this.imageUrl,
        latitude = this.latitude,
        longitude = this.longitude,
        capturedAt = this.capturedAt.orEmpty(),
        aiLabel = this.aiLabel,
        confidence = this.confidence,
        status = this.status ?: "pending",
    )
}

fun TrashPhoto.toDto(): TrashPhotoDto {
    return TrashPhotoDto(
        id = this.id.ifEmpty { null },
        userId = this.userId,
        wardId = this.wardId,
        imageUrl = this.imageUrl,
        latitude = this.latitude,
        longitude = this.longitude,
        capturedAt = this.capturedAt.ifEmpty { null },
        aiLabel = this.aiLabel,
        confidence = this.confidence,
        status = this.status,
    )
}
