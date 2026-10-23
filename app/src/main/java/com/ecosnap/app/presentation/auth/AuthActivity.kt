package com.ecosnap.app.presentation.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ecosnap.app.data.remote.SupabaseProvider
import com.ecosnap.app.data.repository.AuthRepositoryImpl
import com.ecosnap.app.databinding.ActivityAuthBinding
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding

    private val authRepository by lazy { AuthRepositoryImpl(SupabaseProvider.client) }
    private val viewModel: AuthViewModel by viewModels {
        AuthViewModel.Factory(authRepository)
    }

    private var isRegisterMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAutoLogin()
        setupListeners()
        observeViewModel()
    }

    private fun checkAutoLogin() {
        lifecycleScope.launch {
            val client = SupabaseProvider.client
            val currentSession = client.auth.currentSessionOrNull()
            val currentUser = client.auth.currentUserOrNull()

            if (currentSession != null && currentUser != null) {
                val intent = Intent(this@AuthActivity, com.ecosnap.app.presentation.map.HomeMapActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            }
        }
    }

    private fun setupListeners() {
        binding.tvSwitchAuthMode.setOnClickListener {
            isRegisterMode = !isRegisterMode
            updateUiMode()
        }

        binding.btnSubmit.setOnClickListener {
            val email = binding.tieEmail.text?.toString()?.trim().orEmpty()
            val password = binding.tiePassword.text?.toString()?.trim().orEmpty()

            if (email.isEmpty()) {
                binding.tilEmail.error = "Vui lòng nhập Email"
                return@setOnClickListener
            } else {
                binding.tilEmail.error = null
            }

            if (password.isEmpty()) {
                binding.tilPassword.error = "Vui lòng nhập mật khẩu"
                return@setOnClickListener
            } else {
                binding.tilPassword.error = null
            }

            if (isRegisterMode) {
                viewModel.register(email, password)
            } else {
                viewModel.login(email, password)
            }
        }
    }

    private fun updateUiMode() {
        if (isRegisterMode) {
            binding.btnSubmit.text = "Đăng ký"
            binding.tvSwitchAuthMode.text = "Đã có tài khoản? Đăng nhập ngay"
            binding.tilFullName.visibility = View.VISIBLE
            binding.tvRoleLabel.visibility = View.VISIBLE
            binding.rgRole.visibility = View.VISIBLE
        } else {
            binding.btnSubmit.text = "Đăng nhập"
            binding.tvSwitchAuthMode.text = "Chưa có tài khoản? Đăng ký ngay"
            binding.tilFullName.visibility = View.GONE
            binding.tvRoleLabel.visibility = View.GONE
            binding.rgRole.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (state.isLoading) {
                        binding.btnSubmit.isEnabled = false
                        binding.progressBar.visibility = View.VISIBLE
                    } else {
                        binding.btnSubmit.isEnabled = true
                        binding.progressBar.visibility = View.GONE
                    }

                    if (state.authSuccess) {
                        Toast.makeText(this@AuthActivity, "Xác thực thành công!", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@AuthActivity, com.ecosnap.app.presentation.map.HomeMapActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    }

                    state.errorMessage?.let { error ->
                        Toast.makeText(this@AuthActivity, error, Toast.LENGTH_LONG).show()
                        viewModel.clearErrorMessage()
                    }
                }
            }
        }
    }
}
