package com.permanentbrowser.app

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import org.json.JSONArray

object Prefs {
    private const val FILE = "permanent_browser_prefs"
    private const val KEY_HOMEPAGE = "homepage"
    private const val KEY_JS = "javascript_enabled"
    private const val KEY_POPUPS = "block_popups"
    private const val KEY_SEARCH_ENGINE = "search_engine"
    private const val KEY_TAB_URLS = "tab_urls"
    private const val KEY_CURRENT_TAB_INDEX = "current_tab_index"
    private const val KEY_LAST_TAB_DESKTOP = "last_tab_desktop"
    private const val KEY_AD_BLOCK = "ad_block_enabled"
    private const val KEY_NEW_TAB_PAGE = "new_tab_page"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /* ---------------- homepage ---------------- */

    fun getHomepage(ctx: Context): String =
        prefs(ctx).getString(KEY_HOMEPAGE, "https://duckduckgo.com/") ?: "https://duckduckgo.com/"

    fun setHomepage(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_HOMEPAGE, value).apply()

    /* ---------------- javascript ---------------- */

    fun getJavaScriptEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_JS, true)

    fun setJavaScriptEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_JS, value).apply()

    /* ---------------- popups ---------------- */

    fun getBlockPopups(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_POPUPS, true)

    fun setBlockPopups(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_POPUPS, value).apply()

    /* ---------------- ad block ---------------- */

    /** Returns whether the ad / tracker blocker is enabled (default: true). */
    fun getAdBlockEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AD_BLOCK, true)

    fun setAdBlockEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_AD_BLOCK, value).apply()

    /* ---------------- new tab page ---------------- */

    /**
     * Returns the URL to load when a new tab is opened (via the + button).
     * Falls back to [getHomepage] if no custom new-tab page is set OR if the
     * saved value is empty/blank (so a user who clears the dialog goes back
     * to using their homepage, matching Chrome's behavior).
     */
    fun getNewTabPage(ctx: Context): String =
        prefs(ctx).getString(KEY_NEW_TAB_PAGE, null)?.takeIf { it.isNotBlank() }
            ?: getHomepage(ctx)

    fun setNewTabPage(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_NEW_TAB_PAGE, value).apply()

    /* ---------------- search engine ---------------- */

    /**
     * All available search engines. Order matches the picker dialog.
     */
    val SEARCH_ENGINES: List<SearchEngine> = listOf(
        SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
        SearchEngine("google", "Google", "https://www.google.com/search?q="),
        SearchEngine("bing", "Bing", "https://www.bing.com/search?q="),
        SearchEngine("startpage", "Startpage", "https://www.startpage.com/sp/search?query="),
    )

    fun getSearchEngineId(ctx: Context): String =
        prefs(ctx).getString(KEY_SEARCH_ENGINE, "duckduckgo") ?: "duckduckgo"

    fun setSearchEngineId(ctx: Context, id: String) =
        prefs(ctx).edit().putString(KEY_SEARCH_ENGINE, id).apply()

    fun getSearchEngine(ctx: Context): SearchEngine =
        SEARCH_ENGINES.firstOrNull { it.id == getSearchEngineId(ctx) } ?: SEARCH_ENGINES[0]

    /**
     * Build a search URL for the given free-text query using the user's
     * selected search engine.
     */
    fun buildSearchUrl(ctx: Context, query: String): String {
        val engine = getSearchEngine(ctx)
        return engine.queryUrl + android.net.Uri.encode(query)
    }

    /* ---------------- default browser detection ---------------- */

    /**
     * Returns true if THIS app is currently the system default browser.
     *
     * - On Android 10+ (API 29+): uses RoleManager.isRoleHeld(ROLE_BROWSER),
     *   the modern, reliable API.
     * - On older Android: inspects Settings.Secure string for the default
     *   browser package. This is best-effort because the format historically
     *   varied across OEMs.
     */
    fun isDefaultBrowser(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = ctx.getSystemService(Context.ROLE_SERVICE) as android.app.role.RoleManager
            rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)
        } else {
            val pkg = defaultBrowserPackagePreQ(ctx)
            !pkg.isNullOrBlank() && pkg == ctx.packageName
        }
    }

    private fun defaultBrowserPackagePreQ(ctx: Context): String? {
        return try {
            val browsers = ctx.packageManager.queryIntentActivities(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("http://"))
                    .addCategory(android.content.Intent.CATEGORY_BROWSABLE),
                PackageManager.MATCH_DEFAULT_ONLY
            )
            // Best-effort: if there's exactly one, it's the default on many OEM ROMs
            if (browsers.size == 1) browsers[0].activityInfo.packageName
            else Settings.Secure.getString(ctx.contentResolver, "default_browser")
        } catch (_: Exception) {
            null
        }
    }

    /* ---------------- tab persistence ---------------- */

    /**
     * Persists the current set of open tabs so they can be restored after
     * an app restart. We store a JSON array of URLs + the index of the
     * currently-active tab + the current tab's desktop-mode flag.
     *
     * Only URLs are persisted (not page DOM/state) — restored tabs reload
     * their URL fresh. This matches Chrome's "continue where you left off"
     * behavior at a lightweight level.
     *
     * IMPORTANT for the no-incognito guarantee: every restored URL is loaded
     * into a normal-profile WebView and recorded into permanent history
     * exactly like any other navigation. There is no "restore without
     * recording" path.
     */
    fun saveTabs(ctx: Context, urls: List<String>, currentIndex: Int, currentDesktop: Boolean) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        prefs(ctx).edit()
            .putString(KEY_TAB_URLS, arr.toString())
            .putInt(KEY_CURRENT_TAB_INDEX, currentIndex.coerceAtLeast(0))
            .putBoolean(KEY_LAST_TAB_DESKTOP, currentDesktop)
            .apply()
    }

    /**
     * Returns the persisted tab URLs (empty list if none saved).
     */
    fun getSavedTabUrls(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_TAB_URLS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                arr.optString(idx).takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getSavedCurrentTabIndex(ctx: Context): Int =
        prefs(ctx).getInt(KEY_CURRENT_TAB_INDEX, 0)

    fun getLastTabDesktop(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_LAST_TAB_DESKTOP, false)

    /**
     * Clears saved tab state. Called after a successful restore so a crash
     * during the session doesn't restore a stale set.
     */
    fun clearSavedTabs(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_TAB_URLS)
            .remove(KEY_CURRENT_TAB_INDEX)
            .remove(KEY_LAST_TAB_DESKTOP)
            .apply()
    }
}

data class SearchEngine(
    val id: String,
    val displayName: String,
    val queryUrl: String,
)
