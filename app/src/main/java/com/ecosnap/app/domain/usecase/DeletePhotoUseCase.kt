package com.ecosnap.app.domain.usecase

import com.ecosnap.app.domain.repository.ITrashRepository

class DeletePhotoUseCase(
    private val trashRepository: ITrashRepository,
) {
    suspend operator fun invoke(photoId: String, imageUrl: String): Result<Unit> {
        return trashRepository.deletePhoto(photoId, imageUrl)
    }
}
