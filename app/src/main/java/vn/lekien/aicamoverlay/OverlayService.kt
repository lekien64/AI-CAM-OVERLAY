
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
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.min

class OverlayService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private lateinit var container: FrameLayout
    private lateinit var panel: LinearLayout
    private lateinit var opacityText: TextView

    private var orientationListener: OrientationEventListener? = null
    private var expanded = true
    private var mode = "AUTO"
    private var selectedScene = "PHONG CẢNH"
    private var glassAlpha = 205
    private var rotationAngle = 0f
    private var isLandscape = false

    override fun onCreate() {
        super.onCreate()

        orientationListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(degrees: Int) {
                if (degrees == ORIENTATION_UNKNOWN) return

                val angle = when (degrees) {
                    in 45..134 -> 270f
                    in 135..224 -> 180f
                    in 225..314 -> 90f
                    else -> 0f
                }

                if (angle != rotationAngle) {
                    rotationAngle = angle
                    isLandscape = angle == 90f || angle == 270f

                    if (::container.isInitialized &&
                        container.parent != null
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
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        if (!this::container.isInitialized ||
            container.parent == null
        ) {
            wm = getSystemService(WINDOW_SERVICE) as WindowManager
            createWindow()
            renderPanel()
        }
        return START_NOT_STICKY
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
        container = FrameLayout(this)

        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        container.addView(
            panel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )

        params = WindowManager.LayoutParams(
            dp(280),
            dp(300),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(24)
            y = dp(180)
        }

        wm.addView(container, params)
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
            renderPanel()
        }

        val manual = button("MANUAL", mode == "MANUAL") {
            mode = "MANUAL"
            renderPanel()
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

    private fun createSceneChoices(): LinearLayout {
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val scenes = listOf(
            "PHONG CẢNH",
            "CHÂN DUNG",
            "TẠO DÁNG"
        )

        choices.addView(
            text("CHỌN KIỂU CHỤP", 10f, Color.LTGRAY)
        )

        scenes.forEach { scene ->
            val choice = button(
                scene,
                selectedScene == scene
            ) {
                selectedScene = scene
                renderPanel()
            }

            choices.addView(
                choice,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(40)
                ).apply {
                    bottomMargin = dp(6)
                }
            )
        }

        return choices
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
        panel.rotation = 0f

        if (!expanded) {
            renderBubble()
            return
        }

        panel.background = glassBackground()
        panel.setPadding(dp(10), dp(8), dp(10), dp(10))

        if (isLandscape) {
            val density = resources.displayMetrics.density
            val availableLongSide =
                (resources.displayMetrics.heightPixels / density).toInt()

            val contentWidth = min(availableLongSide - 48, 560)
                .coerceAtLeast(300)

            panel.orientation = LinearLayout.HORIZONTAL

            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val right = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setPadding(dp(8), 0, 0, 0)
            }

            left.addView(createHeader())
            left.addView(text("AI CAMERA • FLOW GLASS", 9f))
            left.addView(createModeRow())

            if (mode == "MANUAL") {
                left.addView(createSceneChoices())
            }

            right.addView(
                text(
                    if (mode == "MANUAL")
                        "ĐANG CHỌN: $selectedScene"
                    else
                        "Chế độ tự động sẽ được phát triển ở bước AI",
                    11f
                )
            )
            right.addView(createOpacityControls())

            panel.addView(
                left,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
            panel.addView(
                right,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            panel.measure(
                View.MeasureSpec.makeMeasureSpec(
                    dp(contentWidth),
                    View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(
                    resources.displayMetrics.heightPixels,
                    View.MeasureSpec.AT_MOST
                )
            )

            applyRotatedLayout(
                panel.measuredWidth,
                panel.measuredHeight,
                rotationAngle
            )

        } else {
            panel.orientation = LinearLayout.VERTICAL

            panel.addView(createHeader())
            panel.addView(text("AI CAMERA • FLOW GLASS", 9f))
            panel.addView(createModeRow())

            if (mode == "MANUAL") {
                panel.addView(createSceneChoices())
                panel.addView(
                    text("ĐANG CHỌN: $selectedScene", 11f)
                )
            } else {
                panel.addView(
                    text("Khung hướng dẫn chụp ảnh sẽ hiển thị tại đây", 11f)
                )
            }

            panel.addView(createOpacityControls())

            panel.measure(
                View.MeasureSpec.makeMeasureSpec(
                    dp(280),
                    View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.makeMeasureSpec(
                    resources.displayMetrics.heightPixels,
                    View.MeasureSpec.AT_MOST
                )
            )

            applyRotatedLayout(
                panel.measuredWidth,
                panel.measuredHeight,
                rotationAngle
            )
        }
    }

    private fun applyRotatedLayout(
        measuredWidth: Int,
        measuredHeight: Int,
        angle: Float
    ) {
        val rotated = angle == 90f || angle == 270f

        panel.rotation = angle

        val windowWidth = if (rotated) measuredHeight else measuredWidth
        val windowHeight = if (rotated) measuredWidth else measuredHeight

        val childParams = container.getChildAt(0).layoutParams
            as FrameLayout.LayoutParams

        childParams.width = measuredWidth
        childParams.height = measuredHeight
        childParams.gravity = Gravity.CENTER
        panel.layoutParams = childParams

        container.layoutParams = FrameLayout.LayoutParams(
            windowWidth,
            windowHeight,
            Gravity.CENTER
        )

        params.width = windowWidth
        params.height = windowHeight

        keepWindowOnScreen()

        if (container.parent != null) {
            wm.updateViewLayout(container, params)
        }
    }

    private fun keepWindowOnScreen() {
        val metrics = resources.displayMetrics
        val maxX = (metrics.widthPixels - params.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - params.height).coerceAtLeast(0)

        params.x = params.x.coerceIn(0, maxX)
        params.y = params.y.coerceIn(0, maxY)
    }

    private fun renderBubble() {
        panel.rotation = 0f
        panel.orientation = LinearLayout.VERTICAL
        panel.background = null
        panel.setPadding(0, 0, 0, 0)
        panel.removeAllViews()

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

        container.layoutParams = FrameLayout.LayoutParams(
            dp(48), dp(48), Gravity.CENTER
        )

        params.width = dp(48)
        params.height = dp(48)
        keepWindowOnScreen()

        attachDrag(bubble) {
            expanded = true
            renderPanel()
        }

        wm.updateViewLayout(container, params)
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
        if (this::opacityText.isInitialized) {
            opacityText.text =
                "ĐỘ ĐẬM KÍNH: ${glassAlpha * 100 / 255}%"
        }
    }

    override fun onDestroy() {
        orientationListener?.disable()
        orientationListener = null

        if (this::container.isInitialized &&
            container.parent != null
        ) {
            wm.removeView(container)
        }

        super.onDestroy()
    }
}
