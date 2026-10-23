package com.ecosnap.app.domain.repository

import android.graphics.Bitmap
import com.ecosnap.app.domain.model.ClassificationResult

interface ITrashClassifier {
    fun classify(bitmap: Bitmap): ClassificationResult
}
