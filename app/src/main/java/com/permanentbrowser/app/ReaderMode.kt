package com.permanentbrowser.app

import android.webkit.WebView

/**
 * Reader mode utility using a paragraph-density heuristic.
 *
 * CRITICAL: This is a pure DOM manipulation utility. It MUST NOT call
 * webView.loadUrl() or modify history. Reader mode in MainActivity will
 * call webView.evaluateJavascript(...) to extract the page HTML and pass
 * it to this function.
 */
object ReaderMode {

    private const val READER_STYLE_ID = "__pb_reader_style"

    data class ReaderResult(
        val title: String,
        val content: String,
        val byline: String?
    )

    /**
     * Extracts the main readable content from raw HTML using a
     * paragraph-density heuristic.
     *
     * Algorithm:
     * 1. Parse title from &lt;title&gt; or first &lt;h1&gt;.
     * 2. Parse byline from &lt;meta name="author"&gt;.
     * 3. Find the &lt;div&gt; or &lt;article&gt; with the highest &lt;p&gt; tag count.
     * 4. Strip script/style/nav/header/footer/aside/form tags from it.
     * 5. Return the cleaned inner HTML.
     *
     * @param html The raw HTML string of the page.
     * @param url  The page URL (unused by the heuristic but included for
     *             future enhancements like URL-based scoring).
     */
    fun extract(html: String, url: String): ReaderResult {
        // --- Extract title ---
        val title = extractTitle(html)

        // --- Extract byline ---
        val byline = extractByline(html)

        // --- Strip unwanted top-level tags ---
        var cleanHtml = html
        val tagsToStrip = listOf("script", "style", "nav", "header", "footer", "aside", "form", "noscript")
        for (tag in tagsToStrip) {
            // Remove opening tags, closing tags, and self-closing variants.
            val openPattern = Regex("(?i)<\\s*$tag[^>]*>")
            val closePattern = Regex("(?i)<\\s*/\\s*$tag\\s*>")
            cleanHtml = openPattern.replace(cleanHtml, "")
            cleanHtml = closePattern.replace(cleanHtml, "")
        }

        // --- Find best content container by paragraph density ---
        val bestContent = findBestContainer(cleanHtml)

        return ReaderResult(
            title = title,
            content = bestContent,
            byline = byline
        )
    }

    /**
     * Injects a reader-mode &lt;style&gt; tag into the WebView via evaluateJavascript.
     * Serif font, max-width 65ch, dark text on cream background, larger line-height.
     *
     * CRITICAL: Uses only evaluateJavascript. Does NOT call loadUrl or affect history.
     */
    fun applyReaderCss(webView: WebView) {
        val js = """
        (function() {
            if (document.getElementById('__pb_reader_style')) return;
            var s = document.createElement('style');
            s.id = '__pb_reader_style';
            s.textContent = `
                body {
                    background: #fdf6e3 !important;
                    color: #333 !important;
                    font-family: Georgia, 'Times New Roman', serif !important;
                    font-size: 18px !important;
                    line-height: 1.8 !important;
                    padding: 16px !important;
                    margin: 0 !important;
                }
                .__pb_reader_content {
                    max-width: 65ch;
                    margin: 32px auto;
                    padding: 0 16px;
                }
                .__pb_reader_content p {
                    margin-bottom: 1.2em;
                }
                .__pb_reader_content h1,
                .__pb_reader_content h2,
                .__pb_reader_content h3 {
                    color: #111 !important;
                    line-height: 1.3;
                    margin-top: 1.5em;
                    margin-bottom: 0.5em;
                }
                .__pb_reader_content img {
                    max-width: 100%;
                    height: auto;
                }
                .__pb_reader_content a {
                    color: #1a0dab !important;
                }
                nav, header, footer, aside, .sidebar, .nav, .menu, .ad, .advertisement,
                .comments, .related, .share, .social, .popup, .modal, .overlay {
                    display: none !important;
                }
            `;
            document.head.appendChild(s);
        })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    /**
     * Removes the injected reader-mode style tag from the WebView.
     *
     * CRITICAL: Uses only evaluateJavascript. Does NOT call loadUrl or affect history.
     */
    fun removeReaderCss(webView: WebView) {
        val js = """
        (function() {
            var s = document.getElementById('__pb_reader_style');
            if (s) s.remove();
        })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    // --- Private helpers ---

    private fun extractTitle(html: String): String {
        // Try <title> first.
        val titleTag = Regex("(?i)<\\s*title[^>]*>(.*?)<\\s*/\\s*title>", RegexOption.DOT_MATCHES_ALL)
        val titleMatch = titleTag.find(html)
        if (titleMatch != null) {
            val t = stripTags(titleMatch.groupValues[1]).trim()
            if (t.isNotEmpty()) return t
        }
        // Fall back to first <h1>.
        val h1Tag = Regex("(?i)<\\s*h1[^>]*>(.*?)<\\s*/\\s*h1>", RegexOption.DOT_MATCHES_ALL)
        val h1Match = h1Tag.find(html)
        if (h1Match != null) {
            return stripTags(h1Match.groupValues[1]).trim()
        }
        return "Untitled"
    }

    private fun extractByline(html: String): String? {
        val metaAuthor = Regex("(?i)<\\s*meta[^>]+name\\s*=\\s*[\"']author[\"'][^>]+content\\s*=\\s*[\"'](.*?)[\"']")
        val match = metaAuthor.find(html)
        if (match != null) {
            val byline = match.groupValues[1].trim()
            if (byline.isNotEmpty()) return byline
        }
        // Also try content before name order.
        val metaAuthor2 = Regex("(?i)<\\s*meta[^>]+content\\s*=\\s*[\"'](.*?)[\"'][^>]+name\\s*=\\s*[\"']author[\"']")
        val match2 = metaAuthor2.find(html)
        if (match2 != null) {
            val byline = match2.groupValues[1].trim()
            if (byline.isNotEmpty()) return byline
        }
        return null
    }

    /**
     * Finds the &lt;div&gt; or &lt;article&gt; with the highest &lt;p&gt; tag count.
     * Falls back to the entire body content if no good container is found.
     */
    private fun findBestContainer(html: String): String {
        // Count <p> tags inside <article> and <div> elements.
        // We use a simple regex-based approach since we cannot use Jsoup.

        // Strategy: find all <article> and <div> elements (opening to matching closing),
        // count <p> tags inside each, and pick the one with the most.

        var bestHtml = html
        var bestScore = 0

        // Try <article> elements first (they are semantically the best candidates).
        val articleRegex = Regex(
            "(?is)<article[^>]*>(.*)</article>",
            RegexOption.DOT_MATCHES_ALL
        )
        for (match in articleRegex.findAll(html)) {
            val content = match.groupValues[1]
            val score = countPTags(content)
            if (score > bestScore) {
                bestScore = score
                bestHtml = content
            }
        }

        // If we found a good article, return it.
        if (bestScore >= 2) return bestHtml

        // Otherwise, try <div> elements. We look for divs that contain <p> tags.
        // Use a non-greedy approach to find candidate divs.
        val divRegex = Regex(
            "(?is)<div[^>]*>(.+?)</div>",
            RegexOption.DOT_MATCHES_ALL
        )
        for (match in divRegex.findAll(html)) {
            val content = match.groupValues[1]
            val score = countPTags(content)
            if (score > bestScore) {
                bestScore = score
                bestHtml = content
            }
        }

        // If still no good container, try to extract just the <body> content.
        if (bestScore < 1) {
            val bodyRegex = Regex(
                "(?is)<body[^>]*>(.*)</body>",
                RegexOption.DOT_MATCHES_ALL
            )
            val bodyMatch = bodyRegex.find(html)
            if (bodyMatch != null) {
                bestHtml = bodyMatch.groupValues[1]
            }
        }

        return bestHtml
    }

    /** Counts the number of &lt;p&gt; (opening) tags in the given HTML fragment. */
    private fun countPTags(html: String): Int {
        return Regex("(?i)<\\s*p[\\s>]").findAll(html).count()
    }

    /** Strips all HTML tags from a string, returning plain text. */
    private fun stripTags(html: String): String {
        return Regex("<[^>]*>").replace(html, "")
    }
}
