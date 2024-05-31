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
    private var drawPaint: Paint = Paint()
    private var canvasPaint: Paint? = null
    private var visibleDrawCanvas: Canvas? = null
    private var visibleCanvasBitmap: Bitmap? = null
    private var imageView: ImageView? = null
    private var contentRect: RectF = RectF()

    private var erase: Boolean = false
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

                if (visibleCanvasBitmap == null) {
                    visibleCanvasBitmap = Bitmap.createBitmap(contentRect.width().toInt(), contentRect.height().toInt(), Bitmap.Config.ARGB_8888)
                    visibleDrawCanvas = Canvas(visibleCanvasBitmap!!)
                } else if(visibleCanvasBitmap!!.width != contentRect.width().toInt() || visibleCanvasBitmap!!.height != contentRect.height().toInt()) {
                    val newWidth = contentRect.width().toInt()
                    val newHeight = contentRect.height().toInt()

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
        canvas.drawBitmap(visibleCanvasBitmap!!, 0f, 0f, canvasPaint)
        canvas.drawPath(drawPath, drawPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS) {
            // 부모 View (ViewPager)의 터치 이벤트 차단
            parent.requestDisallowInterceptTouchEvent(true)

            val touchX = event.x
            val touchY = event.y
            Log.d("onTouchEvent", "touchX: $touchX, touchY: $touchY")

            // 터치 위치가 콘텐츠의 범위 내에 있는지 확인
            if (contentRect.contains(touchX, touchY)) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { // 펜이 처음으로 화면과 닿았을 때
                        drawPath.moveTo(touchX - contentRect.left, touchY - contentRect.top)
                    }
                    MotionEvent.ACTION_MOVE -> { // 펜이 화면에서 움직이는 중.
                        if(drawPath.isEmpty) // 펜이 이전에 화면과 닿았지만 화면 밖으로 나갔다가 다시 들어온 상태
                            drawPath.moveTo(touchX - contentRect.left, touchY - contentRect.top) // 펜이 닿은 위치 좌표로 이동
                        drawPath.lineTo(touchX - contentRect.left, touchY - contentRect.top) // 선을 그음.
                        if(erase) { // 지우기 모드일 때
                            visibleDrawCanvas?.drawPath(drawPath, drawPaint)
                            drawPath.reset()
                            drawPath.moveTo(touchX - contentRect.left, touchY - contentRect.top)
                        }
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> { // 선을 땐 경우.
                        if(!erase) { // 지우기 모드가 아닐 때
                            visibleDrawCanvas?.drawPath(drawPath, drawPaint)
                            drawPath.reset()
                        }
                        invalidate()
                    }
                    else -> return false
                }

                invalidate()
                return true
            } else { // 펜이 화면 밖에 있는 경우
                if(!drawPath.isEmpty) { // 그런데 선을 이미 그은 상태인 경우.
                    visibleDrawCanvas?.drawPath(drawPath, drawPaint)
                    drawPath.reset()
                }
                invalidate()
                return true
            }
        }
        return false
    }

    fun getBitmap(): Bitmap {
        return visibleCanvasBitmap!!
    }

    fun setBitmap(bitmap: Bitmap) {


        visibleCanvasBitmap = bitmap
        visibleDrawCanvas = Canvas(visibleCanvasBitmap!!)
        invalidate()
    }

    fun clear() {
        visibleCanvasBitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    fun setPaintProperties(color: Int, strokeWidth: Float) {
        drawPaint.color = color
        drawPaint.strokeWidth = strokeWidth
        drawPaint.xfermode = null
        erase = false
    }

    fun setEraseMode() {
        drawPaint.color = Color.TRANSPARENT
        drawPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        drawPaint.strokeWidth = 50f
        erase = true
    }
}