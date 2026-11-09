
package vn.lekien.aicamoverlay

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.rgb(15, 22, 32))
        }

        val title = TextView(this).apply {
            text = "AI CAM OVERLAY"
            textSize = 28f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val subtitle = TextView(this).apply {
            text = "OFFLINE PHOTOGRAPHY ASSISTANT"
            textSize = 12f
            setTextColor(Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 32)
        }

        status = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, 16, 0, 24)
        }

        val permissionButton = Button(this).apply {
            text = "CẤP QUYỀN OVERLAY"
            setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        val startButton = Button(this).apply {
            text = "BẬT AI CAM OVERLAY"
            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    status.text = "Vui lòng cấp quyền Overlay trước"
                    return@setOnClickListener
                }

                startService(
                    Intent(this@MainActivity, OverlayService::class.java)
                )
                status.text = "Đang bật lớp phủ AI CAM"
            }
        }

        val stopButton = Button(this).apply {
            text = "TẮT AI CAM OVERLAY"
            setOnClickListener {
                stopService(
                    Intent(this@MainActivity, OverlayService::class.java)
                )
                status.text = "Đã yêu cầu tắt lớp phủ"
            }
        }

        layout.addView(title)
        layout.addView(subtitle)
        layout.addView(status)
        layout.addView(permissionButton)
        layout.addView(startButton)
        layout.addView(stopButton)

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
