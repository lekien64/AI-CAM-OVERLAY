
package vn.lekien.aicamoverlay

import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class FaceVisionEngine {

    data class FaceResult(
        val trackingId: Int?,
        val bounds: Rect,
        val normalizedLeft: Float,
        val normalizedTop: Float,
        val normalizedRight: Float,
        val normalizedBottom: Float,
        val centerX: Float,
        val centerY: Float,
        val widthRatio: Float,
        val heightRatio: Float,
        val headEulerAngleY: Float,
        val headEulerAngleZ: Float
    )

    data class AnalysisResult(
        val imageWidth: Int,
        val imageHeight: Int,
        val faces: List<FaceResult>,
        val faceCount: Int,
        val analysisTimeMs: Long,
        val frameNumber: Long
    )

    interface Listener {
        fun onResult(result: AnalysisResult)
        fun onError(error: Exception)
    }

    companion object {
        private const val TAG = "AICAM_FACE"
        private const val MIN_FRAME_INTERVAL_MS = 500L
        private const val MAX_FACES = 30
        private const val MIN_FACE_SIZE_RATIO = 0.015f
    }

    private val busy = AtomicBoolean(false)

    @Volatile
    private var closed = false

    private var lastFrameTime = 0L
    private var frameCounter = 0L

    private val detector: FaceDetector

    init {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(
                FaceDetectorOptions.PERFORMANCE_MODE_FAST
            )
            .setLandmarkMode(
                FaceDetectorOptions.LANDMARK_MODE_NONE
            )
            .setContourMode(
                FaceDetectorOptions.CONTOUR_MODE_NONE
            )
            .setClassificationMode(
                FaceDetectorOptions.CLASSIFICATION_MODE_NONE
            )
            .enableTracking()
            .build()

        detector = FaceDetection.getClient(options)

        Log.d(
            TAG,
            "Face Detector đã khởi tạo | FAST | MULTI-FACE"
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

        frameCounter++
        val currentFrame = frameCounter
        val startTime = SystemClock.elapsedRealtime()

        try {
            val inputImage = InputImage.fromBitmap(
                bitmap,
                normalizeRotation(rotationDegrees)
            )

            detector.process(inputImage)
                .addOnSuccessListener { detectedFaces ->

                    if (closed) return@addOnSuccessListener

                    val faces = detectedFaces
                        .mapNotNull { face ->

                            val bounds = face.boundingBox

                            if (
                                bounds.width() <= 0 ||
                                bounds.height() <= 0
                            ) {
                                return@mapNotNull null
                            }

                            val left = normalize(
                                bounds.left.toFloat() / imageWidth
                            )

                            val top = normalize(
                                bounds.top.toFloat() / imageHeight
                            )

                            val right = normalize(
                                bounds.right.toFloat() / imageWidth
                            )

                            val bottom = normalize(
                                bounds.bottom.toFloat() / imageHeight
                            )

                            val widthRatio = right - left
                            val heightRatio = bottom - top

                            if (
                                widthRatio < MIN_FACE_SIZE_RATIO ||
                                heightRatio < MIN_FACE_SIZE_RATIO
                            ) {
                                return@mapNotNull null
                            }

                            FaceResult(
                                trackingId = face.trackingId,
                                bounds = Rect(bounds),
                                normalizedLeft = left,
                                normalizedTop = top,
                                normalizedRight = right,
                                normalizedBottom = bottom,
                                centerX = (left + right) / 2f,
                                centerY = (top + bottom) / 2f,
                                widthRatio = widthRatio,
                                heightRatio = heightRatio,
                                headEulerAngleY = face.headEulerAngleY,
                                headEulerAngleZ = face.headEulerAngleZ
                            )
                        }
                        .sortedByDescending {
                            it.widthRatio * it.heightRatio
                        }
                        .take(MAX_FACES)

                    val elapsed =
                        SystemClock.elapsedRealtime() - startTime

                    val result = AnalysisResult(
                        imageWidth = imageWidth,
                        imageHeight = imageHeight,
                        faces = faces,
                        faceCount = faces.size,
                        analysisTimeMs = elapsed,
                        frameNumber = currentFrame
                    )

                    Log.d(
                        TAG,
                        "FACE RESULT #$currentFrame | " +
                            "faces=${result.faceCount} | " +
                            "${imageWidth}x$imageHeight | " +
                            "${elapsed}ms"
                    )

                    faces.forEachIndexed { index, face ->
                        Log.d(
                            TAG,
                            "FACE ${index + 1} | " +
                                "tracking=${face.trackingId} | " +
                                "center=(${face.centerX},${face.centerY}) | " +
                                "size=(${face.widthRatio},${face.heightRatio})"
                        )
                    }

                    listener.onResult(result)
                }
                .addOnFailureListener { error ->

                    Log.e(
                        TAG,
                        "Face Detection thất bại | FRAME #$currentFrame",
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
                "Không thể phân tích khuôn mặt",
                error
            )

            if (!closed) {
                listener.onError(error)
            }
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

        Log.d(TAG, "Face Detector đã đóng")
    }
}
