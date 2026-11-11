
package vn.lekien.aicamoverlay

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.atomic.AtomicBoolean

class SceneVisionEngine {

    data class SceneLabel(
        val text: String,
        val confidence: Float
    )

    data class AnalysisResult(
        val labels: List<SceneLabel>,
        val analysisTimeMs: Long
    )

    interface Listener {
        fun onResult(result: AnalysisResult)
        fun onError(error: Exception)
    }

    companion object {
        private const val TAG = "AICAM_SCENE"
        private const val MIN_FRAME_INTERVAL_MS = 1800L
        private const val MIN_CONFIDENCE = 0.45f
        private const val MAX_LABELS = 15
    }

    private val busy = AtomicBoolean(false)

    @Volatile
    private var closed = false

    private var lastFrameTime = 0L

    private val labeler: ImageLabeler =
        ImageLabeling.getClient(
            ImageLabelerOptions.DEFAULT_OPTIONS
        )

    fun analyze(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        listener: Listener
    ) {
        if (closed) return

        if (
            bitmap.isRecycled ||
            bitmap.width <= 0 ||
            bitmap.height <= 0
        ) {
            listener.onError(
                IllegalArgumentException("Bitmap không hợp lệ")
            )
            return
        }

        val now = SystemClock.elapsedRealtime()

        if (now - lastFrameTime < MIN_FRAME_INTERVAL_MS) {
            return
        }

        if (!busy.compareAndSet(false, true)) {
            return
        }

        lastFrameTime = now

        val startTime = SystemClock.elapsedRealtime()

        try {
            val image = InputImage.fromBitmap(
                bitmap,
                normalizeRotation(rotationDegrees)
            )

            labeler.process(image)
                .addOnSuccessListener { labels ->

                    if (closed) return@addOnSuccessListener

                    val results = labels
                        .asSequence()
                        .filter {
                            it.confidence >= MIN_CONFIDENCE
                        }
                        .sortedByDescending {
                            it.confidence
                        }
                        .take(MAX_LABELS)
                        .map {
                            SceneLabel(
                                text = it.text,
                                confidence = it.confidence
                            )
                        }
                        .toList()

                    val elapsed =
                        SystemClock.elapsedRealtime() - startTime

                    Log.d(
                        TAG,
                        "SCENE RESULT | " +
                            "labels=${results.size} | " +
                            "time=${elapsed}ms"
                    )

                    results.forEach {
                        Log.d(
                            TAG,
                            "SCENE LABEL | " +
                                "${it.text}: ${it.confidence}"
                        )
                    }

                    listener.onResult(
                        AnalysisResult(
                            labels = results,
                            analysisTimeMs = elapsed
                        )
                    )
                }
                .addOnFailureListener { error ->

                    Log.e(
                        TAG,
                        "Image Labeling thất bại",
                        error
                    )

                    if (!closed) {
                        listener.onError(error)
                    }
                }
                .addOnCompleteListener {
                    busy.set(false)
                }

        } catch (error: Exception) {
            busy.set(false)

            Log.e(
                TAG,
                "Không thể phân tích hình ảnh",
                error
            )

            if (!closed) {
                listener.onError(error)
            }
        }
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
        labeler.close()

        Log.d(TAG, "Scene Vision Engine đã đóng")
    }
}
