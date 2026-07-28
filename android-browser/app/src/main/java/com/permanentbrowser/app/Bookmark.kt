package com.permanentbrowser.app

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-created bookmark.
 *
 * The [folder] field, added in DB v2 (see [BookmarkDatabase.MIGRATION_1_2]),
 * is the bookmark's folder name. An empty string is the "root / unfiled"
 * sentinel — i.e. a bookmark that lives at the top level of the bookmark
 * list rather than inside any folder.
 *
 * The [sortOrder] field, added in DB v3 (see [BookmarkDatabase.MIGRATION_2_3]),
 * is a 0-based manual position used by drag-and-drop reordering in
 * [BookmarksActivity]. Lower values appear first. New bookmarks default to
 * 0 — when multiple bookmarks share the same value, the load queries fall
 * back to ordering by `createdAt DESC` so the most-recently-added one wins.
 *
 * IMPORTANT: this entity is for bookmarks only — bookmarks are user-curated
 * and may be freely added, edited, moved, and deleted. It has NOTHING to do
 * with the permanent, undeletable browsing HISTORY (see [HistoryEntry]).
 */
@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val url: String,
    val folder: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int = 0
)
