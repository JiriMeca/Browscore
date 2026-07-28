package com.permanentbrowser.app

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

/**
 * A WebViewClient attached to EVERY tab's WebView.
 *
 * It records EVERY page visit into the permanent history database. There is
 * no conditional / incognito path — every navigation in every tab is logged,
 * full stop. This is what keeps multi-tab browsing consistent with the
 * "no private mode, permanent history" guarantee.
 *
 * Ad-blocking intercepts sub-resource requests only. Main-frame navigations
 * NEVER blocked, so `onPageFinished` always fires and history records every
 * page visit. The permanent-history guarantee is preserved.
 *
 * Cosmetic filtering: after page load, injects CSS via JavaScript to hide
 * remaining ad/tracker DOM elements.
 *
 * HTTPS upgrade: when a main-frame HTTP request targets a known-upgradable
 * domain, redirects to HTTPS automatically.
 *
 * @param onUrlChanged Callback when the URL changes (used to update the omnibox).
 * @param onTitleChanged Callback when the page title changes.
 * @param onHistoryRecord Callback to record a page visit into permanent history.
 * @param onCosmeticFilter Callback to inject cosmetic CSS filter JS.
 * @param shouldBlock Lambda `(url, isMainFrame) -> block?`. Wired to [AdBlocker.shouldBlock].
 */
class HistoryRecordingWebViewClient(
    private val onUrlChanged: (String) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onHistoryRecord: (String, String) -> Unit,
    private val onCosmeticFilter: (String) -> Unit = {},
    private val shouldBlock: (String, Boolean) -> Boolean = { _, _ -> false }
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        if (view == null || request == null) return false
        val url = request.url?.toString() ?: return false

        // HTTPS upgrade for main-frame navigations
        if (request.isForMainFrame && url.startsWith("http://")) {
            val upgraded = AdBlocker.upgradeToHttps(url)
            if (upgraded != null) {
                view.loadUrl(upgraded)
                return true
            }
        }

        return false
    }

    /**
     * Ad-blocking interception.
     *
     * - Main-frame requests are ALWAYS passed through to the default loader
     *   so that `onPageFinished` fires and the URL is recorded into history.
     * - Sub-resource requests are passed to [shouldBlock]; if it returns
     *   true, an empty `text/plain` response is returned, silently dropping
     *   the ad / tracker without any visible error.
     */
    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        if (request == null) return null
        val url = request.url?.toString() ?: return null
        // NEVER block main-frame navigations — those must always load so
        // onPageFinished fires and history records the URL.
        if (request.isForMainFrame == false && shouldBlock(url, false)) {
            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
        }
        return null
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url.isNullOrBlank()) return
        val title = view?.title ?: url
        onUrlChanged(url)
        onTitleChanged(title.ifBlank { url })
        onHistoryRecord(url, title.ifBlank { url })

        // Inject cosmetic ad filters after page loads
        onCosmeticFilter(url)
    }
}
