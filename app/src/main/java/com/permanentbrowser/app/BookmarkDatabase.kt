package com.permanentbrowser.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Bookmarks database.
 *
 * NOTE on migrations: bookmarks are user-curated and must NEVER be wiped on
 * an app update — the user would lose their saved pages. We therefore use
 * strictly additive migrations ([MIGRATION_1_2] and [MIGRATION_2_3], both
 * ALTER TABLE ADD COLUMN) and we DO NOT call
 * `fallbackToDestructiveMigration()`. This matches the same safety contract
 * as the history database (which never destructive-migrates either) —
 * though unlike history, bookmarks are still user-deletable one-by-one via
 * the UI.
 */
@Database(
    entities = [Bookmark::class],
    version = 3,
    exportSchema = false
)
abstract class BookmarkDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: BookmarkDatabase? = null

        /**
         * v1 → v2: adds the `folder` column to support bookmark folders.
         *
         * This is purely additive — every existing row keeps its place at
         * the "root" level via the DEFAULT '' sentinel. No data is lost and
         * no destructive migration is involved.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE bookmarks ADD COLUMN folder TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v2 → v3: adds the `sort_order` INTEGER column for drag-and-drop
         * reordering of bookmarks in [BookmarksActivity].
         *
         * Purely additive — every existing row gets the DEFAULT 0, which
         * means "no manual position". The list queries fall back to
         * `createdAt DESC` for ties, so existing behavior is preserved.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE bookmarks ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        fun getInstance(context: Context): BookmarkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BookmarkDatabase::class.java,
                    "bookmarks.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    // Deliberately NOT calling fallbackToDestructiveMigration() —
                    // bookmarks must survive app updates.
                    .build().also { INSTANCE = it }
            }
        }
    }
}
