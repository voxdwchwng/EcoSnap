package com.ecosnap.app.domain.model

data class User(
    val id: String,
    val email: String,
    val fullName: String? = null,
    val avatarUrl: String? = null
)