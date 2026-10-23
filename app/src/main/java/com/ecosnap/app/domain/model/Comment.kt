package com.ecosnap.app.domain.model

data class Comment(
    val id: String,
    val photoId: String,
    val userId: String,
    val content: String,
    val createdAt: String,
)
