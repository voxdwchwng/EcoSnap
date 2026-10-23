package com.ecosnap.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import com.ecosnap.app.data.util.ExifHelper
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class PhotoMetadata(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val capturedAt: String? = null,
)

object ImageUtils {

    fun imageProxyToCompressedByteArray(image: ImageProxy): ByteArray {
        return image.use { proxy ->
            val rotationDegrees = proxy.imageInfo.rotationDegrees
            val originalBitmap = proxy.toBitmap()

            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true,
                )
            } else {
                originalBitmap
            }

            compressBitmapToByteArray(rotatedBitmap)
        }
    }

    fun uriToCompressedByteArray(context: Context, uri: Uri): ByteArray? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            val rotation = getExifOrientationDegrees(context, uri)
            val rotatedBitmap = if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(
                    originalBitmap,
                    0,
                    0,
                    originalBitmap.width,
                    originalBitmap.height,
                    matrix,
                    true,
                )
            } else {
                originalBitmap
            }

            compressBitmapToByteArray(rotatedBitmap)
        } catch (_: Exception) {
            null
        }
    }

    fun extractMetadata(context: Context, imageUri: Uri): PhotoMetadata {
        return try {
            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)

                // 1. Trích xuất tọa độ GPS
                val gpsCoords = ExifHelper.extractGpsCoordinates(context, imageUri)
                val latitude = gpsCoords?.first
                val longitude = gpsCoords?.second

                // 2. Trích xuất thời gian chụp
                val dateTimeRaw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)

                val capturedAt = if (!dateTimeRaw.isNullOrEmpty()) {
                    try {
                        val inputFormat = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
                        val date = inputFormat.parse(dateTimeRaw)
                        if (date != null) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(date)
                        } else {
                            dateTimeRaw.replaceFirst(":", "-").replaceFirst(":", "-")
                        }
                    } catch (_: Exception) {
                        dateTimeRaw.replaceFirst(":", "-").replaceFirst(":", "-")
                    }
                } else {
                    null
                }

                PhotoMetadata(
                    latitude = latitude,
                    longitude = longitude,
                    capturedAt = capturedAt,
                )
            } ?: PhotoMetadata()
        } catch (_: Exception) {
            PhotoMetadata()
        }
    }

    private fun compressBitmapToByteArray(bitmap: Bitmap): ByteArray {
        val maxDimension = 1024
        val width = bitmap.width
        val height = bitmap.height
        val maxEdge = maxOf(width, height)

        val resizedBitmap = if (maxEdge > maxDimension) {
            val scale = maxDimension.toFloat() / maxEdge
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        outputStream.use { stream ->
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            return stream.toByteArray()
        }
    }

    private fun getExifOrientationDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val exif = ExifInterface(inputStream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }
}
