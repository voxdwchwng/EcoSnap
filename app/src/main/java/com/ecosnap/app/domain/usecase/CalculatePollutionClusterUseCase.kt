package com.ecosnap.app.domain.usecase

import android.location.Location
import com.ecosnap.app.domain.model.PollutionCluster
import com.ecosnap.app.domain.model.PollutionLevel
import com.ecosnap.app.domain.model.TrashPhoto
import com.ecosnap.app.domain.model.TrashSpot

class CalculatePollutionClusterUseCase {

    companion object {
        const val MICRO_RADIUS_METERS = 3.0f // Bán kính khử trùng lặp điểm chụp cùng vị trí (3m)
    }

    fun calculatePhotoScore(photo: TrashPhoto): Float {
        val label = (photo.aiLabel ?: "").lowercase()
        return when {
            label.contains("rác rơi vãi") || label.contains("rac-roi-vai") -> 3.0f
            label.contains("đầy tràn") || label.contains("thung-rac-day-tran") -> 2.0f
            label.contains("chưa đầy") || label.contains("thung-rac-chua-day") -> 1.0f
            else -> 1.0f
        }
    }

    fun computeDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0]
    }

    // Giai đoạn 1: Micro-clustering (Khử trùng lặp điểm rác <= 3m)
    fun createMicroSpots(photos: List<TrashPhoto>): List<TrashSpot> {
        if (photos.isEmpty()) return emptyList()

        val processed = BooleanArray(photos.size) { false }
        val spots = mutableListOf<TrashSpot>()

        for (i in photos.indices) {
            if (processed[i]) continue

            val group = mutableListOf<TrashPhoto>()
            group.add(photos[i])
            processed[i] = true

            for (j in (i + 1) until photos.size) {
                if (processed[j]) continue

                val dist = computeDistanceMeters(
                    photos[i].latitude, photos[i].longitude,
                    photos[j].latitude, photos[j].longitude,
                )

                if (dist <= MICRO_RADIUS_METERS) {
                    group.add(photos[j])
                    processed[j] = true
                }
            }

            val avgLat = group.map { it.latitude }.average()
            val avgLng = group.map { it.longitude }.average()

            // TUYỆT ĐỐI KHÔNG DÙNG sumOf! Lấy MAX score của các ảnh trong nhóm 3m
            val maxSpotScore = group.maxOf { calculatePhotoScore(it) }

            val dominantPhoto = group.maxByOrNull { calculatePhotoScore(it) }
            val dominantLabel = dominantPhoto?.aiLabel ?: ""

            spots.add(
                TrashSpot(
                    centerLat = avgLat,
                    centerLng = avgLng,
                    photos = group,
                    spotScore = maxSpotScore,
                    dominantLabel = dominantLabel,
                ),
            )
        }

        return spots
    }

    fun determinePollutionLevel(score: Float): PollutionLevel {
        return when {
            score >= 3.0f -> PollutionLevel.HIGH
            score >= 2.0f -> PollutionLevel.MEDIUM
            else -> PollutionLevel.LOW
        }
    }

    // Trả về trực tiếp danh sách các điểm rác thực địa (Micro-spots <= 3m), loại bỏ hoàn toàn Macro 80m
    fun createClustersFromPhotos(photos: List<TrashPhoto>): List<PollutionCluster> {
        val microSpots = createMicroSpots(photos)
        return microSpots.map { spot ->
            val level = determinePollutionLevel(spot.spotScore)
            PollutionCluster(
                centerLat = spot.centerLat,
                centerLng = spot.centerLng,
                photos = spot.photos,
                spots = listOf(spot),
                totalSeverityScore = spot.spotScore, // Lấy trực tiếp spotScore (MAX)
                reportCount = spot.photos.size,
                pollutionLevel = level,
            )
        }
    }

    fun createCluster(photos: List<TrashPhoto>): PollutionCluster {
        val clusters = createClustersFromPhotos(photos)
        return clusters.firstOrNull() ?: PollutionCluster(
            centerLat = 0.0,
            centerLng = 0.0,
            photos = emptyList(),
            spots = emptyList(),
            totalSeverityScore = 1.0f,
            reportCount = 0,
            pollutionLevel = PollutionLevel.LOW,
        )
    }
}
