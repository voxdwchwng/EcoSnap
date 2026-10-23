package com.ecosnap.app.presentation.map

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ecosnap.app.R
import com.ecosnap.app.data.remote.SupabaseProvider
import com.ecosnap.app.data.repository.TrashRepositoryImpl
import com.ecosnap.app.databinding.ActivityHomeMapBinding
import com.ecosnap.app.domain.model.PollutionCluster
import com.ecosnap.app.domain.model.PollutionLevel
import com.ecosnap.app.domain.model.TrashPhoto
import com.ecosnap.app.domain.usecase.CalculatePollutionClusterUseCase
import com.ecosnap.app.presentation.camera.CameraActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.geometry.LatLngQuad
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.ImageSource

class HomeMapActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeMapBinding
    private var mapLibreMap: MapLibreMap? = null

    private val trashRepository by lazy { TrashRepositoryImpl(SupabaseProvider.client) }
    private val calculateClusterUseCase by lazy { CalculatePollutionClusterUseCase() }

    private val viewModel: HomeMapViewModel by viewModels {
        HomeMapViewModel.Factory(trashRepository, calculateClusterUseCase)
    }

    private var isHeatmapMode = false
    private val currentMarkers = mutableListOf<Marker>()
    private val markerClusterMap = mutableMapOf<Marker, PollutionCluster>()

    companion object {
        const val STATIC_HEATMAP_SOURCE_ID = "static-heatmap-source"
        const val STATIC_HEATMAP_LAYER_ID = "static-heatmap-layer"
        private const val GOONG_MAP_TILES_KEY = "r18Yjg3NlvNJbmI7dxgVyoUMC0pWRG8VR8XIqMbY"
        private const val GOONG_STYLE_URL = "https://tiles.goong.io/assets/goong_map_web.json?api_key=$GOONG_MAP_TILES_KEY"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(this)

        binding = ActivityHomeMapBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mapView.onCreate(savedInstanceState)
        setupMapView()
        setupListeners()
        observeViewModel()

        loadMapData()
    }

    private fun setupMapView() {
        binding.mapView.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri(GOONG_STYLE_URL)) { style ->
                val defaultPoint = LatLng(10.7769, 106.7009)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultPoint, 14.0))

                updateStaticHeatmapOverlay(style, getFilteredPhotos())

                map.addOnCameraMoveListener {
                    val zoom = map.cameraPosition.zoom
                    viewModel.computeClusters(zoom)
                }

                map.setOnMarkerClickListener { marker ->
                    val cluster = markerClusterMap[marker]
                    if (cluster != null) {
                        Toast.makeText(
                            this,
                            "Điểm rác: ${cluster.pollutionLevel.description} (${cluster.reportCount} ảnh)",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    true
                }
            }
        }
    }

    private fun setupListeners() {
        binding.btnZoomIn.setOnClickListener {
            mapLibreMap?.animateCamera(CameraUpdateFactory.zoomIn())
        }

        binding.btnZoomOut.setOnClickListener {
            mapLibreMap?.animateCamera(CameraUpdateFactory.zoomOut())
        }

        binding.btnToggleMapMode.setOnClickListener {
            toggleMapMode()
        }

        binding.btnCapturePhoto.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        binding.btnViewList.setOnClickListener {
            startActivity(Intent(this, com.ecosnap.app.presentation.ward.WardListActivity::class.java))
        }

        binding.btnRefreshMap.setOnClickListener {
            loadMapData()
        }

        binding.cgPollutionFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val selectedId = checkedIds.first()
            when (selectedId) {
                binding.chipFilterAll.id -> viewModel.filterByLevel(null)
                binding.chipFilterLow.id -> viewModel.filterByLevel(PollutionLevel.LOW)
                binding.chipFilterMedium.id -> viewModel.filterByLevel(PollutionLevel.MEDIUM)
                binding.chipFilterHigh.id -> viewModel.filterByLevel(PollutionLevel.HIGH)
            }
        }
    }

    private fun toggleMapMode() {
        isHeatmapMode = !isHeatmapMode
        val style = mapLibreMap?.style ?: return
        val rasterLayer = style.getLayer(STATIC_HEATMAP_LAYER_ID)

        if (isHeatmapMode) {
            // BẬT CHẾ ĐỘ PHỦ TĨNH HEATMAP:
            rasterLayer?.setProperties(PropertyFactory.visibility(Property.VISIBLE))

            // Ẩn toàn bộ Marker ghim vị trí:
            clearMarkers()

            // Đổi icon nút sang chế độ Marker:
            binding.btnToggleMapMode.setImageResource(R.drawable.ic_map_pin)
        } else {
            // BẬT CHẾ ĐỘ XEM ĐIỂM MARKER:
            rasterLayer?.setProperties(PropertyFactory.visibility(Property.NONE))

            // Vẽ lại các Marker ghim vị trí:
            renderClusters(viewModel.uiState.value.displayedClusters)

            // Đổi icon nút sang chế độ Heatmap:
            binding.btnToggleMapMode.setImageResource(R.drawable.ic_heatmap)
        }
    }

    fun calculatePhotosBounds(photos: List<TrashPhoto>): LatLngBounds {
        if (photos.isEmpty()) {
            return LatLngBounds.from(10.95, 106.90, 10.60, 106.50)
        }

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLng = Double.MAX_VALUE
        var maxLng = -Double.MAX_VALUE

        for (p in photos) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLng) minLng = p.longitude
            if (p.longitude > maxLng) maxLng = p.longitude
        }

        val margin = 0.02
        minLat -= margin
        maxLat += margin
        minLng -= margin
        maxLng += margin

        return LatLngBounds.from(maxLat, maxLng, minLat, minLng)
    }

    fun generateCityHeatmapBitmap(
        photos: List<TrashPhoto>,
        bounds: LatLngBounds,
        bitmapWidth: Int = 1000,
        bitmapHeight: Int = 1000,
    ): Bitmap {
        val densityMatrix = Array(bitmapHeight) { FloatArray(bitmapWidth) }

        val latNorth = bounds.latitudeNorth
        val latSouth = bounds.latitudeSouth
        val lngEast = bounds.longitudeEast
        val lngWest = bounds.longitudeWest

        val latRange = latNorth - latSouth
        val lngRange = lngEast - lngWest

        val radius = 25

        for (photo in photos) {
            val weight = when (photo.aiLabel?.lowercase()?.trim()) {
                "rác rơi vãi", "rac-roi-vai" -> 0.15f
                "thùng rác đầy tràn", "thung-rac-day-tran" -> 0.05f
                else -> 0.01f
            }

            val normX = if (lngRange > 0) (photo.longitude - lngWest) / lngRange else 0.5
            val normY = if (latRange > 0) (latNorth - photo.latitude) / latRange else 0.5

            val cx = (normX * (bitmapWidth - 1)).toInt()
            val cy = (normY * (bitmapHeight - 1)).toInt()

            val minX = (cx - radius).coerceAtLeast(0)
            val maxX = (cx + radius).coerceAtMost(bitmapWidth - 1)
            val minY = (cy - radius).coerceAtLeast(0)
            val maxY = (cy + radius).coerceAtMost(bitmapHeight - 1)

            for (y in minY..maxY) {
                val dy = y - cy
                for (x in minX..maxX) {
                    val dx = x - cx
                    val dist = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                    if (dist <= radius) {
                        val falloff = 1.0f - (dist / radius)
                        densityMatrix[y][x] += weight * falloff
                    }
                }
            }
        }

        val pixels = IntArray(bitmapWidth * bitmapHeight)
        for (y in 0 until bitmapHeight) {
            for (x in 0 until bitmapWidth) {
                val density = densityMatrix[y][x]
                pixels[y * bitmapWidth + x] = mapDensityToColor(density)
            }
        }

        return Bitmap.createBitmap(pixels, bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    }

    private fun mapDensityToColor(density: Float): Int {
        return when {
            density < 0.10f -> Color.TRANSPARENT
            density < 0.30f -> {
                val t = (density - 0.10f) / 0.20f
                interpolateColorArgb(Color.parseColor("#4CAF50"), Color.parseColor("#FFEB3B"), t, alpha = 180)
            }
            density < 0.60f -> {
                val t = (density - 0.30f) / 0.30f
                interpolateColorArgb(Color.parseColor("#FFEB3B"), Color.parseColor("#FF9800"), t, alpha = 200)
            }
            density < 0.80f -> {
                val t = (density - 0.60f) / 0.20f
                interpolateColorArgb(Color.parseColor("#FF9800"), Color.parseColor("#E53935"), t, alpha = 220)
            }
            else -> {
                Color.argb(235, 229, 57, 53)
            }
        }
    }

    private fun interpolateColorArgb(color1: Int, color2: Int, fraction: Float, alpha: Int = 200): Int {
        val t = fraction.coerceIn(0f, 1f)
        val r1 = Color.red(color1)
        val g1 = Color.green(color1)
        val b1 = Color.blue(color1)

        val r2 = Color.red(color2)
        val g2 = Color.green(color2)
        val b2 = Color.blue(color2)

        val r = (r1 + t * (r2 - r1)).toInt().coerceIn(0, 255)
        val g = (g1 + t * (g2 - g1)).toInt().coerceIn(0, 255)
        val b = (b1 + t * (b2 - b1)).toInt().coerceIn(0, 255)

        return Color.argb(alpha, r, g, b)
    }

    fun updateStaticHeatmapOverlay(style: Style, photos: List<TrashPhoto>) {
        lifecycleScope.launch(Dispatchers.Default) {
            val bounds = calculatePhotosBounds(photos)
            val bitmap = generateCityHeatmapBitmap(photos, bounds)

            val quad = LatLngQuad(
                LatLng(bounds.latitudeNorth, bounds.longitudeWest),
                LatLng(bounds.latitudeNorth, bounds.longitudeEast),
                LatLng(bounds.latitudeSouth, bounds.longitudeEast),
                LatLng(bounds.latitudeSouth, bounds.longitudeWest),
            )

            withContext(Dispatchers.Main) {
                val existingSource = style.getSource(STATIC_HEATMAP_SOURCE_ID) as? ImageSource
                if (existingSource != null) {
                    existingSource.setImage(bitmap)
                    existingSource.setCoordinates(quad)
                } else {
                    val imageSource = ImageSource(STATIC_HEATMAP_SOURCE_ID, quad, bitmap)
                    style.addSource(imageSource)
                }

                if (style.getLayer(STATIC_HEATMAP_LAYER_ID) == null) {
                    val rasterLayer = RasterLayer(STATIC_HEATMAP_LAYER_ID, STATIC_HEATMAP_SOURCE_ID).apply {
                        setProperties(
                            PropertyFactory.rasterOpacity(0.75f),
                            PropertyFactory.visibility(Property.NONE),
                        )
                    }
                    style.addLayer(rasterLayer)
                }
            }
        }
    }

    private fun getFilteredPhotos(): List<TrashPhoto> {
        val state = viewModel.uiState.value
        return if (state.selectedLevel == null) {
            state.allPhotos
        } else {
            state.displayedClusters.flatMap { it.photos }.distinctBy { it.id }
        }
    }

    private fun loadMapData() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            val wardsResult = trashRepository.getWards()
            wardsResult.onSuccess { wards ->
                val wardIds = wards.map { it.id }
                viewModel.loadAllPhotos(wardIds)
            }.onFailure { error ->
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@HomeMapActivity, error.message ?: "Lỗi nạp dữ liệu bản đồ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    binding.progressBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                    val zoom = mapLibreMap?.cameraPosition?.zoom ?: 14.0
                    if (state.allPhotos.isNotEmpty() && state.allClusters.isEmpty()) {
                        viewModel.computeClusters(zoom)
                    }

                    val filteredPhotos = getFilteredPhotos()
                    mapLibreMap?.style?.let { style ->
                        updateStaticHeatmapOverlay(style, filteredPhotos)
                    }

                    if (!isHeatmapMode) {
                        renderClusters(state.displayedClusters)
                    } else {
                        clearMarkers()
                    }

                    binding.tvMapSummary.text = "Tổng số ảnh: ${state.allPhotos.size} | Cụm hiển thị: ${state.displayedClusters.size}"

                    state.errorMessage?.let { error ->
                        Toast.makeText(this@HomeMapActivity, error, Toast.LENGTH_LONG).show()
                        viewModel.clearErrorMessage()
                    }
                }
            }
        }
    }

    private fun clearMarkers() {
        mapLibreMap?.let { map ->
            currentMarkers.forEach { map.removeMarker(it) }
            currentMarkers.clear()
            markerClusterMap.clear()
        }
    }

    private fun renderClusters(clusters: List<PollutionCluster>) {
        clearMarkers()

        val map = mapLibreMap ?: return

        for (cluster in clusters) {
            val maxScore = ClusterBitmapHelper.getMaxPhotoScore(cluster.photos)
            val drawable = ClusterBitmapHelper.createTrashPinDrawable(
                context = this,
                score = maxScore,
                photoCount = cluster.reportCount,
            )

            val bitmap = drawableToBitmap(drawable)
            val icon = IconFactory.getInstance(this).fromBitmap(bitmap)

            val markerOptions = MarkerOptions()
                .position(LatLng(cluster.centerLat, cluster.centerLng))
                .icon(icon)

            val marker = map.addMarker(markerOptions)
            markerClusterMap[marker] = cluster
            currentMarkers.add(marker)
        }
    }

    private fun drawableToBitmap(drawable: android.graphics.drawable.Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.coerceAtLeast(1),
            drawable.intrinsicHeight.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        binding.mapView.onLowMemory()
    }
}
