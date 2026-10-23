package com.ecosnap.app.presentation.photo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ecosnap.app.domain.model.TrashPhoto
import com.ecosnap.app.domain.repository.ITrashRepository
import com.ecosnap.app.domain.usecase.DeletePhotoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PhotoListUiState(
    val isLoading: Boolean = false,
    val photos: List<TrashPhoto> = emptyList(),
    val errorMessage: String? = null,
)

class PhotoListViewModel(
    private val trashRepository: ITrashRepository,
    private val deletePhotoUseCase: DeletePhotoUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoListUiState())
    val uiState: StateFlow<PhotoListUiState> = _uiState.asStateFlow()

    fun fetchPhotos(wardId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = trashRepository.getPhotosByWard(wardId)
            result.onSuccess { photoList ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        photos = photoList,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Không thể tải danh sách ảnh",
                    )
                }
            }
        }
    }

    fun deletePhoto(photo: TrashPhoto) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = deletePhotoUseCase(photo.id, photo.imageUrl)
            result.onSuccess {
                _uiState.update { state ->
                    val updatedList = state.photos.filterNot { it.id == photo.id }
                    state.copy(
                        isLoading = false,
                        photos = updatedList,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Xóa ảnh thất bại",
                    )
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val trashRepository: ITrashRepository,
        private val deletePhotoUseCase: DeletePhotoUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PhotoListViewModel::class.java)) {
                return PhotoListViewModel(trashRepository, deletePhotoUseCase) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
