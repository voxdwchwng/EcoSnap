package com.ecosnap.app.domain.usecase

import com.ecosnap.app.domain.model.WardCategory
import com.ecosnap.app.domain.repository.ITrashRepository

class GetWardsUseCase(
    private val trashRepository: ITrashRepository,
) {
    suspend operator fun invoke(): Result<List<WardCategory>> {
        return trashRepository.getWards()
    }
}
