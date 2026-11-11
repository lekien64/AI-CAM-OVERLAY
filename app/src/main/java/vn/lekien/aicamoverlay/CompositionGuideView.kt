
package vn.lekien.aicamoverlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.View
import com.google.mlkit.vision.pose.PoseLandmark
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

    companion object {
        private const val MIN_CONFIDENCE = 0.30f
        private const val DUPLICATE_IOU = 0.55f
        private const val CONTAINMENT_THRESHOLD = 0.82f
        private const val MAX_VISIBLE_DETECTIONS = 8
        private const val MAX_BOX_AREA = 0.78f
        private const val MIN_POSE_CONFIDENCE = 0.35f

        private const val ACTION_POSE_RESULT =
            "vn.lekien.aicamoverlay.ACTION_POSE_RESULT"

        private const val EXTRA_POSE_TYPES = "poseTypes"
        private const val EXTRA_POSE_X = "poseX"
        private const val EXTRA_POSE_Y = "poseY"
        private const val EXTRA_POSE_CONFIDENCES = "poseConfidences"
    }

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

    var posePoints: List<PoseVisionEngine.PosePoint> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    private var poseReceiverRegistered = false

    private val poseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_POSE_RESULT) return

            val types = intent.getIntArrayExtra(EXTRA_POSE_TYPES)
                ?: return

            val xs = intent.getFloatArrayExtra(EXTRA_POSE_X)
                ?: return

            val ys = intent.getFloatArrayExtra(EXTRA_POSE_Y)
                ?: return

            val confidences =
                intent.getFloatArrayExtra(EXTRA_POSE_CONFIDENCES)
                    ?: return

            val count = minOf(
                types.size,
                xs.size,
                ys.size,
                confidences.size
            )

            val points = ArrayList<PoseVisionEngine.PosePoint>(count)

            for (i in 0 until count) {
                points.add(
                    PoseVisionEngine.PosePoint(
                        type = types[i],
                        x = xs[i],
                        y = ys[i],
                        confidence = confidences[i]
                    )
                )
            }

            posePoints = points
        }
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

    private val skeletonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(35, 235, 210)
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val skeletonJointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val skeletonJointOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(20, 210, 190)
        style = Paint.Style.STROKE
        strokeWidth = dp(1.8f)
    }

    private fun dp(value: Float): Float =
        value * resources.displayMetrics.density

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        if (poseReceiverRegistered) return

        val filter = IntentFilter(ACTION_POSE_RESULT)

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                poseReceiver,
                filter,
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(poseReceiver, filter)
        }

        poseReceiverRegistered = true
    }

    override fun onDetachedFromWindow() {
        if (poseReceiverRegistered) {
            try {
                context.unregisterReceiver(poseReceiver)
            } catch (_: Exception) {
            }

            poseReceiverRegistered = false
        }

        posePoints = emptyList()

        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (width <= 0 || height <= 0) return

        if (showCompositionGuides) {
            drawComposition(canvas)
        }

        drawDetections(canvas)
        drawPoseSkeleton(canvas)
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

    private fun drawPoseSkeleton(canvas: Canvas) {
        val validPoints = posePoints
            .filter {
                it.confidence >= 0.50f &&
                    it.x in 0f..1f &&
                    it.y in 0f..1f
            }
            .associateBy { it.type }

        fun has(type: Int): Boolean = validPoints.containsKey(type)

        val faceAnchors = listOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_EYE,
            PoseLandmark.RIGHT_EYE,
            PoseLandmark.LEFT_EAR,
            PoseLandmark.RIGHT_EAR
        )

        val hasFace = faceAnchors.any { has(it) }

        val hasLeftShoulder = has(PoseLandmark.LEFT_SHOULDER)
        val hasRightShoulder = has(PoseLandmark.RIGHT_SHOULDER)
        val hasShoulder = hasLeftShoulder || hasRightShoulder

        val hasHip =
            has(PoseLandmark.LEFT_HIP) ||
            has(PoseLandmark.RIGHT_HIP)

        val validPerson =
            (hasFace && hasShoulder) ||
            (hasLeftShoulder && hasRightShoulder && hasHip)

        if (!validPerson) return

        fun point(type: Int): Pair<Float, Float>? {
            val landmark = validPoints[type] ?: return null
            return Pair(
                landmark.x * width,
                landmark.y * height
            )
        }

        fun connect(first: Int, second: Int) {
            val a = point(first) ?: return
            val b = point(second) ?: return

            val dx = (a.first - b.first) / width.toFloat()
            val dy = (a.second - b.second) / height.toFloat()

            if (dx * dx + dy * dy > 0.42f) return

            canvas.drawLine(
                a.first,
                a.second,
                b.first,
                b.second,
                skeletonPaint
            )
        }

        val connections = arrayOf(
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_ELBOW,
            PoseLandmark.LEFT_ELBOW to PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.RIGHT_ELBOW to PoseLandmark.RIGHT_WRIST
        )

        connections.forEach { (first, second) ->
            connect(first, second)
        }

        if (hasLeftShoulder && hasRightShoulder && hasHip) {
            val bodyConnections = arrayOf(
                PoseLandmark.LEFT_SHOULDER to PoseLandmark.LEFT_HIP,
                PoseLandmark.RIGHT_SHOULDER to PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_HIP to PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_HIP to PoseLandmark.LEFT_KNEE,
                PoseLandmark.LEFT_KNEE to PoseLandmark.LEFT_ANKLE,
                PoseLandmark.RIGHT_HIP to PoseLandmark.RIGHT_KNEE,
                PoseLandmark.RIGHT_KNEE to PoseLandmark.RIGHT_ANKLE,
                PoseLandmark.LEFT_ANKLE to PoseLandmark.LEFT_HEEL,
                PoseLandmark.LEFT_HEEL to PoseLandmark.LEFT_FOOT_INDEX,
                PoseLandmark.RIGHT_ANKLE to PoseLandmark.RIGHT_HEEL,
                PoseLandmark.RIGHT_HEEL to PoseLandmark.RIGHT_FOOT_INDEX
            )

            bodyConnections.forEach { (first, second) ->
                connect(first, second)
            }
        }

        val allowedPoints = mutableSetOf(
            PoseLandmark.NOSE,
            PoseLandmark.LEFT_EYE,
            PoseLandmark.RIGHT_EYE,
            PoseLandmark.LEFT_EAR,
            PoseLandmark.RIGHT_EAR,
            PoseLandmark.LEFT_SHOULDER,
            PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_ELBOW,
            PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST,
            PoseLandmark.RIGHT_WRIST
        )

        if (hasLeftShoulder && hasRightShoulder && hasHip) {
            allowedPoints.addAll(
                listOf(
                    PoseLandmark.LEFT_HIP,
                    PoseLandmark.RIGHT_HIP,
                    PoseLandmark.LEFT_KNEE,
                    PoseLandmark.RIGHT_KNEE,
                    PoseLandmark.LEFT_ANKLE,
                    PoseLandmark.RIGHT_ANKLE,
                    PoseLandmark.LEFT_HEEL,
                    PoseLandmark.RIGHT_HEEL,
                    PoseLandmark.LEFT_FOOT_INDEX,
                    PoseLandmark.RIGHT_FOOT_INDEX
                )
            )
        }

        validPoints.values
            .filter { it.type in allowedPoints }
            .forEach { landmark ->
                val x = landmark.x * width
                val y = landmark.y * height

                canvas.drawCircle(
                    x,
                    y,
                    dp(4f),
                    skeletonJointPaint
                )

                canvas.drawCircle(
                    x,
                    y,
                    dp(4f),
                    skeletonJointOutlinePaint
                )
            }
    }

    private fun filterDetections(): List<GuideDetection> {
        val candidates = detections
            .filter { item ->
                item.confidence >= MIN_CONFIDENCE &&
                    item.label.isNotBlank() &&
                    !item.label.equals("Unknown", true) &&
                    !item.label.equals("Đối tượng", true)
            }
            .mapNotNull { item ->
                val left = item.left.coerceIn(0f, 1f)
                val top = item.top.coerceIn(0f, 1f)
                val right = item.right.coerceIn(0f, 1f)
                val bottom = item.bottom.coerceIn(0f, 1f)

                if (right <= left || bottom <= top) {
                    null
                } else {
                    item.copy(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom
                    )
                }
            }
            .sortedByDescending { it.confidence }

        val accepted = mutableListOf<GuideDetection>()

        for (candidate in candidates) {
            val candidateArea = boxArea(candidate)

            if (candidateArea > MAX_BOX_AREA) {
                continue
            }

            val redundant = accepted.any { existing ->
                val iou = intersectionOverUnion(candidate, existing)
                val containment = containmentRatio(candidate, existing)

                val sameLabel =
                    candidate.label.equals(existing.label, true)

                val nearDuplicate =
                    sameLabel && iou >= DUPLICATE_IOU

                val nestedLargeBox =
                    candidateArea > boxArea(existing) &&
                        containment >= CONTAINMENT_THRESHOLD &&
                        boxArea(existing) / candidateArea <= 0.55f

                nearDuplicate || nestedLargeBox
            }

            if (!redundant) {
                accepted.add(candidate)
            }

            if (accepted.size >= MAX_VISIBLE_DETECTIONS) {
                break
            }
        }

        return accepted
    }

    private fun boxArea(item: GuideDetection): Float {
        return max(0f, item.right - item.left) *
            max(0f, item.bottom - item.top)
    }

    private fun intersectionOverUnion(
        first: GuideDetection,
        second: GuideDetection
    ): Float {
        val left = max(first.left, second.left)
        val top = max(first.top, second.top)
        val right = min(first.right, second.right)
        val bottom = min(first.bottom, second.bottom)

        val intersectionWidth = max(0f, right - left)
        val intersectionHeight = max(0f, bottom - top)
        val intersection = intersectionWidth * intersectionHeight

        val union = boxArea(first) + boxArea(second) - intersection

        return if (union > 0f) intersection / union else 0f
    }

    private fun containmentRatio(
        first: GuideDetection,
        second: GuideDetection
    ): Float {
        val left = max(first.left, second.left)
        val top = max(first.top, second.top)
        val right = min(first.right, second.right)
        val bottom = min(first.bottom, second.bottom)

        val intersection =
            max(0f, right - left) * max(0f, bottom - top)

        val smallerArea = min(boxArea(first), boxArea(second))

        return if (smallerArea > 0f) {
            intersection / smallerArea
        } else {
            0f
        }
    }

    private fun drawDetections(canvas: Canvas) {
        val visibleDetections = filterDetections()
        val save = canvas.save()

        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())

        visibleDetections.forEach { item ->
            val left = item.left * width
            val top = item.top * height
            val right = item.right * width
            val bottom = item.bottom * height

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

            val labelTop = if (top >= labelHeight) {
                top - labelHeight
            } else {
                min(
                    height - labelHeight,
                    bottom
                ).coerceAtLeast(0f)
            }

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

        canvas.restoreToCount(save)
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
            else -> source
        }
    }
}
