
package vn.lekien.aicamoverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min

data class GuideDetection(
    val label: String,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

class CompositionGuideView(context: Context) : View(context) {

    var scene: String = "PHONG CẢNH"
        set(value) {
            field = value
            invalidate()
        }

    var isLandscape: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var showCompositionGuides: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var detections: List<GuideDetection> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.3f)
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(175, 190, 235, 205)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 190, 235, 205)
        style = Paint.Style.FILL
    }

    private val detectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 235, 160)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val labelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 8, 18, 18)
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(12f)
        style = Paint.Style.FILL
        typeface = android.graphics.Typeface.create(
            "sans-serif-medium",
            android.graphics.Typeface.BOLD
        )
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        if (showCompositionGuides) {
            drawComposition(canvas)
        }

        drawDetections(canvas)
    }

    private fun drawComposition(canvas: Canvas) {
        val landscapeLayout = width > height

        val area = if (landscapeLayout) {
            RectF(
                width * 0.06f,
                height * 0.10f,
                width * 0.84f,
                height * 0.90f
            )
        } else {
            RectF(
                width * 0.08f,
                height * 0.19f,
                width * 0.92f,
                height * 0.75f
            )
        }

        val save = canvas.save()
        canvas.clipRect(area)

        when (scene) {
            "PHONG CẢNH" -> drawLandscape(canvas, area)
            "CHÂN DUNG" -> drawPortrait(canvas, area)
            "TẠO DÁNG" -> drawPose(canvas, area)
        }

        canvas.restoreToCount(save)
    }

    private fun drawLandscape(canvas: Canvas, area: RectF) {
        val left = area.left
        val top = area.top
        val right = area.right
        val bottom = area.bottom

        val w = area.width()
        val h = area.height()

        canvas.drawRoundRect(area, dp(5f), dp(5f), framePaint)

        val x1 = left + w / 3f
        val x2 = left + w * 2f / 3f
        val y1 = top + h / 3f
        val y2 = top + h * 2f / 3f

        canvas.drawLine(x1, top, x1, bottom, guidePaint)
        canvas.drawLine(x2, top, x2, bottom, guidePaint)
        canvas.drawLine(left, y1, right, y1, guidePaint)
        canvas.drawLine(left, y2, right, y2, guidePaint)

        canvas.drawCircle(x1, y1, dp(3.5f), pointPaint)
        canvas.drawCircle(x2, y1, dp(3.5f), pointPaint)
        canvas.drawCircle(x1, y2, dp(3.5f), pointPaint)
        canvas.drawCircle(x2, y2, dp(3.5f), pointPaint)
    }

    private fun drawPortrait(canvas: Canvas, area: RectF) {
        val w = area.width()
        val h = area.height()
        val cx = area.centerX()

        val faceW = w * 0.31f
        val faceH = h * 0.24f
        val faceTop = area.top + h * 0.10f

        val face = RectF(
            cx - faceW / 2f,
            faceTop,
            cx + faceW / 2f,
            faceTop + faceH
        )

        canvas.drawOval(face, guidePaint)

        val eyeY = face.top + face.height() * 0.42f

        canvas.drawLine(
            face.left - dp(6f),
            eyeY,
            face.right + dp(6f),
            eyeY,
            framePaint
        )

        val shoulders = Path().apply {
            moveTo(cx - w * 0.29f, area.bottom)
            cubicTo(
                cx - w * 0.27f,
                face.bottom + h * 0.09f,
                cx - w * 0.18f,
                face.bottom + h * 0.06f,
                cx,
                face.bottom + h * 0.06f
            )
            cubicTo(
                cx + w * 0.18f,
                face.bottom + h * 0.06f,
                cx + w * 0.27f,
                face.bottom + h * 0.09f,
                cx + w * 0.29f,
                area.bottom
            )
        }

        canvas.drawPath(shoulders, framePaint)
        canvas.drawLine(cx, area.top, cx, area.bottom, guidePaint)
    }

    private fun drawPose(canvas: Canvas, area: RectF) {
        val w = area.width()
        val h = area.height()
        val cx = area.centerX()

        canvas.drawRoundRect(area, dp(15f), dp(15f), framePaint)

        val headY = area.top + h * 0.13f
        val neckY = area.top + h * 0.23f
        val shoulderY = area.top + h * 0.30f
        val hipY = area.top + h * 0.57f
        val kneeY = area.top + h * 0.77f
        val footY = area.bottom - h * 0.04f

        val radius = w * 0.065f

        canvas.drawCircle(cx, headY, radius, guidePaint)
        canvas.drawLine(cx, headY + radius, cx, hipY, guidePaint)

        val leftShoulder = cx - w * 0.16f
        val rightShoulder = cx + w * 0.16f

        canvas.drawLine(
            leftShoulder, shoulderY,
            rightShoulder, shoulderY,
            framePaint
        )

        canvas.drawLine(
            leftShoulder, shoulderY,
            area.left + w * 0.17f,
            shoulderY + h * 0.13f,
            guidePaint
        )

        canvas.drawLine(
            rightShoulder, shoulderY,
            area.right - w * 0.17f,
            shoulderY + h * 0.13f,
            guidePaint
        )

        canvas.drawLine(
            cx - w * 0.12f, hipY,
            cx + w * 0.12f, hipY,
            framePaint
        )

        canvas.drawLine(
            cx - w * 0.09f, hipY,
            cx - w * 0.14f, kneeY,
            guidePaint
        )

        canvas.drawLine(
            cx - w * 0.14f, kneeY,
            cx - w * 0.18f, footY,
            guidePaint
        )

        canvas.drawLine(
            cx + w * 0.09f, hipY,
            cx + w * 0.14f, kneeY,
            guidePaint
        )

        canvas.drawLine(
            cx + w * 0.14f, kneeY,
            cx + w * 0.18f, footY,
            guidePaint
        )

        val joints = listOf(
            cx to neckY,
            leftShoulder to shoulderY,
            rightShoulder to shoulderY,
            cx to hipY,
            cx - w * 0.14f to kneeY,
            cx + w * 0.14f to kneeY
        )

        joints.forEach { (x, y) ->
            canvas.drawCircle(x, y, dp(3.5f), pointPaint)
        }
    }

    private fun drawDetections(canvas: Canvas) {
        detections.forEach { item ->
            val left = item.left.coerceIn(0f, 1f) * width
            val top = item.top.coerceIn(0f, 1f) * height
            val right = item.right.coerceIn(0f, 1f) * width
            val bottom = item.bottom.coerceIn(0f, 1f) * height

            if (right <= left || bottom <= top) return@forEach

            val box = RectF(left, top, right, bottom)

            canvas.drawRoundRect(
                box,
                dp(7f),
                dp(7f),
                detectionPaint
            )

            val confidence = (item.confidence * 100)
                .toInt()
                .coerceIn(0, 100)

            val label = "${toVietnamese(item.label)} $confidence%"

            val padding = dp(6f)
            val labelHeight = dp(25f)
            val labelWidth = labelPaint.measureText(label) + padding * 2

            val labelLeft = left.coerceIn(
                0f,
                max(0f, width - labelWidth)
            )

            val labelTop = (top - labelHeight)
                .coerceAtLeast(0f)

            val labelRect = RectF(
                labelLeft,
                labelTop,
                min(width.toFloat(), labelLeft + labelWidth),
                labelTop + labelHeight
            )

            canvas.drawRoundRect(
                labelRect,
                dp(5f),
                dp(5f),
                labelBackgroundPaint
            )

            canvas.drawText(
                label,
                labelRect.left + padding,
                labelRect.bottom - dp(7f),
                labelPaint
            )
        }
    }

    private fun toVietnamese(source: String): String {
        return when (source.trim().lowercase()) {
            "person", "people" -> "Người"
            "food" -> "Đồ ăn"
            "plant" -> "Thực vật"
            "place" -> "Địa điểm"
            "home goods", "home good" -> "Đồ gia dụng"
            "fashion goods", "fashion good" -> "Đồ thời trang"
            "animal" -> "Động vật"
            "vehicle" -> "Phương tiện"
            "furniture" -> "Nội thất"
            "electronic device" -> "Thiết bị điện tử"
            "unknown" -> "Đối tượng"
            else -> "Đối tượng"
        }
    }
}
