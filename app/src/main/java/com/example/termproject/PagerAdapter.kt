package com.example.termproject

import android.content.Context
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.viewpager.widget.PagerAdapter

class NotePagerAdapter(private val context: Context, private val noteList: List<JsonData>) : PagerAdapter() {
    private val savedBitmaps = mutableMapOf<Int, Bitmap>()

    override fun getCount(): Int = noteList.size

    override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.page_item, container, false)

        val backgroundImageView = view.findViewById<ImageView>(R.id.backgroundImageView)
        val noteDrawView = view.findViewById<DrawView>(R.id.noteDrawView)

        val noteData = noteList[position]
        val backgroundBitmap = Util.decodeBase64(noteData.backgroundBase64)
        backgroundImageView.setImageBitmap(backgroundBitmap)

        // Set DrawView to match ImageView content size
        noteDrawView.setImageView(backgroundImageView)

        // Restore the bitmap if it was saved before
        savedBitmaps[position]?.let {
            noteDrawView.setBitmap(it)
        }

        container.addView(view)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        val view = obj as View
        val noteDrawView = view.findViewById<DrawView>(R.id.noteDrawView)
        savedBitmaps[position] = noteDrawView.getBitmap()
        container.removeView(view)
    }
}