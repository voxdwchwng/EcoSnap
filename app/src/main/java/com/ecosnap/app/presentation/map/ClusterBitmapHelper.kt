package com.ecosnap.app.presentation.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.ecosnap.app.R
import com.ecosnap.app.domain.model.PollutionLevel
import com.ecosnap.app.domain.model.TrashPhoto

object ClusterBitmapHelper {

    fun getMaxPhotoScore(photos: List<TrashPhoto>): Int {
        if (photos.isEmpty()) return 1
        val maxScore = photos.maxOfOrNull { photo ->
            val label = (photo.aiLabel ?: "").lowercase()
            when {
                label.contains("rác rơi vãi") || label.contains("rac-roi-vai") -> 3
                label.contains("đầy tràn") || label.contains("thung-rac-day-tran") -> 2
                label.contains("chưa đầy") || label.contains("thung-rac-chua-day") -> 1
                else -> 1
            }
        } ?: 1
        return maxScore.coerceIn(1, 3)
    }

    fun createTrashPinDrawable(
        context: Context,
        score: Int,
        photoCount: Int,
    ): Drawable {
        val density = context.resources.displayMetrics.density

        // Kích thước Bitmap chuẩn: 48dp x 64dp
        val widthPx = (48 * density).toInt()
        val heightPx = (64 * density).toInt()

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Tọa độ ghim vị trí dạng giọt nước (Teardrop Location Pin)
        val cx = widthPx / 2f
        val cy = 22 * density
        val r = 18 * density
        val tipX = widthPx / 2f
        val tipY = 58 * density

        // Chọn màu sắc và icon bên trong theo score:
        // 3: Rác rơi vãi (Đỏ sẫm #D32F2F)
        // 2: Thùng rác đầy tràn (Cam đậm #F57C00)
        // 1: Thùng rác chưa đầy (Xanh lá cây #388E3C)
        val (pinColorHex, iconResId) = when (score) {
            3 -> Pair("#D32F2F", R.drawable.ic_trash_litter)
            2 -> Pair("#F57C00", R.drawable.ic_trash_overflowing)
            else -> Pair("#388E3C", R.drawable.ic_trash_bin)
        }

        val pinColor = Color.parseColor(pinColorHex)

        // 1. Tạo Path cho hình dáng Ghim vị trí (Teardrop Pin) có mũi nhọn hướng xuống dưới
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        val pinPath = Path().apply {
            arcTo(oval, 150f, 240f, false)
            lineTo(tipX, tipY)
            close()
        }

        // 2. Vẽ viền trắng ngoài ghim
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            strokeJoin = Paint.Join.ROUND
        }
        canvas.drawPath(pinPath, strokePaint)

        // 3. Vẽ màu nền ghim
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = pinColor
            style = Paint.Style.FILL
        }
        canvas.drawPath(pinPath, fillPaint)

        // 4. Vẽ icon Vector màu trắng ở trung tâm phần đầu tròn của ghim
        val iconDrawable = ContextCompat.getDrawable(context, iconResId)
        iconDrawable?.let { drawable ->
            drawable.mutate()
            drawable.setTint(Color.WHITE)
            val iconSize = (22 * density).toInt()
            val left = (cx - iconSize / 2f).toInt()
            val top = (cy - iconSize / 2f).toInt()
            val right = left + iconSize
            val bottom = top + iconSize
            drawable.setBounds(left, top, right, bottom)
            drawable.draw(canvas)
        }

        // 5. Nếu photoCount > 1: Vẽ badge đếm số lượng ảnh ở góc trên bên phải (#212121)
        if (photoCount > 1) {
            val badgeRadius = 9 * density
            val badgeCx = widthPx - badgeRadius - (2 * density)
            val badgeCy = badgeRadius + (2 * density)

            val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#212121")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(badgeCx, badgeCy, badgeRadius, badgeBgPaint)

            val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
            }
            canvas.drawCircle(badgeCx, badgeCy, badgeRadius, badgeBorderPaint)

            val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 9.5f * density
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }

            val badgeText = "x$photoCount"
            val textY = badgeCy - ((badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2)
            canvas.drawText(badgeText, badgeCx, textY, badgeTextPaint)
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    @Deprecated("Dùng createTrashPinDrawable thay thế")
    fun createClusterDrawable(
        context: Context,
        count: Int,
        level: PollutionLevel,
        score: Float,
    ): Drawable {
        val mappedScore = when {
            score >= 3.0f -> 3
            score >= 2.0f -> 2
            else -> 1
        }
        return createTrashPinDrawable(context, mappedScore, count)
    }
}
