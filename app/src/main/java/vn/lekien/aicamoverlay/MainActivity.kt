
package vn.lekien.aicamoverlay

import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    companion object {
        private const val PREFS = "ai_cam_setup"
        private const val KEY_SETUP_DONE = "setup_done"
        private const val REQ_OVERLAY = 5101
        private const val REQ_USAGE = 5102
        private const val REQ_PROJECTION = 5103
    }

    private lateinit var status: TextView
    private lateinit var prefs: SharedPreferences

    private var permissionFlowStarted = false
    private var waitingForOverlay = false
    private var waitingForUsage = false

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun hasUsageAccess(): Boolean {
        val appOps =
            getSystemService(APP_OPS_SERVICE) as AppOpsManager

        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    private fun glassBackground(radius: Int) =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.rgb(10, 12, 18),
                Color.rgb(27, 31, 40),
                Color.rgb(12, 14, 20)
            )
        ).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(160, 220, 230, 245))
        }

    private fun pill(
        label: String,
        action: () -> Unit
    ) = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)

        background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(
                Color.rgb(57, 63, 76),
                Color.rgb(31, 35, 44)
            )
        ).apply {
            cornerRadius = dp(40).toFloat()
            setStroke(dp(1), Color.argb(150, 210, 220, 235))
        }

        setPadding(dp(18), dp(15), dp(18), dp(15))
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)

        buildScreen()
    }

    private fun buildScreen() {
        val landscape =
            resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(
                dp(if (landscape) 32 else 24),
                dp(24),
                dp(if (landscape) 32 else 24),
                dp(24)
            )

            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(5, 7, 11),
                    Color.rgb(19, 22, 30),
                    Color.rgb(7, 9, 14)
                )
            )
        }

        val glass = LinearLayout(this).apply {
            orientation = if (landscape) {
                LinearLayout.HORIZONTAL
            } else {
                LinearLayout.VERTICAL
            }
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(24), dp(20), dp(24))
            background = glassBackground(30)
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = "✦  AI CAM OVERLAY"
            textSize = 25f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "SMART FRAME • FLOW GLASS"
            textSize = 11f
            setTextColor(Color.rgb(190, 198, 212))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(20))
        }

        status = TextView(this).apply {
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(225, 230, 240))
            setPadding(dp(8), dp(12), dp(8), dp(20))
        }

        val overlayButton = pill("CẤP QUYỀN OVERLAY") {
            openOverlaySettings()
        }

        val usageButton = pill("CẤP QUYỀN THEO DÕI ỨNG DỤNG") {
            openUsageSettings()
        }

        val startButton = pill("BẬT AI CAM OVERLAY") {
            startAiCam()
        }

        val smartFrameButton = pill("BẬT SMART FRAME") {
            requestScreenCapture()
        }

        val stopButton = pill("TẮT AI CAM OVERLAY") {
            stopAllServices()
        }

        info.addView(title)
        info.addView(subtitle)
        info.addView(status)

        listOf(
            overlayButton,
            usageButton,
            startButton,
            smartFrameButton,
            stopButton
        ).forEach { button ->
            controls.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
            )
        }

        if (landscape) {
            glass.addView(
                info,
                LinearLayout.LayoutParams(0, -2, 1f)
            )
            glass.addView(
                controls,
                LinearLayout.LayoutParams(0, -2, 1f)
            )
        } else {
            glass.addView(info)
            glass.addView(
                controls,
                LinearLayout.LayoutParams(-1, -2)
            )
        }

        root.addView(
            glass,
            LinearLayout.LayoutParams(-1, -2)
        )

        setContentView(root)
        updateStatus()
    }

    private fun openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            continuePermissionFlow()
            return
        }

        waitingForOverlay = true

        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )

        startActivityForResult(intent, REQ_OVERLAY)
    }

    private fun openUsageSettings() {
        if (hasUsageAccess()) {
            continuePermissionFlow()
            return
        }

        waitingForUsage = true

        startActivityForResult(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
            REQ_USAGE
        )
    }

    private fun continuePermissionFlow() {
        if (isFinishing) return

        if (!Settings.canDrawOverlays(this)) {
            status.text = "Vui lòng bật quyền hiển thị trên ứng dụng khác"
            return
        }

        if (!hasUsageAccess()) {
            status.text = "Vui lòng bật quyền truy cập dữ liệu sử dụng"
            openUsageSettings()
            return
        }

        if (prefs.getBoolean(KEY_SETUP_DONE, false)) {
            status.text = "Đã hoàn tất thiết lập quyền"
            return
        }

        status.text = "Đang yêu cầu quyền Smart Frame..."
        requestScreenCapture()
    }

    private fun requestScreenCapture() {
        val manager =
            getSystemService(MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager

        startActivityForResult(
            manager.createScreenCaptureIntent(),
            REQ_PROJECTION
        )
    }

    private fun startAiCam() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "Cần cấp quyền Overlay trước"
            openOverlaySettings()
            return
        }

        if (!hasUsageAccess()) {
            status.text = "Cần cấp quyền Usage Access trước"
            openUsageSettings()
            return
        }

        startService(
            Intent(this, OverlayService::class.java)
        )

        status.text = "AI CAM OVERLAY đang hoạt động"
    }

    private fun stopAllServices() {
        stopService(
            Intent(this, ScreenCaptureService::class.java)
        )

        stopService(
            Intent(this, OverlayService::class.java)
        )

        status.text = "Đã tắt AI CAM OVERLAY và Smart Frame"
    }

    @Deprecated("Activity result required for system permission screens")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        when (requestCode) {
            REQ_OVERLAY -> {
                waitingForOverlay = false
                if (Settings.canDrawOverlays(this)) {
                    continuePermissionFlow()
                } else {
                    status.text = "Bạn chưa cấp quyền Overlay"
                }
            }

            REQ_USAGE -> {
                waitingForUsage = false
                if (hasUsageAccess()) {
                    continuePermissionFlow()
                } else {
                    status.text = "Bạn chưa cấp quyền Usage Access"
                }
            }

            REQ_PROJECTION -> {
                if (resultCode == RESULT_OK && data != null) {
                    val serviceIntent = Intent(
                        this,
                        ScreenCaptureService::class.java
                    ).apply {
                        putExtra("resultCode", resultCode)
                        putExtra("projectionData", data)
                    }

                    startForegroundService(serviceIntent)

                    startAiCam()

                    prefs.edit()
                        .putBoolean(KEY_SETUP_DONE, true)
                        .apply()

                    status.text = "Thiết lập hoàn tất. AI CAM đã bật."
                } else {
                    status.text = "Bạn chưa cấp quyền Smart Frame"
                }
            }
        }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()

        if (::status.isInitialized) {
            updateStatus()

            if (!prefs.getBoolean(KEY_SETUP_DONE, false) &&
                !permissionFlowStarted
            ) {
                permissionFlowStarted = true
                continuePermissionFlow()
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildScreen()
    }

    private fun updateStatus() {
        if (!::status.isInitialized) return

        status.text =
            "Overlay: ${if (Settings.canDrawOverlays(this)) "ĐÃ CẤP" else "CHƯA CẤP"}\n" +
            "Usage Access: ${if (hasUsageAccess()) "ĐÃ CẤP" else "CHƯA CẤP"}"
    }
}
