package com.ecosnap.app.domain.repository

import com.ecosnap.app.domain.model.TrashPhoto
import com.ecosnap.app.domain.model.WardCategory
import kotlinx.coroutines.flow.Flow

interface ITrashRepository {
    suspend fun getWards(): Result<List<WardCategory>>
    suspend fun getPhotosByWard(wardId: String): Result<List<TrashPhoto>>
    suspend fun uploadTrashPhoto(photo: TrashPhoto, imageBytes: ByteArray): Result<TrashPhoto>
    suspend fun deletePhoto(photoId: String, imageUrl: String): Result<Unit>
    suspend fun getOrCreateWard(wardName: String): String
    fun observeTrashPhotos(): Flow<List<TrashPhoto>>
}
