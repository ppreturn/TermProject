package com.example.termproject

import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import androidx.viewpager.widget.ViewPager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class ExtendNoteViewActivity : AppCompatActivity(), onExtendButtonClickListener, FragmentInteractionListener {
    private lateinit var fileDescriptor: ParcelFileDescriptor

    private lateinit var notes : Notes
    private var mainListMap = mutableMapOf<Int, ListInfo>()
    private var extendedListMap = mutableMapOf<Int, MutableMap<Int, ListInfo>>()
    private var updateJob: Job? = null
    private var currentPosition: Int = 0
    private lateinit var adapter: ExtendNotePagerAdapter

    private var jsonUri: Uri? = null
    private var pdfUri: Uri? = null
    private var fileHash: String? = null
    private var currentPdfPage: Int = 0

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extend_note_view)

        val jsonUriString = intent.getStringExtra("jsonUri")
        val pdfUriString = intent.getStringExtra("pdfUri")
        fileHash = intent.getStringExtra("fileHash")
        currentPdfPage = intent.getIntExtra("currentPdfPage", 0)

        pdfUriString?.let {
            pdfUri = Uri.parse(it)
        }

        jsonUriString?.let {
            jsonUri = Uri.parse(it)
            loadNoteListFromFile(jsonUri!!)
            processNoteList()

            val unique = notes.noteMap[currentPosition]!!.unique

            val viewPager = findViewById<ViewPager>(R.id.viewPager)
            adapter = ExtendNotePagerAdapter(
                this,
                this,
                notes.nextPage,
                unique,
                openPdfRenderer(pdfUri!!).openPage(currentPdfPage),
                fileHash!!,
                extendedListMap[currentPdfPage] ?: emptyMap<Int, ListInfo>().toMutableMap(),
                viewPager
            )
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

        pressedStateUpdate()
    }

    override fun removeFragment(containerId: Int) {
        val fragment = supportFragmentManager.findFragmentById(containerId)
        if (fragment != null) {
            supportFragmentManager.beginTransaction().remove(fragment).commit()
            findViewById<View>(containerId).visibility = View.GONE
        }
    }

    private fun startUpdateJob() { // 폴더에 비트맵 저장 (Ext/HashFolder/0.png, 1.png, ...)
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                if(currentPosition < (extendedListMap[currentPdfPage]?.size ?: 0)) {
                    val noteDrawView = adapter.getDrawViewAt(currentPosition)
                    noteDrawView?.let {
                        val bitmap = it.getBitmap()
                        val saveDir = File(getExternalFilesDir(null), "$fileHash")
                        val unique = getExtendUniqueFromCurrentPosition()
                        Util.saveImage(bitmap, saveDir, "${unique}.png")
                    }
                    delay(200)
                }
            }
        }
    }

    /*********************************
     *
     * takes O(n) time
     *
     *********************************/

    private fun getExtendUniqueFromCurrentPosition() :Int {
        var cur = extendedListMap[currentPdfPage]!!.values.find { it.prevIndex == -1 } // notes.noteMap[notes.noteMap[currentPdfPage]!!.keyIndex]
        var cnt = 0
        while(cnt < currentPosition && notes.noteMap[cur!!.nextIndex] != null) {
            cur = notes.noteMap[cur!!.nextIndex]
            cnt += 1
        }
        return cur!!.unique
    }

    private fun setupViewSettings() {
        // Extend 버튼 클릭 리스너 설정
        findViewById<Button>(R.id.deleteButton).setOnClickListener { // 현재 페이지 삭제
            if(currentPosition == (extendedListMap[currentPdfPage]?.size ?: 0)) {
                return@setOnClickListener
            }
            val unique = getExtendUniqueFromCurrentPosition()
            val deleteElement = notes.noteMap[unique]

            if(deleteElement!!.prevIndex == -1 && deleteElement.nextIndex == -1) {
                notes.noteMap[deleteElement.keyIndex]!!.keyIndex = -1
            }
            if(deleteElement!!.prevIndex != -1) {
                notes.noteMap[deleteElement.prevIndex]!!.nextIndex = deleteElement.nextIndex
            }
            if(deleteElement!!.nextIndex != -1) {
                notes.noteMap[deleteElement.nextIndex]!!.prevIndex = deleteElement.prevIndex
            }

            val fileDir = File(getExternalFilesDir(null), "$fileHash")
            val file = File(fileDir, "${unique}.png")
            file.delete()

            notes.noteMap.remove(unique)
            extendedListMap[currentPdfPage]!!.remove(unique)

            saveToJson(jsonUri!!)
            loadNoteListFromFile(jsonUri!!)
            processNoteList()
            val viewPager = findViewById<ViewPager>(R.id.viewPager)
            val newAdapter = ExtendNotePagerAdapter(
                this,
                this,
                notes.nextPage,
                unique,
                openPdfRenderer(pdfUri!!).openPage(currentPdfPage),
                fileHash!!,
                extendedListMap[currentPdfPage] ?: emptyMap<Int, ListInfo>().toMutableMap(),
                viewPager
            )
            adapter = newAdapter
            viewPager.adapter = adapter
            viewPager.setCurrentItem(currentPosition, true)
            // adapter.notifyDataSetChanged()

        }

        findViewById<Button>(R.id.drawButton).setOnClickListener {
            if(!Paints.getEraseMode()) {
                showDrawSettingsFragment()
            } else {
                removeFragment(R.id.fragmentContainer)
            }
            Paints.setEraseMode(false)
            val drawView = adapter.getDrawViewAt(currentPosition)
            drawView?.setDrawMode()
            pressedStateUpdate()
        }

        findViewById<Button>(R.id.eraseButton).setOnClickListener {
            if(Paints.getEraseMode()) {
                showEraseSettingsFragment()
            } else {
                removeFragment(R.id.fragmentContainer)
            }
            Paints.setEraseMode(true)
            val drawView = adapter.getDrawViewAt(currentPosition)
            drawView?.setEraseMode()
            pressedStateUpdate()
        }
    }

    private fun pressedStateUpdate() {
        if(Paints.getEraseMode()) {
            findViewById<Button>(R.id.eraseButton).setSelected(true)
            findViewById<Button>(R.id.drawButton).setSelected(false)
        } else {
            findViewById<Button>(R.id.drawButton).setSelected(true)
            findViewById<Button>(R.id.eraseButton).setSelected(false);
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
        val constraintLayout = findViewById<ConstraintLayout>(R.id.extendNoteViewLayout)
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

    override fun onBackPressed() {
        if(isFragmentInContainer(R.id.fragmentContainer)) {
            removeFragment(R.id.fragmentContainer)
        } else {
            super.onBackPressed()
        }
    }

    private fun isFragmentInContainer(containerId: Int): Boolean {
        val fragment: Fragment? = supportFragmentManager.findFragmentById(containerId)
        return fragment != null
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
    private fun openPdfRenderer(uri: Uri) : PdfRenderer {
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

    override fun modifyNotesAndSaveJsonFile(
        nextPage: Int,
        extendListMap: MutableMap<Int, ListInfo>
    ) {
        notes.nextPage = nextPage
        extendedListMap[currentPdfPage] = extendListMap
        saveToJson(jsonUri!!)
        loadNoteListFromFile(jsonUri!!)
        processNoteList()
        adapter.notifyDataSetChanged()
    }
}