package com.example.termproject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.view.WindowInsets
import android.view.WindowManager
import java.io.File

class NotePagerAdapter(private val context: Context,
                       private val pdfRenderer: PdfRenderer,
                       private val fileHash: String,
                       private val noteList: List<ListInfo>,
                       private val viewPager: ViewPager) : PagerAdapter() {
    override fun getCount(): Int = noteList.size

    override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.page_item, container, false)

        val backgroundImageView = view.findViewById<ImageView>(R.id.backgroundImageView)
        val noteDrawView = view.findViewById<DrawView>(R.id.noteDrawView)

        val noteData = noteList[position]
        val pdfPage = pdfRenderer.openPage(position)

        val backgroundBitmap = getScaledPdfBitmap(pdfPage, context)

        pdfPage.render(backgroundBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        backgroundImageView.setImageBitmap(backgroundBitmap)

        // Set DrawView to match ImageView content size
        noteDrawView.setImageView(backgroundImageView)
        pdfPage.close()

        val fileDir = File(context.getExternalFilesDir(null), "${fileHash}")
        val pngFile = File(fileDir, "${noteData.unique}.png")
        Log.d("Absolute Path", "${pngFile.absolutePath}")
        val bitmap = (if(pngFile.exists()) BitmapFactory.decodeFile(pngFile.absolutePath) else null) ?:
                            createDefaultBitmap(pdfPage.width, pdfPage.height)
        val convertedNoteBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        noteDrawView.setBitmap(convertedNoteBitmap)

        // Tag the view with its position
        view.tag = position

        container.addView(view)
        return view
    }

    private fun getScaledPdfBitmap(pdfPage: PdfRenderer.Page, context: Context): Bitmap {
        var scaleFactor : Float = 0f
        var screenWidth : Int = 0
        var screenHeight : Int = 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.systemBars()
            )
            val bounds = windowMetrics.bounds
            screenWidth = bounds.width() - insets.left - insets.right
            screenHeight = bounds.height() - insets.top - insets.bottom
        } else {
            val displayMetrics = context.resources.displayMetrics
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
        }
        scaleFactor = 2*minOf(screenWidth/pdfPage.width.toFloat(), screenHeight/pdfPage.height.toFloat())
        val backgroundWidth = (pdfPage.width * scaleFactor).toInt()
        val backgroundHeight = (pdfPage.height * scaleFactor).toInt()
        val scaledBitmap = Bitmap.createBitmap(backgroundWidth, backgroundHeight, Bitmap.Config.ARGB_8888)
        return scaledBitmap
    }

    private fun createDefaultBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.TRANSPARENT
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        val view = obj as View
        container.removeView(view)
    }

    fun getDrawViewAt(position: Int): DrawView? {
        return viewPager.findViewWithTag<View>(position)?.findViewById(R.id.noteDrawView)
    }
}
