
package vn.lekien.aicamoverlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.net.Uri
import android.provider.Settings
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    private val projectionRequestCode = 4101

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

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

        val permissionButton = pill("CẤP QUYỀN OVERLAY") {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        val startButton = pill("BẬT AI CAM OVERLAY") {
            if (!Settings.canDrawOverlays(this@MainActivity)) {
                status.text = "Vui lòng cấp quyền Overlay trước"
                return@pill
            }

            startService(
                Intent(this@MainActivity, OverlayService::class.java)
            )
            status.text = "Đang bật lớp phủ AI CAM"
        }

        val smartFrameButton = pill("BẬT SMART FRAME") {
            requestScreenCapture()
        }

        val stopButton = pill("TẮT AI CAM OVERLAY") {
            stopService(
                Intent(this@MainActivity, ScreenCaptureService::class.java)
            )

            stopService(
                Intent(this@MainActivity, OverlayService::class.java)
            )

            status.text = "Đã yêu cầu dừng AI CAM và Smart Frame"
        }

        info.addView(title)
        info.addView(subtitle)
        info.addView(status)

        for (button in listOf(
            permissionButton,
            startButton,
            smartFrameButton,
            stopButton
        )) {
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
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            glass.addView(
                controls,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        } else {
            glass.addView(info)

            glass.addView(
                controls,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        root.addView(
            glass,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(root)
        updateStatus()
    }

    private fun requestScreenCapture() {
        val manager =
            getSystemService(MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager

        startActivityForResult(
            manager.createScreenCaptureIntent(),
            projectionRequestCode
        )
    }

    @Deprecated("Using activity result for MediaProjection permission")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != projectionRequestCode) return

        if (resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(
                this,
                ScreenCaptureService::class.java
            ).apply {
                putExtra("resultCode", resultCode)
                putExtra("projectionData", data)
            }

            startForegroundService(serviceIntent)

            status.text = "Đã cấp quyền Smart Frame"
        } else {
            status.text = "Bạn chưa cấp quyền chụp màn hình"
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        buildScreen()
    }

    private fun updateStatus() {
        status.text = if (Settings.canDrawOverlays(this)) {
            "Quyền Overlay: ĐÃ CẤP"
        } else {
            "Quyền Overlay: CHƯA CẤP"
        }
    }
}
