
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

    var guideRotation: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(205, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    private val softPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(125, 190, 235, 205)
        style = Paint.Style.STROKE
        strokeWidth = dp(2f)
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 190, 235, 205)
        style = Paint.Style.FILL
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        val save = canvas.save()

        canvas.rotate(
            guideRotation,
            width / 2f,
            height / 2f
        )

        val rotated = guideRotation == 90f ||
            guideRotation == 270f

        val logicalWidth =
            if (rotated) height.toFloat() else width.toFloat()

        val logicalHeight =
            if (rotated) width.toFloat() else height.toFloat()

        val left = logicalWidth * 0.08f
        val right = logicalWidth * 0.92f
        val top = logicalHeight * 0.13f
        val bottom = logicalHeight * 0.87f

        when (scene) {
            "PHONG CẢNH" -> drawLandscape(
                canvas, left, top, right, bottom
            )

            "CHÂN DUNG" -> drawPortrait(
                canvas, left, top, right, bottom
            )

            "TẠO DÁNG" -> drawPose(
                canvas, left, top, right, bottom
            )
        }

        canvas.restoreToCount(save)
    }

    private fun drawLandscape(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        val width = right - left
        val height = bottom - top

        // Khung ảnh và lưới quy tắc 1/3.
        canvas.drawRect(left, top, right, bottom, linePaint)

        val x1 = left + width / 3f
        val x2 = left + width * 2f / 3f
        val y1 = top + height / 3f
        val y2 = top + height * 2f / 3f

        canvas.drawLine(x1, top, x1, bottom, softPaint)
        canvas.drawLine(x2, top, x2, bottom, softPaint)
        canvas.drawLine(left, y1, right, y1, softPaint)
        canvas.drawLine(left, y2, right, y2, softPaint)

        // Điểm giao giúp căn chủ thể.
        canvas.drawCircle(x1, y1, dp(4f), pointPaint)
        canvas.drawCircle(x2, y1, dp(4f), pointPaint)
        canvas.drawCircle(x1, y2, dp(4f), pointPaint)
        canvas.drawCircle(x2, y2, dp(4f), pointPaint)
    }

    private fun drawPortrait(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        val width = right - left
        val height = bottom - top

        // Khung căn chỉnh chân dung.
        val faceWidth = width * 0.34f
        val faceHeight = height * 0.30f
        val centerX = (left + right) / 2f
        val faceTop = top + height * 0.12f

        val face = RectF(
            centerX - faceWidth / 2f,
            faceTop,
            centerX + faceWidth / 2f,
            faceTop + faceHeight
        )

        canvas.drawOval(face, softPaint)

        // Đường mắt.
        val eyeY = face.top + face.height() * 0.42f
        canvas.drawLine(
            face.left - dp(8f),
            eyeY,
            face.right + dp(8f),
            eyeY,
            linePaint
        )

        // Khung vai và thân trên.
        val shoulderPath = Path().apply {
            moveTo(centerX - width * 0.34f, bottom)
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
                bottom
            )
        }

        canvas.drawPath(shoulderPath, linePaint)

        // Đường căn giữa khuôn mặt.
        canvas.drawLine(
            centerX,
            top,
            centerX,
            bottom,
            softPaint
        )
    }

    private fun drawPose(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ) {
        val width = right - left
        val height = bottom - top
        val cx = (left + right) / 2f

        // Khung toàn thân.
        canvas.drawRoundRect(
            RectF(left, top, right, bottom),
            dp(18f),
            dp(18f),
            linePaint
        )

        // Đường trục cơ thể.
        val headY = top + height * 0.13f
        val neckY = top + height * 0.23f
        val shoulderY = top + height * 0.30f
        val hipY = top + height * 0.59f
        val kneeY = top + height * 0.79f
        val footY = bottom - height * 0.04f

        val headRadius = width * 0.075f

        canvas.drawCircle(
            cx,
            headY,
            headRadius,
            softPaint
        )

        canvas.drawLine(
            cx,
            headY + headRadius,
            cx,
            hipY,
            softPaint
        )

        // Vai và tay.
        val leftShoulder = cx - width * 0.18f
        val rightShoulder = cx + width * 0.18f

        canvas.drawLine(
            leftShoulder,
            shoulderY,
            rightShoulder,
            shoulderY,
            linePaint
        )

        canvas.drawLine(
            leftShoulder,
            shoulderY,
            left + width * 0.12f,
            hipY * 0.55f + shoulderY * 0.45f,
            softPaint
        )

        canvas.drawLine(
            rightShoulder,
            shoulderY,
            right - width * 0.12f,
            hipY * 0.55f + shoulderY * 0.45f,
            softPaint
        )

        // Hông và chân.
        canvas.drawLine(
            cx - width * 0.13f,
            hipY,
            cx + width * 0.13f,
            hipY,
            linePaint
        )

        canvas.drawLine(
            cx - width * 0.10f,
            hipY,
            cx - width * 0.15f,
            kneeY,
            softPaint
        )

        canvas.drawLine(
            cx + width * 0.10f,
            hipY,
            cx + width * 0.15f,
            kneeY,
            softPaint
        )

        canvas.drawLine(
            cx - width * 0.15f,
            kneeY,
            cx - width * 0.20f,
            footY,
            softPaint
        )

        canvas.drawLine(
            cx + width * 0.15f,
            kneeY,
            cx + width * 0.20f,
            footY,
            softPaint
        )

        // Các khớp xương.
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
