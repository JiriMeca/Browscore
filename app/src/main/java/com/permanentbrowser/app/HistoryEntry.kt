package com.permanentbrowser.app

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single browsing-history entry.
 *
 * IMPORTANT: This table is APPEND-ONLY by design. The DAO exposes only
 * insert + query operations — there is intentionally NO delete method.
 */
@Entity(
    tableName = "history",
    indices = [Index(value = ["visitedAt"])]
)
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long = System.currentTimeMillis()
)
