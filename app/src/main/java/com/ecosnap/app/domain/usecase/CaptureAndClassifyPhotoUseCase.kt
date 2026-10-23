package com.ecosnap.app.domain.usecase

import android.graphics.BitmapFactory
import android.util.Log
import com.ecosnap.app.domain.model.TrashPhoto
import com.ecosnap.app.domain.repository.ITrashClassifier
import com.ecosnap.app.domain.repository.ITrashRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CaptureAndClassifyPhotoUseCase(
    private val trashRepository: ITrashRepository,
    private val trashClassifier: ITrashClassifier,
) {
    suspend operator fun invoke(photo: TrashPhoto, imageBytes: ByteArray): Result<TrashPhoto> {
        return withContext(Dispatchers.Default) {
            try {
                Log.d("EcoSnap_Flow", "Chặng 1: Bắt đầu phân loại AI với Swin-T...")

                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                    ?: return@withContext Result.failure(Exception("Không thể giải mã dữ liệu hình ảnh"))

                val classificationResult = trashClassifier.classify(bitmap)
                Log.d(
                    "EcoSnap_Flow",
                    "Chặng 1 hoàn tất - Phân loại thành công: ${classificationResult.label} (${classificationResult.confidence}) | isValidTrash: ${classificationResult.isValidTrash}",
                )

                if (!classificationResult.isValidTrash) {
                    val percent = (classificationResult.confidence * 100).toInt()
                    val label = classificationResult.label
                    val errorMessage = if (label == "Không rác" || label == "Thùng rác chưa đầy") {
                        "Khu vực này sạch sẽ hoặc rác chưa đầy ($label - $percent%). Không cần báo cáo!"
                    } else {
                        "Độ tin cậy AI chưa đủ cao ($percent% < 45%). Vui lòng chụp lại gần hơn hoặc rõ hơn!"
                    }
                    Log.w("EcoSnap_Flow", "Ảnh bị từ chối bởi AI: $errorMessage")
                    return@withContext Result.failure(Exception(errorMessage))
                }

                Log.d("EcoSnap_Flow", "Chặng 2: Đang kiểm tra tọa độ GPS (Lat: ${photo.latitude}, Lng: ${photo.longitude})...")
                val lat = photo.latitude
                val lng = photo.longitude
                val hasValidGps = (lat != 0.0 && lng != 0.0) && (lat in -90.0..90.0) && (lng in -180.0..180.0)

                if (!hasValidGps) {
                    Log.e("EcoSnap_Flow", "Tọa độ GPS không hợp lệ: lat=$lat, lng=$lng")
                    return@withContext Result.failure(
                        IllegalArgumentException("Ảnh không chứa tọa độ GPS hợp lệ. Vui lòng chọn hoặc chụp ảnh từ thiết bị có bật định vị vị trí!"),
                    )
                }

                Log.d("EcoSnap_Flow", "Chặng 3: Đang tra cứu/tạo khu vực wardName='${photo.wardId}'...")
                val realWardId = trashRepository.getOrCreateWard(photo.wardId)
                Log.d("EcoSnap_Flow", "Đã lấy realWardId UUID: $realWardId")

                val updatedPhoto = photo.copy(
                    wardId = realWardId,
                    aiLabel = classificationResult.label,
                    confidence = classificationResult.confidence,
                    status = "pending",
                )

                Log.d("EcoSnap_Flow", "Chặng 4: Đang tải ảnh lên Supabase Storage & lưu bảng trash_photos...")
                val uploadResult = trashRepository.uploadTrashPhoto(updatedPhoto, imageBytes)

                uploadResult.onSuccess { uploaded ->
                    Log.d("EcoSnap_Flow", "Chặng 5: Hoàn tất luồng xử lý! Photo ID=${uploaded.id}, URL=${uploaded.imageUrl}")
                }.onFailure { error ->
                    Log.e("EcoSnap_Flow", "Chặng 4 thất bại khi lưu vào Supabase: ${error.message}", error)
                }

                uploadResult
            } catch (e: Exception) {
                Log.e("EcoSnap_Flow", "Lỗi trong luồng CaptureAndClassifyPhotoUseCase: ${e.message}", e)
                Result.failure(e)
            }
        }
    }
}
