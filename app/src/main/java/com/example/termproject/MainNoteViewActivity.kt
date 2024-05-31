package com.example.termproject

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.viewpager.widget.ViewPager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

import com.example.termproject.Util
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainNoteViewActivity : AppCompatActivity() {
    private lateinit var fileDescriptor: ParcelFileDescriptor

    private lateinit var notes : Notes
    private val mainList = mutableListOf<ListInfo>()
    private val extendedList = mutableMapOf<Int, MutableList<ListInfo>>()
    private var updateJob: Job? = null
    private var currentPosition: Int = 0
    private lateinit var adapter: MainNotePagerAdapter

    private var jsonUri: Uri? = null
    private var pdfUri: Uri? = null
    private var fileHash: String? = null


    private var isEraseMode = false
    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_note_view)

        // requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        val jsonUriString = intent.getStringExtra("jsonUri")
        val pdfUriString = intent.getStringExtra("pdfUri")
        fileHash = intent.getStringExtra("fileHash")

        pdfUriString?.let {
            pdfUri = Uri.parse(it)
        }

        jsonUriString?.let {
            jsonUri = Uri.parse(it)
            loadNoteListFromFile(jsonUri!!)
            processNoteList()

            val viewPager = findViewById<ViewPager>(R.id.viewPager)
            adapter = MainNotePagerAdapter(this, openPdfRenderer(pdfUri!!), fileHash!!, mainList, viewPager)
            viewPager.adapter = adapter

            viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {
                    currentPosition = position
                    val drawView = adapter.getDrawViewAt(currentPosition)
                    if (isEraseMode) {
                        drawView?.setEraseMode()
                    } else {
                        drawView?.setPaintProperties(Color.BLACK, 5f)
                    }
                }

                override fun onPageScrollStateChanged(state: Int) {}
            })

            // Start coroutine for the initial page
            startUpdateJob()
        }

        // Extend 버튼 클릭 리스너 설정
        findViewById<Button>(R.id.extendButton).setOnClickListener {
            val file = File(cacheDir, "extendList.json")
            val extendJsonUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
            saveToExtendJson(extendJsonUri)
            val intent = Intent(this, ExtendNoteViewActivity::class.java).apply {
                putExtra("extendJsonUri", extendJsonUri.toString())
                putExtra("pdfUri", pdfUriString)
                putExtra("fileHash", fileHash)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {
            jsonUri?.let { uri ->
                saveToJson(uri)
            }
        }

        findViewById<Button>(R.id.drawButton).setOnClickListener {
            isEraseMode = false
            val drawView = adapter.getDrawViewAt(currentPosition)
            drawView?.setPaintProperties(Color.BLACK, 5f)
        }

        findViewById<Button>(R.id.eraseButton).setOnClickListener {
            isEraseMode = true
            val drawView = adapter.getDrawViewAt(currentPosition)
            drawView?.setEraseMode()
        }
    }

    private fun startUpdateJob() { // 폴더에 비트맵 저장 (Ext/HashFolder/0.png, 1.png, ...)
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(200)
                val noteDrawView = adapter.getDrawViewAt(currentPosition)
                noteDrawView?.let {
                    val bitmap = it.getBitmap()
                    notes.noteList[currentPosition].unique // todo with unique number
                    val saveDir = File(getExternalFilesDir(null), "$fileHash")
                    Util.saveImage(bitmap, saveDir, "${notes.noteList[currentPosition].unique}.png")
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
        jsonUri?.let {
            saveToJson(it)
        }
    }

    private fun loadNoteListFromFile(uri: Uri) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
            reader.close()
            inputStream.close()

            val jsonString = stringBuilder.toString()
            notes = gson.fromJson(jsonString, Notes::class.java)
        }
    }

    private fun processNoteList() {
        // Start element 찾기
        var startElement = notes.noteList.find { it.tag == 0 && it.prevIndex == -1 }

        // Start element가 없는 경우 A4용지 크기의 배경과 투명 노트를 생성
        if (startElement == null) {
            startElement = ListInfo(
                unique = 0,
                nextIndex = -1,
                prevIndex = -1,
                keyIndex = -1,
                tag = 0
            )
            notes.nextPage = 1
        }

        // mainList 초기화 및 요소 추가
        mainList.add(startElement)
        while (mainList.last().nextIndex != -1) {
            val nextElement = notes.noteList[mainList.last().nextIndex]
            nextElement.prevIndex = mainList.size - 1
            mainList.last().nextIndex = mainList.size
            mainList.add(nextElement)
        }

        // extendedList 초기화 및 요소 추가
        mainList.forEachIndexed { i, element ->
            if (element.keyIndex != -1) {
                var extendedStartElement = notes.noteList[element.keyIndex]
                val tmpMutableList = mutableListOf(extendedStartElement)
                while (tmpMutableList.last().nextIndex != -1) {
                    val et = notes.noteList[tmpMutableList.last().nextIndex]
                    et.prevIndex = tmpMutableList.size - 1
                    tmpMutableList.last().nextIndex = tmpMutableList.size
                    tmpMutableList.add(et)
                }
                extendedList[i] = tmpMutableList
            }
        }
    }

    private fun saveToJson(uri: Uri) {
        val modifiedNoteList = ArrayList<ListInfo>()

        mainList.forEachIndexed { i, element ->
            element.keyIndex = -1
            element.tag = 0
            modifiedNoteList.add(element)
        }
        extendedList.forEach { (key, list) ->
            if(list.size != 0) {
                modifiedNoteList[list[0].keyIndex].keyIndex = modifiedNoteList.size
                list.forEachIndexed { i, element ->
                    element.prevIndex = if(i != 0) modifiedNoteList.size - 1 else -1
                    element.nextIndex = if(i != list.size - 1) modifiedNoteList.size + 1 else -1
                    element.tag = 1
                    modifiedNoteList.add(element)
                }
            }
        }
        val modifiedNotes = Notes(notes.nextPage, modifiedNoteList)
        val json = gson.toJson(modifiedNotes).replace("\n", "")
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(json)
            }
        }
    }

    private fun saveToExtendJson(uri: Uri) {
        val extendedNoteList = ArrayList<ListInfo>()
        extendedList[currentPosition]?.forEachIndexed { i, element ->
            val copyElement = element
            copyElement.prevIndex = if(i != 0) extendedNoteList.size - 1 else -1
            copyElement.nextIndex = if(i != extendedList[currentPosition]!!.size - 1) extendedNoteList.size + 1 else -1
            extendedNoteList.add(copyElement)
        }

        val extendedNotes = Notes(extendedNoteList.size, extendedNoteList)
        val json = gson.toJson(extendedNotes).replace("\n", "")
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            OutputStreamWriter(outputStream).use { writer ->
                writer.write(json)
            }
        }
    }

    @Throws(IOException::class)
    private fun openPdfRenderer(uri: Uri) :PdfRenderer {
        val inputStream = contentResolver.openInputStream(uri)
        val file = File(cacheDir, fileHash)
        val outputStream = FileOutputStream(file)
        inputStream.use { input ->
            outputStream.use { output ->
                input?.copyTo(output)
            }
        }
        fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(fileDescriptor)
    }
}