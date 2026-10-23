package com.ecosnap.app.presentation.camera

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ecosnap.app.data.classifier.TFLiteTrashClassifier
import com.ecosnap.app.data.remote.SupabaseProvider
import com.ecosnap.app.data.repository.TrashRepositoryImpl
import com.ecosnap.app.data.service.geocoding.GoongGeocodingServiceImpl
import com.ecosnap.app.data.service.location.FusedLocationServiceImpl
import com.ecosnap.app.data.util.ExifHelper
import com.ecosnap.app.databinding.ActivityCameraBinding
import com.ecosnap.app.domain.usecase.CaptureAndClassifyPhotoUseCase
import com.ecosnap.app.utils.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding
    private var imageCapture: ImageCapture? = null

    private val trashClassifier by lazy { TFLiteTrashClassifier(applicationContext) }
    private val locationService by lazy { FusedLocationServiceImpl(applicationContext) }
    private val geocodingService by lazy { GoongGeocodingServiceImpl(applicationContext) }
    private val trashRepository by lazy { TrashRepositoryImpl(SupabaseProvider.client) }
    private val captureAndClassifyPhotoUseCase by lazy {
        CaptureAndClassifyPhotoUseCase(trashRepository, trashClassifier)
    }

    private val viewModel: CameraViewModel by viewModels {
        CameraViewModel.Factory(
            captureAndClassifyPhotoUseCase = captureAndClassifyPhotoUseCase,
            locationService = locationService,
            geocodingService = geocodingService,
        )
    }

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineGranted || coarseGranted) {
            viewModel.fetchCurrentLocation()
        } else {
            Toast.makeText(this, "Cần cấp quyền vị trí để ghi nhận khu vực rác", Toast.LENGTH_LONG).show()
        }
    }

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Cần cấp quyền Camera để chụp ảnh rác thải", Toast.LENGTH_LONG).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            try {
                contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: Exception) {}

            binding.layoutLoading.visibility = View.VISIBLE
            binding.tvLoadingStatus.text = "Đang trích xuất dữ liệu ảnh..."

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    Log.d("EcoSnap_Flow", "Bắt đầu xử lý file ảnh từ URI: $selectedUri")
                    val compressedBytes = ImageUtils.uriToCompressedByteArray(applicationContext, selectedUri)
                    if (compressedBytes == null) {
                        withContext(Dispatchers.Main) {
                            binding.layoutLoading.visibility = View.GONE
                            Toast.makeText(this@CameraActivity, "Không thể đọc file ảnh từ thư viện", Toast.LENGTH_SHORT).show()
                        }
                        return@launch
                    }

                    val gpsCoords = ExifHelper.extractGpsCoordinates(applicationContext, selectedUri)
                    val metadata = ImageUtils.extractMetadata(applicationContext, selectedUri)

                    var targetLat = gpsCoords?.first ?: metadata.latitude
                    var targetLng = gpsCoords?.second ?: metadata.longitude
                    var targetWard = viewModel.uiState.value.wardName

                    if (targetLat != null && targetLng != null && targetLat != 0.0 && targetLng != 0.0) {
                        Log.d("EcoSnap_Flow", "Đang geocode tọa độ EXIF: ($targetLat, $targetLng)")
                        val resolvedWard = geocodingService.getWardFromCoordinates(targetLat, targetLng)
                        if (!resolvedWard.isNullOrEmpty()) {
                            targetWard = resolvedWard
                        }
                    } else {
                        Log.d("EcoSnap_Flow", "Không có tọa độ EXIF hợp lệ trong ảnh, sử dụng vị trí GPS hiện tại...")
                        val currentLoc = viewModel.uiState.value.currentLocation
                        targetLat = currentLoc?.latitude
                        targetLng = currentLoc?.longitude
                        if (currentLoc?.wardName?.isNotEmpty() == true) {
                            targetWard = currentLoc.wardName
                        }
                    }

                    val capturedAt = metadata.capturedAt
                        ?: SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    withContext(Dispatchers.Main) {
                        viewModel.uploadPhoto(
                            imageBytes = compressedBytes,
                            wardId = targetWard,
                            latitude = targetLat,
                            longitude = targetLng,
                            capturedAt = capturedAt,
                        )
                    }
                } catch (e: Exception) {
                    Log.e("EcoSnap_Flow", "Lỗi xử lý chọn ảnh từ thư viện: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        binding.layoutLoading.visibility = View.GONE
                        Toast.makeText(this@CameraActivity, "Lỗi xử lý ảnh: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeViewModel()
        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        if (locationService.hasLocationPermission()) {
            viewModel.fetchCurrentLocation()
        } else {
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                permissionsToRequest.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
            requestLocationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }

        requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture,
                )
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi kết nối CameraX: ${e.message}", e)
                Toast.makeText(this, "Không thể mở Camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupListeners() {
        binding.btnCapture.setOnClickListener {
            takePhoto()
        }

        binding.btnPickGallery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_MEDIA_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                requestLocationPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_MEDIA_LOCATION,
                    ),
                )
            }
            pickImageLauncher.launch(arrayOf("image/*"))
        }

        binding.btnExit.setOnClickListener {
            finish()
        }

        binding.btnOpenWards.setOnClickListener {
            finish()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        binding.layoutLoading.visibility = View.VISIBLE
        binding.tvLoadingStatus.text = "Đang chụp và xử lý ảnh..."

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    lifecycleScope.launch(Dispatchers.Default) {
                        try {
                            val compressedBytes = ImageUtils.imageProxyToCompressedByteArray(image)
                            withContext(Dispatchers.Main) {
                                val currentWard = viewModel.uiState.value.wardName
                                viewModel.uploadPhoto(
                                    imageBytes = compressedBytes,
                                    wardId = currentWard,
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("EcoSnap_Flow", "Lỗi nén/chuyển đổi ảnh chụp Camera: ${e.message}", e)
                            withContext(Dispatchers.Main) {
                                binding.layoutLoading.visibility = View.GONE
                                Toast.makeText(this@CameraActivity, "Lỗi xử lý ảnh chụp: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    binding.layoutLoading.visibility = View.GONE
                    Log.e(TAG, "Lỗi chụp ảnh: ${exception.message}", exception)
                    Toast.makeText(this@CameraActivity, "Chụp ảnh thất bại: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.tvWardDetected.text = state.wardName
                    binding.tvGpsStatus.text = state.gpsStatus

                    if (state.isLoading) {
                        binding.layoutLoading.visibility = View.VISIBLE
                        binding.tvLoadingStatus.text = state.loadingStatus ?: "Đang xử lý..."
                    } else {
                        binding.layoutLoading.visibility = View.GONE
                    }

                    state.uploadSuccessPhoto?.let { photo ->
                        Toast.makeText(
                            this@CameraActivity,
                            "Tải ảnh thành công!",
                            Toast.LENGTH_LONG,
                        ).show()
                        viewModel.clearMessages()
                    }

                    state.errorMessage?.let { error ->
                        Toast.makeText(this@CameraActivity, error, Toast.LENGTH_LONG).show()
                        viewModel.clearMessages()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "CameraActivity"
    }
}
