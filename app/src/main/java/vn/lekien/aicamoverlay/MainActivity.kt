
package vn.lekien.aicamoverlay

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    private fun dp(v: Int): Int =
        (v * resources.displayMetrics.density).toInt()

    private fun gradient(
        radius: Int,
        colors: IntArray
    ) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        colors
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb(180, 255, 255, 255))
    }

    private fun pill(
        label: String,
        action: () -> Unit
    ) = TextView(this).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(38, 55, 75))
        setTypeface(null, android.graphics.Typeface.BOLD)
        background = gradient(
            40,
            intArrayOf(
                Color.rgb(255, 255, 255),
                Color.rgb(225, 240, 255),
                Color.rgb(242, 235, 255)
            )
        )
        setPadding(dp(18), dp(15), dp(18), dp(15))
        setOnClickListener { action() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.rgb(239, 248, 255),
                    Color.rgb(255, 255, 255),
                    Color.rgb(239, 235, 255)
                )
            )
        }

        val glass = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(24), dp(18), dp(24))
            background = gradient(
                30,
                intArrayOf(
                    Color.argb(235, 255, 255, 255),
                    Color.argb(220, 235, 247, 255),
                    Color.argb(225, 246, 240, 255)
                )
            )
        }

        val title = TextView(this).apply {
            text = "✦  AI CAM OVERLAY"
            textSize = 25f
            setTextColor(Color.rgb(35, 55, 78))
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "FLOW GLASS PHOTOGRAPHY ASSISTANT"
            textSize = 11f
            setTextColor(Color.rgb(100, 120, 145))
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(20))
        }

        status = TextView(this).apply {
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(50, 90, 115))
            setPadding(0, dp(12), 0, dp(20))
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

        val stopButton = pill("TẮT AI CAM OVERLAY") {
            stopService(
                Intent(this@MainActivity, OverlayService::class.java)
            )
            status.text = "Đã yêu cầu tắt lớp phủ"
        }

        glass.addView(title)
        glass.addView(subtitle)
        glass.addView(status)

        for (button in listOf(
            permissionButton,
            startButton,
            stopButton
        )) {
            glass.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
            )
        }

        layout.addView(
            glass,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        setContentView(layout)
        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized) updateStatus()
    }

    private fun updateStatus() {
        status.text = if (Settings.canDrawOverlays(this)) {
            "Quyền Overlay: ĐÃ CẤP"
        } else {
            "Quyền Overlay: CHƯA CẤP"
        }
    }
}
