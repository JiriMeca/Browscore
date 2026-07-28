package com.permanentbrowser.app

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryAdapter(
    private val onClick: (HistoryEntry) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.VH>() {

    private val items = mutableListOf<HistoryEntry>()
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun submit(list: List<HistoryEntry>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val root: android.view.View) : RecyclerView.ViewHolder(root) {
        val title: TextView = root.findViewById(R.id.history_title)
        val url: TextView = root.findViewById(R.id.history_url)
        val time: TextView = root.findViewById(R.id.history_time)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_history, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title.ifBlank { item.url }
        holder.url.text = item.url
        holder.time.text = timeFmt.format(Date(item.visitedAt))
        holder.root.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = items.size
}
