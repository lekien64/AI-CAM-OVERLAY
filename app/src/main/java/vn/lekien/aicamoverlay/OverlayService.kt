
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
import android.view.OrientationEventListener
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var panel: LinearLayout
    private lateinit var opacityText: TextView

    private var orientationListener: OrientationEventListener? = null
    private var expanded = true
    private var mode = "AUTO"
    private var glassAlpha = 205
    private var isLandscape = false

    override fun onCreate() {
        super.onCreate()

        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return

                val landscape =
                    degrees in 45..135 || degrees in 225..315

                if (landscape != isLandscape) {
                    isLandscape = landscape

                    if (this@OverlayService::panel.isInitialized &&
                        panel.parent != null
                    ) {
                        renderPanel()
                    }
                }
            }
        }

        if (orientationListener?.canDetectOrientation() == true) {
            orientationListener?.enable()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?, flags: Int, startId: Int
    ): Int {
        if (!this::panel.isInitialized || panel.parent == null) {
            wm = getSystemService(WINDOW_SERVICE) as WindowManager

            isLandscape =
                resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE

            createWindow()
            renderPanel()
        }
        return START_NOT_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Không ghi đè trạng thái cảm biến khi Android đang khóa xoay.
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            isLandscape = true
        }

        if (this::panel.isInitialized && panel.parent != null) {
            renderPanel()
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun glassBackground(radius: Int = 26) =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(glassAlpha, 8, 10, 14),
                Color.argb(glassAlpha, 22, 25, 32),
                Color.argb(glassAlpha, 9, 11, 16)
            )
        ).apply {
            cornerRadius = dp(radius).toFloat()
            setStroke(dp(1), Color.argb(150, 220, 230, 245))
        }

    private fun pillBackground(selected: Boolean = false) =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (selected) {
                intArrayOf(
                    Color.rgb(75, 82, 96),
                    Color.rgb(48, 54, 66)
                )
            } else {
                intArrayOf(
                    Color.argb(210, 48, 53, 64),
                    Color.argb(210, 29, 33, 42)
                )
            }
        ).apply {
            cornerRadius = dp(40).toFloat()
            setStroke(dp(1), Color.argb(150, 210, 220, 235))
        }

    private fun text(
        value: String,
        size: Float,
        color: Int = Color.WHITE
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

    private fun createHeader(): LinearLayout {
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
        attachDrag(title)

        return header
    }

    private fun createModeRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val auto = button("AUTO", mode == "AUTO") {
            mode = "AUTO"
            updateMode(row)
        }

        val manual = button("MANUAL", mode == "MANUAL") {
            mode = "MANUAL"
            updateMode(row)
        }

        row.addView(
            auto,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginEnd = dp(5)
            }
        )

        row.addView(
            manual,
            LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginStart = dp(5)
            }
        )

        return row
    }

    private fun createOpacityControls(): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        opacityText = text("", 11f)

        val slider = SeekBar(this).apply {
            max = 95
            progress = glassAlpha - 150
            progressTintList =
                android.content.res.ColorStateList.valueOf(Color.WHITE)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(Color.WHITE)

            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        glassAlpha = progress + 150
                        panel.background = glassBackground(26)
                        updateOpacityText()
                    }

                    override fun onStartTrackingTouch(
                        seekBar: SeekBar?
                    ) {}

                    override fun onStopTrackingTouch(
                        seekBar: SeekBar?
                    ) {}
                }
            )
        }

        column.addView(opacityText)
        column.addView(slider)

        column.addView(
            button("ĐÓNG OVERLAY") {
                stopSelf()
            }
        )

        updateOpacityText()
        return column
    }

    private fun renderPanel() {
        panel.removeAllViews()

        if (!expanded) {
            renderBubble()
            return
        }

        panel.background = glassBackground(26)
        panel.setPadding(dp(10), dp(8), dp(10), dp(10))

        if (isLandscape) {
            val screenWidth = resources.configuration.screenWidthDp
            params.width = dp(min(screenWidth - 32, 560))

            panel.orientation = LinearLayout.HORIZONTAL
            panel.gravity = Gravity.CENTER_VERTICAL

            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val right = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setPadding(dp(8), 0, 0, 0)
            }

            left.addView(createHeader())
            left.addView(text("AI CAMERA • FLOW GLASS", 9f))
            left.addView(createModeRow())

            right.addView(
                text("Khung hướng dẫn chụp ảnh sẽ hiển thị tại đây", 11f)
            )
            right.addView(createOpacityControls())

            panel.addView(left)
            panel.addView(right)

        } else {
            params.width = dp(280)
            panel.orientation = LinearLayout.VERTICAL

            panel.addView(createHeader())
            panel.addView(text("AI CAMERA • FLOW GLASS", 9f))
            panel.addView(createModeRow())

            panel.addView(
                text("Khung hướng dẫn chụp ảnh sẽ hiển thị tại đây", 11f)
            )

            panel.addView(createOpacityControls())
        }

        wm.updateViewLayout(panel, params)
    }

    private fun renderBubble() {
        params.width = dp(48)
        panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(0, 0, 0, 0)

        val bubble = text("✦", 21f).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.argb(glassAlpha, 10, 12, 18),
                    Color.argb(glassAlpha, 35, 40, 50)
                )
            ).apply {
                cornerRadius = dp(30).toFloat()
                setStroke(dp(1), Color.argb(190, 220, 230, 245))
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

    private fun attachDrag(
        view: View,
        onTap: (() -> Unit)? = null
    ) {
        view.setOnTouchListener(object : View.OnTouchListener {
            var startX = 0
            var startY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - touchX
                        val dy = event.rawY - touchY

                        if (abs(dx) > dp(4) || abs(dy) > dp(4)) {
                            moved = true
                        }

                        if (moved) {
                            params.x = startX + dx.toInt()
                            params.y = startY + dy.toInt()
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
            item.background = pillBackground(
                item.text.toString() == mode
            )
        }
    }

    private fun updateOpacityText() {
        if (this::opacityText.isInitialized) {
            opacityText.text =
                "ĐỘ ĐẬM KÍNH: ${glassAlpha * 100 / 255}%"
        }
    }

    override fun onDestroy() {
        orientationListener?.disable()
        orientationListener = null

        if (this::panel.isInitialized && panel.parent != null) {
            wm.removeView(panel)
        }

        super.onDestroy()
    }
}
