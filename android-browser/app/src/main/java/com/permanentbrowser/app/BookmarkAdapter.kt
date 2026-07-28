package com.permanentbrowser.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * Adapter for the flat bookmark list below the folder-chip strip.
 *
 * Tap = open the bookmark (delegated to [onClick]); long-press = show a
 * context menu of actions (Move / Edit title / Delete), delegated to
 * [onLongClick]. The activity owns the menu UI so this adapter stays
 * simple.
 *
 * The [items] list is public and the [moveItem] helper exists so that
 * [androidx.recyclerview.widget.ItemTouchHelper] in [BookmarksActivity]
 * can reorder the list in-place during a drag, then persist the new
 * `sortOrder` values when the drag ends.
 *
 * Bookmark deletion and editing are allowed — bookmarks are user-curated,
 * NOT browsing history. Only the History* family is permanent.
 */
class BookmarkAdapter(
    private val onClick: (Bookmark) -> Unit,
    private val onLongClick: (Bookmark) -> Unit
) : RecyclerView.Adapter<BookmarkAdapter.VH>() {

    val items = mutableListOf<Bookmark>()

    fun submit(list: List<Bookmark>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /**
     * Moves the item at [fromPos] to [toPos] in the local list and fires
     * [notifyItemMoved] so the RecyclerView animates the swap. The
     * underlying `sort_order` column is NOT updated here — the activity
     * does that in `clearView` once the drag gesture ends, so we don't
     * thrash the database during an in-flight drag.
     */
    fun moveItem(fromPos: Int, toPos: Int) {
        if (fromPos == toPos) return
        if (fromPos < 0 || toPos < 0) return
        if (fromPos >= items.size || toPos >= items.size) return
        val item = items.removeAt(fromPos)
        items.add(toPos, item)
        notifyItemMoved(fromPos, toPos)
    }

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val title: TextView = root.findViewById(R.id.bookmark_title)
        val url: TextView = root.findViewById(R.id.bookmark_url)
        val dragHandle: ImageView = root.findViewById(R.id.bookmark_drag_handle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bookmark, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.title.text = item.title.ifBlank { item.url }
        // If the bookmark is in a folder, prefix the URL line with the folder
        // name so the user can tell at a glance which folder it belongs to
        // (especially useful when the "All" chip is selected).
        holder.url.text = if (item.folder.isEmpty()) {
            item.url
        } else {
            "${item.folder}  -  ${item.url}"
        }
        holder.root.setOnClickListener { onClick(item) }
        holder.root.setOnLongClickListener {
            onLongClick(item)
            true
        }
        holder.dragHandle.contentDescription =
            holder.root.context.getString(R.string.bookmark_drag_hint)
    }

    override fun getItemCount(): Int = items.size
}
