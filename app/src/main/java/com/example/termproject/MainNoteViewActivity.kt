package com.example.termproject

import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
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

class MainNoteViewActivity : AppCompatActivity(), FragmentInteractionListener {
    private val OPEN_EXTEND_NOTE_VIEW_REQUEST_CODE = 1

    private lateinit var fileDescriptor: ParcelFileDescriptor

    private lateinit var notes : Notes
    private var mainListMap = mutableMapOf<Int, ListInfo>()
    private var extendedListMap = mutableMapOf<Int, MutableMap<Int, ListInfo>>()
    private var updateJob: Job? = null
    private var currentPosition: Int = 0
    private lateinit var adapter: MainNotePagerAdapter

    private var jsonUri: Uri? = null
    private var pdfUri: Uri? = null
    private var fileHash: String? = null

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_note_view)

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
            adapter = MainNotePagerAdapter(this, openPdfRenderer(pdfUri!!), fileHash!!, mainListMap, viewPager)
            viewPager.adapter = adapter

            viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
                override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

                override fun onPageSelected(position: Int) {
                    currentPosition = position
                    drawViewUpdate(position)
                }

                override fun onPageScrollStateChanged(state: Int) {}


            })

            startUpdateJob()
        }
        setupViewSettings()

        if(Paints.getEraseMode()) findViewById<Button>(R.id.eraseButton).setPressed(true)
        else findViewById<Button>(R.id.drawButton).setPressed(true)
    }

    override fun removeFragment(containerId: Int) {
        val fragment = supportFragmentManager.findFragmentById(containerId)
        if (fragment != null) {
            supportFragmentManager.beginTransaction().remove(fragment).commit()
            findViewById<View>(containerId).visibility = View.GONE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == OPEN_EXTEND_NOTE_VIEW_REQUEST_CODE && resultCode == RESULT_OK) {
            loadNoteListFromFile(jsonUri!!)
            processNoteList()
        }
    }

    private fun startUpdateJob() { // 폴더에 비트맵 저장 (Ext/HashFolder/0.png, 1.png, ...)
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val noteDrawView = adapter.getDrawViewAt(currentPosition)
                noteDrawView?.let {
                    val bitmap = it.getBitmap()
                    val saveDir = File(getExternalFilesDir(null), "$fileHash")
                    Util.saveImage(bitmap, saveDir, "${notes.noteMap[currentPosition]?.unique}.png")
                }
                delay(200)
            }
        }
    }

    private fun setupViewSettings() {
        // Extend 버튼 클릭 리스너 설정
        findViewById<Button>(R.id.extendButton).setOnClickListener {
            val intent = Intent(this, ExtendNoteViewActivity::class.java).apply {
                putExtra("jsonUri", jsonUri.toString())
                putExtra("pdfUri", pdfUri.toString()) // TODO :: maybe cause error
                putExtra("fileHash", fileHash)
                putExtra("currentPdfPage", currentPosition)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivityForResult(intent, OPEN_EXTEND_NOTE_VIEW_REQUEST_CODE)
        }

        findViewById<Button>(R.id.drawButton).setOnClickListener {
            if(!Paints.getEraseMode()) {
                showDrawSettingsFragment()
            }
            Paints.setEraseMode(false)
            val drawView = adapter.getDrawViewAt(currentPosition)
            drawView?.setDrawMode()
        }

        findViewById<Button>(R.id.eraseButton).setOnClickListener {
            if(Paints.getEraseMode()) {
                showEraseSettingsFragment()
            }
            Paints.setEraseMode(true)
            val drawView = adapter.getDrawViewAt(currentPosition)
            drawView?.setEraseMode()
        }
    }

    private fun showDrawSettingsFragment() {
        val fragment = DrawSettingsFragment()
        fragment.onSettingsChangeListener = { strokeWidth, strokeColor ->
            Paints.setDrawPaint(strokeColor, strokeWidth)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        findViewById<View>(R.id.fragmentContainer).visibility = View.VISIBLE
    }

    private fun showEraseSettingsFragment() {
        val fragment = EraseSettingsFragment()
        fragment.onSettingsChangeListener = { eraserWidth ->
            Paints.setErasePaint(eraserWidth)
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        findViewById<View>(R.id.fragmentContainer).visibility = View.VISIBLE
    }

    private fun adjustFragmentContainerConstraints(drawButtonId: Int) {
        val constraintLayout = findViewById<ConstraintLayout>(R.id.mainNoteViewLayout)
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        constraintSet.clear(R.id.fragmentContainer, ConstraintSet.BOTTOM)
        constraintSet.clear(R.id.fragmentContainer, ConstraintSet.TOP)
        constraintSet.clear(R.id.fragmentContainer, ConstraintSet.START)
        constraintSet.clear(R.id.fragmentContainer, ConstraintSet.END)

        constraintSet.connect(R.id.fragmentContainer, ConstraintSet.BOTTOM, drawButtonId, ConstraintSet.TOP)
        constraintSet.connect(R.id.fragmentContainer, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        constraintSet.connect(R.id.fragmentContainer, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)

        constraintSet.applyTo(constraintLayout)
    }

    private fun drawViewUpdate(position: Int) {
        val drawView = adapter.getDrawViewAt(position)
        if (Paints.getEraseMode()) {
            drawView?.setEraseMode()
        } else {
            drawView?.setDrawMode()
        }
    }
    override fun onResume() {
        super.onResume()

        loadNoteListFromFile(jsonUri!!)
        processNoteList()
        startUpdateJob()


        removeFragment(R.id.fragmentContainer)
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
        mainListMap = mutableMapOf<Int, ListInfo>()
        extendedListMap = mutableMapOf<Int, MutableMap<Int, ListInfo>>()

        // Start element 찾기
        var startElement = notes.noteMap.values.find { it.tag == 0 && it.prevIndex == -1 }

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
        mainListMap[startElement.unique] = startElement
        var curElement = startElement
        while (curElement?.nextIndex != -1) {
            val nextElement = notes.noteMap[curElement?.nextIndex]
            mainListMap[nextElement!!.unique] = nextElement
            curElement = nextElement
        }

        // extendedList 초기화 및 요소 추가
        mainListMap.values.forEachIndexed { i, element ->
            if (element.keyIndex != -1) {
                var extendedElement = notes.noteMap[element.keyIndex]
                val tmpMutableMap = mutableMapOf<Int, ListInfo>(extendedElement!!.unique to extendedElement)
                while (extendedElement!!.nextIndex != -1) {
                    val nextElement = notes.noteMap[extendedElement?.nextIndex]
                    tmpMutableMap.put(nextElement!!.unique, nextElement)
                    extendedElement = nextElement
                }
                extendedListMap[i] = tmpMutableMap
            }
        }
    }

    private fun saveToJson(uri: Uri) {
        val modifiedNoteMap = mutableMapOf<Int, ListInfo>()


        mainListMap.forEach { (i, element) ->
            element.keyIndex = -1
            modifiedNoteMap.put(i, element)
        }
        extendedListMap.forEach { (key, map) ->
            if(map.size != 0) {
                map.forEach { (i, element) ->
                    element.keyIndex = key
                    if(element.prevIndex == -1) modifiedNoteMap[element.keyIndex]!!.keyIndex = i
                    modifiedNoteMap.put(i, element)
                }
            }
        }
        val modifiedNotes = Notes(notes.nextPage, modifiedNoteMap)
        val json = gson.toJson(modifiedNotes).replace("\n", "")
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