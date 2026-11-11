
package vn.lekien.aicamoverlay

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean

class PoseVisionEngine {

    data class PosePoint(
        val type: Int,
        val x: Float,
        val y: Float,
        val confidence: Float
    )

    data class PoseResult(
        val imageWidth: Int,
        val imageHeight: Int,
        val points: List<PosePoint>,
        val analysisTimeMs: Long
    ) {
        val personDetected: Boolean
            get() = points.count { it.confidence >= 0.5f } >= 5
    }

    interface Listener {
        fun onResult(result: PoseResult)
        fun onError(error: Exception)
    }

    companion object {
        private const val TAG = "AICAM_POSE"
        private const val MIN_FRAME_INTERVAL_MS = 250L
        private const val MIN_LANDMARK_CONFIDENCE = 0.25f
    }

    private val busy = AtomicBoolean(false)

    @Volatile
    private var closed = false

    private var lastFrameTime = 0L

    private val detector: PoseDetector

    init {
        val options = PoseDetectorOptions.Builder()
            .setDetectorMode(
                PoseDetectorOptions.STREAM_MODE
            )
            .build()

        detector = PoseDetection.getClient(options)

        Log.d(
            TAG,
            "Pose Detector đã khởi tạo | STREAM_MODE"
        )
    }

    fun analyze(
        sourceBitmap: Bitmap,
        rotationDegrees: Int = 0,
        listener: Listener
    ) {
        if (closed) return

        if (
            sourceBitmap.isRecycled ||
            sourceBitmap.width <= 0 ||
            sourceBitmap.height <= 0
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

        val bitmap = try {
            sourceBitmap.copy(
                Bitmap.Config.ARGB_8888,
                false
            )
        } catch (error: Exception) {
            busy.set(false)
            listener.onError(error)
            return
        }

        if (bitmap == null) {
            busy.set(false)
            listener.onError(
                IllegalStateException("Không sao chép được Bitmap")
            )
            return
        }

        val imageWidth = bitmap.width
        val imageHeight = bitmap.height
        val startTime = SystemClock.elapsedRealtime()

        try {
            val rotation = normalizeRotation(rotationDegrees)

            val inputImage = InputImage.fromBitmap(
                bitmap,
                rotation
            )

            detector.process(inputImage)
                .addOnSuccessListener { pose ->

                    if (closed) return@addOnSuccessListener

                    val points = pose.allPoseLandmarks
                        .mapNotNull { landmark ->

                            val confidence =
                                landmark.inFrameLikelihood

                            if (
                                confidence <
                                MIN_LANDMARK_CONFIDENCE
                            ) {
                                return@mapNotNull null
                            }

                            PosePoint(
                                type = landmark.landmarkType,
                                x = (
                                    landmark.position.x /
                                        imageWidth.toFloat()
                                ).coerceIn(0f, 1f),
                                y = (
                                    landmark.position.y /
                                        imageHeight.toFloat()
                                ).coerceIn(0f, 1f),
                                confidence = confidence
                            )
                        }

                    val elapsed =
                        SystemClock.elapsedRealtime() - startTime

                    Log.d(
                        TAG,
                        "POSE RESULT | " +
                            "points=${points.size}/33 | " +
                            "person=${points.size >= 5} | " +
                            "time=${elapsed}ms"
                    )

                    listener.onResult(
                        PoseResult(
                            imageWidth = imageWidth,
                            imageHeight = imageHeight,
                            points = points,
                            analysisTimeMs = elapsed
                        )
                    )
                }
                .addOnFailureListener { error ->

                    Log.e(
                        TAG,
                        "Pose Detection thất bại",
                        error
                    )

                    if (!closed) {
                        listener.onError(error)
                    }
                }
                .addOnCompleteListener {
                    if (!bitmap.isRecycled) {
                        bitmap.recycle()
                    }

                    busy.set(false)
                }

        } catch (error: Exception) {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }

            busy.set(false)

            Log.e(
                TAG,
                "Không thể phân tích tư thế",
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
        detector.close()

        Log.d(TAG, "Pose Detector đã đóng")
    }
}
