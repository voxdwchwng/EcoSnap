package com.ecosnap.app.domain.usecase

import com.ecosnap.app.domain.model.Comment
import com.ecosnap.app.domain.repository.ICommentRepository

class AddCommentUseCase(
    private val repository: ICommentRepository,
) {
    suspend operator fun invoke(photoId: String, content: String): Result<Comment> {
        return repository.addComment(photoId, content)
    }
}
