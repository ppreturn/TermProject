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
import java.io.File

class NotePagerAdapter(private val context: Context,
                       private val pdfRenderer: PdfRenderer,
                       private val fileHash: String,
                       private val noteList: List<ListInfo>,
                       private val viewPager: ViewPager) : PagerAdapter() {
    //private val savedBitmaps = mutableMapOf<Int, Bitmap?>()

    override fun getCount(): Int = noteList.size

    override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.page_item, container, false)

        val backgroundImageView = view.findViewById<ImageView>(R.id.backgroundImageView)
        val noteDrawView = view.findViewById<DrawView>(R.id.noteDrawView)

        val noteData = noteList[position]
        val pdfPage = pdfRenderer.openPage(position)
        val backgroundBitmap = Bitmap.createBitmap(pdfPage.width, pdfPage.height, Bitmap.Config.ARGB_8888)
        pdfPage.render(backgroundBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        backgroundImageView.setImageBitmap(backgroundBitmap)

        // Set DrawView to match ImageView content size
        noteDrawView.setImageView(backgroundImageView)
        pdfPage.close()
        // Restore the bitmap if it was saved before
       // if(savedBitmaps[position] == null) {
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
        val noteDrawView = view.findViewById<DrawView>(R.id.noteDrawView)
        // savedBitmaps[position] = noteDrawView.getBitmap()
        container.removeView(view)
    }

    fun getDrawViewAt(position: Int): DrawView? {
        return viewPager.findViewWithTag<View>(position)?.findViewById(R.id.noteDrawView)
    }
}
