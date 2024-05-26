package com.example.termproject

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader

class MainNoteViewActivity : AppCompatActivity() {
    private lateinit var noteList: MutableList<JsonData>
    private val mainList = mutableListOf<JsonData>()
    private val extendedList = mutableMapOf<Int, MutableList<JsonData>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_note_view)

        val noteListUriString = intent.getStringExtra("noteListUri")
        Log.d("on noteViewActivity", noteListUriString.toString())
        noteListUriString?.let {
            val noteListUri = Uri.parse(it)
            Log.d("MainNoteViewActivity", "Received URI: $noteListUri")
            loadNoteListFromFile(noteListUri)
            processNoteList()

            val viewPager = findViewById<ViewPager>(R.id.viewPager)
            val adapter = NotePagerAdapter(this, mainList)
            viewPager.adapter = adapter
        }
    }

    private fun loadNoteListFromFile(uri: Uri) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<MutableList<JsonData>>() {}.type
            noteList = Gson().fromJson(reader, type)
        }
    }

    private fun processNoteList() {
        // Start element 찾기
        var startElement = noteList.find { it.tag == 0 && it.prevIndex == -1 }

        // Start element가 없는 경우 A4용지 크기의 배경과 투명 노트를 생성
        if (startElement == null) {
            val a4Width = 2480 // A4 용지 너비 (픽셀, 300dpi 기준)
            val a4Height = 3508 // A4 용지 높이 (픽셀, 300dpi 기준)
            val whiteBackground = Util.createBase64Image(a4Width, a4Height, android.graphics.Color.WHITE)
            val transparentNote = Util.createBase64Image(a4Width, a4Height, android.graphics.Color.TRANSPARENT)

            startElement = JsonData(
                backgroundBase64 = whiteBackground,
                noteBase64 = transparentNote,
                nextIndex = -1,
                prevIndex = -1,
                keyIndex = -1,
                tag = 0
            )
        }

        // mainList 초기화 및 요소 추가
        mainList.add(startElement)
        while (mainList.last().nextIndex != -1) {
            val nextElement = noteList[mainList.last().nextIndex]
            nextElement.prevIndex = mainList.size - 1
            mainList.last().nextIndex = mainList.size
            mainList.add(nextElement)
        }

        // extendedList 초기화 및 요소 추가
        mainList.forEachIndexed { i, element ->
            if (element.keyIndex != -1) {
                var extendedStartElement = noteList[element.keyIndex]
                val tmpMutableList = mutableListOf<JsonData>(extendedStartElement)
                while (tmpMutableList.last().nextIndex != -1) {
                    val et = noteList[tmpMutableList.last().nextIndex]
                    et.prevIndex = tmpMutableList.size - 1
                    tmpMutableList.last().nextIndex = tmpMutableList.size
                    tmpMutableList.add(et)
                }
                extendedList[i] = tmpMutableList
            }
        }
    }
}