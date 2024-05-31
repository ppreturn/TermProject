package com.example.termproject

import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager.widget.ViewPager
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader

class ExtendNoteViewActivity : AppCompatActivity() {
    private lateinit var fileDescriptor: ParcelFileDescriptor

    private lateinit var notes : Notes
    private var extendedList = mutableListOf<ListInfo>()
    private var currentPosition: Int = 0
    private lateinit var adapter: ExtendNotePagerAdapter

    private var jsonUri: Uri? = null
    private var pdfUri: Uri? = null
    private var fileHash: String? = null

    private var isEraseMode = false

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_extend_note_view)
        // 필요한 초기화 작업을 여기에 추가하세요.

        val jsonUriString = intent.getStringExtra("extendJsonUri")
        val pdfUriString = intent.getStringExtra("pdfUri")
        fileHash = intent.getStringExtra("fileHash")

        pdfUriString?.let {
            pdfUri = Uri.parse(it)
        }

        jsonUriString?.let {
            jsonUri = Uri.parse(it)
            loadNoteListFromFile(jsonUri!!)

            val viewPager = findViewById<ViewPager>(R.id.viewPager)
            adapter = ExtendNotePagerAdapter(this, openPdfRenderer(pdfUri!!), fileHash!!, extendedList, viewPager)
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
        }

        findViewById<Button>(R.id.deleteButton).setOnClickListener {

        }

        findViewById<Button>(R.id.saveButton).setOnClickListener {

        }

        findViewById<Button>(R.id.drawButton).setOnClickListener {

        }

        findViewById<Button>(R.id.eraseButton).setOnClickListener {

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
            extendedList = notes.noteList
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
}