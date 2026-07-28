package com.permanentbrowser.app

import android.app.Application

class BrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-warm the database so it is created on first launch.
        HistoryDatabase.getInstance(this)
        BookmarkDatabase.getInstance(this)

        // Restore session block stats from prefs
        val sessionStats = Prefs.getBlockStatsSession(this)
        if (sessionStats > 0) {
            // Persist previous session stats into lifetime total on cold start
            Prefs.persistBlockStats(this, sessionStats)
        }
    }
}
