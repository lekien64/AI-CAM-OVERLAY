
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var container: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var guideView: CompositionGuideView

    private lateinit var opacityText: TextView

    private var orientationListener: OrientationEventListener? = null

    private var expanded = true
    private var landscape = false
    private var mode = "AUTO"
    private var selectedScene = "PHONG CẢNH"
    private var glassAlpha = 204

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()

        landscape =
            resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE

        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return

                val newLandscape =
                    degrees in 60..120 || degrees in 240..300

                if (newLandscape != landscape) {
                    landscape = newLandscape

                    if (::panel.isInitialized &&
                        container.parent != null
                    ) {
                        renderPanel()
                        updateGuide()
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
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (!::container.isInitialized ||
            container.parent == null
        ) {
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            createGuide()
            createPanelWindow()
            renderPanel()
            updateGuide()
        }

        return START_NOT_STICKY
    }

    private fun glassBackground() =
        GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.argb(glassAlpha, 8, 10, 16),
                Color.argb(glassAlpha, 26, 30, 40),
                Color.argb(glassAlpha, 9, 12, 18)
            )
        ).apply {
            cornerRadius = dp(25).toFloat()
            setStroke(dp(1), Color.argb(165, 220, 230, 245))
        }

    private fun pillBackground(selected: Boolean = false) =
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            if (selected) {
                intArrayOf(
                    Color.rgb(76, 84, 100),
                    Color.rgb(48, 55, 68)
                )
            } else {
                intArrayOf(
                    Color.argb(215, 48, 53, 65),
                    Color.argb(215, 28, 32, 42)
                )
            }
        ).apply {
            cornerRadius = dp(35).toFloat()
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
        setPadding(dp(7), dp(5), dp(7), dp(5))
    }

    private fun actionButton(
        value: String,
        selected: Boolean = false,
        action: () -> Unit
    ) = text(value, 13f).apply {
        background = pillBackground(selected)
        setTypeface(null, Typeface.BOLD)
        setOnClickListener { action() }
    }

    private fun createGuide() {
        guideView = CompositionGuideView(this).apply {
            visibility = View.GONE
            isClickable = false
            isFocusable = false
        }

        val guideParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        wm.addView(guideView, guideParams)
    }

    private fun createPanelWindow() {
        container = FrameLayout(this)

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        container.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        )

        params = WindowManager.LayoutParams(
            dp(280),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(35)
        }

        wm.addView(container, params)
    }

    private fun updateGuide() {
        if (!::guideView.isInitialized) return

        guideView.scene = selectedScene
        guideView.isLandscape = landscape
        guideView.visibility =
            if (mode == "MANUAL") View.VISIBLE else View.GONE

        guideView.invalidate()
    }

    private fun createHeader(): LinearLayout {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = text("✦  AI CAM", 18f).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                0,
                dp(38),
                1f
            )
        }

        val minimize = actionButton("−") {
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

        row.addView(
            actionButton("AUTO", mode == "AUTO") {
                mode = "AUTO"
                renderPanel()
                updateGuide()
            },
            LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginEnd = dp(4)
            }
        )

        row.addView(
            actionButton("MANUAL", mode == "MANUAL") {
                mode = "MANUAL"
                renderPanel()
                updateGuide()
            },
            LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                marginStart = dp(4)
            }
        )

        return row
    }

    private fun createSceneChoices(): LinearLayout {
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        choices.addView(
            text("CHỌN KIỂU CHỤP", 10f, Color.LTGRAY)
        )

        listOf(
            "PHONG CẢNH",
            "CHÂN DUNG",
            "TẠO DÁNG"
        ).forEach { scene ->

            val selected = selectedScene == scene

            val choice = TextView(this).apply {
                text = if (selected) "✓  $scene" else scene
                textSize = 12f
                gravity = Gravity.CENTER
                setTypeface(null, Typeface.BOLD)

                setTextColor(
                    if (selected) {
                        Color.rgb(174, 235, 185)
                    } else {
                        Color.WHITE
                    }
                )

                background = pillBackground(selected)
                setPadding(dp(5), dp(3), dp(5), dp(3))

                setOnClickListener {
                    selectedScene = scene
                    renderPanel()
                    updateGuide()
                }
            }

            choices.addView(
                choice,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(if (landscape) 32 else 36)
                ).apply {
                    bottomMargin = dp(3)
                }
            )
        }

        return choices
    }

    private fun createOpacityControls(): LinearLayout {
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        opacityText = text("", 10f)

        val slider = SeekBar(this).apply {
            max = 90
            progress = (glassAlpha * 100 / 255)
                .coerceIn(6, 96) - 6

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
                        glassAlpha = (progress + 6) * 255 / 100
                        panel.background = glassBackground()
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

        val close = TextView(this).apply {
            text = "×"
            textSize = 22f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER

            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(43, 48, 59))
                setStroke(dp(1), Color.argb(180, 220, 230, 245))
            }

            setOnClickListener { stopSelf() }

            layoutParams = LinearLayout.LayoutParams(
                dp(38),
                dp(38)
            ).apply {
                gravity = Gravity.END
                topMargin = dp(3)
            }
        }

        column.addView(close)
        updateOpacityText()

        return column
    }

    private fun renderPanel() {
        panel.removeAllViews()
        panel.rotation = 0f

        if (!expanded) {
            renderBubble()
            return
        }

        panel.background = glassBackground()
        panel.setPadding(dp(8), dp(6), dp(8), dp(7))

        if (landscape) {
            // Bố cục ngang nhỏ gọn, không xoay chữ.
            panel.orientation = LinearLayout.HORIZONTAL

            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }

            val right = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(5), 0, 0, 0)
            }

            left.addView(createHeader())
            left.addView(text("AI CAMERA • FLOW GLASS", 8f))
            left.addView(createModeRow())

            if (mode == "MANUAL") {
                left.addView(createSceneChoices())
            }

            right.addView(createOpacityControls())

            panel.addView(
                left,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1.35f
                )
            )

            panel.addView(
                right,
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    0.85f
                )
            )

            params.width = dp(320)

        } else {
            panel.orientation = LinearLayout.VERTICAL

            panel.addView(createHeader())
            panel.addView(text("AI CAMERA • FLOW GLASS", 9f))
            panel.addView(createModeRow())

            if (mode == "MANUAL") {
                panel.addView(createSceneChoices())
            }

            panel.addView(createOpacityControls())

            params.width = dp(280)
        }

        params.height = WindowManager.LayoutParams.WRAP_CONTENT

        container.layoutParams = FrameLayout.LayoutParams(
            params.width,
            FrameLayout.LayoutParams.WRAP_CONTENT
        )

        keepWindowOnScreen()
        wm.updateViewLayout(container, params)
    }

    private fun renderBubble() {
        panel.removeAllViews()
        panel.rotation = 0f
        panel.orientation = LinearLayout.VERTICAL
        panel.background = null
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

        params.width = dp(48)
        params.height = dp(48)

        container.layoutParams = FrameLayout.LayoutParams(
            dp(48),
            dp(48)
        )

        attachDrag(bubble) {
            expanded = true
            renderPanel()
        }

        keepWindowOnScreen()
        wm.updateViewLayout(container, params)
    }

    private fun keepWindowOnScreen() {
        val metrics = resources.displayMetrics

        params.x = params.x.coerceIn(
            0,
            (metrics.widthPixels - params.width).coerceAtLeast(0)
        )

        params.y = params.y.coerceIn(
            0,
            (metrics.heightPixels - dp(80)).coerceAtLeast(0)
        )
    }

    private fun attachDrag(view: View, onTap: (() -> Unit)? = null) {
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
                            keepWindowOnScreen()
                            wm.updateViewLayout(container, params)
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

    private fun updateOpacityText() {
        if (::opacityText.isInitialized) {
            val percent = (glassAlpha * 100 / 255).coerceIn(6, 96)
            opacityText.text = "ĐỘ ĐẬM KÍNH: ${percent}%"
        }
    }

    override fun onDestroy() {
        orientationListener?.disable()
        orientationListener = null

        if (::container.isInitialized && container.parent != null) {
            wm.removeView(container)
        }

        if (::guideView.isInitialized && guideView.parent != null) {
            wm.removeView(guideView)
        }

        super.onDestroy()
    }
}
