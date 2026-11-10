
package vn.lekien.aicamoverlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
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

    private var firstFrameLogged = false

    private val projectionCallback =
        object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection đã dừng")
                stopSelf()
            }
        }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

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
                .setContentText("Smart Frame đang hoạt động")
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

        captureHandler =
            Handler(captureThread!!.looper)

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

                val image = reader.acquireLatestImage()

                if (image != null) {
                    try {
                        if (!firstFrameLogged) {

                            Log.d(
                                TAG,
                                "Đã nhận khung hình: " +
                                    "${image.width} x ${image.height}"
                            )

                            firstFrameLogged = true
                        }
                    } finally {
                        image.close()
                    }
                }
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
    }

    override fun onDestroy() {

        try {
            imageReader?.setOnImageAvailableListener(null, null)

            virtualDisplay?.release()
            virtualDisplay = null

            imageReader?.close()
            imageReader = null

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

        Log.d(TAG, "Smart Frame đã tắt")

        super.onDestroy()
    }
}
