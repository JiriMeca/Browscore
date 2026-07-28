package com.permanentbrowser.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter that displays autocomplete suggestions below the omnibox.
 *
 * Each row shows:
 *   - An icon indicating the suggestion type (bookmark / history / search)
 *   - The suggestion title (bold)
 *   - A subtitle (URL for bookmarks/history, search engine name for search suggestions)
 *
 * Clicking a row calls [onSuggestionClick].
 */
class SuggestionAdapter(
    private val onSuggestionClick: (SuggestionItem) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.ViewHolder>() {

    private val items = mutableListOf<SuggestionItem>()

    fun submit(newItems: List<SuggestionItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_suggestion, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.suggestion_icon)
        private val title: TextView = itemView.findViewById(R.id.suggestion_title)
        private val subtitle: TextView = itemView.findViewById(R.id.suggestion_subtitle)

        fun bind(item: SuggestionItem) {
            title.text = item.title
            subtitle.text = item.subtitle
            subtitle.visibility = if (item.subtitle.isNotBlank()) View.VISIBLE else View.GONE

            icon.setImageResource(
                when (item.type) {
                    SuggestionType.BOOKMARK -> R.drawable.ic_bookmark
                    SuggestionType.HISTORY -> R.drawable.ic_history
                    SuggestionType.SEARCH -> R.drawable.ic_search
                }
            )

            // Color the bookmark icon with the accent color
            val tint = if (item.type == SuggestionType.BOOKMARK) {
                itemView.context.getColor(R.color.accent_blue)
            } else {
                itemView.context.getColor(R.color.icon_default)
            }
            icon.setColorFilter(tint)

            itemView.setOnClickListener { onSuggestionClick(item) }
        }
    }
}
