package com.ecosnap.app.presentation.comment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ecosnap.app.data.remote.SupabaseProvider
import com.ecosnap.app.data.repository.CommentRepositoryImpl
import com.ecosnap.app.databinding.BottomSheetCommentsBinding
import com.ecosnap.app.domain.usecase.AddCommentUseCase
import com.ecosnap.app.domain.usecase.GetCommentsUseCase
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class CommentBottomSheetDialogFragment : BottomSheetDialogFragment() {

    private var _binding: BottomSheetCommentsBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: CommentAdapter

    private val commentRepository by lazy { CommentRepositoryImpl(SupabaseProvider.client) }
    private val getCommentsUseCase by lazy { GetCommentsUseCase(commentRepository) }
    private val addCommentUseCase by lazy { AddCommentUseCase(commentRepository) }

    private val viewModel: CommentViewModel by lazy {
        val factory = CommentViewModel.Factory(getCommentsUseCase, addCommentUseCase)
        ViewModelProvider(this, factory)[CommentViewModel::class.java]
    }

    private var photoId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoId = arguments?.getString(KEY_PHOTO_ID).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = BottomSheetCommentsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        observeViewModel()

        if (photoId.isNotEmpty()) {
            // Tạm thời tắt truy vấn bình luận
            // viewModel.loadComments(photoId)
        } else {
            Toast.makeText(context, "Mã ảnh không hợp lệ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = CommentAdapter()
        binding.rvComments.layoutManager = LinearLayoutManager(context)
        binding.rvComments.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnSendComment.setOnClickListener {
            val content = binding.etComment.text?.toString()?.trim().orEmpty()
            if (content.isEmpty()) {
                binding.tilComment.error = "Vui lòng nhập nội dung bình luận"
                return@setOnClickListener
            } else {
                binding.tilComment.error = null
            }

            viewModel.sendComment(photoId, content)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    adapter.submitList(state.comments)

                    if (!state.isLoading && state.comments.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                    }

                    if (state.isSendSuccess) {
                        binding.etComment.setText("")
                        binding.rvComments.smoothScrollToPosition(adapter.itemCount.coerceAtLeast(1) - 1)
                    }

                    state.errorMessage?.let { error ->
                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        viewModel.clearErrorMessage()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_PHOTO_ID = "KEY_PHOTO_ID"

        fun newInstance(photoId: String): CommentBottomSheetDialogFragment {
            return CommentBottomSheetDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(KEY_PHOTO_ID, photoId)
                }
            }
        }
    }
}
