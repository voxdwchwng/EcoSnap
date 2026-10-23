package com.ecosnap.app.data.repository

import android.util.Log
import com.ecosnap.app.data.remote.dto.TrashPhotoDto
import com.ecosnap.app.data.remote.dto.WardDto
import com.ecosnap.app.data.remote.mapper.toDomain
import com.ecosnap.app.domain.model.TrashPhoto
import com.ecosnap.app.domain.model.WardCategory
import com.ecosnap.app.domain.repository.ITrashRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrashRepositoryImpl(
    private val supabaseClient: SupabaseClient,
) : ITrashRepository {

    override suspend fun getWards(): Result<List<WardCategory>> = withContext(Dispatchers.IO) {
        runCatching {
            val wardDtos = supabaseClient.postgrest.from("wards")
                .select {
                    order(column = "total_photos", order = Order.DESCENDING)
                }
                .decodeList<WardDto>()

            wardDtos.map { it.toDomain() }
        }
    }

    override suspend fun getPhotosByWard(wardId: String): Result<List<TrashPhoto>> = withContext(Dispatchers.IO) {
        runCatching {
            val photoDtos = supabaseClient.postgrest.from("trash_photos")
                .select {
                    filter {
                        eq("ward_id", wardId)
                    }
                }
                .decodeList<TrashPhotoDto>()

            photoDtos.map { it.toDomain() }
        }
    }

    override suspend fun getOrCreateWard(wardName: String): String = withContext(Dispatchers.IO) {
        val cleanWardName = wardName.ifEmpty { "Khu vực chưa xác định" }
        Log.d("EcoSnap_Flow", "TrashRepositoryImpl: Đang tra cứu khu vực '$cleanWardName' trong bảng wards...")

        val existingWard = supabaseClient.postgrest.from("wards")
            .select {
                filter {
                    eq("ward_name", cleanWardName)
                }
            }
            .decodeList<WardDto>()
            .firstOrNull()

        if (existingWard?.id != null) {
            Log.d("EcoSnap_Flow", "TrashRepositoryImpl: Đã tìm thấy khu vực có sẵn ID=${existingWard.id}")
            return@withContext existingWard.id
        }

        Log.d("EcoSnap_Flow", "TrashRepositoryImpl: Chưa có khu vực '$cleanWardName', tạo mới trong bảng wards...")
        val newWard = WardDto(wardName = cleanWardName)
        val insertedWard = supabaseClient.postgrest.from("wards")
            .insert(newWard) {
                select()
            }
            .decodeSingle<WardDto>()

        val newWardId = insertedWard.id ?: throw IllegalStateException("Không thể tạo ID khu vực cho $cleanWardName")
        Log.d("EcoSnap_Flow", "TrashRepositoryImpl: Đã tạo mới khu vực ID=$newWardId")
        newWardId
    }

    override fun observeTrashPhotos(): Flow<List<TrashPhoto>> = callbackFlow {
        val channel = supabaseClient.channel("trash_photos_realtime")
        val changeFlow = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "trash_photos"
        }

        suspend fun fetchAndEmitPhotos() {
            try {
                val photoDtos = supabaseClient.postgrest.from("trash_photos")
                    .select {
                        filter {
                            eq("status", "pending")
                        }
                    }
                    .decodeList<TrashPhotoDto>()

                val domainPhotos = photoDtos.map { it.toDomain() }
                trySend(domainPhotos)
            } catch (_: Exception) {
            }
        }

        launch {
            fetchAndEmitPhotos()
        }

        val job = launch {
            changeFlow.collect {
                fetchAndEmitPhotos()
            }
        }

        launch {
            try {
                channel.subscribe()
            } catch (_: Exception) {
            }
        }

        awaitClose {
            job.cancel()
            launch {
                try {
                    channel.unsubscribe()
                } catch (_: Exception) {
                }
            }
        }
    }

    override suspend fun uploadTrashPhoto(
        photo: TrashPhoto,
        imageBytes: ByteArray,
    ): Result<TrashPhoto> = withContext(Dispatchers.IO) {
        runCatching {
            Log.d("EcoSnap_Flow", "TrashRepositoryImpl: Đang upload file JPEG (${imageBytes.size} bytes) lên Storage bucket 'trash_images'...")
            val fileName = "trash_${System.currentTimeMillis()}.jpg"
            val bucket = supabaseClient.storage.from("trash_images")

            bucket.upload(path = fileName, data = imageBytes)
            val publicUrl = bucket.publicUrl(path = fileName)
            Log.d("EcoSnap_Flow", "TrashRepositoryImpl: Upload Storage thành công! Public URL=$publicUrl")

            val cleanCapturedAt = photo.capturedAt.ifEmpty {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            }

            val dtoToInsert = TrashPhotoDto(
                userId = photo.userId,
                wardId = photo.wardId,
                imageUrl = publicUrl,
                latitude = photo.latitude,
                longitude = photo.longitude,
                capturedAt = cleanCapturedAt,
                aiLabel = photo.aiLabel,
                confidence = photo.confidence,
                status = photo.status,
            )

            Log.d("EcoSnap_Flow", "TrashRepositoryImpl: Đang insert TrashPhotoDto vào Postgres table 'trash_photos': $dtoToInsert")

            val insertedDto = supabaseClient.postgrest.from("trash_photos")
                .insert(dtoToInsert) {
                    select()
                }
                .decodeSingle<TrashPhotoDto>()

            Log.d("EcoSnap_Flow", "TrashRepositoryImpl: Insert Database thành công! DTO id=${insertedDto.id}")
            insertedDto.toDomain()
        }
    }

    override suspend fun deletePhoto(
        photoId: String,
        imageUrl: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val fileName = imageUrl.substringAfterLast("/")
            val bucket = supabaseClient.storage.from("trash_images")

            try {
                bucket.delete(fileName)
            } catch (_: Exception) {
                // Fallback nếu file không tồn tại trên storage
            }

            supabaseClient.postgrest.from("trash_photos").delete {
                filter {
                    eq("id", photoId)
                }
            }

            Unit
        }
    }
}
