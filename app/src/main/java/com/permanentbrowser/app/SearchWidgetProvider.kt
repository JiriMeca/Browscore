package com.permanentbrowser.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews

/**
 * Chrome-style AppWidgetProvider for Brows home-screen search bar.
 *
 * Following the RemoteViews widget guide:
 *   - Layout uses only RemoteViews-safe views (LinearLayout, ImageView, TextView)
 *   - Clicks wired via setOnClickPendingIntent (not OnClickListener)
 *   - 3 tappable zones, each with its own PendingIntent
 *
 * | Zone                     | View ID                 | Action                          |
 * |--------------------------|-------------------------|---------------------------------|
 * | Whole bar / hint text    | search_bar_container    | Opens SearchActivity (keyboard) |
 * | Mic icon                 | mic_icon                | Opens SearchActivity (keyboard) |
 * | Logo icon                | logo_icon               | Opens the host app (MainActivity)|
 *
 * The search bar uses FLAG_ACTIVITY_NEW_TASK so it launches cleanly from
 * the home screen. The logo opens MainActivity so the user can quickly
 * jump back to their last browsing session.
 *
 * No RECORD_AUDIO permission is needed — the mic icon is decorative here
 * and opens search like the bar does.
 */
class SearchWidgetProvider : AppWidgetProvider() {

    companion object {
        // Request codes for PendingIntents — must be unique per zone
        private const val REQUEST_SEARCH = 0
        private const val REQUEST_MIC = 1
        private const val REQUEST_LAUNCH = 2
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.search_widget)

        // ---- 1) Tap the bar / hint text -> open SearchActivity ---------------
        // Uses a deep link into SearchActivity so the keyboard opens immediately.
        // This is the Chrome pattern — a transparent "trampoline" that focuses
        // the search input and shows the soft keyboard.
        val searchIntent = Intent(context, SearchActivity::class.java).apply {
            action = SearchActivity.ACTION_SEARCH
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            data = Uri.parse("widget://search/$appWidgetId")
        }
        val searchPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_SEARCH,
            searchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.search_bar_container, searchPendingIntent)

        // ---- 2) Tap the mic icon -> also opens SearchActivity ----------------
        // Voice-to-text is not needed; mic icon is decorative to match
        // the Chrome look. Tapping it opens search with the keyboard,
        // identical to tapping the bar.
        val micIntent = Intent(context, SearchActivity::class.java).apply {
            action = SearchActivity.ACTION_SEARCH
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            data = Uri.parse("widget://mic/$appWidgetId")
        }
        val micPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_MIC,
            micIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.mic_icon, micPendingIntent)

        // ---- 3) Tap the logo icon -> open the host app ----------------------
        // Opens MainActivity so the user returns to their last browsing session.
        // Uses getLaunchIntentForPackage which resolves to the activity with
        // ACTION_MAIN + CATEGORY_LAUNCHER (i.e., MainActivity).
        val launchAppIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }

        if (launchAppIntent != null) {
            val appPendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_LAUNCH,
                launchAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.logo_icon, appPendingIntent)
        }

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
