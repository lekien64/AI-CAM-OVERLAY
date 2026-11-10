
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

    // Giữ thuộc tính để tương thích với OverlayService.
    // Bố cục thực tế được xác định bằng kích thước View.
    var isLandscape: Boolean = false
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
        color = Color.argb(145, 190, 235, 205)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.4f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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

        // Dựa vào kích thước giao diện thực tế,
        // không dựa vào góc xoay vật lý của điện thoại.
        val landscapeLayout = width > height

        val left: Float
        val top: Float
        val right: Float
        val bottom: Float

        if (landscapeLayout) {
            left = width * 0.06f
            right = width * 0.84f
            top = height * 0.10f
            bottom = height * 0.90f
        } else {
            // Vùng xem trước Camera Xiaomi ở chiều dọc.
            // Chừa thanh trên và khu vực điều khiển phía dưới.
            left = width * 0.08f
            right = width * 0.92f
            top = height * 0.19f
            bottom = height * 0.75f
        }

        val safeArea = RectF(left, top, right, bottom)

        val save = canvas.save()
        canvas.clipRect(safeArea)

        when (scene) {
            "PHONG CẢNH" -> drawLandscape(canvas, safeArea)
            "CHÂN DUNG" -> drawPortrait(canvas, safeArea)
            "TẠO DÁNG" -> drawPose(canvas, safeArea)
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

        canvas.drawCircle(x1, y1, dp(3.5f), pointPaint)
        canvas.drawCircle(x2, y1, dp(3.5f), pointPaint)
        canvas.drawCircle(x1, y2, dp(3.5f), pointPaint)
        canvas.drawCircle(x2, y2, dp(3.5f), pointPaint)
    }

    private fun drawPortrait(
        canvas: Canvas,
        area: RectF
    ) {
        val width = area.width()
        val height = area.height()
        val centerX = area.centerX()

        // Khung khuôn mặt gọn hơn, đặt ở 1/3 phía trên.
        val faceWidth = width * 0.31f
        val faceHeight = height * 0.24f
        val faceTop = area.top + height * 0.10f

        val face = RectF(
            centerX - faceWidth / 2f,
            faceTop,
            centerX + faceWidth / 2f,
            faceTop + faceHeight
        )

        canvas.drawOval(face, guidePaint)

        // Đường ngang định vị mắt.
        val eyeY = face.top + face.height() * 0.42f

        canvas.drawLine(
            face.left - dp(6f),
            eyeY,
            face.right + dp(6f),
            eyeY,
            framePaint
        )

        // Khung vai và thân trên thu gọn.
        val shoulderPath = Path().apply {
            moveTo(
                centerX - width * 0.29f,
                area.bottom
            )

            cubicTo(
                centerX - width * 0.27f,
                face.bottom + height * 0.09f,
                centerX - width * 0.18f,
                face.bottom + height * 0.06f,
                centerX,
                face.bottom + height * 0.06f
            )

            cubicTo(
                centerX + width * 0.18f,
                face.bottom + height * 0.06f,
                centerX + width * 0.27f,
                face.bottom + height * 0.09f,
                centerX + width * 0.29f,
                area.bottom
            )
        }

        canvas.drawPath(shoulderPath, framePaint)

        // Trục căn giữa khuôn mặt.
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
            dp(15f),
            dp(15f),
            framePaint
        )

        // Tỷ lệ hình người được thu gọn để nằm trong preview.
        val headY = area.top + height * 0.13f
        val neckY = area.top + height * 0.23f
        val shoulderY = area.top + height * 0.30f
        val hipY = area.top + height * 0.57f
        val kneeY = area.top + height * 0.77f
        val footY = area.bottom - height * 0.04f

        val headRadius = width * 0.065f

        canvas.drawCircle(
            cx,
            headY,
            headRadius,
            guidePaint
        )

        canvas.drawLine(
            cx,
            headY + headRadius,
            cx,
            hipY,
            guidePaint
        )

        val leftShoulder = cx - width * 0.16f
        val rightShoulder = cx + width * 0.16f

        canvas.drawLine(
            leftShoulder,
            shoulderY,
            rightShoulder,
            shoulderY,
            framePaint
        )

        // Tay trái.
        canvas.drawLine(
            leftShoulder,
            shoulderY,
            area.left + width * 0.17f,
            shoulderY + height * 0.13f,
            guidePaint
        )

        // Tay phải.
        canvas.drawLine(
            rightShoulder,
            shoulderY,
            area.right - width * 0.17f,
            shoulderY + height * 0.13f,
            guidePaint
        )

        // Hông.
        canvas.drawLine(
            cx - width * 0.12f,
            hipY,
            cx + width * 0.12f,
            hipY,
            framePaint
        )

        // Chân trái.
        canvas.drawLine(
            cx - width * 0.09f,
            hipY,
            cx - width * 0.14f,
            kneeY,
            guidePaint
        )

        canvas.drawLine(
            cx - width * 0.14f,
            kneeY,
            cx - width * 0.18f,
            footY,
            guidePaint
        )

        // Chân phải.
        canvas.drawLine(
            cx + width * 0.09f,
            hipY,
            cx + width * 0.14f,
            kneeY,
            guidePaint
        )

        canvas.drawLine(
            cx + width * 0.14f,
            kneeY,
            cx + width * 0.18f,
            footY,
            guidePaint
        )

        // Các điểm khớp.
        val joints = listOf(
            cx to neckY,
            leftShoulder to shoulderY,
            rightShoulder to shoulderY,
            cx to hipY,
            cx - width * 0.14f to kneeY,
            cx + width * 0.14f to kneeY
        )

        joints.forEach { (x, y) ->
            canvas.drawCircle(
                x,
                y,
                dp(3.5f),
                pointPaint
            )
        }
    }
}
