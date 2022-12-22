package kg.nurtelecom.text_recognizer.overlay

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

open abstract class Overlay@JvmOverloads constructor(context: Context,
                                                     attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {

    open val outerFillColor = 0x77000000
    protected var canvasWidth: Int = 0
    protected var canvasHeight: Int = 0
    protected lateinit var paint: Paint
    private lateinit var auxCanvas: Canvas

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        canvasWidth = canvas!!.width
        canvasHeight = canvas.height

        val bitmap: Bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        auxCanvas = Canvas(bitmap)
        paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outerFillColor
            style = Paint.Style.FILL
        }
        auxCanvas.drawPaint(paint)

        paint.run {
            color = ContextCompat.getColor(context, com.design.chili.R.color.magenta_1  )
            style = Paint.Style.STROKE
            strokeWidth = resources.getDimension(com.design.chili.R.dimen.view_4dp)
        }

        drawShapes(auxCanvas)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
    }

    abstract fun drawShapes(canvas: Canvas)

    fun getEraser(): Paint {
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = outerFillColor
            style = Paint.Style.FILL
            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        }
    }

    fun drawColor(@ColorRes colorId: Int, alpha: Int = 77) {
        val color = ColorUtils.setAlphaComponent(ContextCompat.getColor(context, colorId), alpha)
        auxCanvas.drawColor(color)
    }

}