package com.example.termproject

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.termproject.databinding.ActivityMainBinding
import com.example.termproject.databinding.ItemDataBinding

class MyViewHolder(val binding: ItemDataBinding): RecyclerView.ViewHolder(binding.root)

class MyAdapter(val datas:MutableList<Pair<String,String>>, val listener: OnItemClickListener) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(data: Pair<String,String>)
    }
    override fun getItemCount(): Int = datas.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder = MyViewHolder(
        ItemDataBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int){
        val binding = (holder as MyViewHolder).binding

        binding.itemData.text=datas[position].first
        binding.itemRoot.setOnClickListener{
            listener.onItemClick(datas[position])
        }
    }
}