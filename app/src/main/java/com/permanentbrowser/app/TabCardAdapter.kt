package com.permanentbrowser.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for the tab-switcher grid. Each card shows a tab's title + URL and
 * a close button. Tapping the card switches to that tab.
 */
class TabCardAdapter(
    private val onClick: (Tab) -> Unit,
    private val onClose: (Tab) -> Unit
) : RecyclerView.Adapter<TabCardAdapter.VH>() {

    private val items = mutableListOf<Tab>()

    fun submit(list: List<Tab>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val title: TextView = root.findViewById(R.id.tab_title)
        val url: TextView = root.findViewById(R.id.tab_url)
        val close: ImageButton = root.findViewById(R.id.tab_close)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab_card, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val tab = items[position]
        holder.title.text = tab.title.ifBlank { holder.root.context.getString(R.string.new_tab_blank) }
        holder.url.text = tab.url
        holder.root.setOnClickListener { onClick(tab) }
        holder.close.setOnClickListener { onClose(tab) }
    }

    override fun getItemCount(): Int = items.size
}
