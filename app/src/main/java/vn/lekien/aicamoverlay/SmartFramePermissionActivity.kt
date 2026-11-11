
package vn.lekien.aicamoverlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log

class SmartFramePermissionActivity : Activity() {

    companion object {
        private const val REQUEST_SCREEN_CAPTURE = 6101
        private const val TAG = "AICAM_Permission"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val manager = getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

            val permissionIntent =
                manager.createScreenCaptureIntent()

            startActivityForResult(
                permissionIntent,
                REQUEST_SCREEN_CAPTURE
            )

        } catch (error: Exception) {
            Log.e(
                TAG,
                "Không mở được hộp thoại cấp quyền",
                error
            )
            finish()
        }
    }

    @Deprecated("Activity result required for MediaProjection permission")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode != REQUEST_SCREEN_CAPTURE) {
            return
        }

        if (resultCode == RESULT_OK && data != null) {
            startSmartFrameService(resultCode, data)
        } else {
            sendSmartFrameState(false)
        }

        finish()
    }

    private fun startSmartFrameService(
        resultCode: Int,
        data: Intent
    ) {
        try {
            val serviceIntent = Intent(
                this,
                ScreenCaptureService::class.java
            ).apply {
                putExtra("resultCode", resultCode)
                putExtra("projectionData", data)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            sendSmartFrameState(true)

        } catch (error: Exception) {
            Log.e(
                TAG,
                "Không khởi động được Smart Frame",
                error
            )

            sendSmartFrameState(false)
        }
    }

    private fun sendSmartFrameState(active: Boolean) {
        val intent = Intent(
            OverlayService.ACTION_SMART_FRAME_STATE
        ).apply {
            setPackage(packageName)

            putExtra(
                OverlayService.EXTRA_SMART_FRAME_ACTIVE,
                active
            )
        }

        sendBroadcast(intent)
    }
}
