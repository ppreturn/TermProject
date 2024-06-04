package com.example.termproject

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import kotlin.properties.Delegates

class ColorAdapter(
    private val context: Context,
    private val listener: OnColorClickListener
) : RecyclerView.Adapter<ColorAdapter.ColorViewHolder>() {

    private val colors = listOf(
        Color.RED, Color.parseColor("#FFA500"), Color.YELLOW,
        Color.GREEN, Color.BLUE, Color.parseColor("#4B0082"), Color.parseColor("#800080"),
        Color.BLACK
    )

    private var selectedPosition by Delegates.observable(-1) { _, oldPos, newPos ->
        notifyItemChanged(oldPos)
        notifyItemChanged(newPos)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.color_item, parent, false)
        return ColorViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorViewHolder, position: Int) {
        holder.bind(colors[position], position == selectedPosition)
    }

    override fun getItemCount(): Int {
        return colors.size
    }

    inner class ColorViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val colorView: ImageView = itemView.findViewById(R.id.colorView)
        private val checkView: ImageView = itemView.findViewById(R.id.checkView)

        init {
            itemView.setOnClickListener {
                if (selectedPosition != adapterPosition) {
                    selectedPosition = adapterPosition
                    val color = colors[selectedPosition]
                    listener.onColorClick(color)
                } else {
                    selectedPosition = -1
                }
            }
        }

        fun bind(color: Int, isSelected: Boolean) {
            colorView.setBackgroundColor(color)
            if (isSelected) {
                checkView.visibility = View.VISIBLE
                checkView.setColorFilter(getComplementaryColor(color))
            } else {
                checkView.visibility = View.GONE
            }
        }

        private fun getComplementaryColor(color: Int): Int {
            val r = 255 - Color.red(color)
            val g = 255 - Color.green(color)
            val b = 255 - Color.blue(color)
            return Color.rgb(r, g, b)
        }
    }
}
