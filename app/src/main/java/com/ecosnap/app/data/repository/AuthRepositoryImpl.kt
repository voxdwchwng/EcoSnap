package com.ecosnap.app.data.repository

import com.ecosnap.app.domain.model.User
import com.ecosnap.app.domain.repository.IAuthRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthRepositoryImpl(
    private val supabaseClient: SupabaseClient,
) : IAuthRepository {

    override suspend fun login(email: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = pass
            }
            Unit
        }
    }

    override suspend fun register(email: String, pass: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.auth.signUpWith(Email) {
                this.email = email
                this.password = pass
            }
            Unit
        }
    }

    override suspend fun signUp(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            val currentUserInfo = supabaseClient.auth.currentUserOrNull()
            User(
                id = currentUserInfo?.id ?: "",
                email = currentUserInfo?.email ?: email,
            )
        }
    }

    override suspend fun signIn(email: String, password: String): Result<User> = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            val currentUserInfo = supabaseClient.auth.currentUserOrNull()
            User(
                id = currentUserInfo?.id ?: "",
                email = currentUserInfo?.email ?: email,
            )
        }
    }

    override suspend fun getCurrentUser(): User? = withContext(Dispatchers.IO) {
        try {
            val userInfo = supabaseClient.auth.currentUserOrNull()
            if (userInfo != null) {
                User(
                    id = userInfo.id,
                    email = userInfo.email.orEmpty(),
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabaseClient.auth.signOut()
            Unit
        }
    }
}
