
package vn.lekien.aicamoverlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log

class SmartFramePermissionActivity : Activity() {

    companion object {
        private const val REQUEST_SCREEN_CAPTURE = 6101
        private const val TAG = "AICAM_Permission"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = getSystemService(
            MEDIA_PROJECTION_SERVICE
        ) as MediaProjectionManager

        try {
            startActivityForResult(
                manager.createScreenCaptureIntent(),
                REQUEST_SCREEN_CAPTURE
            )
        } catch (error: Exception) {
            Log.e(TAG, "Không mở được hộp thoại cấp quyền", error)
            finish()
        }
    }

    @Deprecated("Activity result required for MediaProjection permission")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SCREEN_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                try {
                    val serviceIntent = Intent(
                        this,
                        ScreenCaptureService::class.java
                    ).apply {
                        putExtra("resultCode", resultCode)
                        putExtra("projectionData", data)
                    }

                    startForegroundService(serviceIntent)

                    sendSmartFrameState(true)
                } catch (error: Exception) {
                    Log.e(TAG, "Không khởi động được Smart Frame", error)
                    sendSmartFrameState(false)
                }
            }

            finish()
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
