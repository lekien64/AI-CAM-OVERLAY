
package vn.lekien.aicamoverlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
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

    private var expanded = true
    private var opacityIndex = 0
    private var mode = "AUTO"

    private val opacities = floatArrayOf(1f, 0.85f, 0.70f, 0.55f)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (this::panel.isInitialized && panel.parent != null) {
            return START_NOT_STICKY
        }

        showPanel()
        return START_NOT_STICKY
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun glassBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(22).toFloat()
            setColor(Color.argb(225, 18, 29, 43))
            setStroke(dp(1), Color.argb(110, 130, 205, 235))
        }
    }

    private fun makeText(
        value: String,
        size: Float,
        color: Int
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }
    }

    private fun makeButton(
        value: String,
        onClick: () -> Unit
    ): TextView {
        return makeText(value, 13f, Color.WHITE).apply {
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(100, 70, 145, 190))
                setStroke(dp(1), Color.argb(100, 150, 220, 255))
            }
            setOnClickListener { onClick() }
        }
    }

    private fun showPanel() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassBackground()
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
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
            )
        }

        val minimize = makeButton("−") {
            toggleExpanded()
        }

        header.addView(title)
        header.addView(minimize)
        panel.addView(header)

        val content = LinearLayout(this).apply {
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

        val autoButton = makeButton("AUTO") {
            mode = "AUTO"
            updateMode(modeRow)
        }

        val manualButton = makeButton("MANUAL") {
            mode = "MANUAL"
            updateMode(modeRow)
        }

        modeRow.addView(
            autoButton,
            LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginEnd = dp(5)
            }
        )

        modeRow.addView(
            manualButton,
            LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                marginStart = dp(5)
            }
        )

        val guide = makeText(
            "Khung hướng dẫn chụp ảnh sẽ hiển thị tại đây",
            11f,
            Color.LTGRAY
        )

        val opacityButton = makeButton("ĐỘ TRONG SUỐT: 100%") {
            opacityIndex = (opacityIndex + 1) % opacities.size
            panel.alpha = opacities[opacityIndex]
            (it as TextView).text =
                "ĐỘ TRONG SUỐT: ${ (opacities[opacityIndex] * 100).toInt() }%"
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

        header.setOnTouchListener(
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
                    }
                    return false
                }
            }
        )

        wm.addView(panel, params)
    }

    private fun updateMode(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val button = row.getChildAt(i) as TextView
            val selected = button.text.toString() == mode

            button.alpha = if (selected) 1f else 0.55f
        }
    }

    private fun toggleExpanded() {
        expanded = !expanded

        val content = panel.getChildAt(1)
        content.visibility =
            if (expanded) View.VISIBLE else View.GONE

        val header = panel.getChildAt(0) as LinearLayout
        val button = header.getChildAt(1) as TextView
        button.text = if (expanded) "−" else "+"
    }

    override fun onDestroy() {
        if (this::panel.isInitialized && panel.parent != null) {
            wm.removeView(panel)
        }
        super.onDestroy()
    }
}
