package com.permanentbrowser.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bookmarks screen.
 *
 * Shows a horizontal folder-chip strip ("All" + one chip per distinct
 * folder + a "+" chip to create a new folder) above the flat bookmark
 * list. Tapping a chip filters the list to that folder; "All" shows
 * every bookmark.
 *
 * Long-pressing a bookmark opens a small action sheet with:
 *  - Move to folder…
 *  - Edit title
 *  - Delete
 *
 * The overflow button (top-right) opens a popup menu with:
 *  - Export bookmarks   (writes JSON to app-private external storage)
 *  - Import bookmarks   (SAF OpenDocument picker → parses JSON → inserts)
 *  - New folder         (prompts for a name and selects the new chip)
 *
 * SAFETY NOTE: bookmarks are user-curated. Editing, moving, and deleting
 * bookmarks here is intentional and DOES NOT affect the permanent browsing
 * history. The History* family of files is untouched by this screen.
 */
class BookmarksActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var emptyView: View
    private lateinit var folderChips: RecyclerView
    private lateinit var btnOverflow: ImageButton

    private val dao by lazy {
        BookmarkDatabase.getInstance(applicationContext).bookmarkDao()
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())

    private val folderAdapter = FolderChipAdapter(
        onFolderSelected = { folder -> loadBookmarks(folder) },
        onAddFolder = { showNewFolderDialog() }
    )

    private val bookmarkAdapter = BookmarkAdapter(
        onClick = { bookmark ->
            val intent = Intent(this, MainActivity::class.java).apply {
                data = Uri.parse(bookmark.url)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        },
        onLongClick = { bookmark -> showBookmarkActions(bookmark) }
    )

    /**
     * SAF picker for importing a JSON bookmark file. No runtime permission
     * needed — `OpenDocument` returns a `content://` URI granted temporary
     * read access for the lifetime of this task. Same pattern as
     * `roleRequestLauncher` in SettingsActivity.
     */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) importBookmarksFromUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bookmarks)

        findViewById<ImageButton>(R.id.btn_back_nav).setOnClickListener { finish() }
        btnOverflow = findViewById(R.id.btn_overflow)
        list = findViewById(R.id.bookmarks_list)
        emptyView = findViewById(R.id.empty_bookmarks)
        folderChips = findViewById(R.id.folder_chips)

        list.layoutManager = LinearLayoutManager(this)
        list.adapter = bookmarkAdapter

        folderChips.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        folderChips.adapter = folderAdapter

        btnOverflow.setOnClickListener { showOverflowMenu(it) }

        // Drag-and-drop reordering: drag the handle (or the whole row) up
        // or down to reorder. On drop, persist sort_order 0,1,2,… via
        // BookmarkDao.update — a NON-destructive operation that doesn't
        // touch the bookmark's id, folder, or any other field.
        setupDragReorder()
    }

    override fun onResume() {
        super.onResume()
        loadFolders()
        loadBookmarks(folderAdapter.currentSelection())
    }

    /* ==================== overflow menu ==================== */

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.bookmarks_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.opt_export_bookmarks -> {
                    exportBookmarks()
                    true
                }
                R.id.opt_import_bookmarks -> {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    true
                }
                R.id.opt_new_folder -> {
                    showNewFolderDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /* ==================== folders / loading ==================== */

    private fun loadFolders() {
        ioScope.launch {
            val folders = dao.getFolders()
            withContext(Dispatchers.Main) {
                folderAdapter.submitFolders(folders)
            }
        }
    }

    private fun loadBookmarks(folder: String?) {
        ioScope.launch {
            // Use the sort-order-respecting queries so manual reordering
            // is reflected in the list when the user returns to the screen.
            val items = if (folder == null) dao.getAllBySortOrder() else dao.getByFolderSorted(folder)
            withContext(Dispatchers.Main) {
                if (items.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    list.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    list.visibility = View.VISIBLE
                    bookmarkAdapter.submit(items)
                }
            }
        }
    }

    /* ==================== drag-and-drop reorder ==================== */

    /**
     * Attaches an [ItemTouchHelper] supporting UP/DOWN drag to the
     * bookmarks list. The drag is initiated from the row's drag handle
     * (or the whole row — ItemTouchHelper's default is whole-row drag).
     *
     * On every move, [BookmarkAdapter.moveItem] reorders the in-memory
     * list so the RecyclerView animates the swap. The database is NOT
     * touched during the drag — we persist the new order only when the
     * gesture ends (see [persistSortOrder] in [clearView]).
     */
    private fun setupDragReorder() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0 /* no swipe */
        ) {
            override fun onMove(
                rv: RecyclerView,
                source: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = source.adapterPosition
                val to = target.adapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                bookmarkAdapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // No swipe action — swipe is disabled (0 swipe dirs above).
            }

            override fun clearView(rv: RecyclerView, holder: RecyclerView.ViewHolder) {
                super.clearView(rv, holder)
                // Drag finished — persist the new sort order.
                persistSortOrder()
            }

            override fun isLongPressDragEnabled(): Boolean = true
        }
        ItemTouchHelper(callback).attachToRecyclerView(list)
    }

    /**
     * Reassigns `sortOrder` 0, 1, 2, … to every bookmark currently visible
     * in the adapter and persists each changed row via [BookmarkDao.update].
     *
     * Runs on the IO dispatcher. The `update` call only changes the
     * `sort_order` column — id, title, url, folder, and createdAt are
     * preserved by the data-class `copy()`.
     */
    private fun persistSortOrder() {
        ioScope.launch {
            val items = bookmarkAdapter.items
            items.forEachIndexed { index, bookmark ->
                if (bookmark.sortOrder != index) {
                    dao.update(bookmark.copy(sortOrder = index))
                }
            }
        }
    }

    /* ==================== long-press actions ==================== */

    private fun showBookmarkActions(bookmark: Bookmark) {
        val labels = arrayOf(
            getString(R.string.bookmarks_move_to_folder),
            getString(R.string.bookmarks_edit_title),
            getString(R.string.bookmarks_delete)
        )
        AlertDialog.Builder(this)
            .setTitle(bookmark.title.ifBlank { bookmark.url })
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> showMoveToFolderDialog(bookmark)
                    1 -> showEditTitleDialog(bookmark)
                    2 -> confirmDelete(bookmark)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showMoveToFolderDialog(bookmark: Bookmark) {
        ioScope.launch {
            val folders = dao.getFolders()
            withContext(Dispatchers.Main) {
                // Build the choice list: [existing folders…] + "New folder…" + "None (root)"
                val labels = folders.toMutableList<String>()
                val newFolderIndex = labels.size
                labels.add(getString(R.string.bookmarks_move_new_folder))
                val noneIndex = labels.size
                labels.add(getString(R.string.bookmarks_move_none))

                val currentFolder = bookmark.folder
                val checked = if (currentFolder.isEmpty()) {
                    noneIndex
                } else {
                    val idx = folders.indexOf(currentFolder)
                    if (idx < 0) noneIndex else idx
                }

                AlertDialog.Builder(this@BookmarksActivity)
                    .setTitle(R.string.bookmarks_move_to_folder)
                    .setSingleChoiceItems(labels.toTypedArray(), checked) { dialog, which ->
                        dialog.dismiss()
                        when (which) {
                            noneIndex -> moveBookmark(bookmark, "")
                            newFolderIndex -> promptForNewFolderName { name ->
                                moveBookmark(bookmark, name)
                            }
                            else -> moveBookmark(bookmark, labels[which])
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun moveBookmark(bookmark: Bookmark, newFolder: String) {
        ioScope.launch {
            dao.update(bookmark.copy(folder = newFolder))
            withContext(Dispatchers.Main) {
                loadFolders()
                loadBookmarks(folderAdapter.currentSelection())
            }
        }
    }

    private fun showEditTitleDialog(bookmark: Bookmark) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(bookmark.title)
            setSelection(bookmark.title.length)
            setSingleLine(true)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, 0)

        AlertDialog.Builder(this)
            .setTitle(R.string.bookmarks_edit_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty()) {
                    ioScope.launch {
                        dao.update(bookmark.copy(title = newTitle))
                        withContext(Dispatchers.Main) {
                            loadBookmarks(folderAdapter.currentSelection())
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(bookmark: Bookmark) {
        AlertDialog.Builder(this)
            .setTitle(R.string.bookmarks_delete)
            .setMessage(bookmark.title.ifBlank { bookmark.url })
            .setPositiveButton(android.R.string.ok) { _, _ ->
                ioScope.launch {
                    dao.delete(bookmark)
                    withContext(Dispatchers.Main) {
                        loadFolders()
                        loadBookmarks(folderAdapter.currentSelection())
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /* ==================== new folder dialog ==================== */

    private fun showNewFolderDialog() {
        promptForNewFolderName { name ->
            // No bookmark to move — just select the new chip and reload.
            folderAdapter.setSelected(name)
            loadFolders()
            loadBookmarks(name)
            Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
        }
    }

    private fun promptForNewFolderName(after: (String) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.bookmarks_folder_name)
            setSingleLine(true)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, 0)

        AlertDialog.Builder(this)
            .setTitle(R.string.bookmarks_new_folder)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) after(name)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /* ==================== export / import ==================== */

    /**
     * Exports ALL bookmarks (regardless of which folder chip is currently
     * selected) to a JSON file in app-private external storage
     * (`getExternalFilesDir(null)` — no permission needed on API 19+),
     * then toasts the absolute path so the user can find it.
     *
     * We intentionally do NOT use a FileProvider share intent — that
     * would require new manifest entries. The toast-with-path is enough
     * for v1 of this feature.
     */
    private fun exportBookmarks() {
        ioScope.launch {
            try {
                val all = dao.getAllGrouped()
                val json = BookmarkIO.exportToJson(all)
                val dir = getExternalFilesDir(null)
                if (dir == null) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@BookmarksActivity,
                            getString(R.string.bookmarks_import_failed, "External storage unavailable"),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }
                val ts = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val file = File(dir, "permanent-browser-bookmarks-$ts.json")
                file.writeText(json, Charsets.UTF_8)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@BookmarksActivity,
                        getString(R.string.bookmarks_exported_to, file.absolutePath),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@BookmarksActivity,
                        getString(R.string.bookmarks_import_failed, e.message ?: "export error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun importBookmarksFromUri(uri: Uri) {
        ioScope.launch {
            try {
                val json = contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().toString(Charsets.UTF_8)
                } ?: throw IllegalArgumentException("Could not open file")

                val parsed = BookmarkIO.importFromJson(json)
                for (b in parsed) {
                    dao.insert(b)
                }
                withContext(Dispatchers.Main) {
                    loadFolders()
                    loadBookmarks(folderAdapter.currentSelection())
                    Toast.makeText(
                        this@BookmarksActivity,
                        getString(R.string.bookmarks_imported_count, parsed.size),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@BookmarksActivity,
                        getString(R.string.bookmarks_import_failed, e.message ?: "unknown error"),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }
}
