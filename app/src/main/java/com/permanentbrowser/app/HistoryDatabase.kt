package com.permanentbrowser.app

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [HistoryEntry::class],
    version = 1,
    exportSchema = false
)
abstract class HistoryDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao

    companion object {
        @Volatile
        private var INSTANCE: HistoryDatabase? = null

        fun getInstance(context: Context): HistoryDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HistoryDatabase::class.java,
                    "permanent_history.db"
                )
                    // We deliberately DO NOT call fallbackToDestructiveMigration().
                    // History must survive app updates.
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
