package com.ecosnap.app.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ecosnap.app.domain.model.PollutionCluster
import com.ecosnap.app.domain.model.PollutionLevel
import com.ecosnap.app.domain.model.TrashPhoto
import com.ecosnap.app.domain.repository.ITrashRepository
import com.ecosnap.app.domain.usecase.CalculatePollutionClusterUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeMapUiState(
    val isLoading: Boolean = false,
    val allPhotos: List<TrashPhoto> = emptyList(),
    val allClusters: List<PollutionCluster> = emptyList(),
    val displayedClusters: List<PollutionCluster> = emptyList(),
    val selectedLevel: PollutionLevel? = null,
    val errorMessage: String? = null,
)

class HomeMapViewModel(
    private val trashRepository: ITrashRepository,
    private val calculateClusterUseCase: CalculatePollutionClusterUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeMapUiState())
    val uiState: StateFlow<HomeMapUiState> = _uiState.asStateFlow()

    private var currentZoomLevel: Double = 15.0

    init {
        observeRealtimePhotos()
    }

    fun observeRealtimePhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            trashRepository.observeTrashPhotos()
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Lỗi kết nối Realtime",
                        )
                    }
                }
                .collect { updatedPhotos ->
                    _uiState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            allPhotos = updatedPhotos,
                        )
                    }
                    computeClusters(currentZoomLevel)
                }
        }
    }

    fun loadAllPhotos(wardList: List<String>) {
        observeRealtimePhotos()
    }

    fun setPhotos(photos: List<TrashPhoto>) {
        val pendingPhotos = photos.filter { it.status == "pending" }
        _uiState.update { it.copy(allPhotos = pendingPhotos) }
        computeClusters(currentZoomLevel)
    }

    fun computeClusters(zoomLevel: Double) {
        currentZoomLevel = zoomLevel
        val photos = _uiState.value.allPhotos
        if (photos.isEmpty()) {
            _uiState.update {
                it.copy(
                    allClusters = emptyList(),
                    displayedClusters = emptyList(),
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            // Gom cụm 2 tầng có giới hạn bán kính mét thực địa tuyệt đối (HARD CAP <= 80m)
            val clusters = calculateClusterUseCase.createClustersFromPhotos(photos)

            withContext(Dispatchers.Main) {
                _uiState.update { state ->
                    val filtered = if (state.selectedLevel == null) {
                        clusters
                    } else {
                        clusters.filter { it.pollutionLevel == state.selectedLevel }
                    }
                    state.copy(
                        allClusters = clusters,
                        displayedClusters = filtered,
                    )
                }
            }
        }
    }

    fun filterByLevel(level: PollutionLevel?) {
        _uiState.update { state ->
            val filtered = if (level == null) {
                state.allClusters
            } else {
                state.allClusters.filter { it.pollutionLevel == level }
            }
            state.copy(
                selectedLevel = level,
                displayedClusters = filtered,
            )
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    class Factory(
        private val trashRepository: ITrashRepository,
        private val calculateClusterUseCase: CalculatePollutionClusterUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(HomeMapViewModel::class.java)) {
                return HomeMapViewModel(trashRepository, calculateClusterUseCase) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
