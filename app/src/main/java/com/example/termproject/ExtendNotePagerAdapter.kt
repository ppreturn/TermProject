package com.example.termproject

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

class ExtendNotePagerAdapter(private val context: Context,
                             private val pdfRenderer: PdfRenderer,
                             private val fileHash: String,
                             private val extendNoteList: List<ListInfo>,
                             private val viewPager: ViewPager
) : PagerAdapter() {
    override fun getCount(): Int = extendNoteList.size + 1

    override fun isViewFromObject(view: View, obj: Any): Boolean = view == obj

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val inflater = LayoutInflater.from(context)
        val layoutResource = if (position < extendNoteList.size) {
            R.layout.page_item
        } else {
            R.layout.extend_last_page_item
        }

        val view = inflater.inflate(layoutResource, container, false)

        if (position < extendNoteList.size) {

        } else {
            val addPortraitButton = view.findViewById<Button>(R.id.addPortraitBtn)
            val addLandscapeButton = view.findViewById<Button>(R.id.addLandscapeBtn)

            addPortraitButton.setOnClickListener {

            }

            addLandscapeButton.setOnClickListener {

            }

        }

        view.tag = position

        container.addView(view)
        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, obj: Any) {
        val view = obj as View
        container.removeView(view)
    }

    fun getDrawViewAt(position: Int): DrawView? {
        return viewPager.findViewWithTag<View>(position)?.findViewById(R.id.noteDrawView)
    }
}