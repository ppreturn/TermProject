package com.example.termproject

import android.Manifest.permission

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), MyAdapter.OnItemClickListener {
    private val OPEN_FILE_REQUEST_CODE = 1
    private var notes: Notes? = null
    private var openedUri : Uri? = null

    private val gson: Gson = GsonBuilder().disableHtmlEscaping().create()

    val tableName = "files"
    var fileList:MutableList<Pair<String,String>> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val database: SQLiteDatabase?=openOrCreateDatabase("pdffile", MODE_PRIVATE,null)
        if (database!=null)
        {
            database?.execSQL("create table if not exists ${tableName}"+
                    "( id integer PRIMARY KEY autoincrement, "+
                    "filename text, "+
                    "uri text, " +
                    "rodate datetime) ")
        }

        val cursor=database?.rawQuery("select filename,uri,rodate "+
                "from ${tableName} "+
                "order by rodate desc",null)
        for(index in 0 until cursor!!.count) {
            cursor.moveToNext()
            val fileName = cursor.getString(0)
            val fileUri = cursor.getString(1)
            fileList.add(Pair(fileName, fileUri))
        }

        val recentopenedRv: RecyclerView = findViewById(R.id.recentOpenedRv)

        recentopenedRv.layoutManager= LinearLayoutManager(this)
        recentopenedRv.adapter = MyAdapter(fileList, this)
        recentopenedRv.addItemDecoration(
            DividerItemDecoration(
                this, LinearLayoutManager.VERTICAL
            )
        )

        val openFileButton: Button = findViewById(R.id.openFileButton)

        openFileButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/pdf"
                // Persistable 권한 요청을 위한 플래그 설정
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, OPEN_FILE_REQUEST_CODE)
        }
    }

    override fun onResume() {
        super.onResume()
        val recentopenedRv: RecyclerView =findViewById(R.id.recentOpenedRv)
        val database:SQLiteDatabase?=openOrCreateDatabase("pdffile", MODE_PRIVATE,null)
        fileList.clear()
        val cursor=database?.rawQuery("select filename,uri "+
                "from files "+
                "order by rodate desc",null)
        for(index in 0 until cursor!!.count) {
            cursor.moveToNext()
            val fileName = cursor.getString(0)
            val fileUri = cursor.getString(1)
            fileList.add(Pair(fileName, fileUri))
        }

        recentopenedRv.layoutManager= LinearLayoutManager(this)
        recentopenedRv.adapter = MyAdapter(fileList, this)
        recentopenedRv.addItemDecoration(
            DividerItemDecoration(
                this, LinearLayoutManager.VERTICAL
            )
        )
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val database: SQLiteDatabase? = openOrCreateDatabase("pdffile", MODE_PRIVATE,null)
        if (requestCode == OPEN_FILE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            openedUri = data?.data
            contentResolver.takePersistableUriPermission(openedUri!!, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val cursor: Cursor? = openedUri?.let { contentResolver.query(it,null,null,null,null) }
            val nameIndex = cursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            cursor?.moveToFirst()
            var fileName = nameIndex?.let { cursor?.getString(it) }
            if(Pair(fileName, openedUri.toString()) in fileList){
                database?.rawQuery("update files SET rodate=(select datetime('now','localtime')) where uri='${openedUri}'",null)
            }
            else{
                database?.execSQL("insert into files(filename,uri,rodate) values"+
                        "('${fileName}','${openedUri}',(select datetime('now','localtime')))")
                fileList.add(Pair(fileName.toString(), openedUri.toString()))
            }
            openedUri?.let {
                notes = Notes(0, emptyMap<Int, ListInfo>().toMutableMap())
                handlePdfFile(it)
                saveJson()
                openNoteViewActivity()
            }
        }
    }

    private fun calculateFileHash(uri: Uri): String {
        // 파일의 내용을 읽기
        val inputStream = contentResolver.openInputStream(uri)
        val buffer = inputStream?.readBytes()
        inputStream?.close()

        // 파일이 추가된 날짜를 문자열로 변환 (여기서는 현재 시간을 사용)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val dateAdded = dateFormat.format(Date())

        // 해시를 계산할 데이터 결합
        val dataToHash = buffer?.plus(dateAdded.toByteArray())

        // 해시 계산
        val md = MessageDigest.getInstance("MD5")
        val hashBytes = md.digest(dataToHash)

        // 해시값을 16진수 문자열로 변환하여 반환
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun handlePdfFile(uri: Uri) {
        val fileHash = calculateFileHash(uri)
        if(File(getExternalFilesDir(null), "$fileHash.json").exists()) {
            handleJsonFile(File(getExternalFilesDir(null)
                , "$fileHash.json"
            ).toUri())
            return
        }
        contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)
            val pageCount = pdfRenderer.pageCount

            notes?.nextPage = pageCount
            for (i in 0 until pageCount) {
                val jsonData = ListInfo(
                    unique = i,
                    nextIndex = -1,
                    prevIndex = if (notes?.noteMap!!.isNotEmpty()) notes?.noteMap!!.size - 1 else -1,
                    keyIndex = -1,
                    tag = 0
                )
                if (notes?.noteMap!!.isNotEmpty()) {
                    notes?.noteMap!![i - 1]?.nextIndex = notes?.noteMap!!.size
                }

                notes?.noteMap!![i] = jsonData
            }
        }
    }

    private fun handleJsonFile(uri: Uri) {
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

    private fun saveJson() {
        // 파일 이름 및 경로 설정
        val fileHash = calculateFileHash(openedUri!!)
        val fileName = "$fileHash.json"
        val file = File(getExternalFilesDir(null), fileName)
        val json = gson.toJson(notes).replace("\n", "")

        FileOutputStream(file).use {
            it.write(json.toByteArray())
        }
        // 파일 저장 여부 확인
        if (file.exists()) {
            Log.d("MainActivity", "File saved successfully: ${file.absolutePath}")
        } else {
            Log.e("MainActivity", "File not saved")
        }
    }

    private fun openNoteViewActivity() {
        // 파일 이름 및 경로 설정
        val fileHash = calculateFileHash(openedUri!!)
        val fileName = "$fileHash.json"
        val file = File(getExternalFilesDir(null), fileName)

        Log.d("json file open", "File saved successfully: ${file.absolutePath}")

        if (!file.exists()) {
            Log.e("MainActivity", "File not found: ${file.absolutePath}")
            return
        }
        // 파일 URI 가져오기
        val jsonUri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", file)
        val intent = Intent(this, MainNoteViewActivity::class.java).apply {
            putExtra("jsonUri", jsonUri.toString())
            putExtra("pdfUri", openedUri.toString())
            putExtra("fileHash", fileHash)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(intent)
    }

    override fun onItemClick(data: Pair<String,String>) {
        openedUri = data.second.toUri()
        notes = Notes(0, emptyMap<Int, ListInfo>().toMutableMap())
        handlePdfFile(openedUri!!)
        saveJson()
        openNoteViewActivity()
    }
}