package com.ecosnap.app.data.remote.dto

import com.ecosnap.app.domain.model.Comment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommentDto(
    @SerialName("id") val id: String? = null,
    @SerialName("photo_id") val photoId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("content") val content: String,
    @SerialName("created_at") val createdAt: String? = null,
)

fun CommentDto.toDomain(): Comment {
    return Comment(
        id = this.id.orEmpty(),
        photoId = this.photoId,
        userId = this.userId,
        content = this.content,
        createdAt = this.createdAt.orEmpty(),
    )
}
