package com.ecosnap.app.data.repository

import com.ecosnap.app.data.remote.dto.CommentDto
import com.ecosnap.app.data.remote.dto.toDomain
import com.ecosnap.app.domain.model.Comment
import com.ecosnap.app.domain.repository.ICommentRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommentRepositoryImpl(
    private val supabaseClient: SupabaseClient,
) : ICommentRepository {

    override suspend fun getComments(photoId: String): Result<List<Comment>> = withContext(Dispatchers.IO) {
        runCatching {
            val commentDtos = supabaseClient.postgrest.from("comments")
                .select {
                    filter {
                        eq("photo_id", photoId)
                    }
                    order(column = "created_at", order = Order.ASCENDING)
                }
                .decodeList<CommentDto>()

            commentDtos.map { it.toDomain() }
        }
    }

    override suspend fun addComment(
        photoId: String,
        content: String,
    ): Result<Comment> = withContext(Dispatchers.IO) {
        runCatching {
            val currentUserId = supabaseClient.auth.currentUserOrNull()?.id
                ?: "00000000-0000-0000-0000-000000000001"

            val dtoToInsert = CommentDto(
                photoId = photoId,
                userId = currentUserId,
                content = content,
            )

            val insertedDto = supabaseClient.postgrest.from("comments")
                .insert(dtoToInsert) {
                    select()
                }
                .decodeSingle<CommentDto>()

            insertedDto.toDomain()
        }
    }
}
