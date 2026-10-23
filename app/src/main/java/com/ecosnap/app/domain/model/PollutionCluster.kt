package com.ecosnap.app.domain.model

enum class PollutionLevel(val colorHex: String, val description: String) {
    LOW("#4CAF50", "Thùng rác chưa đầy (1đ)"),
    MEDIUM("#FF9800", "Thùng rác đầy tràn (2đ)"),
    HIGH("#E53935", "Rác rơi vãi (3đ)"),
}

data class TrashSpot(
    val centerLat: Double,
    val centerLng: Double,
    val photos: List<TrashPhoto>,
    val spotScore: Float,
    val dominantLabel: String = "",
)

data class PollutionCluster(
    val centerLat: Double,
    val centerLng: Double,
    val photos: List<TrashPhoto>,
    val spots: List<TrashSpot> = emptyList(),
    val totalSeverityScore: Float, // Lấy chính xác spotScore (MAX score của nhóm <= 15m)
    val reportCount: Int,          // Số lượng ảnh trong nhóm (photos.size)
    val pollutionLevel: PollutionLevel,
)
