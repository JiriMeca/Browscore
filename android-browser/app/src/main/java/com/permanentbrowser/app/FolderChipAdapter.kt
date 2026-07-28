package com.permanentbrowser.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip

/**
 * Adapter for the horizontal folder-chip strip above the bookmark list.
 *
 * Renders one special "All" chip (the always-present first chip), then one
 * chip per distinct user-defined folder, then a trailing "+" chip that
 * invokes [onAddFolder] to create a new folder.
 *
 * The currently-selected folder is tracked via [selectedFolder]:
 *  - `null` means "All" (show every bookmark regardless of folder)
 *  - any non-null string means "filter to this folder"
 *
 * Clicking a folder chip calls [onFolderSelected] with the new selection.
 * The "+" chip calls [onAddFolder] instead and does NOT change the
 * selection.
 */
class FolderChipAdapter(
    private val onFolderSelected: (folder: String?) -> Unit,
    private val onAddFolder: () -> Unit
) : RecyclerView.Adapter<FolderChipAdapter.VH>() {

    /** Sentinels for the special chip slots. */
    private object Slot {
        const val TYPE_ALL = 0
        const val TYPE_FOLDER = 1
        const val TYPE_ADD = 2
    }

    private val folders = mutableListOf<String>()
    private var selectedFolder: String? = null

    /** Locks the "All" chip into slot 0; the "+" chip is always last. */
    private val addChipPosition: Int
        get() = 1 + folders.size

    inner class VH(val root: View) : RecyclerView.ViewHolder(root) {
        val chip: Chip = root as Chip
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_folder_chip, parent, false)
        return VH(v)
    }

    override fun getItemViewType(position: Int): Int = when {
        position == 0 -> Slot.TYPE_ALL
        position == addChipPosition -> Slot.TYPE_ADD
        else -> Slot.TYPE_FOLDER
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val ctx = holder.root.context
        when (getItemViewType(position)) {
            Slot.TYPE_ALL -> {
                holder.chip.text = ctx.getString(R.string.bookmarks_folder_all)
                holder.chip.isChecked = selectedFolder == null
                holder.chip.setOnClickListener {
                    selectedFolder = null
                    notifyDataSetChanged()
                    onFolderSelected(null)
                }
            }
            Slot.TYPE_ADD -> {
                holder.chip.text = ctx.getString(R.string.bookmarks_new_folder)
                holder.chip.isChecked = false
                holder.chip.isCheckable = false
                holder.chip.setOnClickListener { onAddFolder() }
            }
            Slot.TYPE_FOLDER -> {
                val folder = folders[position - 1]
                holder.chip.text = folder
                holder.chip.isCheckable = true
                holder.chip.isChecked = folder == selectedFolder
                holder.chip.setOnClickListener {
                    selectedFolder = folder
                    notifyDataSetChanged()
                    onFolderSelected(folder)
                }
            }
        }
    }

    override fun getItemCount(): Int = 1 + folders.size + 1 // All + folders + Add

    /**
     * Replaces the set of folder chips. Does not change the current
     * selection unless the selected folder is no longer in the new list
     * (in which case the selection is cleared back to "All").
     */
    fun submitFolders(newFolders: List<String>) {
        folders.clear()
        folders.addAll(newFolders)
        if (selectedFolder != null && selectedFolder !in folders) {
            selectedFolder = null
        }
        notifyDataSetChanged()
    }

    /**
     * Forces a specific folder selection (e.g. after the user picks "Move
     * to folder…" for a bookmark). Pass null for "All".
     */
    fun setSelected(folder: String?) {
        selectedFolder = folder
        notifyDataSetChanged()
    }

    /** The currently selected folder (null = All). */
    fun currentSelection(): String? = selectedFolder
}
