package com.luics415.biogesture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.View

/** Diagnostic overlay fed with the exact same screen points used by accessibility actions. */
class SkeletonView(context: Context) : View(context) {
    private var points: List<PointF> = emptyList()
    private var handednessLabel: String = ""
    private var inferenceLabel: String = ""

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }
    private val indexPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 255, 255, 255)
        strokeWidth = dp(2f)
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = dp(13f)
        setShadowLayer(dp(2f), 0f, 0f, Color.BLACK)
    }

    fun setPoints(screenPoints: List<PointF>, handedness: String, inferenceTimeMs: Long) {
        points = screenPoints.map { PointF(it.x, it.y) }
        handednessLabel = handedness
        inferenceLabel = "${inferenceTimeMs} ms"
        invalidate()
    }

    fun clear() {
        points = emptyList()
        handednessLabel = ""
        inferenceLabel = ""
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < LANDMARK_COUNT) return

        for ((fromIndex, toIndex) in CONNECTIONS) {
            val from = points[fromIndex]
            val to = points[toIndex]
            canvas.drawLine(from.x, from.y, to.x, to.y, linePaint)
        }
        points.forEachIndexed { index, point ->
            val paint = if (index == INDEX_FINGER_TIP) indexPaint else pointPaint
            val radius = if (index == INDEX_FINGER_TIP) dp(5.5f) else dp(3.5f)
            canvas.drawCircle(point.x, point.y, radius, paint)
        }
        if (handednessLabel.isNotBlank()) {
            canvas.drawText("$handednessLabel · $inferenceLabel", dp(12f), dp(24f), textPaint)
        }
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    companion object {
        private const val LANDMARK_COUNT = 21
        private const val INDEX_FINGER_TIP = 8
        private val CONNECTIONS = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 4,
            0 to 5, 5 to 6, 6 to 7, 7 to 8,
            5 to 9, 9 to 10, 10 to 11, 11 to 12,
            9 to 13, 13 to 14, 14 to 15, 15 to 16,
            13 to 17, 17 to 18, 18 to 19, 19 to 20,
            0 to 17,
        )
    }
}
