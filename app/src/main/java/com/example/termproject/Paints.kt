package com.example.termproject

import android.app.Application
import android.graphics.Paint
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode

class Paints : Application() {
    private var drawPaint: Paint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 5f
        style = Paint.Style.STROKE
        xfermode = null
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private var erasePaint: Paint = Paint().apply {
        color = Color.TRANSPARENT
        strokeWidth = 50f
        style = Paint.Style.STROKE
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    private var eraseMode: Boolean = false
    companion object {
        private lateinit var instance: Paints

        fun getDrawPaint(): Paint {
            return instance.drawPaint
        }
        fun getErasePaint(): Paint {
            return instance.erasePaint
        }
        fun getEraseMode(): Boolean {
            return instance.eraseMode
        }

        fun setEraseMode(mode: Boolean) {
            instance.eraseMode = mode
        }

        fun setDrawPaint(color: Int, width: Float) {
            instance.drawPaint.color = color
            instance.drawPaint.strokeWidth = width
        }

        fun setErasePaint(width: Float) {
            instance.erasePaint.strokeWidth = width
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}