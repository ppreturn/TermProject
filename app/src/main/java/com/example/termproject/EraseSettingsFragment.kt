package com.example.termproject

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment

class EraseSettingsFragment : Fragment() {
    var onSettingsChangeListener: ((Float) -> Unit)? = null
    private var eraserWidth: Float = 8f  // Default eraser width
    private lateinit var eraserWidthTextView: TextView

    private var listener: FragmentInteractionListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FragmentInteractionListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement FragmentInteractionListener")
        }
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_erase_settings, container, false)

        val seekBar: SeekBar = view.findViewById(R.id.seekBar)
        eraserWidthTextView = view.findViewById(R.id.eraserWidthTextView)
        val closeButton: ImageButton = view.findViewById(R.id.closeButton)

        eraserWidth = Paints.getErasePaint().strokeWidth

        eraserWidthTextView.text = eraserWidth.toInt().toString()

        seekBar.progress = eraserWidth.toInt()  // Set the initial progress

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                eraserWidth = progress.toFloat()  // Save the eraser width
                eraserWidthTextView.text = progress.toString()
                onSettingsChangeListener?.invoke(eraserWidth)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        closeButton.setOnClickListener {
            listener?.removeFragment(R.id.fragmentContainer)
        }

        return view
    }
}
