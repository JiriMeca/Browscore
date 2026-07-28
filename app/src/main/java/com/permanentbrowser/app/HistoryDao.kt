package com.permanentbrowser.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * History DAO.
 *
 * SECURITY / DESIGN NOTE — why this file matters:
 *
 * The user's requirement is "history cannot be deleted". We enforce this at
 * the data-access layer by deliberately NOT exposing any @Delete method and
 * NOT exposing any DELETE / UPDATE SQL query. Even if a future UI screen
 * tried to clear history, there is no method here it could call.
 *
 * If you ever need to add maintenance (e.g. cap to last 10,000 rows), do it
 * with a controlled migration — never expose a public "clearAll()".
 */
@Dao
interface HistoryDao {

    @Insert
    suspend fun insert(entry: HistoryEntry): Long

    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    suspend fun getAll(): List<HistoryEntry>

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<HistoryEntry>

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int

    @Query("SELECT * FROM history WHERE url LIKE '%' || :term || '%' OR title LIKE '%' || :term || '%' ORDER BY visitedAt DESC")
    suspend fun search(term: String): List<HistoryEntry>

    /**
     * Returns the most-recent history entries whose title or URL contains [term],
     * limited to [limit] rows. Used by the omnibox autocomplete.
     * Distinct by URL so the same site doesn't appear multiple times.
     */
    @Query(
        "SELECT * FROM history WHERE url LIKE '%' || :term || '%' " +
            "OR title LIKE '%' || :term || '%' " +
            "GROUP BY url ORDER BY visitedAt DESC LIMIT :limit"
    )
    suspend fun searchSuggestions(term: String, limit: Int): List<HistoryEntry>
}
