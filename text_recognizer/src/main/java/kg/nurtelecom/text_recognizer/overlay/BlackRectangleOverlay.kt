package kg.nurtelecom.text_recognizer.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import kg.nurtelecom.text_recognizer.R
import kg.nurtelecom.text_recognizer.extension.dp

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
        val marginPx = 16.dp
        println(marginPx)

        val rectWidth = canvasWidth - 2 * marginPx
        val rectHeight = rectWidth * (6.0f / 9.0f)

        val left = marginPx
        var top = canvasHeight / 5f
        val right = left + rectWidth
        val bottom = top + rectHeight

        if (bottom > canvasHeight - marginPx) {
            val excess = bottom - (canvasHeight - marginPx)
            top -= excess
        }

        val rect = RectF(left, top, right, bottom)
        val radius = 16.0f
        canvas.drawRoundRect(rect, radius, radius, getEraser())
        drawMRZMasks(canvas, rect)
        drawText(canvas, rect)
    }

    private fun drawMRZMasks(canvas: Canvas, rect: RectF) {
        val marginBottomPx = 20.dp
        val marginHorizontalPx = 20.dp
        val gapPx = 10.dp

        val maskHeight = 10.dp
        val maskWidth = rect.width() - 2 * marginHorizontalPx
        val maskLeft = rect.left + marginHorizontalPx
        val maskRight = maskLeft + maskWidth

        val maskAlpha = (0.35 * 255).toInt()

        val maskPaint = Paint().apply {
            color = Color.parseColor("#BFBFBF")
            alpha = maskAlpha
        }

        val maskRadius = 6.dp
        val baseMaskTop = rect.bottom - marginBottomPx - maskHeight
        for (i in 0 until 3) {
            val maskTop = baseMaskTop - i * (maskHeight + gapPx)
            val maskBottom = maskTop + maskHeight
            val maskRect = RectF(maskLeft, maskTop, maskRight, maskBottom)
            canvas.drawRoundRect(maskRect, maskRadius, maskRadius, maskPaint)
        }
    }

    private fun drawText(canvas: Canvas, rect: RectF) {
        val text = context.getString(R.string.text_recognizer_desc_photo_capture)
        val textColor = ContextCompat.getColor(context, com.design.chili.R.color.white_1)
        val textSizePx = 16.dp

        val marginTopPx = 24.dp
        val lineSpacingPx = 8.dp

        val textPaint = Paint().apply {
            color = textColor
            textSize = textSizePx
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            letterSpacing = -0.38588235f / textSizePx
            typeface = Typeface.create("Roboto", Typeface.NORMAL)
        }

        val lines = text.split("\n")

        val textX = canvas.width / 2f
        var textY = rect.bottom + marginTopPx + textSizePx

        for (line in lines) {
            canvas.drawText(line, textX, textY, textPaint)
            textY += textSizePx + lineSpacingPx
        }
    }
}