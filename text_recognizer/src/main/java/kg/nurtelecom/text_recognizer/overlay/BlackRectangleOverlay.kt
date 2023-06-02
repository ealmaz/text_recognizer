package kg.nurtelecom.text_recognizer.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet

class BlackRectangleOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : Overlay(context, attrs, defStyleAttr) {

    override fun drawShapes(canvas: Canvas) {
        drawColor(com.design.chili.R.color.black_1)
        drawRectangle(canvas)
    }

    private fun drawRectangle(canvas: Canvas) {
        val top = canvasHeight / 4.2f
        val bottom = canvasHeight - (top * 2)
        val left = canvasWidth / 30f
        val right = canvasWidth - left
        val rect = RectF(left, top, right, bottom)
        val radius = 16.0f
        canvas.drawRoundRect(rect, radius, radius, getEraser())
    }

}