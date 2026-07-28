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
 * @param shouldBlock Lambda `(url, isMainFrame) -> block?`. The caller
 * (MainActivity) wires this to [AdBlocker.shouldBlock]. The isMainFrame
 * parameter is passed for future flexibility (e.g. per-frame policies) but
 * the caller currently ignores it — main-frame requests are NEVER blocked
 * because [shouldInterceptRequest] short-circuits them before invoking the
 * lambda.
 */
class HistoryRecordingWebViewClient(
    private val onUrlChanged: (String) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onHistoryRecord: (String, String) -> Unit,
    private val shouldBlock: (String, Boolean) -> Boolean = { _, _ -> false }
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
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
        return super.shouldInterceptRequest(view, request)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        if (url.isNullOrBlank()) return
        val title = view?.title ?: url
        onUrlChanged(url)
        onTitleChanged(title.ifBlank { url })
        onHistoryRecord(url, title.ifBlank { url })
    }
}
