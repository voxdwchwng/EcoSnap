package com.ecosnap.app.presentation.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ecosnap.app.data.remote.SupabaseProvider
import com.ecosnap.app.data.service.geocoding.IGeocodingService
import com.ecosnap.app.data.service.location.ILocationService
import com.ecosnap.app.domain.model.LocationData
import com.ecosnap.app.domain.model.TrashPhoto
import com.ecosnap.app.domain.usecase.CaptureAndClassifyPhotoUseCase
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CameraUiState(
    val isLoading: Boolean = false,
    val loadingStatus: String? = null,
    val wardName: String = "Đang xác định vị trí...",
    val gpsStatus: String = "Đang lấy tọa độ GPS...",
    val currentLocation: LocationData? = null,
    val uploadSuccessPhoto: TrashPhoto? = null,
    val errorMessage: String? = null,
)

class CameraViewModel(
    private val captureAndClassifyPhotoUseCase: CaptureAndClassifyPhotoUseCase,
    private val locationService: ILocationService,
    private val geocodingService: IGeocodingService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun fetchCurrentLocation() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    wardName = "Đang xác định vị trí...",
                    gpsStatus = "Đang lấy tọa độ GPS...",
                )
            }

            val locationResult = locationService.getCurrentLocation()
            locationResult.onSuccess { locationData ->
                val gpsText = "Tọa độ: ${"%.5f".format(locationData.latitude)}, ${"%.5f".format(locationData.longitude)}"
                _uiState.update {
                    it.copy(
                        currentLocation = locationData,
                        gpsStatus = gpsText,
                        wardName = "Đang tra cứu tên phường...",
                    )
                }

                val ward = geocodingService.getWardFromCoordinates(
                    locationData.latitude,
                    locationData.longitude,
                ) ?: "Chưa xác định phường"

                val updatedLocation = locationData.copy(wardName = ward)
                _uiState.update {
                    it.copy(
                        currentLocation = updatedLocation,
                        wardName = ward,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        gpsStatus = "Không thể lấy tọa độ GPS",
                        wardName = "Vị trí không xác định",
                        errorMessage = error.message ?: "Lỗi lấy vị trí",
                    )
                }
            }
        }
    }

    fun uploadPhoto(
        imageBytes: ByteArray,
        wardId: String,
        latitude: Double? = null,
        longitude: Double? = null,
        capturedAt: String? = null,
    ) {
        viewModelScope.launch {
            val currentUserId = SupabaseProvider.client.auth.currentUserOrNull()?.id
                ?: "00000000-0000-0000-0000-000000000001"

            _uiState.update {
                it.copy(
                    isLoading = true,
                    loadingStatus = "Đang phân loại và tải ảnh lên...",
                    errorMessage = null,
                    uploadSuccessPhoto = null,
                )
            }

            val currentLoc = _uiState.value.currentLocation
            val finalLat = latitude ?: currentLoc?.latitude ?: 0.0
            val finalLng = longitude ?: currentLoc?.longitude ?: 0.0
            val finalCapturedAt = capturedAt.orEmpty().ifEmpty {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            }

            val photoToUpload = TrashPhoto(
                id = "",
                userId = currentUserId,
                wardId = wardId,
                imageUrl = "",
                latitude = finalLat,
                longitude = finalLng,
                capturedAt = finalCapturedAt,
            )

            val result = captureAndClassifyPhotoUseCase(photoToUpload, imageBytes)
            result.onSuccess { uploadedPhoto ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingStatus = null,
                        uploadSuccessPhoto = uploadedPhoto,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        loadingStatus = null,
                        errorMessage = error.message ?: "Tải ảnh lên thất bại",
                    )
                }
            }
        }
    }

    fun clearMessages() {
        _uiState.update {
            it.copy(errorMessage = null, uploadSuccessPhoto = null)
        }
    }

    class Factory(
        private val captureAndClassifyPhotoUseCase: CaptureAndClassifyPhotoUseCase,
        private val locationService: ILocationService,
        private val geocodingService: IGeocodingService,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CameraViewModel::class.java)) {
                return CameraViewModel(
                    captureAndClassifyPhotoUseCase,
                    locationService,
                    geocodingService,
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
