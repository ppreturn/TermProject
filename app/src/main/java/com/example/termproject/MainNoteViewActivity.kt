package com.example.termproject

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class MainNoteViewActivity : AppCompatActivity() {
    private lateinit var noteList: MutableList<JsonData>
    private val mainList = mutableListOf<JsonData>()
    private val extendedList = mutableMapOf<Int, MutableList<JsonData>>()
    private var updateJob: Job? = null
    private var currentPosition: Int = 0
    private lateinit var adapter: NotePagerAdapter

    private var noteListUri: Uri? = null
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_note_view)

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val noteListUriString = intent.getStringExtra("noteListUri")
        noteListUriString?.let {
            noteListUri = Uri.parse(it)
            loadNoteListFromFile(noteListUri!!)
            processNoteList()

            val viewPager = findViewById<ViewPager>(R.id.viewPager)
            adapter = NotePagerAdapter(this, mainList, viewPager)
            viewPager.adapter = adapter

            viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {
                    currentPosition = position
                }

                override fun onPageScrollStateChanged(state: Int) {}
            })

            // Start coroutine for the initial page
            startUpdateJob()
        }

        // Extend 버튼 클릭 리스너 설정
        findViewById<Button>(R.id.extendButton).setOnClickListener {
            val intent = Intent(this, ExtendNoteViewActivity::class.java)
            startActivity(intent)
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            noteListUri?.let { uri ->
                saveToJson(uri)
            }
        }
    }

    private fun startUpdateJob() {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Default).launch {
            var prvNoteBase64 = ""
            while (isActive) {
                delay(200)
                //Log.d("startUpdateJob()", "currentPosition : ${currentPosition}")
                val noteDrawView = adapter.getDrawViewAt(currentPosition)
                noteDrawView?.let {
                    val bitmap = it.getBitmap()
                    val noteBase64 = Util.encodeBase64(bitmap)
                    noteList[currentPosition].noteBase64 = noteBase64
                    prvNoteBase64 = noteBase64
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startUpdateJob()
    }

    override fun onPause() {
        super.onPause()
        updateJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        noteListUri?.let {
            saveToJson(it)
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

    private fun saveToJson(uri: Uri) {
        var modifiedNoteList = mutableListOf<JsonData>()

        mainList.forEachIndexed { i, element ->
            element.keyIndex = -1
            element.tag = 0
            modifiedNoteList.add(element)
        }
        extendedList.forEach { (key, List) ->
            modifiedNoteList[List[0].keyIndex].keyIndex = modifiedNoteList.size
            List.forEachIndexed { i, element ->
                element.prevIndex = if(i != 0) modifiedNoteList.size - 1 else -1
                element.nextIndex = if(i != List.size - 1) modifiedNoteList.size + 1 else -1
                element.tag = 1
                modifiedNoteList.add(element)
            }
        }
        val json = gson.toJson(modifiedNoteList).replace("\n", "")
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(json)
            }
        }
    }
}