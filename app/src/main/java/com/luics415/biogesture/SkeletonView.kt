package com.luics415.biogesture

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class SkeletonView(context: Context) : View(context) {
    private var result: HandLandmarkerResult? = null
    private val paintPoint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 15f
        style = Paint.Style.FILL
    }
    private val paintLine = Paint().apply {
        color = Color.WHITE
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    fun setResults(res: HandLandmarkerResult) {
        result = res
        invalidate() // Forzar redibujado
    }

    fun clear() {
        result = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        result?.let { res ->
            if (res.landmarks().isNotEmpty()) {
                val hand = res.landmarks()[0]

                // Dibujar puntos
                for (point in hand) {
                    // Mapeo directo: MediaPipe entrega 0.0-1.0 normalizado
                    // Como usaremos la imagen rotada, x es x y y es y.
                    // Invertimos X (1 - x) porque es cámara frontal (espejo)
                    val px = (1f - point.x()) * width
                    val py = point.y() * height
                    canvas.drawCircle(px, py, 10f, paintPoint)
                }
            }
        }
    }
}