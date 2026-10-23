package com.ecosnap.app.presentation.ward

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ecosnap.app.data.remote.SupabaseProvider
import com.ecosnap.app.data.repository.TrashRepositoryImpl
import com.ecosnap.app.databinding.ActivityWardListBinding
import com.ecosnap.app.domain.usecase.GetWardsUseCase
import com.ecosnap.app.presentation.photo.PhotoListActivity
import kotlinx.coroutines.launch

class WardListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWardListBinding
    private lateinit var adapter: WardListAdapter

    private val trashRepository by lazy { TrashRepositoryImpl(SupabaseProvider.client) }
    private val getWardsUseCase by lazy { GetWardsUseCase(trashRepository) }

    private val viewModel: WardListViewModel by viewModels {
        WardListViewModel.Factory(getWardsUseCase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWardListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        observeViewModel()

        viewModel.fetchWards()
    }

    private fun setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = WardListAdapter { ward ->
            val intent = Intent(this, PhotoListActivity::class.java).apply {
                putExtra(PhotoListActivity.EXTRA_WARD_ID, ward.id)
                putExtra(PhotoListActivity.EXTRA_WARD_NAME, ward.wardName)
            }
            startActivity(intent)
        }
        binding.rvWards.layoutManager = LinearLayoutManager(this)
        binding.rvWards.adapter = adapter
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    adapter.submitList(state.wards)

                    if (!state.isLoading && state.wards.isEmpty()) {
                        binding.tvEmptyState.visibility = View.VISIBLE
                    } else {
                        binding.tvEmptyState.visibility = View.GONE
                    }

                    state.errorMessage?.let { error ->
                        Toast.makeText(this@WardListActivity, error, Toast.LENGTH_LONG).show()
                        viewModel.clearErrorMessage()
                    }
                }
            }
        }
    }
}
