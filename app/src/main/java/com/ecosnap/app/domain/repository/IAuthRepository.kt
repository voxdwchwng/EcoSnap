package com.ecosnap.app.domain.repository

import com.ecosnap.app.domain.model.User

interface IAuthRepository {
    suspend fun signUp(email: String, password: String): Result<User>
    suspend fun signIn(email: String, password: String): Result<User>
    suspend fun getCurrentUser(): User?
    suspend fun signOut(): Result<Unit>
    suspend fun login(email: String, pass: String): Result<Unit>
    suspend fun register(email: String, pass: String): Result<Unit>
}
