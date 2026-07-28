package com.permanentbrowser.app

import android.app.Application

class BrowserApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Pre-warm the database so it is created on first launch.
        HistoryDatabase.getInstance(this)
        BookmarkDatabase.getInstance(this)
    }
}
