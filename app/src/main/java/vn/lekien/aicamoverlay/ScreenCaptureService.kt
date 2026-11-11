
package vn.lekien.aicamoverlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "AICAM_ScreenCapture"
        private const val CHANNEL_ID = "ai_cam_screen_capture"
        private const val NOTIFICATION_ID = 4102

        private const val EXTRA_IMAGE_WIDTH = "imageWidth"
        private const val EXTRA_IMAGE_HEIGHT = "imageHeight"
        private const val EXTRA_FRAME_NUMBER = "frameNumber"
        private const val EXTRA_AI_ERROR = "aiError"
        private const val EXTRA_AI_MESSAGE = "aiMessage"

        private const val ACTION_POSE_RESULT =
            "vn.lekien.aicamoverlay.ACTION_POSE_RESULT"

        private const val EXTRA_POSE_TYPES = "poseTypes"
        private const val EXTRA_POSE_X = "poseX"
        private const val EXTRA_POSE_Y = "poseY"
        private const val EXTRA_POSE_CONFIDENCES = "poseConfidences"
        private const val EXTRA_POSE_TIME = "poseTime"
        private const val EXTRA_POSE_DETECTED = "poseDetected"
        private const val EXTRA_POSE_WIDTH = "poseWidth"
        private const val EXTRA_POSE_HEIGHT = "poseHeight"

        private const val ACTION_FACE_RESULT =
            "vn.lekien.aicamoverlay.ACTION_FACE_RESULT"
        private const val EXTRA_FACE_COUNT = "faceCount"
        private const val EXTRA_FACE_BOXES = "faceBoxes"
        private const val EXTRA_FACE_TRACKING_IDS = "faceTrackingIds"
        private const val EXTRA_FACE_TIME = "faceTime"
    }

    private data class BitmapStats(
        val averageLuma: Int,
        val visibleRatio: Int
    )

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var aiVisionEngine: AiVisionEngine? = null
    private var poseVisionEngine: PoseVisionEngine? = null
    private var faceVisionEngine: FaceVisionEngine? = null

    private var firstFrameLogged = false
    private var capturedFrameCount = 0L
    private var analyzedFrameCount = 0L

    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection đã dừng")
                stopSelf()
            }
        }

    private val visionListener =
        object : AiVisionEngine.Listener {

            override fun onResult(
                result: AiVisionEngine.AnalysisResult
            ) {
                analyzedFrameCount++

                val stats = latestBitmapStats

                val message =
                    "Khung #${result.frameNumber} • " +
                    "${result.imageWidth}x${result.imageHeight} • " +
                    "${result.detections.size} đối tượng • " +
                    "${result.analysisTimeMs}ms • " +
                    "Độ sáng ${stats.averageLuma}/255 • " +
                    "Vùng sáng ${stats.visibleRatio}%"

                Log.d(TAG, "AI RESULT: $message")

                val labels = ArrayList<String>()
                val confidences = ArrayList<Float>()
                val boxes = ArrayList<Float>()

                result.detections.forEach { item ->

                    Log.d(
                        TAG,
                        "AI OBJECT: ${item.label} | " +
                            "confidence=${item.confidence}"
                    )

                    labels.add(item.label)
                    confidences.add(item.confidence)

                    boxes.add(item.normalizedLeft)
                    boxes.add(item.normalizedTop)
                    boxes.add(item.normalizedRight)
                    boxes.add(item.normalizedBottom)
                }

                sendAiResult(
                    labels = labels,
                    confidences = confidences,
                    boxes = boxes,
                    analysisTime = result.analysisTimeMs,
                    imageWidth = result.imageWidth,
                    imageHeight = result.imageHeight,
                    frameNumber = result.frameNumber,
                    message = message
                )
            }

            override fun onError(error: Exception) {
                val message = error.message
                    ?: error.javaClass.simpleName

                Log.e(TAG, "AI Vision phân tích thất bại", error)

                sendAiError(message)
            }
        }

    private val faceListener =
        object : FaceVisionEngine.Listener {

            override fun onResult(
                result: FaceVisionEngine.AnalysisResult
            ) {
                val boxes = FloatArray(result.faces.size * 4)

                val trackingIds = IntArray(result.faces.size) { index ->
                    result.faces[index].trackingId ?: -1
                }

                result.faces.forEachIndexed { index, face ->
                    val offset = index * 4

                    boxes[offset] = face.normalizedLeft
                    boxes[offset + 1] = face.normalizedTop
                    boxes[offset + 2] = face.normalizedRight
                    boxes[offset + 3] = face.normalizedBottom
                }

                val intent = Intent(ACTION_FACE_RESULT).apply {
                    setPackage(packageName)

                    putExtra(EXTRA_FACE_COUNT, result.faceCount)
                    putExtra(EXTRA_FACE_BOXES, boxes)
                    putExtra(EXTRA_FACE_TRACKING_IDS, trackingIds)
                    putExtra(EXTRA_FACE_TIME, result.analysisTimeMs)

                    putExtra(EXTRA_IMAGE_WIDTH, result.imageWidth)
                    putExtra(EXTRA_IMAGE_HEIGHT, result.imageHeight)
                    putExtra(EXTRA_FRAME_NUMBER, result.frameNumber)
                }

                sendBroadcast(intent)

                Log.d(
                    TAG,
                    "FACE BROADCAST | faces=${result.faceCount} | " +
                        "time=${result.analysisTimeMs}ms"
                )
            }

            override fun onError(error: Exception) {
                Log.e(
                    TAG,
                    "Face Detection phân tích thất bại",
                    error
                )
            }
        }

    private val poseListener =
        object : PoseVisionEngine.Listener {

            override fun onResult(
                result: PoseVisionEngine.PoseResult
            ) {
                val types = IntArray(result.points.size)
                val xs = FloatArray(result.points.size)
                val ys = FloatArray(result.points.size)
                val confidences = FloatArray(result.points.size)

                result.points.forEachIndexed { index, point ->
                    types[index] = point.type
                    xs[index] = point.x
                    ys[index] = point.y
                    confidences[index] = point.confidence
                }

                Log.d(
                    TAG,
                    "POSE BROADCAST | " +
                        "points=${result.points.size} | " +
                        "person=${result.personDetected} | " +
                        "time=${result.analysisTimeMs}ms"
                )

                val intent = Intent(ACTION_POSE_RESULT).apply {
                    setPackage(packageName)

                    putExtra(EXTRA_POSE_TYPES, types)
                    putExtra(EXTRA_POSE_X, xs)
                    putExtra(EXTRA_POSE_Y, ys)
                    putExtra(EXTRA_POSE_CONFIDENCES, confidences)

                    putExtra(
                        EXTRA_POSE_TIME,
                        result.analysisTimeMs
                    )

                    putExtra(
                        EXTRA_POSE_DETECTED,
                        result.personDetected
                    )

                    putExtra(EXTRA_POSE_WIDTH, result.imageWidth)
                    putExtra(EXTRA_POSE_HEIGHT, result.imageHeight)
                }

                sendBroadcast(intent)
            }

            override fun onError(error: Exception) {
                Log.e(
                    TAG,
                    "Pose Detection phân tích thất bại",
                    error
                )
            }
        }

    @Volatile
    private var latestBitmapStats = BitmapStats(0, 0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (mediaProjection != null) {
            return START_NOT_STICKY
        }

        val resultCode =
            intent?.getIntExtra("resultCode", 0) ?: 0

        val projectionData: Intent? =
            if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(
                    "projectionData",
                    Intent::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra("projectionData")
            }

        if (
            resultCode != Activity.RESULT_OK ||
            projectionData == null
        ) {
            Log.e(TAG, "Thiếu dữ liệu cấp quyền MediaProjection")
            sendAiError("Thiếu quyền MediaProjection")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startCaptureForeground()

        try {
            startScreenCapture(resultCode, projectionData)
        } catch (e: Exception) {
            Log.e(TAG, "Không thể khởi chạy chụp màn hình", e)
            sendAiError("Không khởi chạy được: ${e.message}")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI CAM Smart Frame",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Dịch vụ phân tích vùng xem camera"
            }

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    private fun startCaptureForeground() {

        val openAppIntent =
            Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            NOTIFICATION_ID,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                PendingIntent.FLAG_IMMUTABLE
        )

        val notification =
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("AI CAM OVERLAY")
                .setContentText("AI Vision đang phân tích hình ảnh")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build()

        startForeground(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        )
    }

    private fun startScreenCapture(
        resultCode: Int,
        data: Intent
    ) {

        val manager =
            getSystemService(MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager

        val projection =
            manager.getMediaProjection(resultCode, data)
                ?: throw SecurityException(
                    "Không thể khởi tạo MediaProjection"
                )

        mediaProjection = projection

        captureThread = HandlerThread(
            "AICAM_CaptureThread"
        ).apply {
            start()
        }

        captureHandler = Handler(captureThread!!.looper)

        aiVisionEngine = AiVisionEngine()
        poseVisionEngine = PoseVisionEngine()
        faceVisionEngine = FaceVisionEngine()

        projection.registerCallback(
            projectionCallback,
            captureHandler
        )

        val metrics = resources.displayMetrics

        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener(
            { reader ->
                processLatestFrame(reader)
            },
            captureHandler
        )

        virtualDisplay = projection.createVirtualDisplay(
            "AI CAM Smart Frame",
            width,
            height,
            densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            captureHandler
        )

        Log.d(
            TAG,
            "Smart Frame đã khởi chạy: ${width}x${height}"
        )

        Log.d(TAG, "AI Vision Engine đã kết nối")
        Log.d(TAG, "Pose Vision Engine đã kết nối")
        Log.d(TAG, "Face Vision Engine đã kết nối")

        sendAiError("Đang chờ khung hình đầu tiên")
    }

    private fun sendAiResult(
        labels: ArrayList<String>,
        confidences: ArrayList<Float>,
        boxes: ArrayList<Float>,
        analysisTime: Long,
        imageWidth: Int,
        imageHeight: Int,
        frameNumber: Long,
        message: String
    ) {
        val confidenceArray = FloatArray(confidences.size) {
            confidences[it]
        }

        val boxArray = FloatArray(boxes.size) {
            boxes[it]
        }

        val resultIntent = Intent(
            OverlayService.ACTION_AI_RESULT
        ).apply {
            setPackage(packageName)

            putStringArrayListExtra(
                OverlayService.EXTRA_LABELS,
                labels
            )

            putExtra(
                OverlayService.EXTRA_CONFIDENCES,
                confidenceArray
            )

            putExtra(
                OverlayService.EXTRA_BOXES,
                boxArray
            )

            putExtra(
                OverlayService.EXTRA_ANALYSIS_TIME,
                analysisTime
            )

            putExtra(
                OverlayService.EXTRA_DETECTION_COUNT,
                labels.size
            )

            putExtra(EXTRA_IMAGE_WIDTH, imageWidth)
            putExtra(EXTRA_IMAGE_HEIGHT, imageHeight)
            putExtra(EXTRA_FRAME_NUMBER, frameNumber)
            putExtra(EXTRA_AI_MESSAGE, message)
            putExtra(EXTRA_AI_ERROR, "")
        }

        sendBroadcast(resultIntent)
    }

    private fun sendAiError(message: String) {
        val errorIntent = Intent(
            OverlayService.ACTION_AI_RESULT
        ).apply {
            setPackage(packageName)

            putStringArrayListExtra(
                OverlayService.EXTRA_LABELS,
                arrayListOf()
            )

            putExtra(EXTRA_AI_ERROR, message)
            putExtra(EXTRA_AI_MESSAGE, "Lỗi: $message")
        }

        sendBroadcast(errorIntent)
    }

    private fun processLatestFrame(reader: ImageReader) {

        val image = try {
            reader.acquireLatestImage()
        } catch (error: Exception) {
            Log.e(TAG, "Không lấy được khung hình", error)
            sendAiError("Không lấy được khung hình: ${error.message}")
            null
        } ?: return

        try {
            capturedFrameCount++

            if (!firstFrameLogged) {
                Log.d(
                    TAG,
                    "Đã nhận khung hình đầu tiên: " +
                        "${image.width} x ${image.height}"
                )
                firstFrameLogged = true
            }

            if (capturedFrameCount % 30L == 0L) {
                Log.d(
                    TAG,
                    "Đã thu được $capturedFrameCount khung hình"
                )
            }

            val bitmap = imageToBitmap(image)

            if (bitmap == null) {
                sendAiError("Không chuyển được Image thành Bitmap")
                return
            }

            latestBitmapStats = inspectBitmap(bitmap)

            Log.d(
                TAG,
                "BITMAP CHECK | " +
                    "luma=${latestBitmapStats.averageLuma}/255 | " +
                    "visible=${latestBitmapStats.visibleRatio}%"
            )

            // Face Detection tự sao chép bitmap và giới hạn nhịp 500ms.
            faceVisionEngine?.analyze(
                sourceBitmap = bitmap,
                rotationDegrees = 0,
                listener = faceListener
            )

            // Pose Detection tự sao chép bitmap đồng bộ.
            poseVisionEngine?.analyze(
                sourceBitmap = bitmap,
                rotationDegrees = 0,
                listener = poseListener
            )

            // Object Detection tiếp tục sử dụng bitmap gốc.
            aiVisionEngine?.analyze(
                bitmap = bitmap,
                rotationDegrees = 0,
                listener = visionListener
            ) ?: run {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
                sendAiError("AI Vision Engine chưa sẵn sàng")
            }

        } catch (error: Exception) {
            Log.e(TAG, "Lỗi xử lý khung hình", error)
            sendAiError("Lỗi xử lý ảnh: ${error.message}")
        } finally {
            image.close()
        }
    }

    private fun inspectBitmap(bitmap: Bitmap): BitmapStats {
        val sampleSize = 24
        var totalLuma = 0L
        var visiblePixels = 0
        var samples = 0

        for (y in 0 until sampleSize) {
            val py = ((y + 0.5f) * bitmap.height / sampleSize)
                .toInt()
                .coerceIn(0, bitmap.height - 1)

            for (x in 0 until sampleSize) {
                val px = ((x + 0.5f) * bitmap.width / sampleSize)
                    .toInt()
                    .coerceIn(0, bitmap.width - 1)

                val pixel = bitmap.getPixel(px, py)

                val red = (pixel shr 16) and 0xFF
                val green = (pixel shr 8) and 0xFF
                val blue = pixel and 0xFF

                val luma =
                    (red * 299 + green * 587 + blue * 114) / 1000

                totalLuma += luma

                if (luma > 20) {
                    visiblePixels++
                }

                samples++
            }
        }

        if (samples == 0) {
            return BitmapStats(0, 0)
        }

        return BitmapStats(
            averageLuma = (totalLuma / samples).toInt(),
            visibleRatio = (visiblePixels * 100 / samples)
        )
    }

    private fun imageToBitmap(image: Image): Bitmap? {

        val plane = image.planes.firstOrNull() ?: return null

        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride

        if (pixelStride <= 0 || rowStride <= 0) {
            return null
        }

        val width = image.width
        val height = image.height

        val rowPadding =
            rowStride - pixelStride * width

        val paddedWidth =
            width + rowPadding / pixelStride

        val paddedBitmap = Bitmap.createBitmap(
            paddedWidth,
            height,
            Bitmap.Config.ARGB_8888
        )

        buffer.rewind()
        paddedBitmap.copyPixelsFromBuffer(buffer)

        if (paddedWidth == width) {
            return paddedBitmap
        }

        val croppedBitmap = Bitmap.createBitmap(
            paddedBitmap,
            0,
            0,
            width,
            height
        )

        if (croppedBitmap !== paddedBitmap) {
            paddedBitmap.recycle()
        }

        return croppedBitmap
    }

    override fun onDestroy() {

        try {
            imageReader?.setOnImageAvailableListener(null, null)

            virtualDisplay?.release()
            virtualDisplay = null

            imageReader?.close()
            imageReader = null

            faceVisionEngine?.close()
            faceVisionEngine = null

            poseVisionEngine?.close()
            poseVisionEngine = null

            aiVisionEngine?.close()
            aiVisionEngine = null

            mediaProjection?.unregisterCallback(
                projectionCallback
            )

            mediaProjection?.stop()
            mediaProjection = null

            captureThread?.quitSafely()
            captureThread = null
            captureHandler = null

        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi giải phóng tài nguyên", e)
        }

                Log.d(
            TAG,
            "Smart Frame, AI Vision, Pose Vision và Face Vision đã tắt"
        )

        sendBroadcast(
            Intent(OverlayService.ACTION_SMART_FRAME_STATE).apply {
                setPackage(packageName)
                putExtra(
                    OverlayService.EXTRA_SMART_FRAME_ACTIVE,
                    false
                )
            }
        )

        super.onDestroy()
    }
}
