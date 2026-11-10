
package vn.lekien.aicamoverlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

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

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(190, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(145, 190, 235, 205)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 190, 235, 205)
        style = Paint.Style.FILL
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(25, 190, 235, 205)
        style = Paint.Style.FILL
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        /*
         * Vùng preview căn theo giao diện Camera Xiaomi.
         * Không sử dụng toàn bộ màn hình.
         *
         * Dọc: tránh thanh công cụ trên và vùng nút chụp dưới.
         * Ngang: chừa vùng an toàn ở cạnh phải cho điều khiển camera.
         */
        val left: Float
        val top: Float
        val right: Float
        val bottom: Float

        if (isLandscape) {
            left = width * 0.06f
            right = width * 0.84f
            top = height * 0.10f
            bottom = height * 0.90f
        } else {
            left = width * 0.08f
            right = width * 0.92f
            top = height * 0.14f
            bottom = height * 0.74f
        }

        val safeArea = RectF(left, top, right, bottom)

        val save = canvas.save()
        canvas.clipRect(safeArea)

        when (scene) {
            "PHONG CẢNH" -> drawLandscape(
                canvas, safeArea
            )

            "CHÂN DUNG" -> drawPortrait(
                canvas, safeArea
            )

            "TẠO DÁNG" -> drawPose(
                canvas, safeArea
            )
        }

        canvas.restoreToCount(save)
    }

    private fun drawLandscape(
        canvas: Canvas,
        area: RectF
    ) {
        val left = area.left
        val top = area.top
        val right = area.right
        val bottom = area.bottom

        val width = area.width()
        val height = area.height()

        canvas.drawRoundRect(
            area,
            dp(5f),
            dp(5f),
            framePaint
        )

        val x1 = left + width / 3f
        val x2 = left + width * 2f / 3f
        val y1 = top + height / 3f
        val y2 = top + height * 2f / 3f

        canvas.drawLine(x1, top, x1, bottom, guidePaint)
        canvas.drawLine(x2, top, x2, bottom, guidePaint)
        canvas.drawLine(left, y1, right, y1, guidePaint)
        canvas.drawLine(left, y2, right, y2, guidePaint)

        canvas.drawCircle(x1, y1, dp(4f), pointPaint)
        canvas.drawCircle(x2, y1, dp(4f), pointPaint)
        canvas.drawCircle(x1, y2, dp(4f), pointPaint)
        canvas.drawCircle(x2, y2, dp(4f), pointPaint)
    }

    private fun drawPortrait(
        canvas: Canvas,
        area: RectF
    ) {
        val width = area.width()
        val height = area.height()
        val centerX = area.centerX()

        val faceWidth = width * 0.34f
        val faceHeight = height * 0.28f
        val faceTop = area.top + height * 0.10f

        val face = RectF(
            centerX - faceWidth / 2f,
            faceTop,
            centerX + faceWidth / 2f,
            faceTop + faceHeight
        )

        canvas.drawOval(face, guidePaint)

        val eyeY = face.top + face.height() * 0.42f

        canvas.drawLine(
            face.left - dp(7f),
            eyeY,
            face.right + dp(7f),
            eyeY,
            framePaint
        )

        val shoulderPath = Path().apply {
            moveTo(centerX - width * 0.34f, area.bottom)

            cubicTo(
                centerX - width * 0.32f,
                face.bottom + height * 0.05f,
                centerX - width * 0.20f,
                face.bottom + height * 0.04f,
                centerX,
                face.bottom + height * 0.04f
            )

            cubicTo(
                centerX + width * 0.20f,
                face.bottom + height * 0.04f,
                centerX + width * 0.32f,
                face.bottom + height * 0.05f,
                centerX + width * 0.34f,
                area.bottom
            )
        }

        canvas.drawPath(shoulderPath, framePaint)

        canvas.drawLine(
            centerX,
            area.top,
            centerX,
            area.bottom,
            guidePaint
        )
    }

    private fun drawPose(
        canvas: Canvas,
        area: RectF
    ) {
        val width = area.width()
        val height = area.height()
        val cx = area.centerX()

        canvas.drawRoundRect(
            area,
            dp(18f),
            dp(18f),
            framePaint
        )

        val headY = area.top + height * 0.13f
        val neckY = area.top + height * 0.23f
        val shoulderY = area.top + height * 0.30f
        val hipY = area.top + height * 0.59f
        val kneeY = area.top + height * 0.79f
        val footY = area.bottom - height * 0.04f

        val headRadius = width * 0.075f

        canvas.drawCircle(cx, headY, headRadius, guidePaint)

        canvas.drawLine(
            cx,
            headY + headRadius,
            cx,
            hipY,
            guidePaint
        )

        val leftShoulder = cx - width * 0.18f
        val rightShoulder = cx + width * 0.18f

        canvas.drawLine(
            leftShoulder,
            shoulderY,
            rightShoulder,
            shoulderY,
            framePaint
        )

        canvas.drawLine(
            leftShoulder,
            shoulderY,
            area.left + width * 0.12f,
            shoulderY + height * 0.12f,
            guidePaint
        )

        canvas.drawLine(
            rightShoulder,
            shoulderY,
            area.right - width * 0.12f,
            shoulderY + height * 0.12f,
            guidePaint
        )

        canvas.drawLine(
            cx - width * 0.13f,
            hipY,
            cx + width * 0.13f,
            hipY,
            framePaint
        )

        canvas.drawLine(
            cx - width * 0.10f,
            hipY,
            cx - width * 0.15f,
            kneeY,
            guidePaint
        )

        canvas.drawLine(
            cx + width * 0.10f,
            hipY,
            cx + width * 0.15f,
            kneeY,
            guidePaint
        )

        canvas.drawLine(
            cx - width * 0.15f,
            kneeY,
            cx - width * 0.20f,
            footY,
            guidePaint
        )

        canvas.drawLine(
            cx + width * 0.15f,
            kneeY,
            cx + width * 0.20f,
            footY,
            guidePaint
        )

        val joints = listOf(
            cx to neckY,
            leftShoulder to shoulderY,
            rightShoulder to shoulderY,
            cx to hipY,
            cx - width * 0.15f to kneeY,
            cx + width * 0.15f to kneeY
        )

        joints.forEach { (x, y) ->
            canvas.drawCircle(x, y, dp(4f), pointPaint)
        }
    }
}
