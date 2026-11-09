
package vn.lekien.aicamoverlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var panel: LinearLayout

    private lateinit var content: LinearLayout
    private lateinit var autoButton: TextView
    private lateinit var manualButton: TextView
    private lateinit var minimizeButton: TextView
    private lateinit var opacityButton: TextView

    private var expanded = true
    private var opacityIndex = 0
    private var mode = "AUTO"

    private val opacities = floatArrayOf(
        1f, 0.85f, 0.70f, 0.55f
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (!this::panel.isInitialized || panel.parent == null) {
            showPanel()
        }
        return START_NOT_STICKY
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun makeBackground(
        color: Int,
        strokeColor: Int = Color.TRANSPARENT
    ) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(18).toFloat()
        setColor(color)
        setStroke(dp(1), strokeColor)
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    private fun makeButton(
        value: String,
        onClick: () -> Unit
    ) = makeText(value, 13f, Color.WHITE).apply {
        background = makeBackground(
            Color.argb(90, 65, 135, 185),
            Color.argb(100, 150, 220, 255)
        )
        setOnClickListener { onClick() }
    }

    private fun showPanel() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeBackground(
                Color.argb(235, 18, 29, 43),
                Color.argb(130, 130, 205, 235)
            )
            setPadding(dp(10), dp(8), dp(10), dp(10))
        }

        params = WindowManager.LayoutParams(
            dp(270),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(180)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = makeText(
            "✦  AI CAM",
            17f,
            Color.WHITE
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            )
        }

        minimizeButton = makeButton("−") {
            toggleExpanded()
        }

        header.addView(title)
        header.addView(minimizeButton)
        panel.addView(header)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val subtitle = makeText(
            "OFFLINE PHOTOGRAPHY ASSISTANT",
            9f,
            Color.LTGRAY
        )

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        autoButton = makeButton("AUTO") {
            mode = "AUTO"
            updateModeButtons()
        }

        manualButton = makeButton("MANUAL") {
            mode = "MANUAL"
            updateModeButtons()
        }

        modeRow.addView(
            autoButton,
            LinearLayout.LayoutParams(
                0, dp(46), 1f
            ).apply {
                marginEnd = dp(5)
            }
        )

        modeRow.addView(
            manualButton,
            LinearLayout.LayoutParams(
                0, dp(46), 1f
            ).apply {
                marginStart = dp(5)
            }
        )

        val guide = makeText(
            "Khung hướng dẫn chụp ảnh sẽ hiển thị tại đây",
            11f,
            Color.LTGRAY
        )

        opacityButton = makeButton(
            "ĐỘ TRONG SUỐT: 100%"
        ) {
            opacityIndex = (opacityIndex + 1) % opacities.size
            panel.alpha = opacities[opacityIndex]
            updateOpacityText()
        }

        val closeButton = makeButton("ĐÓNG OVERLAY") {
            stopSelf()
        }

        content.addView(subtitle)
        content.addView(modeRow)
        content.addView(guide)
        content.addView(opacityButton)
        content.addView(closeButton)

        panel.addView(content)

        title.setOnTouchListener(
            object : View.OnTouchListener {
                private var startX = 0
                private var startY = 0
                private var touchX = 0f
                private var touchY = 0f

                override fun onTouch(
                    view: View,
                    event: MotionEvent
                ): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = params.x
                            startY = params.y
                            touchX = event.rawX
                            touchY = event.rawY
                            return true
                        }

                        MotionEvent.ACTION_MOVE -> {
                            params.x = startX +
                                (event.rawX - touchX).toInt()
                            params.y = startY +
                                (event.rawY - touchY).toInt()

                            wm.updateViewLayout(panel, params)
                            return true
                        }

                        MotionEvent.ACTION_UP -> return true
                    }
                    return true
                }
            }
        )

        updateModeButtons()
        updateOpacityText()

        wm.addView(panel, params)
    }

    private fun updateModeButtons() {
        autoButton.alpha = if (mode == "AUTO") 1f else 0.55f
        manualButton.alpha = if (mode == "MANUAL") 1f else 0.55f

        autoButton.setTypeface(
            null,
            if (mode == "AUTO") Typeface.BOLD else Typeface.NORMAL
        )

        manualButton.setTypeface(
            null,
            if (mode == "MANUAL") Typeface.BOLD else Typeface.NORMAL
        )
    }

    private fun updateOpacityText() {
        opacityButton.text =
            "ĐỘ TRONG SUỐT: ${(opacities[opacityIndex] * 100).toInt()}%"
    }

    private fun toggleExpanded() {
        expanded = !expanded

        content.visibility =
            if (expanded) View.VISIBLE else View.GONE

        minimizeButton.text = if (expanded) "−" else "+"

        panel.requestLayout()
        wm.updateViewLayout(panel, params)
    }

    override fun onDestroy() {
        if (
            this::panel.isInitialized &&
            panel.parent != null
        ) {
            wm.removeView(panel)
        }
        super.onDestroy()
    }
}
