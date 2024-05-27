package com.example.termproject

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

data class JsonData(
    val backgroundBase64: String,
    var noteBase64: String,
    var nextIndex: Int,
    var prevIndex: Int,
    var keyIndex: Int,
    var tag: Int
)

class MainActivity : AppCompatActivity() {
    private val OPEN_FILE_REQUEST_CODE = 1
    private val noteList = mutableListOf<JsonData>()
    private var openedUri : Uri? = null

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openFileButton: Button = findViewById(R.id.openFileButton)
        val saveButton: Button = findViewById(R.id.saveButton)

        openFileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            startActivityForResult(intent, OPEN_FILE_REQUEST_CODE)
        }

        saveButton.setOnClickListener {
            saveNoteListToFile()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            openedUri = data?.data
            openedUri?.let {
                val fileType = contentResolver.getType(it)
                if (fileType == "application/pdf") {
                    handlePdfFile(it)
                    saveNoteListToFile()
                    openNoteViewActivity()
                    noteList.clear()
                } else {
                    Toast.makeText(this, "지원하지 않는 파일 형식입니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null

        if (uri.scheme.equals("content")) {
            val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (columnIndex != -1) {
                        fileName = cursor.getString(columnIndex)
                    }
                }
            } finally {
                cursor?.close()
            }
        }

        if (fileName == null) {
            fileName = uri.path
            val cut = fileName?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                fileName = fileName?.substring(cut + 1)
            }
        }

        fileName?.let {
            val dotIndex = it.lastIndexOf('.')
            if (dotIndex > 0) {
                fileName = it.substring(0, dotIndex)
            }
        }

        return fileName
    }

    private fun handlePdfFile(uri: Uri) {
        if(File(cacheDir, getFileNameFromUri(this, openedUri!!).toString() + ".json").exists()) {
            handleJsonFile(File(cacheDir, getFileNameFromUri(this, openedUri!!).toString() + ".json").toUri())
            return
        }
        contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)
            val pageCount = pdfRenderer.pageCount

            for (i in 0 until pageCount) {
                val page = pdfRenderer.openPage(i)
                val width = page.width
                val height = page.height
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                val backgroundBase64 = Util.encodeBase64(bitmap)
                val noteBase64 = Util.encodeBase64(Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))

                val jsonData = JsonData(
                    backgroundBase64 = backgroundBase64,
                    noteBase64 = noteBase64,
                    nextIndex = -1,
                    prevIndex = if (noteList.isNotEmpty()) noteList.size - 1 else -1,
                    keyIndex = -1,
                    tag = 0
                )

                if (noteList.isNotEmpty()) {
                    noteList.last().nextIndex = noteList.size
                }

                noteList.add(jsonData)
            }
        }
    }

    private fun handleJsonFile(uri: Uri) {
        contentResolver.openInputStream(uri)?.use { inputStream ->
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<JsonData>>() {}.type
            val jsonDataList: List<JsonData> = gson.fromJson(reader, type)
            noteList.addAll(jsonDataList)
        }
    }

    private fun saveNoteListToFile() {
        // 파일 이름 및 경로 설정
        val fileName = getFileNameFromUri(this, openedUri!!).toString() + ".json"
        val file = File(cacheDir, fileName)
        val json = gson.toJson(noteList)

        FileOutputStream(file).use {
            it.write(json.toByteArray())
        }
        // 파일 저장 여부 확인
        if (file.exists()) {
            Log.d("MainActivity", "File saved successfully: ${file.absolutePath}")
            Toast.makeText(this, "파일이 저장되었습니다: $fileName", Toast.LENGTH_SHORT).show()
        } else {
            Log.e("MainActivity", "File not saved")
        }
    }

    private fun openNoteViewActivity() {
        // 파일 이름 및 경로 설정
        val fileName = getFileNameFromUri(this, openedUri!!).toString() + ".json"
        val file = File(cacheDir, fileName)
        Log.d("넘겨 주는 파일 경로", file.absolutePath)
        if (!file.exists()) {
            Log.e("MainActivity", "File not found: ${file.absolutePath}")
            return
        }
        // 파일 URI 가져오기
        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
        val intent = Intent(this, MainNoteViewActivity::class.java).apply {
            putExtra("noteListUri", uri.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }
}