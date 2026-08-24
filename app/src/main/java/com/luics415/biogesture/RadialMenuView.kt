package com.luics415.biogesture

import android.content.Context
import android.graphics.*
import android.view.View
import com.luics415.biogesture.menu.MenuLevelId
import com.luics415.biogesture.menu.RadialMenuCatalog
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class RadialMenuView(context: Context) : View(context) {
    private val paintBack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintSelection = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintThumb = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintSelectionFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintProgress = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintDeadZone = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintLine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val menuRect = RectF()

    private var options = listOf<String>()
    private var activeSector = -1
    private var menuCX = 0f
    private var menuCY = 0f
    private var currentThumbX = 0f
    private var currentThumbY = 0f
    private var requestedRadius: Float? = null
    private var deadZoneRadius = 0f
    private var dwellProgress = 0f
    private var selectionArmed = true
    private val catalog = RadialMenuCatalog.DEFAULT

    // Color Azul Pastel solicitado
    private val pastelBlue = Color.rgb(182, 208, 226)

    init {
        textPaint.color = Color.WHITE
        textPaint.textSize = dp(13f)
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.setShadowLayer(5f, 0f, 0f, Color.BLACK)

        paintBack.color = Color.argb(204, 0, 0, 0)
        paintBack.style = Paint.Style.FILL

        paintSelection.color = pastelBlue
        paintSelection.style = Paint.Style.STROKE
        paintSelection.strokeWidth = dp(7f)

        paintThumb.color = pastelBlue
        paintThumb.style = Paint.Style.FILL

        paintSelectionFill.color = pastelBlue
        paintSelectionFill.alpha = 60
        paintSelectionFill.style = Paint.Style.FILL

        paintProgress.color = Color.WHITE
        paintProgress.style = Paint.Style.STROKE
        paintProgress.strokeCap = Paint.Cap.ROUND
        paintProgress.strokeWidth = dp(4f)

        paintDeadZone.color = Color.argb(95, 182, 208, 226)
        paintDeadZone.style = Paint.Style.FILL

        paintLine.color = Color.GRAY
        paintLine.strokeWidth = dp(1.5f)
    }

    fun openMenu(level: MenuLevelId, x: Float, y: Float) {
        menuCX = x
        menuCY = y
        currentThumbX = x
        currentThumbY = y

        options = catalog.definition(level).items.map { it.label }
        activeSector = -1
        dwellProgress = 0f
        invalidate()
    }

    fun updateThumbPosition(x: Float, y: Float) {
        currentThumbX = x
        currentThumbY = y
        invalidate()
    }

    fun highlightSector(index: Int) {
        if (activeSector != index) {
            activeSector = index
            invalidate()
        }
    }

    fun getOption(index: Int): String {
        if (index >= 0 && index < options.size) return options[index]
        return ""
    }

    fun setInteractionState(deadZoneRadius: Float, dwellProgress: Float, selectionArmed: Boolean) {
        this.deadZoneRadius = deadZoneRadius.coerceAtLeast(0f)
        this.dwellProgress = dwellProgress.coerceIn(0f, 1f)
        this.selectionArmed = selectionArmed
        invalidate()
    }

    fun sectorCount(): Int = options.size.coerceAtLeast(1)

    fun setMenuRadius(radius: Float) {
        requestedRadius = radius.coerceAtLeast(dp(72f))
        invalidate()
    }

    fun menuRadius(): Float {
        requestedRadius?.let { return it }
        val availableSide = min(width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels,
            height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels)
        return min(dp(280f), availableSide * 0.42f).coerceAtLeast(dp(112f))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (options.isEmpty()) return

        val radius = menuRadius()
        canvas.drawCircle(menuCX, menuCY, radius, paintBack)

        paintDeadZone.alpha = if (selectionArmed) 42 else 118
        canvas.drawCircle(menuCX, menuCY, deadZoneRadius, paintDeadZone)

        menuRect.set(menuCX - radius, menuCY - radius, menuCX + radius, menuCY + radius)
        val sweepAngle = 360f / sectorCount()

        for (i in options.indices) {
            val startAngle = (i * sweepAngle) - 90 - (sweepAngle / 2)

            if (i == activeSector) {
                paintSelection.style = Paint.Style.STROKE
                canvas.drawArc(menuRect, startAngle, sweepAngle, false, paintSelection)
                canvas.drawArc(menuRect, startAngle, sweepAngle, true, paintSelectionFill)
                if (dwellProgress > 0f) {
                    canvas.drawArc(
                        menuRect,
                        startAngle,
                        sweepAngle * dwellProgress,
                        false,
                        paintProgress,
                    )
                }
            }

            if (i < options.size) {
                val angleRad = Math.toRadians(((i * sweepAngle) - 90).toDouble())
                val tx = menuCX + (radius * 0.7f) * cos(angleRad).toFloat()
                val ty = menuCY + (radius * 0.7f) * sin(angleRad).toFloat()
                val textOffset = (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(options[i], tx, ty - textOffset, textPaint)
            }
        }

        canvas.drawCircle(currentThumbX, currentThumbY, dp(5f), paintThumb)
        canvas.drawLine(menuCX, menuCY, currentThumbX, currentThumbY, paintLine)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
