
package vn.lekien.aicamoverlay

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
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
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var panel: LinearLayout
    private lateinit var opacityText: TextView

    private var expanded = true
    private var mode = "AUTO"
    private var glassAlpha = 38
    private var isLandscape = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int
    ): Int {
        if (!this::panel.isInitialized || panel.parent == null) {
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            isLandscape = resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE
            createWindow()
            renderPanel()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isLandscape = newConfig.orientation ==
            Configuration.ORIENTATION_LANDSCAPE

        if (this::panel.isInitialized && panel.parent != null) {
            renderPanel()
        }
    }

    private fun dp(v: Int) =
        (v * resources.displayMetrics.density).toInt()

    private fun glassBackground(radius: Int = 24) =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(glassAlpha, 255, 255, 255),
                Color.argb(glassAlpha, 225, 242, 255),
                Color.argb(glassAlpha, 245, 235, 255)
            )
        ).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(190, 255, 255, 255))
        }

    private fun pillBackground(selected: Boolean = false) =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (selected) {
                intArrayOf(
                    Color.rgb(220, 241, 255),
                    Color.rgb(238, 231, 255)
                )
            } else {
                intArrayOf(
                    Color.argb(175, 255, 255, 255),
                    Color.argb(145, 235, 245, 255)
                )
            }
        ).apply {
            cornerRadius = dp(40).toFloat()
            setStroke(dp(1), Color.argb(190, 255, 255, 255))
        }

    private fun text(
        value: String,
        size: Float,
        color: Int = Color.rgb(35, 53, 72)
    ) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(9), dp(12), dp(9))
    }

    private fun button(
        value: String,
        selected: Boolean = false,
        action: () -> Unit
    ) = text(value, 13f).apply {
        background = pillBackground(selected)
        setTypeface(null, Typeface.BOLD)
        setOnClickListener { action() }
    }

    private fun createWindow() {
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        params = WindowManager.LayoutParams(
            dp(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(180)
        }

        wm.addView(panel, params)
    }

    private fun renderPanel() {
        panel.removeAllViews()

        if (!expanded) {
            renderBubble()
            return
        }

        params.width = dp(if (isLandscape) 245 else 280)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(10), dp(8), dp(10), dp(10))
        panel.background = glassBackground(26)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = text("✦  AI CAM", 18f).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
        }

        val minimize = button("−") {
            expanded = false
            renderPanel()
        }

        header.addView(title)
        header.addView(minimize)
        panel.addView(header)
        attachDrag(title)

        panel.addView(text("WHITE FLOW GLASS CAMERA", 9f))

        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val auto = button("AUTO", mode == "AUTO") {
            mode = "AUTO"
            updateMode(modeRow)
        }

        val manual = button("MANUAL", mode == "MANUAL") {
            mode = "MANUAL"
            updateMode(modeRow)
        }

        modeRow.addView(auto, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginEnd = dp(5)
        })
        modeRow.addView(manual, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
            marginStart = dp(5)
        })

        panel.addView(modeRow)

        panel.addView(
            text("Khung hướng dẫn chụp ảnh sẽ hiển thị tại đây", 11f)
        )

        opacityText = text("", 11f)
        panel.addView(opacityText)

        val slider = SeekBar(this).apply {
            max = 60
            progress = glassAlpha - 15
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?, progress: Int, fromUser: Boolean
                    ) {
                        glassAlpha = progress + 15
                        panel.background = glassBackground(26)
                        updateOpacityText()
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                }
            )
        }

        panel.addView(slider)

        panel.addView(button("ĐÓNG OVERLAY") {
            stopSelf()
        })

        updateMode(modeRow)
        updateOpacityText()
        wm.updateViewLayout(panel, params)
    }

    private fun renderBubble() {
        params.width = dp(48)
        panel.setPadding(0, 0, 0, 0)

        val bubble = text("✦", 21f).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.argb(glassAlpha, 255, 255, 255),
                    Color.argb(glassAlpha, 220, 240, 255)
                )
            ).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), Color.argb(210, 255, 255, 255))
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }

        panel.addView(bubble)
        attachDrag(bubble) {
            expanded = true
            renderPanel()
        }
        wm.updateViewLayout(panel, params)
    }

    private fun attachDrag(view: View, onTap: (() -> Unit)? = null) {
        view.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0
            var sy = 0
            var tx = 0f
            var ty = 0f
            var moved = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        sx = params.x
                        sy = params.y
                        tx = e.rawX
                        ty = e.rawY
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - tx
                        val dy = e.rawY - ty
                        if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                            moved = true
                        }
                        if (moved) {
                            params.x = sx + dx.toInt()
                            params.y = sy + dy.toInt()
                            wm.updateViewLayout(panel, params)
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) onTap?.invoke()
                        return true
                    }
                }
                return true
            }
        })
    }

    private fun updateMode(row: LinearLayout) {
        for (i in 0 until row.childCount) {
            val item = row.getChildAt(i) as TextView
            val selected = item.text.toString() == mode
            item.background = pillBackground(selected)
        }
    }

    private fun updateOpacityText() {
        if (this::opacityText.isInitialized) {
            opacityText.text = "ĐỘ TRONG SUỐT: ${100 - glassAlpha}%"
        }
    }

    override fun onDestroy() {
        if (this::panel.isInitialized && panel.parent != null) {
            wm.removeView(panel)
        }
        super.onDestroy()
    }
}
