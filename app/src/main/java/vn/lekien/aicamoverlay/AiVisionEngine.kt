
package vn.lekien.aicamoverlay

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * AI Vision Prototype 02
 *
 * - Phân tích khung hình ngay trên thiết bị.
 * - Nhận diện và phân loại đối tượng.
 * - Trả về tọa độ đối tượng đã chuẩn hóa.
 * - Tự giải phóng Bitmap sau khi xử lý.
 *
 * Hình ảnh không được gửi lên máy chủ.
 */
class AiVisionEngine {

    data class Detection(
        val label: String,
        val confidence: Float,
        val trackingId: Int?,
        val bounds: Rect,
        val normalizedLeft: Float,
        val normalizedTop: Float,
        val normalizedRight: Float,
        val normalizedBottom: Float
    )

    data class AnalysisResult(
        val imageWidth: Int,
        val imageHeight: Int,
        val detections: List<Detection>,
        val analysisTimeMs: Long
    )

    interface Listener {
        fun onResult(result: AnalysisResult)
        fun onError(error: Exception)
    }

    companion object {
        private const val MIN_FRAME_INTERVAL_MS = 900L

        const val CATEGORY_FOOD = "Food"
        const val CATEGORY_PLANT = "Plant"
        const val CATEGORY_PLACE = "Place"
        const val CATEGORY_HOME_GOODS = "Home goods"
        const val CATEGORY_FASHION_GOODS = "Fashion goods"
        const val CATEGORY_UNKNOWN = "Unknown"
    }

    private val busy = AtomicBoolean(false)

    @Volatile
    private var closed = false

    private var lastFrameTime = 0L

    private val detector: ObjectDetector

    init {
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableClassification()
            .build()

        detector = ObjectDetection.getClient(options)
    }

    /**
     * Engine nhận quyền sở hữu Bitmap.
     * Bitmap sẽ được recycle khi xử lý xong hoặc bị bỏ qua.
     */
    fun analyze(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        listener: Listener
    ) {
        if (closed) {
            recycleSafely(bitmap)
            return
        }

        if (bitmap.isRecycled ||
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            recycleSafely(bitmap)
            listener.onError(
                IllegalArgumentException("Bitmap không hợp lệ")
            )
            return
        }

        val now = SystemClock.elapsedRealtime()

        if (now - lastFrameTime < MIN_FRAME_INTERVAL_MS) {
            recycleSafely(bitmap)
            return
        }

        if (!busy.compareAndSet(false, true)) {
            recycleSafely(bitmap)
            return
        }

        lastFrameTime = now

        val startTime = SystemClock.elapsedRealtime()

        try {
            val input = InputImage.fromBitmap(
                bitmap,
                normalizeRotation(rotationDegrees)
            )

            detector.process(input)
                .addOnSuccessListener { objects ->

                    if (closed) return@addOnSuccessListener

                    val detections = objects.mapNotNull { detected ->

                        val bounds = detected.boundingBox

                        if (bounds.width() <= 0 ||
                            bounds.height() <= 0
                        ) {
                            return@mapNotNull null
                        }

                        val labelInfo = detected.labels
                            .maxByOrNull { it.confidence }

                        val label = labelInfo?.text
                            ?: CATEGORY_UNKNOWN

                        val confidence = labelInfo?.confidence ?: 0f

                        Detection(
                            label = label,
                            confidence = confidence,
                            trackingId = detected.trackingId,
                            bounds = Rect(bounds),
                            normalizedLeft = normalize(
                                bounds.left.toFloat() / bitmap.width
                            ),
                            normalizedTop = normalize(
                                bounds.top.toFloat() / bitmap.height
                            ),
                            normalizedRight = normalize(
                                bounds.right.toFloat() / bitmap.width
                            ),
                            normalizedBottom = normalize(
                                bounds.bottom.toFloat() / bitmap.height
                            )
                        )
                    }

                    val elapsed =
                        SystemClock.elapsedRealtime() - startTime

                    listener.onResult(
                        AnalysisResult(
                            imageWidth = bitmap.width,
                            imageHeight = bitmap.height,
                            detections = detections,
                            analysisTimeMs = elapsed
                        )
                    )
                }
                .addOnFailureListener { error ->
                    if (!closed) {
                        listener.onError(error)
                    }
                }
                .addOnCompleteListener {
                    recycleSafely(bitmap)
                    busy.set(false)
                }

        } catch (error: Exception) {
            recycleSafely(bitmap)
            busy.set(false)

            if (!closed) {
                listener.onError(error)
            }
        }
    }

    private fun recycleSafely(bitmap: Bitmap) {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }

    private fun normalize(value: Float): Float {
        return min(1f, max(0f, value))
    }

    private fun normalizeRotation(value: Int): Int {
        val normalized = ((value % 360) + 360) % 360

        return when (normalized) {
            0, 90, 180, 270 -> normalized
            else -> 0
        }
    }

    fun close() {
        if (closed) return

        closed = true
        detector.close()
    }
}
