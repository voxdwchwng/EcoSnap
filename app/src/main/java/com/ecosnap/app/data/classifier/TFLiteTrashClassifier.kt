package com.ecosnap.app.data.classifier

import android.content.Context
import android.graphics.Bitmap
import com.ecosnap.app.domain.model.ClassificationResult
import com.ecosnap.app.domain.repository.ITrashClassifier
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import kotlin.math.exp

data class ModelLabel(
    val displayName: String,
    val isValidTrash: Boolean,
)

class TFLiteTrashClassifier(
    private val context: Context,
) : ITrashClassifier {

    private val interpreter: Interpreter? by lazy {
        try {
            val numProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            val options = Interpreter.Options().apply {
                setNumThreads(numProcessors)
            }
            val mappedByteBuffer = try {
                FileUtil.loadMappedFile(context, "swin_model_float32.tflite")
            } catch (_: Exception) {
                FileUtil.loadMappedFile(context, "waste_classifier.tflite")
            }
            Interpreter(mappedByteBuffer, options)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private val labelMapping = listOf(
        ModelLabel(displayName = "Thùng rác đầy tràn", isValidTrash = true),   // Index 0: thung-rac-day-tran
        ModelLabel(displayName = "Thùng rác chưa đầy", isValidTrash = true),   // Index 1: thung-rac-chua-day
        ModelLabel(displayName = "Rác rơi vãi", isValidTrash = true),          // Index 2: rac-roi-vai
        ModelLabel(displayName = "Không rác", isValidTrash = false),           // Index 3: khong-rac
    )

    override fun classify(bitmap: Bitmap): ClassificationResult {
        val activeInterpreter = interpreter ?: return ClassificationResult(
            label = "Mô hình không khả dụng",
            confidence = 0f,
            isValidTrash = false,
        )

        return try {
            val imageProcessor = ImageProcessor.Builder()
                .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.BILINEAR))
                .add(
                    NormalizeOp(
                        floatArrayOf(123.675f, 116.28f, 103.53f),
                        floatArrayOf(58.395f, 57.12f, 57.375f),
                    ),
                )
                .build()

            var tensorImage = TensorImage(DataType.FLOAT32)
            tensorImage.load(bitmap)
            tensorImage = imageProcessor.process(tensorImage)

            val logits = Array(1) { FloatArray(4) }
            activeInterpreter.run(tensorImage.buffer, logits)

            val rawLogits = logits[0]
            val probabilities = softmax(rawLogits)

            var maxIndex = 0
            var maxConfidence = -1f

            for (i in probabilities.indices) {
                if (probabilities[i] > maxConfidence) {
                    maxConfidence = probabilities[i]
                    maxIndex = i
                }
            }

            val targetLabel = labelMapping.getOrElse(maxIndex) {
                ModelLabel(displayName = "Không rác", isValidTrash = false)
            }

            val computedIsValid = targetLabel.isValidTrash && maxConfidence >= 0.45f

            ClassificationResult(
                label = targetLabel.displayName,
                confidence = maxConfidence,
                isValidTrash = computedIsValid,
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ClassificationResult(
                label = "Lỗi phân loại",
                confidence = 0f,
                isValidTrash = false,
            )
        }
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxLogit = logits.maxOrNull() ?: 0f
        val exp = logits.map { exp((it - maxLogit).toDouble()) }
        val sumExp = exp.sum()
        return exp.map { (it / sumExp).toFloat() }.toFloatArray()
    }
}
