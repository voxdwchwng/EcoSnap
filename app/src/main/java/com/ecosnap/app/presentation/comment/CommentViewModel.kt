package com.ecosnap.app.presentation.comment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ecosnap.app.domain.model.Comment
import com.ecosnap.app.domain.usecase.AddCommentUseCase
import com.ecosnap.app.domain.usecase.GetCommentsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommentUiState(
    val comments: List<Comment> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSendSuccess: Boolean = false,
)

class CommentViewModel(
    private val getCommentsUseCase: GetCommentsUseCase,
    private val addCommentUseCase: AddCommentUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommentUiState())
    val uiState: StateFlow<CommentUiState> = _uiState.asStateFlow()

    fun loadComments(photoId: String) {
        // Tạm thời ngưng gửi request truy vấn Supabase
        _uiState.update { it.copy(isLoading = false, comments = emptyList()) }
    }

    fun sendComment(photoId: String, content: String) {
        val cleanContent = content.trim()
        if (cleanContent.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isSendSuccess = false) }

            val result = addCommentUseCase(photoId, cleanContent)
            result.onSuccess { newComment ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        comments = state.comments + newComment,
                        isSendSuccess = true,
                        errorMessage = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isSendSuccess = false,
                        errorMessage = error.message ?: "Gửi bình luận thất bại",
                    )
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null, isSendSuccess = false) }
    }

    class Factory(
        private val getCommentsUseCase: GetCommentsUseCase,
        private val addCommentUseCase: AddCommentUseCase,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CommentViewModel::class.java)) {
                return CommentViewModel(getCommentsUseCase, addCommentUseCase) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
