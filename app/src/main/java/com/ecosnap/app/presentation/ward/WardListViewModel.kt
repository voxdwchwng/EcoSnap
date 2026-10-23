package com.ecosnap.app.presentation.ward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ecosnap.app.domain.model.WardCategory
import com.ecosnap.app.domain.usecase.GetWardsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WardListUiState(
    val isLoading: Boolean = false,
    val wards: List<WardCategory> = emptyList(),
    val errorMessage: String? = null,
)

class WardListViewModel(
    private val getWardsUseCase: GetWardsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WardListUiState())
    val uiState: StateFlow<WardListUiState> = _uiState.asStateFlow()

    fun fetchWards() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = getWardsUseCase()
            result.onSuccess { list ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        wards = list,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Không thể tải danh sách khu vực",
                    )
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val getWardsUseCase: GetWardsUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(WardListViewModel::class.java)) {
                return WardListViewModel(getWardsUseCase) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
