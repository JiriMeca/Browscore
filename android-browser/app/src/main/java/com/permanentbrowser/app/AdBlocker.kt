package com.permanentbrowser.app

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream

/**
 * Lightweight, domain-list-based ad / tracker blocker.
 *
 * Operates via [shouldInterceptRequest] on sub-resource requests only.
 * Main-frame navigations are NEVER blocked so that `onPageFinished` always
 * fires and every page visit is recorded into permanent history.
 */
object AdBlocker {

    /** Hardcoded set of ~50 common ad / tracker domain suffixes. */
    private val BLOCKED_DOMAINS: HashSet<String> = hashSetOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "google-analytics.com",
        "adservice.google.com",
        "adsense.com",
        "adnxs.com",
        "criteo.com",
        "scorecardresearch.com",
        "quantserve.com",
        "taboola.com",
        "outbrain.com",
        "facebook.com/tr",
        "connect.facebook.net",
        "analytics.twitter.com",
        "ads.twitter.com",
        "amazon-adsystem.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "adform.net",
        "adcolony.com",
        "applovin.com",
        "chartbeat.com",
        "hotjar.com",
        "mixpanel.com",
        "segment.io",
        "amplitude.com",
        "fullstory.com",
        "loggly.com",
        "sentry.io",
        "bugsnag.com",
        "branch.io",
        "onesignal.com",
        "pushwoosh.com",
        "appsflyer.com",
        "adjust.com",
        "kochava.com",
        "mathtag.com",
        "serving-sys.com",
        "ml314.com",
        "adsymptotic.com",
        "casalemedia.com",
        "3lift.com",
        "liadm.com",
        "rlcdn.com",
        "bluekai.com",
        "tidaltv.com",
        "yldmgrimg.net",
        "advertising.com"
    )

    /** Small LRU cache (10 entries) of parsed host strings to avoid re-parsing. */
    private val hostCache = object : LinkedHashMap<String, String>(10, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 10
        }
    }

    /**
     * Returns true if the URL's host ends with (or equals) a blocked domain.
     * Uses suffix matching so `analytics.example.doubleclick.net` matches
     * `doubleclick.net`.
     */
    fun isAd(url: String): Boolean {
        val host = parseHost(url) ?: return false
        for (domain in BLOCKED_DOMAINS) {
            if (host == domain || host.endsWith(".$domain")) {
                return true
            }
        }
        return false
    }

    /**
     * Returns true if ad-blocking is enabled AND the URL matches a
     * blocked domain.
     */
    fun shouldBlock(ctx: Context, url: String): Boolean {
        return isEnabled(ctx) && isAd(url)
    }

    /** Whether the user has enabled the ad-blocker (default: true). */
    fun isEnabled(ctx: Context): Boolean = Prefs.getAdBlockEnabled(ctx)

    /**
     * Builds an empty [WebResourceResponse] used to silently drop an
     * ad/tracker request. The empty body prevents any content from loading.
     */
    fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    /**
     * Convenience: checks [shouldBlock] for the given [WebResourceRequest].
     * Returns an empty response if blocked, or null if it should proceed normally.
     */
    fun interceptIfBlocked(ctx: Context, request: WebResourceRequest): WebResourceResponse? {
        // NEVER block main-frame navigations — those must always load so
        // onPageFinished fires and history records the URL.
        if (request.isForMainFrame) return null
        val url = request.url.toString()
        return if (shouldBlock(ctx, url)) emptyResponse() else null
    }

    // --- internal ---

    /** Parses the host from a URL string, using a 10-entry LRU cache. */
    private fun parseHost(url: String): String? {
        return hostCache.getOrPut(url) {
            try { Uri.parse(url).host ?: "" } catch (_: Exception) { "" }
        }.ifBlank { null }
    }
}
