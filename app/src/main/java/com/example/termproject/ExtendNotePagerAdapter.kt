package com.example.termproject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import java.io.File

class ExtendNotePagerAdapter(private val context: Context,
                             private val listener: onExtendButtonClickListener?,
                             private var nextPage: Int,
                             private val parentKeyIndex: Int,
                             private val pdfPage: PdfRenderer.Page,
                             private val fileHash: String,
                             public val extendNoteMap: MutableMap<Int, ListInfo>,
                             private val viewPager: ViewPager
) : PagerAdapter() {

    private val PORTRAIT_PAGE = 2
    private val LANDSCAPE_PAGE = 3

    override fun getCount(): Int = extendNoteMap.size + 1

    override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val inflater = LayoutInflater.from(context)
        val layoutResource = if (position < extendNoteMap.size) {
            R.layout.page_item
        } else {
            R.layout.extend_last_page_item
        }

        val view = inflater.inflate(layoutResource, container, false)

        if (position < extendNoteMap.size) {
            val backgroundImageView = view.findViewById<ImageView>(R.id.backgroundImageView)
            val noteDrawView = view.findViewById<DrawView>(R.id.noteDrawView)

            val unique = getUniqueFromCurrentPosition(position)
            val noteData = extendNoteMap[unique]
            val tmpBackgroundBitmap = createA4SizeWhiteBitmap(noteData!!.tag)

            val backgroundBitmap = tmpBackgroundBitmap.copy(Bitmap.Config.ARGB_8888, true)
            backgroundImageView.setImageBitmap(backgroundBitmap)

            noteDrawView.setImageView(backgroundImageView)

            val fileDir = File(context.getExternalFilesDir(null), "${fileHash}")
            val pngFile = File(fileDir, "${unique}.png")
            val bitmap = (if(pngFile.exists()) BitmapFactory.decodeFile(pngFile.absolutePath) else null) ?:
                createDefaultBitmap(backgroundBitmap.width, backgroundBitmap.height, context)
            val convertedNoteBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

            noteDrawView.setBitmap(convertedNoteBitmap)

            Log.d("background bitmap", "width: ${backgroundBitmap.width}, height: ${backgroundBitmap.height}")
            Log.d("converted bitmap", "width: ${convertedNoteBitmap.width}, height: ${convertedNoteBitmap.height}")

            if(Paints.getEraseMode()) noteDrawView.setEraseMode()
            else noteDrawView.setDrawMode()

        } else {
            val addPortraitButton = view.findViewById<Button>(R.id.addPortraitBtn)
            val addLandscapeButton = view.findViewById<Button>(R.id.addLandscapeBtn)

            addPortraitButton.setOnClickListener {
                addPageBeforeLast(PORTRAIT_PAGE)
                listener?.modifyNotesAndSaveJsonFile(nextPage, extendNoteMap)
            }

            addLandscapeButton.setOnClickListener {
                addPageBeforeLast(LANDSCAPE_PAGE)
                listener?.modifyNotesAndSaveJsonFile(nextPage, extendNoteMap)
            }

        }

        view.tag = position

        container.addView(view)
        return view
    }


    private fun getScaleFactor(width: Int, height: Int, context: Context): Float{
        var portraitScaleFactor : Float = 0f
        var landscapeScaleFactor : Float = 0f
        var screenWidth : Int = 0
        var screenHeight : Int = 0

        val displayMetrics = context.resources.displayMetrics
        screenWidth = displayMetrics.widthPixels
        screenHeight = displayMetrics.heightPixels

        portraitScaleFactor = minOf(screenWidth/width.toFloat(), screenHeight/height.toFloat())
        landscapeScaleFactor = minOf(screenWidth/height.toFloat(), screenHeight/width.toFloat())
        return maxOf(portraitScaleFactor, landscapeScaleFactor)
    }

    private fun createA4SizeWhiteBitmap(direction: Int): Bitmap {
        var scaleFactor : Float = 0f
        scaleFactor = getScaleFactor(Util.a4Width, Util.a4Height, context)
        val width = (Util.a4Width * scaleFactor).toInt()
        val height = (Util.a4Height * scaleFactor).toInt()

        val bitmap = if(direction == 2) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888)
        }
        bitmap.eraseColor(Color.WHITE)

        return bitmap
    }

    private fun createDefaultBitmap(width:Int, height:Int, context: Context): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()
        paint.color = Color.TRANSPARENT
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    private fun getUniqueFromCurrentPosition(currentPosition: Int) :Int {
        var cur = extendNoteMap.values.find { it.prevIndex == -1 }
        var cnt = 0
        while(cnt < currentPosition) {
            cur = extendNoteMap[cur!!.nextIndex]
            cnt += 1
        }
        return cur!!.unique
    }


    /***************************************
     *
     * type 0 is main note
     * type 1 >> with Pdf Page file (plan)
     * type 2 >> Portrait
     * type 3 >> Landscape
     *
     ***************************************/
    private fun addPageBeforeLast(newTag: Int) {
        var element = ListInfo(
            unique = nextPage,
            nextIndex = -1,
            prevIndex = -1,
            keyIndex = parentKeyIndex,
            tag = newTag
        )
        if(extendNoteMap.size > 0) {
            val lastElementUnique = (extendNoteMap.values.find { it.nextIndex == -1 })!!.unique
            element.prevIndex = lastElementUnique
            extendNoteMap[lastElementUnique]!!.nextIndex = nextPage
        }
        extendNoteMap.put(nextPage, element)
        nextPage += 1
        // notifyDataSetChanged()
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        val view = obj as View
        container.removeView(view)
    }

    fun getDrawViewAt(position: Int): DrawView? {
        return viewPager.findViewWithTag<View>(position)?.findViewById(R.id.noteDrawView)
    }

    override fun getItemPosition(`object`: Any): Int {
        return POSITION_NONE // notifyDataSetChanged()를 위해 필요
    }
}