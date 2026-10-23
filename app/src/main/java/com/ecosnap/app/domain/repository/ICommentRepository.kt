package com.ecosnap.app.domain.repository

import com.ecosnap.app.domain.model.Comment

interface ICommentRepository {
    suspend fun getComments(photoId: String): Result<List<Comment>>
    suspend fun addComment(photoId: String, content: String): Result<Comment>
}
