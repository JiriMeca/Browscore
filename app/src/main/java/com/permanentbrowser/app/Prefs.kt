package com.permanentbrowser.app

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicLong

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
    private const val KEY_BLOCK_STATS_TOTAL = "block_stats_total"
    private const val KEY_BLOCK_STATS_SESSION = "block_stats_session"

    // Per-category blocking keys
    private const val KEY_AD_BLOCK_ADS = "ad_block_cat_ads"
    private const val KEY_AD_BLOCK_TRACKERS = "ad_block_cat_trackers"
    private const val KEY_AD_BLOCK_ANALYTICS = "ad_block_cat_analytics"
    private const val KEY_AD_BLOCK_SOCIAL = "ad_block_cat_social"
    private const val KEY_AD_BLOCK_FINGERPRINT = "ad_block_cat_fingerprint"
    private const val KEY_AD_BLOCK_CRYPTO = "ad_block_cat_crypto"
    private const val KEY_AD_BLOCK_POPUPS = "ad_block_cat_popups"
    private const val KEY_AD_BLOCK_ANNOYANCES = "ad_block_cat_annoyances"
    private const val KEY_AD_BLOCK_MALWARE = "ad_block_cat_malware"
    private const val KEY_AD_BLOCK_CONTENT_FARM = "ad_block_cat_content_farm"
    private const val KEY_AD_BLOCK_OTHER = "ad_block_cat_other"
    private const val KEY_HTTPS_UPGRADE = "https_upgrade_enabled"
    private const val KEY_COSMETIC_FILTERS = "cosmetic_filters_enabled"

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

    /* ---------------- ad block categories ---------------- */

    fun getAdBlockCategoryEnabled(ctx: Context, category: AdBlocker.BlockCategory): Boolean {
        val key = when (category) {
            AdBlocker.BlockCategory.ADVERTISING -> KEY_AD_BLOCK_ADS
            AdBlocker.BlockCategory.TRACKERS -> KEY_AD_BLOCK_TRACKERS
            AdBlocker.BlockCategory.ANALYTICS -> KEY_AD_BLOCK_ANALYTICS
            AdBlocker.BlockCategory.SOCIAL -> KEY_AD_BLOCK_SOCIAL
            AdBlocker.BlockCategory.FINGERPRINTING -> KEY_AD_BLOCK_FINGERPRINT
            AdBlocker.BlockCategory.CRYPTO_MINING -> KEY_AD_BLOCK_CRYPTO
            AdBlocker.BlockCategory.POPUPS -> KEY_AD_BLOCK_POPUPS
            AdBlocker.BlockCategory.ANNOYANCES -> KEY_AD_BLOCK_ANNOYANCES
            AdBlocker.BlockCategory.MALWARE -> KEY_AD_BLOCK_MALWARE
            AdBlocker.BlockCategory.CONTENT_FARMING -> KEY_AD_BLOCK_CONTENT_FARM
            AdBlocker.BlockCategory.OTHER -> KEY_AD_BLOCK_OTHER
        }
        return prefs(ctx).getBoolean(key, true) // all categories default to enabled
    }

    fun setAdBlockCategoryEnabled(ctx: Context, category: AdBlocker.BlockCategory, enabled: Boolean) {
        val key = when (category) {
            AdBlocker.BlockCategory.ADVERTISING -> KEY_AD_BLOCK_ADS
            AdBlocker.BlockCategory.TRACKERS -> KEY_AD_BLOCK_TRACKERS
            AdBlocker.BlockCategory.ANALYTICS -> KEY_AD_BLOCK_ANALYTICS
            AdBlocker.BlockCategory.SOCIAL -> KEY_AD_BLOCK_SOCIAL
            AdBlocker.BlockCategory.FINGERPRINTING -> KEY_AD_BLOCK_FINGERPRINT
            AdBlocker.BlockCategory.CRYPTO_MINING -> KEY_AD_BLOCK_CRYPTO
            AdBlocker.BlockCategory.POPUPS -> KEY_AD_BLOCK_POPUPS
            AdBlocker.BlockCategory.ANNOYANCES -> KEY_AD_BLOCK_ANNOYANCES
            AdBlocker.BlockCategory.MALWARE -> KEY_AD_BLOCK_MALWARE
            AdBlocker.BlockCategory.CONTENT_FARMING -> KEY_AD_BLOCK_CONTENT_FARM
            AdBlocker.BlockCategory.OTHER -> KEY_AD_BLOCK_OTHER
        }
        prefs(ctx).edit().putBoolean(key, enabled).apply()
    }

    /* ---------------- HTTPS upgrade ---------------- */

    fun getHttpsUpgradeEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_HTTPS_UPGRADE, true)

    fun setHttpsUpgradeEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_HTTPS_UPGRADE, value).apply()

    /* ---------------- Cosmetic filters ---------------- */

    fun getCosmeticFiltersEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_COSMETIC_FILTERS, true)

    fun setCosmeticFiltersEnabled(ctx: Context, value: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_COSMETIC_FILTERS, value).apply()

    /* ---------------- blocking stats ---------------- */

    fun getBlockStatsTotal(ctx: Context): Long =
        prefs(ctx).getLong(KEY_BLOCK_STATS_TOTAL, 0L)

    fun setBlockStatsTotal(ctx: Context, value: Long) =
        prefs(ctx).edit().putLong(KEY_BLOCK_STATS_TOTAL, value).apply()

    fun getBlockStatsSession(ctx: Context): Long =
        prefs(ctx).getLong(KEY_BLOCK_STATS_SESSION, 0L)

    fun setBlockStatsSession(ctx: Context, value: Long) =
        prefs(ctx).edit().putLong(KEY_BLOCK_STATS_SESSION, value).apply()

    /** Accumulate session stats into the lifetime total. Called on app exit. */
    fun persistBlockStats(ctx: Context, sessionTotal: Long) {
        val lifetime = getBlockStatsTotal(ctx)
        prefs(ctx).edit()
            .putLong(KEY_BLOCK_STATS_TOTAL, lifetime + sessionTotal)
            .putLong(KEY_BLOCK_STATS_SESSION, 0L)
            .apply()
    }

    /** Reset lifetime stats. */
    fun resetBlockStats(ctx: Context) {
        prefs(ctx).edit()
            .putLong(KEY_BLOCK_STATS_TOTAL, 0L)
            .putLong(KEY_BLOCK_STATS_SESSION, 0L)
            .apply()
    }

    /* ---------------- new tab page ---------------- */

    fun getNewTabPage(ctx: Context): String =
        prefs(ctx).getString(KEY_NEW_TAB_PAGE, null)?.takeIf { it.isNotBlank() }
            ?: getHomepage(ctx)

    fun setNewTabPage(ctx: Context, value: String) =
        prefs(ctx).edit().putString(KEY_NEW_TAB_PAGE, value).apply()

    /* ---------------- search engine ---------------- */

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

    fun buildSearchUrl(ctx: Context, query: String): String {
        val engine = getSearchEngine(ctx)
        return engine.queryUrl + android.net.Uri.encode(query)
    }

    /* ---------------- default browser detection ---------------- */

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
            if (browsers.size == 1) browsers[0].activityInfo.packageName
            else Settings.Secure.getString(ctx.contentResolver, "default_browser")
        } catch (_: Exception) {
            null
        }
    }

    /* ---------------- tab persistence ---------------- */

    fun saveTabs(ctx: Context, urls: List<String>, currentIndex: Int, currentDesktop: Boolean) {
        val arr = JSONArray()
        urls.forEach { arr.put(it) }
        prefs(ctx).edit()
            .putString(KEY_TAB_URLS, arr.toString())
            .putInt(KEY_CURRENT_TAB_INDEX, currentIndex.coerceAtLeast(0))
            .putBoolean(KEY_LAST_TAB_DESKTOP, currentDesktop)
            .apply()
    }

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
