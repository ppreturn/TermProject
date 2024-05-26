package com.example.termproject

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView

class DrawView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var drawPath: Path = Path()
    private var drawPaint: Paint = Paint()
    private var canvasPaint: Paint? = null
    private var drawCanvas: Canvas? = null
    private var canvasBitmap: Bitmap? = null
    private var imageView: ImageView? = null
    private var contentRect: RectF = RectF()

    init {
        setupDrawing()
    }

    private fun setupDrawing() {
        drawPaint.color = Color.BLACK
        drawPaint.isAntiAlias = true
        drawPaint.strokeWidth = 5f
        drawPaint.style = Paint.Style.STROKE
        drawPaint.strokeJoin = Paint.Join.ROUND
        drawPaint.strokeCap = Paint.Cap.ROUND
        canvasPaint = Paint(Paint.DITHER_FLAG)
    }

    fun setImageView(imageView: ImageView) {
        this.imageView = imageView
        imageView.viewTreeObserver.addOnGlobalLayoutListener {
            adjustCanvasSize()
        }
    }

    private fun adjustCanvasSize() {
        imageView?.let {
            val drawable = it.drawable
            if (drawable != null) {
                val intrinsicWidth = drawable.intrinsicWidth
                val intrinsicHeight = drawable.intrinsicHeight
                val imageViewWidth = it.width
                val imageViewHeight = it.height

                // Calculate the scale
                val scale: Float
                val dx: Float = 0f
                val dy: Float = 0f

                if (intrinsicWidth * imageViewHeight > imageViewWidth * intrinsicHeight) {
                    scale = imageViewWidth / intrinsicWidth.toFloat()
                } else {
                    scale = imageViewHeight / intrinsicHeight.toFloat()
                }

                contentRect.set(dx, dy, dx + intrinsicWidth * scale, dy + intrinsicHeight * scale)

                if (canvasBitmap == null || canvasBitmap!!.width != contentRect.width().toInt() || canvasBitmap!!.height != contentRect.height().toInt()) {
                    canvasBitmap = Bitmap.createBitmap(contentRect.width().toInt(), contentRect.height().toInt(), Bitmap.Config.ARGB_8888)
                    drawCanvas = Canvas(canvasBitmap!!)
                }

                layoutParams.width = contentRect.width().toInt()
                layoutParams.height = contentRect.height().toInt()
                requestLayout()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(canvasBitmap!!, 0f, 0f, canvasPaint)
        canvas.drawPath(drawPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            // 부모 View (ViewPager)의 터치 이벤트 차단
            parent.requestDisallowInterceptTouchEvent(true)

            val touchX = event.x
            val touchY = event.y

            // 터치 위치가 콘텐츠의 범위 내에 있는지 확인
            if (contentRect.contains(touchX, touchY)) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        drawPath.moveTo(touchX - contentRect.left, touchY - contentRect.top)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        drawPath.lineTo(touchX - contentRect.left, touchY - contentRect.top)
                    }
                    MotionEvent.ACTION_UP -> {
                        drawCanvas?.drawPath(drawPath, drawPaint)
                        drawPath.reset()
                    }
                    else -> return false
                }

                invalidate()
                return true
            }
        }
        return false
    }

    fun getBitmap(): Bitmap {
        return canvasBitmap!!
    }

    fun setBitmap(bitmap: Bitmap) {
        canvasBitmap = bitmap
        drawCanvas = Canvas(canvasBitmap!!)
        invalidate()
    }

    fun clear() {
        canvasBitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }
}