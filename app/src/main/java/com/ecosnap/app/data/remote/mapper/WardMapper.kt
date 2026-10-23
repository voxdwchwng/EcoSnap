package com.ecosnap.app.data.remote.mapper

import com.ecosnap.app.data.remote.dto.WardDto
import com.ecosnap.app.domain.model.WardCategory

fun WardDto.toDomain(): WardCategory {
    return WardCategory(
        id = this.id.orEmpty(),
        wardName = this.wardName,
        totalPhotos = this.totalPhotos
    )
}