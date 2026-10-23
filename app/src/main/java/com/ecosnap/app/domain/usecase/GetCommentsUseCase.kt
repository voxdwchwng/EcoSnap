package com.ecosnap.app.domain.usecase

import com.ecosnap.app.domain.model.Comment
import com.ecosnap.app.domain.repository.ICommentRepository

class GetCommentsUseCase(
    private val repository: ICommentRepository,
) {
    suspend operator fun invoke(photoId: String): Result<List<Comment>> {
        return repository.getComments(photoId)
    }
}
