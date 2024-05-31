package com.example.termproject

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

    fun createA4WhiteBitmap(direction: Int, dpi: Int = 300): Bitmap {
        val width = (210 * dpi / 25.4).toInt()
        val height = (297 * dpi / 25.4).toInt()

        val bitmap = if(direction == 2) {
            Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        bitmap.eraseColor(Color.WHITE)
        return bitmap
    }

}