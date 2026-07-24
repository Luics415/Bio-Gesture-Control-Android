package com.luics415.biogesture

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class RadialMenuView(context: Context) : View(context) {
    private val paintBack = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintSelection = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paintThumb = Paint(Paint.ANTI_ALIAS_FLAG)

    private var options = listOf<String>()
    private var activeSector = -1
    private var menuCX = 0f
    private var menuCY = 0f
    private var currentThumbX = 0f
    private var currentThumbY = 0f

    // Color Azul Pastel solicitado
    private val pastelBlue = Color.parseColor("#B6D0E2")

    init {
        textPaint.color = Color.WHITE
        textPaint.textSize = 32f
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.typeface = Typeface.DEFAULT_BOLD
        textPaint.setShadowLayer(5f, 0f, 0f, Color.BLACK)

        paintBack.color = Color.parseColor("#CC000000")
        paintBack.style = Paint.Style.FILL

        paintSelection.color = pastelBlue
        paintSelection.style = Paint.Style.STROKE
        paintSelection.strokeWidth = 20f

        paintThumb.color = pastelBlue
        paintThumb.style = Paint.Style.FILL
    }

    fun openMenu(level: String, x: Float, y: Float) {
        menuCX = x
        menuCY = y
        currentThumbX = x
        currentThumbY = y

        options = when(level) {
            "PRINCIPAL" -> listOf("CONFIG", "EDIT", "WEB", "MEDIA", "PLAY", "VOLUME", "NAV", "BACK")

            "CONFIG" -> listOf("AJUSTES", "PERMISOS", "GESTOS", "NULL", "NULL", "NULL", "NULL", "VOLVER")

            "EDIT" -> listOf("COPIAR", "PEGAR", "TODO", "CORTAR", "NULL", "NULL", "NULL", "VOLVER")

            "WEB" -> listOf("ATRAS", "ADELANTE", "SCROLL UP", "SCROLL DN", "NUEVA T", "RECARGAR", "CERRAR T", "VOLVER")

            // MENU MEDIA ACTUALIZADO PARA YOUTUBE
            "MEDIA" -> listOf("PLAY/PAUSE", "SIGUIENTE", "ANTERIOR", "NULL", "FULLSCREEN", "ADELAN 10s", "ATRAS 10s", "VOLVER")

            "PLAY" -> listOf("PLAY", "PAUSE", "VOLVER", "NULL", "NULL", "NULL", "NULL", "NULL")

            "VOLUME" -> listOf("SUBIR", "BAJAR", "MUTE", "NULL", "NULL", "NULL", "NULL", "VOLVER")

            "NAV" -> listOf("ATRAS", "INICIO", "RECIENTES", "NOTIF", "NULL", "NULL", "NULL", "VOLVER")

            else -> listOf("1", "2", "3", "4", "5", "6", "7", "VOLVER")
        }
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (options.isEmpty()) return

        val radius = 300f
        canvas.drawCircle(menuCX, menuCY, radius, paintBack)

        val rect = RectF(menuCX - radius, menuCY - radius, menuCX + radius, menuCY + radius)
        val sweepAngle = 360f / 8f

        for (i in 0 until 8) {
            val startAngle = (i * sweepAngle) - 90 - (sweepAngle / 2)

            if (i == activeSector) {
                paintSelection.style = Paint.Style.STROKE
                canvas.drawArc(rect, startAngle, sweepAngle, false, paintSelection)

                val paintFill = Paint()
                paintFill.color = pastelBlue
                paintFill.alpha = 60
                canvas.drawArc(rect, startAngle, sweepAngle, true, paintFill)
            }

            if (i < options.size && options[i] != "NULL") {
                val angleRad = Math.toRadians(((i * sweepAngle) - 90).toDouble())
                val tx = menuCX + (radius * 0.7f) * cos(angleRad).toFloat()
                val ty = menuCY + (radius * 0.7f) * sin(angleRad).toFloat()
                val textOffset = (textPaint.descent() + textPaint.ascent()) / 2
                canvas.drawText(options[i], tx, ty - textOffset, textPaint)
            }
        }

        canvas.drawCircle(currentThumbX, currentThumbY, 15f, paintThumb)
        val paintLine = Paint().apply { color = Color.GRAY; strokeWidth = 3f }
        canvas.drawLine(menuCX, menuCY, currentThumbX, currentThumbY, paintLine)
    }
}