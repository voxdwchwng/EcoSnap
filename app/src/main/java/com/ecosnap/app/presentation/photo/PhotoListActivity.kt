package com.ecosnap.app.presentation.photo

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.ecosnap.app.data.remote.SupabaseProvider
import com.ecosnap.app.data.repository.TrashRepositoryImpl
import com.ecosnap.app.databinding.ActivityPhotoListBinding
import com.ecosnap.app.domain.model.TrashPhoto
import com.ecosnap.app.domain.usecase.DeletePhotoUseCase
import kotlinx.coroutines.launch

class PhotoListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoListBinding
    private lateinit var adapter: PhotoListAdapter

    private val trashRepository by lazy { TrashRepositoryImpl(SupabaseProvider.client) }
    private val deletePhotoUseCase by lazy { DeletePhotoUseCase(trashRepository) }

    private val viewModel: PhotoListViewModel by viewModels {
        PhotoListViewModel.Factory(trashRepository, deletePhotoUseCase)
    }

    private var wardId: String = ""
    private var wardName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        wardId = intent.getStringExtra(EXTRA_WARD_ID).orEmpty()
        wardName = intent.getStringExtra(EXTRA_WARD_NAME).orEmpty()

        setupToolbar()
        setupRecyclerView()
        observeViewModel()

        if (wardId.isNotEmpty()) {
            viewModel.fetchPhotos(wardId)
        } else {
            Toast.makeText(this, "Mã khu vực không hợp lệ", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupToolbar() {
        if (wardName.isNotEmpty()) {
            binding.topAppBar.title = wardName
        }
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = PhotoListAdapter(
            onDeleteClick = { photo ->
                showDeleteConfirmDialog(photo)
            },
            onPhotoClick = null,
        )
        binding.rvPhotos.layoutManager = GridLayoutManager(this, 2)
        binding.rvPhotos.adapter = adapter
    }

    private fun showDeleteConfirmDialog(photo: TrashPhoto) {
        AlertDialog.Builder(this)
            .setTitle("Xác nhận xóa")
            .setMessage("Bạn có chắc chắn muốn xóa ảnh rác này?")
            .setPositiveButton("Xóa") { _, _ ->
                viewModel.deletePhoto(photo)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    adapter.submitList(state.photos)

                    if (!state.isLoading && state.photos.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                    }

                    state.errorMessage?.let { error ->
                        Toast.makeText(this@PhotoListActivity, error, Toast.LENGTH_LONG).show()
                        viewModel.clearErrorMessage()
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_WARD_ID = "EXTRA_WARD_ID"
        const val EXTRA_WARD_NAME = "EXTRA_WARD_NAME"
    }
}
