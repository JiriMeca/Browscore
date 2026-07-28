package com.permanentbrowser.app

import android.webkit.WebView

/**
 * A single open browser tab.
 *
 * Every tab owns its own WebView. All WebViews use the NORMAL Android System
 * WebView profile (the private-browsing constructor was removed in API 17),
 * so every tab records history identically. There is no such thing as a
 * "private tab" in this data model — by design.
 *
 * [desktopMode] is per-tab (like Chrome): when true, the tab's WebView uses
 * a desktop User-Agent string so sites serve their desktop layout. It does
 * NOT change the WebView profile — history is still recorded normally.
 *
 * [readerMode] is per-tab: when true, the page's DOM has been restructured
 * via injected JavaScript to show a clean reading view. It does NOT navigate,
 * so it does NOT affect history.
 */
data class Tab(
    val id: Long,
    val webView: WebView,
    var title: String = "",
    var url: String = "",
    var isLoading: Boolean = false,
    var desktopMode: Boolean = false,
    var readerMode: Boolean = false
)
