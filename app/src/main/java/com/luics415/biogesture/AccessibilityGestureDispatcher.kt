package com.luics415.biogesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.view.ViewConfiguration
import kotlin.math.abs

/** Serializes injected touch input and keeps drag strokes continuous across camera frames. */
class AccessibilityGestureDispatcher(
    private val service: AccessibilityService,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val onUnexpectedDragEnd: () -> Unit = {},
) {
    private var activeStroke: GestureDescription.StrokeDescription? = null
    private var dispatchedEndPoint: PointF? = null
    private var latestTarget: PointF? = null
    private var dragSegmentInFlight = false
    private var finishRequested = false
    private var finishingSegmentInFlight = false

    val isDragging: Boolean
        get() = activeStroke != null || dragSegmentInFlight

    fun tap(point: PointF, durationMs: Long = TAP_DURATION_MS): Boolean {
        if (isDragging) return false
        val path = Path().apply { moveTo(point.x, point.y) }
        return service.dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L)))
                .build(),
            null,
            handler,
        )
    }

    fun longPress(point: PointF): Boolean = tap(
        point,
        ViewConfiguration.getLongPressTimeout().toLong() + LONG_PRESS_PADDING_MS,
    )

    fun swipe(from: PointF, to: PointF, durationMs: Long): Boolean {
        if (isDragging) return false
        if (abs(from.x - to.x) < 1f && abs(from.y - to.y) < 1f) return false
        val path = Path().apply {
            moveTo(from.x, from.y)
            lineTo(to.x, to.y)
        }
        return service.dispatchGesture(
            GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1L)))
                .build(),
            null,
            handler,
        )
    }

    fun doubleTap(point: PointF): Boolean {
        if (isDragging) return false
        val firstAccepted = tap(point, DOUBLE_TAP_PRESS_MS)
        if (!firstAccepted) return false
        handler.postDelayed({ tap(point, DOUBLE_TAP_PRESS_MS) }, DOUBLE_TAP_GAP_MS)
        return true
    }

    fun startDrag(point: PointF): Boolean {
        if (isDragging) return false
        latestTarget = PointF(point.x, point.y)
        dispatchedEndPoint = PointF(point.x, point.y)
        finishRequested = false
        finishingSegmentInFlight = false

        val path = Path().apply { moveTo(point.x, point.y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, DRAG_SEGMENT_MS, true)
        activeStroke = stroke
        return dispatchDragStroke(
            stroke = stroke,
            finalSegment = false,
            notifySynchronousRejection = false,
        )
    }

    /** Coalesces high-frequency hand frames; only the newest point is used by the next segment. */
    fun moveDrag(point: PointF) {
        if (!isDragging || finishRequested) return
        latestTarget = PointF(point.x, point.y)
    }

    fun endDrag(point: PointF? = null) {
        if (!isDragging) return
        point?.let { latestTarget = PointF(it.x, it.y) }
        finishRequested = true
        if (!dragSegmentInFlight && !finishingSegmentInFlight) {
            dispatchNextDragSegment()
        }
    }

    private fun dispatchNextDragSegment() {
        val previousStroke = activeStroke ?: return clearDragState(notifyUnexpected = !finishRequested)
        val from = dispatchedEndPoint ?: return clearDragState(notifyUnexpected = !finishRequested)
        val requestedTarget = latestTarget ?: from
        val to = PointF(requestedTarget.x, requestedTarget.y)
        val finalSegment = finishRequested

        val path = Path().apply {
            moveTo(from.x, from.y)
            if (abs(from.x - to.x) >= 0.5f || abs(from.y - to.y) >= 0.5f) {
                lineTo(to.x, to.y)
            }
        }
        val continued = try {
            previousStroke.continueStroke(path, 0, DRAG_SEGMENT_MS, !finalSegment)
        } catch (_: IllegalArgumentException) {
            clearDragState(notifyUnexpected = !finishRequested)
            return
        }
        activeStroke = continued
        dispatchedEndPoint = to
        dispatchDragStroke(
            stroke = continued,
            finalSegment = finalSegment,
            notifySynchronousRejection = !finalSegment,
        )
    }

    private fun dispatchDragStroke(
        stroke: GestureDescription.StrokeDescription,
        finalSegment: Boolean,
        notifySynchronousRejection: Boolean,
    ): Boolean {
        dragSegmentInFlight = !finalSegment
        finishingSegmentInFlight = finalSegment
        val accepted = service.dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    dragSegmentInFlight = false
                    if (finalSegment) {
                        clearDragState()
                    } else {
                        dispatchNextDragSegment()
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    clearDragState(notifyUnexpected = !finishRequested)
                }
            },
            handler,
        )
        if (!accepted) clearDragState(notifyUnexpected = notifySynchronousRejection)
        return accepted
    }

    private fun clearDragState(notifyUnexpected: Boolean = false) {
        activeStroke = null
        dispatchedEndPoint = null
        latestTarget = null
        dragSegmentInFlight = false
        finishRequested = false
        finishingSegmentInFlight = false
        if (notifyUnexpected) onUnexpectedDragEnd()
    }

    companion object {
        private const val TAP_DURATION_MS = 80L
        private const val DOUBLE_TAP_PRESS_MS = 55L
        private const val DOUBLE_TAP_GAP_MS = 110L
        private const val DRAG_SEGMENT_MS = 90L
        private const val LONG_PRESS_PADDING_MS = 80L
    }
}
