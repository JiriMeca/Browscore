package com.permanentbrowser.app

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

/**
 * Bookmark DAO.
 *
 * Unlike history, bookmarks ARE user-managed: users can add, edit, move,
 * and remove their own bookmarks. Only browsing HISTORY is permanent —
 * see [HistoryDao] which deliberately exposes no @Delete/@Update/DELETE.
 *
 * Therefore @Delete and @Update here are intentional and appropriate.
 */
@Dao
interface BookmarkDao {

    @Insert
    suspend fun insert(bookmark: Bookmark): Long

    @Update
    suspend fun update(bookmark: Bookmark)

    @Delete
    suspend fun delete(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun getAll(): List<Bookmark>

    /**
     * All bookmarks ordered by manual drag-and-drop position (ascending),
     * with most-recently-created as the tiebreaker. This is the canonical
     * order used by [BookmarksActivity] when the user has reorganized rows.
     */
    @Query(
        "SELECT * FROM bookmarks ORDER BY sort_order ASC, createdAt DESC"
    )
    suspend fun getAllBySortOrder(): List<Bookmark>

    /**
     * Returns the distinct set of non-empty folder names, sorted
     * case-insensitively. An empty string is the "root / unfiled"
     * sentinel and is intentionally excluded — it's not a real folder
     * but rather the absence of a folder.
     */
    @Query(
        "SELECT DISTINCT folder FROM bookmarks WHERE folder != '' " +
            "ORDER BY folder COLLATE NOCASE"
    )
    suspend fun getFolders(): List<String>

    /**
     * Returns all bookmarks in the given folder. Pass the empty string to
     * get the root / unfiled bookmarks.
     */
    @Query("SELECT * FROM bookmarks WHERE folder = :folder ORDER BY createdAt DESC")
    suspend fun getByFolder(folder: String): List<Bookmark>

    /**
     * Same as [getByFolder] but respects the manual drag-and-drop position
     * (ascending), with most-recently-created as the tiebreaker.
     */
    @Query(
        "SELECT * FROM bookmarks WHERE folder = :folder " +
            "ORDER BY sort_order ASC, createdAt DESC"
    )
    suspend fun getByFolderSorted(folder: String): List<Bookmark>

    /**
     * Returns all bookmarks sorted by folder (case-insensitive) then by
     * most-recently-created first. Used for export — produces a stable,
     * human-readable ordering in the exported JSON.
     */
    @Query(
        "SELECT * FROM bookmarks ORDER BY folder COLLATE NOCASE ASC, createdAt DESC"
    )
    suspend fun getAllGrouped(): List<Bookmark>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    suspend fun exists(url: String): Boolean

    /**
     * Deletes a bookmark by URL. Used by the toggle-bookmark feature in
     * MainActivity when the user taps the bookmark icon on an already-
     * bookmarked page.
     */
    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    /** Returns the bookmark entity for the given URL, or null if not bookmarked. */
    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): Bookmark?

    /**
     * Returns bookmarks whose title or URL contains [term], ordered by
     * most-recently-created first. Used by the omnibox autocomplete.
     */
    @Query(
        "SELECT * FROM bookmarks WHERE title LIKE '%' || :term || '%' " +
            "OR url LIKE '%' || :term || '%' ORDER BY createdAt DESC LIMIT :limit"
    )
    suspend fun searchSuggestions(term: String, limit: Int): List<Bookmark>
}
