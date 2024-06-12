package com.example.termproject

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView

class DrawView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var drawPath: Path = Path()
    private var visibleDrawPath: Path = Path()
    private var drawPaint: Paint = Paint()
    private var canvasPaint: Paint? = null
    private var visibleDrawCanvas: Canvas? = null
    private var visibleCanvasBitmap: Bitmap? = null
    private var imageView: ImageView? = null
    private var contentRect: RectF = RectF()

    private var drawCanvas: Canvas? = null
    private var canvasBitmap: Bitmap? = null

    private var erase: Boolean = false
    init {
        setupDrawing()
    }


    private fun getScale(): Float {
        if (canvasBitmap == null || visibleCanvasBitmap == null) return 1f
        return canvasBitmap!!.width.toFloat() / visibleCanvasBitmap!!.width.toFloat()
    }
    private fun setupDrawing() {
        drawPaint = Paints.getDrawPaint()
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

                if (visibleCanvasBitmap == null) {
                    visibleCanvasBitmap = Bitmap.createBitmap(contentRect.width().toInt(), contentRect.height().toInt(), Bitmap.Config.ARGB_8888)
                    visibleDrawCanvas = Canvas(visibleCanvasBitmap!!)
                } else if(visibleCanvasBitmap!!.width != contentRect.width().toInt() || visibleCanvasBitmap!!.height != contentRect.height().toInt()) {
                    val newWidth = contentRect.width().toInt()
                    val newHeight = contentRect.height().toInt()
                    if(newWidth == 0 || newHeight == 0) return@let
                    visibleCanvasBitmap = Bitmap.createScaledBitmap(visibleCanvasBitmap!!, newWidth, newHeight, true)
                    visibleDrawCanvas = Canvas(visibleCanvasBitmap!!)
                }

                layoutParams.width = contentRect.width().toInt()
                layoutParams.height = contentRect.height().toInt()
                requestLayout()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val originalStrokeWidth = drawPaint.strokeWidth
        drawPaint.strokeWidth = drawPaint.strokeWidth/getScale()
        canvas.drawBitmap(visibleCanvasBitmap!!, 0f, 0f, canvasPaint)
        canvas.drawPath(visibleDrawPath, drawPaint)
        drawPaint.strokeWidth = originalStrokeWidth
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
                    MotionEvent.ACTION_DOWN -> { // 펜이 처음으로 화면과 닿았을 때
                        onActionDown(touchX, touchY)
                    }
                    MotionEvent.ACTION_MOVE -> { // 펜이 화면에서 움직이는 중.
                        onActionMove(touchX, touchY)
                    }
                    MotionEvent.ACTION_UP -> { // 선을 땐 경우.
                        onActionUp(touchX, touchY)
                    }
                    else -> return false
                }

                invalidate()
                return true
            } else { // 펜이 화면 밖에 있는 경우
                if(!visibleDrawPath.isEmpty) { // 그런데 선을 이미 그은 상태인 경우.
                    val tmpStrokeWidth = drawPaint.strokeWidth
                    drawPaint.strokeWidth = drawPaint.strokeWidth/getScale()
                    visibleDrawCanvas?.drawPath(visibleDrawPath, drawPaint)
                    visibleDrawPath.reset()
                    drawPaint.strokeWidth = tmpStrokeWidth
                    drawCanvas?.drawPath(drawPath, drawPaint)
                    drawPath.reset()
                }
                invalidate()
                return true
            }
        }
        return false
    }


    private fun onActionDown(touchX: Float, touchY: Float) {
        visibleDrawPath.moveTo(touchX - contentRect.left, touchY - contentRect.top)
        drawPath.moveTo(touchX*getScale() - contentRect.left, touchY*getScale() - contentRect.top)
    }

    private fun onActionMove(touchX: Float, touchY: Float) {
        if(visibleDrawPath.isEmpty) {// 펜이 이전에 화면과 닿았지만 화면 밖으로 나갔다가 다시 들어온 상태
            visibleDrawPath.moveTo(touchX - contentRect.left, touchY - contentRect.top) // 펜이 닿은 위치 좌표로 이동
            drawPath.moveTo(touchX*getScale() - contentRect.left, touchY*getScale() - contentRect.top)
        }
        val originalStrokeWidth = drawPaint.strokeWidth
        drawPaint.strokeWidth = drawPaint.strokeWidth/getScale()
        visibleDrawPath.lineTo(touchX - contentRect.left, touchY - contentRect.top) // 선을 그음.

        drawPaint.strokeWidth = originalStrokeWidth
        drawPath.lineTo(touchX*getScale() - contentRect.left, touchY*getScale() - contentRect.top)

        if(erase) { // 지우기 모드일 때
            val originalStrokeWidth = drawPaint.strokeWidth
            drawPaint.strokeWidth = drawPaint.strokeWidth/getScale()
            visibleDrawCanvas?.drawPath(visibleDrawPath, drawPaint)

            drawPaint.strokeWidth = originalStrokeWidth
            drawCanvas?.drawPath(drawPath, drawPaint)

            visibleDrawPath.reset()
            drawPath.reset()
            visibleDrawPath.moveTo(touchX - contentRect.left, touchY - contentRect.top)
            drawPath.moveTo(touchX*getScale() - contentRect.left, touchY*getScale() - contentRect.top)
        }
        invalidate()
    }

    private fun onActionUp(touchX: Float, touchY: Float) {
        if(!erase) { // 지우기 모드가 아닐 때
            val originalStrokeWidth = drawPaint.strokeWidth
            drawPaint.strokeWidth = drawPaint.strokeWidth/getScale()
            visibleDrawCanvas?.drawPath(visibleDrawPath, drawPaint)
            visibleDrawPath.reset()

            drawPaint.strokeWidth = originalStrokeWidth
            drawCanvas?.drawPath(drawPath, drawPaint)
            drawPath.reset()
        }
        invalidate()
    }


    fun getBitmap(): Bitmap {
        return canvasBitmap!!
    }

    fun setBitmap(bitmap: Bitmap) {
        canvasBitmap = bitmap
        drawCanvas = Canvas(canvasBitmap!!)

        visibleCanvasBitmap = bitmap
        visibleDrawCanvas = Canvas(visibleCanvasBitmap!!)
        invalidate()
    }

    fun clear() {
        visibleCanvasBitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    fun setDrawMode() {
        drawPaint = Paints.getDrawPaint()
        erase = false
    }

    fun setEraseMode() {
        drawPaint = Paints.getErasePaint()
        erase = true
    }
}