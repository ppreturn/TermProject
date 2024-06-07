package com.example.termproject

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
data class ListInfo(
    val unique: Int,
    var nextIndex: Int,
    var prevIndex: Int,
    var keyIndex: Int,
    var tag: Int
)
data class Notes(
    var nextPage: Int,
    val noteMap: MutableMap<Int, ListInfo>
)
object Util {
    const val a4Width = 210 // A4 용지 너비 (픽셀, 300dpi 기준)
    const val a4Height = 297 // A4 용지 높이 (픽셀, 300dpi 기준)

    fun saveImage(bitmap: Bitmap, fileDir: File, fileName: String) {
        if (!fileDir.exists()) {
            fileDir.mkdirs()
        }
        val file = File(fileDir, fileName)
        var fileOutputStream: FileOutputStream? = null
        try {
            fileOutputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                fileOutputStream?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}