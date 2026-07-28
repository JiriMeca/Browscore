package com.permanentbrowser.app

import android.webkit.WebView

/**
 * Per-tab dark mode CSS injection.
 *
 * Applies or removes a CSS filter that inverts page colors for a
 * dark-mode reading experience. Each WebView/tab can be toggled
 * independently.
 *
 * CRITICAL: This is purely a CSS injection. It MUST NOT call
 * webView.loadUrl() or affect history.
 */
object DarkModeInjector {

    /**
     * Injects or removes the dark-mode CSS filter for the given WebView.
     *
     * When enabled: injects a style element with id '__pb_dark_invert'
     * containing html { filter: invert(1) hue-rotate(180deg) } and a
     * counter-inversion for images/videos/iframes.
     *
     * When disabled: removes the injected style element if present.
     *
     * CRITICAL: Uses only evaluateJavascript. Does NOT call loadUrl.
     *
     * @param webView The WebView to apply dark mode to.
     * @param enabled Whether dark mode should be active.
     */
    fun apply(webView: WebView, enabled: Boolean) {
        val js = if (enabled) {
            """
            (function(){var s=document.getElementById('__pb_dark_invert');if(!s){s=document.createElement('style');s.id='__pb_dark_invert';s.textContent='html{filter:invert(1) hue-rotate(180deg);background-color:#fafafa}img,video,iframe{filter:invert(1) hue-rotate(180deg)}';document.head.appendChild(s)}})();
            """.trimIndent()
        } else {
            """
            (function(){var s=document.getElementById('__pb_dark_invert');if(s){s.remove()}})();
            """.trimIndent()
        }
        webView.evaluateJavascript(js, null)
    }
}
