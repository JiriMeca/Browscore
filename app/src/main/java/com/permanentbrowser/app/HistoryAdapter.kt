package com.permanentbrowser.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Chrome-style grouped history adapter.
 *
 * Displays history entries grouped by day, with a white card date header
 * separating each day's entries. Under each entry, only the time (HH:MM)
 * is shown — no date, no seconds.
 */
class HistoryAdapter(
    private val onClick: (HistoryEntry) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Sealed list item: either a date header or a history entry. */
    private sealed class ListItem {
        data class DateHeader(val label: String) : ListItem()
        data class HistoryItem(val entry: HistoryEntry) : ListItem()
    }

    private val items = mutableListOf<ListItem>()

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_HISTORY_ITEM = 1
    }

    // --- Date formatters ---
    private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val todayFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    fun submit(list: List<HistoryEntry>) {
        items.clear()
        if (list.isEmpty()) {
            notifyDataSetChanged()
            return
        }

        val cal = Calendar.getInstance()
        val today = startOfDay(cal)
        cal.add(Calendar.DAY_OF_MONTH, -1)
        val yesterday = startOfDay(cal)

        var lastDayLabel: String? = null

        for (entry in list) {
            val entryCal = Calendar.getInstance().apply { timeInMillis = entry.visitedAt }
            val entryDay = startOfDay(entryCal)

            val dayLabel = when {
                entryDay == today -> "Today"
                entryDay == yesterday -> "Yesterday"
                else -> {
                    // Format like "Jun 15, 2025" or "Dec 3, 2024"
                    val df = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                    df.format(Date(entry.visitedAt))
                }
            }

            // Insert a date header when the day changes
            if (dayLabel != lastDayLabel) {
                items.add(ListItem.DateHeader(dayLabel))
                lastDayLabel = dayLabel
            }

            items.add(ListItem.HistoryItem(entry))
        }

        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ListItem.DateHeader -> TYPE_DATE_HEADER
            is ListItem.HistoryItem -> TYPE_HISTORY_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_history_date_header, parent, false)
                DateHeaderVH(v)
            }
            else -> {
                val v = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_history, parent, false)
                HistoryItemVH(v)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ListItem.DateHeader -> (holder as DateHeaderVH).bind(item.label)
            is ListItem.HistoryItem -> (holder as HistoryItemVH).bind(item.entry)
        }
    }

    override fun getItemCount(): Int = items.size

    /** ViewHolder for date header row. */
    class DateHeaderVH(val root: View) : RecyclerView.ViewHolder(root) {
        val label: TextView = root.findViewById(R.id.date_header_text)
        fun bind(text: String) { label.text = text }
    }

    /** ViewHolder for history entry row. */
    inner class HistoryItemVH(val root: View) : RecyclerView.ViewHolder(root) {
        val title: TextView = root.findViewById(R.id.history_title)
        val url: TextView = root.findViewById(R.id.history_url)
        val time: TextView = root.findViewById(R.id.history_time)

        fun bind(entry: HistoryEntry) {
            title.text = entry.title.ifBlank { entry.url }
            url.text = entry.url
            time.text = timeFmt.format(Date(entry.visitedAt))
            root.setOnClickListener { onClick(entry) }
        }
    }

    /** Returns a Calendar set to the start of day (midnight) for the given Calendar's date. */
    private fun startOfDay(cal: Calendar): Long {
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, cal.get(Calendar.YEAR))
            set(Calendar.MONTH, cal.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, cal.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
