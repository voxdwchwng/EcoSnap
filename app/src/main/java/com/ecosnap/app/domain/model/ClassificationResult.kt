package com.ecosnap.app.domain.model

data class ClassificationResult(
    val label: String,
    val confidence: Float,
    val isValidTrash: Boolean,
)
