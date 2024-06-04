package com.example.termproject

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class DrawSettingsFragment : Fragment(), OnColorClickListener {
    var onSettingsChangeListener: ((Float, Int) -> Unit)? = null
    private var selectedColor: Int = Color.BLACK
    private var strokeWidth: Float = 8f  // Default stroke width
    private lateinit var strokeWidthTextView: TextView

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
        val view = inflater.inflate(R.layout.fragment_draw_settings, container, false)

        val seekBar: SeekBar = view.findViewById(R.id.seekBar)
        val colorRecyclerView: RecyclerView = view.findViewById(R.id.colorRecyclerView)
        strokeWidthTextView = view.findViewById(R.id.strokeWidthTextView)
        val closeButton: ImageButton = view.findViewById(R.id.closeButton)

        // Get the saved state from arguments
        strokeWidth = Paints.getDrawPaint().strokeWidth
        selectedColor = Paints.getDrawPaint().color

        strokeWidthTextView.text = strokeWidth.toInt().toString()

        seekBar.progress = strokeWidth.toInt()  // Set the initial progress

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                strokeWidth = progress.toFloat()  // Save the stroke width
                strokeWidthTextView.text = progress.toString()
                onSettingsChangeListener?.invoke(strokeWidth, selectedColor)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        colorRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        colorRecyclerView.adapter = ColorAdapter(requireContext(), this)

        closeButton.setOnClickListener {
            listener?.removeFragment(R.id.fragmentContainer)
        }

        return view
    }

    override fun onColorClick(color: Int) {
        selectedColor = color
        onSettingsChangeListener?.invoke(strokeWidth, selectedColor)
    }
}
