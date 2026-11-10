
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
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var aiVisionEngine: AiVisionEngine? = null

    private var firstFrameLogged = false
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

                Log.d(
                    TAG,
                    "AI FRAME #$analyzedFrameCount | " +
                        "${result.imageWidth}x${result.imageHeight} | " +
                        "Thời gian: ${result.analysisTimeMs}ms | " +
                        "Số đối tượng: ${result.detections.size}"
                )

                result.detections.forEach { item ->
                    Log.d(
                        TAG,
                        "AI OBJECT: ${item.label} | " +
                            "confidence=${item.confidence} | " +
                            "box=(" +
                            "${item.normalizedLeft}, " +
                            "${item.normalizedTop}, " +
                            "${item.normalizedRight}, " +
                            "${item.normalizedBottom})"
                    )
                }
            }

            override fun onError(error: Exception) {
                Log.e(TAG, "AI Vision phân tích thất bại", error)
            }
        }

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
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startCaptureForeground()

        try {
            startScreenCapture(resultCode, projectionData)
        } catch (e: Exception) {
            Log.e(TAG, "Không thể khởi chạy chụp màn hình", e)
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
    }

    private fun processLatestFrame(reader: ImageReader) {

        val image = try {
            reader.acquireLatestImage()
        } catch (error: Exception) {
            Log.e(TAG, "Không lấy được khung hình", error)
            null
        } ?: return

        try {
            if (!firstFrameLogged) {
                Log.d(
                    TAG,
                    "Đã nhận khung hình: " +
                        "${image.width} x ${image.height}"
                )
                firstFrameLogged = true
            }

            val bitmap = imageToBitmap(image) ?: return

            aiVisionEngine?.analyze(
                bitmap = bitmap,
                rotationDegrees = 0,
                listener = visionListener
            ) ?: run {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }

        } catch (error: Exception) {
            Log.e(TAG, "Lỗi xử lý khung hình", error)
        } finally {
            image.close()
        }
    }

    /**
     * Chuyển Image RGBA_8888 thành Bitmap.
     * Xử lý cả rowStride và pixelStride để tránh lệch ảnh.
     */
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

        Log.d(TAG, "Smart Frame và AI Vision đã tắt")

        super.onDestroy()
    }
}
