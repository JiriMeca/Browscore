package com.permanentbrowser.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.webkit.ValueCallback
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    companion object {
        /** Desktop Chrome User-Agent used when a tab is in desktop mode. */
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        /** Request code for the file chooser activity result. */
        private const val FILE_CHOOSER_REQUEST_CODE = 1001
    }

    // --- views ---
    private lateinit var urlBar: EditText
    private lateinit var omniboxSearchIcon: ImageView
    private lateinit var omniboxSecurityIcon: ImageView
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnForward: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnBookmark: ImageButton
    private lateinit var btnOverflowMenu: ImageButton
    private lateinit var btnShieldContainer: View
    private lateinit var btnShield: ImageView
    private lateinit var shieldBadge: TextView
    private lateinit var btnTabsContainer: View
    private lateinit var tabCountBadge: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var webviewContainer: FrameLayout
    private lateinit var suggestionList: RecyclerView
    private val suggestionAdapter = SuggestionAdapter(
        onSuggestionClick = { item -> onSuggestionClicked(item) }
    )
    private var suggestionJob: Job? = null

    // --- tab switcher overlay ---
    private lateinit var tabSwitcherOverlay: View
    private lateinit var tabCardsList: RecyclerView
    private lateinit var tabsCountLabel: TextView
    private val tabAdapter = TabCardAdapter(
        onClick = { tab -> switchToTab(tab.id); hideTabSwitcher() },
        onClose = { tab -> closeTab(tab.id) }
    )

    // --- find in page bar ---
    private lateinit var findBar: View
    private lateinit var findInput: EditText
    private lateinit var findResultCount: TextView
    private var findActive = false

    // --- tab state ---
    private val tabs = mutableListOf<Tab>()
    private var currentTabId: Long = -1L
    private var tabIdCounter = 0L
    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Make the status bar blend with our white background
        window.statusBarColor = Color.WHITE
        setContentView(R.layout.activity_main)

        bindViews()
        wireToolbar()
        wireUrlBar()
        wireSuggestions()
        wireTabSwitcher()

        // If launched from an external http/https intent (e.g. tapped a link
        // outside the app), restore existing tabs first, then add the URL
        // as a new tab so nothing is lost.
        val externalUrl = intent?.data?.toString()
        if (externalUrl != null && externalUrl.startsWith("http")) {
            restoreTabs()
            addTab(url = externalUrl, switchTo = true)
            return
        }

        restoreTabs()
    }

    /**
     * Restores previously-open tabs from SharedPreferences (Chrome-style
     * "continue where you left off"). If no saved tabs exist, opens the
     * homepage.
     */
    private fun restoreTabs() {
        val savedUrls = Prefs.getSavedTabUrls(this)
        if (savedUrls.isNotEmpty()) {
            val savedIndex = Prefs.getSavedCurrentTabIndex(this)
            savedUrls.forEachIndexed { idx, url ->
                val switchTo = idx == savedIndex
                addTab(url = url, switchTo = switchTo)
            }
            // Apply the saved desktop-mode flag to the restored current tab.
            if (Prefs.getLastTabDesktop(this)) {
                currentTab()?.let { tab ->
                    tab.desktopMode = true
                    applyDesktopMode(tab.webView, true)
                }
            }
            Prefs.clearSavedTabs(this)
        } else {
            // Fresh launch with no saved tabs → open the homepage.
            addTab(url = Prefs.getHomepage(this))
        }
    }

    /**
     * Handles new intents delivered while the activity is already running.
     * This is triggered when SearchActivity (or any external app) navigates
     * here with ACTION_VIEW + a URL. Instead of destroying the activity
     * (which would lose all open tabs), we simply add a new tab with the URL.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = intent.data?.toString()
        if (url != null && url.startsWith("http")) {
            addTab(url = url, switchTo = true)
        }
    }

    /* ============================ view binding ============================ */

    private fun bindViews() {
        urlBar = findViewById(R.id.url_bar)
        omniboxSearchIcon = findViewById(R.id.omnibox_search_icon)
        omniboxSecurityIcon = findViewById(R.id.omnibox_security_icon)
        btnRefresh = findViewById(R.id.btn_refresh)
        btnBack = findViewById(R.id.btn_back)
        btnForward = findViewById(R.id.btn_forward)
        btnHome = findViewById(R.id.btn_home)
        btnBookmark = findViewById(R.id.btn_bookmark_page)
        btnOverflowMenu = findViewById(R.id.btn_overflow_menu)
        btnShieldContainer = findViewById(R.id.btn_shield_container)
        btnShield = findViewById(R.id.btn_shield)
        shieldBadge = findViewById(R.id.shield_blocked_badge)
        btnTabsContainer = findViewById(R.id.btn_tabs_container)
        tabCountBadge = findViewById(R.id.tab_count_badge)
        progressBar = findViewById(R.id.progress_bar)
        webviewContainer = findViewById(R.id.webview_container)

        // Suggestion dropdown list
        suggestionList = findViewById(R.id.suggestion_list)
        suggestionList.layoutManager = LinearLayoutManager(this)
        suggestionList.adapter = suggestionAdapter

        tabSwitcherOverlay = findViewById(R.id.tab_switcher_overlay)
        tabCardsList = findViewById(R.id.tab_cards_list)
        tabsCountLabel = findViewById(R.id.tabs_count_label)

        tabCardsList.layoutManager = GridLayoutManager(this, 2)
        tabCardsList.adapter = tabAdapter

        // Find-in-page bar (included from view_find_in_page.xml)
        findBar = findViewById(R.id.find_bar)
        findInput = findViewById(R.id.find_input)
        findResultCount = findViewById(R.id.find_result_count)
        findViewById<ImageButton>(R.id.btn_find_up).setOnClickListener {
            currentTab()?.webView?.findNext(true)
        }
        findViewById<ImageButton>(R.id.btn_find_down).setOnClickListener {
            currentTab()?.webView?.findNext(false)
        }
        findViewById<ImageButton>(R.id.btn_find_close).setOnClickListener {
            hideFindInPage()
        }
    }

    /* ============================ toolbar wiring =========================== */

    private fun wireToolbar() {
        btnBack.setOnClickListener {
            currentTab()?.webView?.let { if (it.canGoBack()) it.goBack() }
        }
        btnForward.setOnClickListener {
            currentTab()?.webView?.let { if (it.canGoForward()) it.goForward() }
        }
        btnHome.setOnClickListener {
            currentTab()?.webView?.loadUrl(Prefs.getHomepage(this))
        }
        btnRefresh.setOnClickListener {
            currentTab()?.webView?.reload()
        }
        btnBookmark.setOnClickListener { bookmarkCurrentPage() }
        btnOverflowMenu.setOnClickListener { showOverflowMenu(it) }
        btnShieldContainer.setOnClickListener { showShieldInfo() }
        btnTabsContainer.setOnClickListener { showTabSwitcher() }

        // Long-press back shows a small back history toast (minimal)
        btnBack.setOnLongClickListener {
            // no-op placeholder for future back-stack popup
            false
        }
    }

    private fun wireUrlBar() {
        urlBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                navigateFromOmnibox()
                true
            } else false
        }

        // When the omnibox gains focus, select all so typing replaces the URL.
        // Post selectAll to avoid race condition with setText() from updateOmnibox().
        urlBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                omniboxSearchIcon.isVisible = true
                omniboxSecurityIcon.isVisible = false
                // Post selectAll to next frame to avoid conflict with setText()
                urlBar.post { try { urlBar.selectAll() } catch (_: Exception) {} }
                // Show suggestions immediately if there's text
                onOmniboxTextChanged()
            } else {
                hideSuggestions()
            }
        }

        // Debounced text watcher for autocomplete suggestions.
        urlBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (urlBar.isFocused) onOmniboxTextChanged()
            }
        })
    }

    /**
     * Reads the text from the URL bar, normalizes it, and navigates the current tab.
     * Also hides the keyboard and clears focus.
     */
    private fun navigateFromOmnibox() {
        val text = urlBar.text.toString().trim()
        if (text.isNotEmpty()) {
            currentTab()?.webView?.loadUrl(normalizeInput(text))
            urlBar.clearFocus()
            hideKeyboard()
        }
    }

    private fun wireTabSwitcher() {
        findViewById<ImageButton>(R.id.btn_new_tab).setOnClickListener {
            addTab(url = Prefs.getHomepage(this))
            hideTabSwitcher()
        }
        findViewById<ImageButton>(R.id.btn_done).setOnClickListener {
            hideTabSwitcher()
        }
    }

    private fun wireSuggestions() {
        // Dismiss suggestion list when the user taps the WebView area.
        webviewContainer.setOnClickListener {
            if (suggestionList.isVisible) {
                urlBar.clearFocus()
                hideSuggestions()
            }
        }
    }

    /* ============================ find in page ============================ */

    private fun showFindInPage() {
        if (findActive) {
            findInput.requestFocus()
            showKeyboard(findInput)
            return
        }
        findActive = true
        findBar.visibility = View.VISIBLE
        findBar.translationY = findBar.height.toFloat().coerceAtLeast(48f)
        findBar.animate().translationY(0f).setDuration(150).start()
        findInput.setText("")
        findResultCount.visibility = View.GONE
        findInput.requestFocus()
        showKeyboard(findInput)

        // React to typing: search as you type (Chrome behavior).
        findInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                val wv = currentTab()?.webView
                if (q.isEmpty()) {
                    findResultCount.visibility = View.GONE
                    wv?.clearMatches()
                } else {
                    wv?.findAllAsync(q)
                }
            }
        })

        // Enter → jump to next match.
        findInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                currentTab()?.webView?.findNext(false)
                true
            } else false
        }
    }

    private fun hideFindInPage() {
        if (!findActive) return
        findActive = false
        findBar.animate().translationY(findBar.height.toFloat().coerceAtLeast(48f))
            .setDuration(150)
            .withEndAction { findBar.visibility = View.GONE }
            .start()
        currentTab()?.webView?.clearMatches()
    }

    /* ============================ autocomplete suggestions ================== */

    /**
     * Called when the user types in the omnibox (or when it first gains focus
     * with existing text). Debounces by 150ms and fetches suggestions from:
     *   1. Bookmarks (local DB)
     *   2. History (local DB)
     *   3. Search engine autocomplete API
     */
    private fun onOmniboxTextChanged() {
        val query = urlBar.text.toString().trim()
        if (query.isBlank()) {
            hideSuggestions()
            return
        }

        // Cancel any in-flight suggestion request
        suggestionJob?.cancel()

        suggestionJob = ioScope.launch {
            delay(150) // debounce

            val items = mutableListOf<SuggestionItem>()
            val lowerQuery = query.lowercase()

            // 1. Search bookmarks
            try {
                val bookmarks = BookmarkDatabase.getInstance(applicationContext)
                    .bookmarkDao().searchSuggestions(lowerQuery, 3)
                for (b in bookmarks) {
                    items.add(
                        SuggestionItem(
                            title = b.title.ifBlank { b.url },
                            subtitle = b.url,
                            type = SuggestionType.BOOKMARK,
                            url = b.url
                        )
                    )
                }
            } catch (_: Exception) {}

            // 2. Search history (exclude items already shown from bookmarks)
            try {
                val bookmarkUrls = items.filter { it.type == SuggestionType.BOOKMARK }.map { it.url }.toSet()
                val history = HistoryDatabase.getInstance(applicationContext)
                    .historyDao().searchSuggestions(lowerQuery, 5)
                for (h in history) {
                    if (h.url !in bookmarkUrls) {
                        items.add(
                            SuggestionItem(
                                title = h.title.ifBlank { h.url },
                                subtitle = h.url,
                                type = SuggestionType.HISTORY,
                                url = h.url
                            )
                        )
                    }
                }
            } catch (_: Exception) {}

            // 3. Search engine autocomplete
            try {
                val engineName = Prefs.getSearchEngine(this@MainActivity).displayName
                val searchSuggestions = SearchSuggestionProvider.fetchSuggestions(this@MainActivity, query, 5)
                for (s in searchSuggestions) {
                    items.add(
                        SuggestionItem(
                            title = s,
                            subtitle = engineName,
                            type = SuggestionType.SEARCH,
                            url = ""
                        )
                    )
                }
            } catch (_: Exception) {}

            withContext(Dispatchers.Main) {
                if (items.isEmpty()) {
                    hideSuggestions()
                } else {
                    suggestionAdapter.submit(items)
                    suggestionList.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun hideSuggestions() {
        suggestionList.visibility = View.GONE
        suggestionAdapter.clear()
        suggestionJob?.cancel()
    }

    /**
     * Called when the user taps a suggestion row.
     * - URL suggestions (bookmark/history) navigate directly.
     * - Search suggestions perform a search using the configured engine.
     */
    private fun onSuggestionClicked(item: SuggestionItem) {
        val url = if (item.url.isNotBlank()) item.url else Prefs.buildSearchUrl(this, item.title)
        currentTab()?.webView?.loadUrl(url)
        urlBar.clearFocus()
        hideKeyboard()
        hideSuggestions()
    }

    /**
     * Sets a per-WebView FindListener that updates the match counter.
     * Called from configureWebView for every new tab.
     */
    private fun installFindListener(wv: WebView) {
        wv.setFindListener { activeMatchCount, numberOfMatches, isDoneCounting ->
            if (!isDoneCounting) return@setFindListener
            runOnUiThread {
                if (numberOfMatches > 0) {
                    findResultCount.visibility = View.VISIBLE
                    val idx = (activeMatchCount + 1).coerceAtLeast(1).coerceAtMost(numberOfMatches)
                    findResultCount.text = getString(
                        R.string.find_result_format, idx, numberOfMatches
                    )
                } else {
                    findResultCount.visibility = View.VISIBLE
                    findResultCount.text = getString(R.string.find_no_results)
                }
            }
        }
    }

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }

    /* ============================ desktop mode ============================ */

    /**
     * Toggles the current tab between mobile and desktop User-Agent.
     * Desktop mode forces a desktop UA string + wide viewport, which makes
     * most sites serve their full desktop layout. It does NOT change the
     * WebView profile, so history is still recorded normally.
     */
    private fun toggleDesktopMode(): Boolean {
        val tab = currentTab() ?: return false
        tab.desktopMode = !tab.desktopMode
        applyDesktopMode(tab.webView, tab.desktopMode)
        // Reload so the new UA takes effect immediately.
        tab.webView.reload()
        Toast.makeText(
            this,
            if (tab.desktopMode) R.string.action_desktop_mode else R.string.action_refresh,
            Toast.LENGTH_SHORT
        ).show()
        return tab.desktopMode
    }

    private fun applyDesktopMode(wv: WebView, desktop: Boolean) {
        val s = wv.settings
        if (desktop) {
            s.userAgentString = DESKTOP_UA
            s.useWideViewPort = true
            s.loadWithOverviewMode = true
        } else {
            // Reset to default (mobile) by passing null → WebView uses its default UA.
            s.userAgentString = null
            s.useWideViewPort = true
            s.loadWithOverviewMode = true
        }
    }

    private fun updateDesktopModeMenuCheck(menu: PopupMenu) {
        val tab = currentTab()
        val item = menu.menu.findItem(R.id.opt_desktop_mode)
        item?.isChecked = tab?.desktopMode == true
    }

    private fun updateReaderModeMenuCheck(menu: PopupMenu) {
        val tab = currentTab()
        val item = menu.menu.findItem(R.id.opt_reader_mode)
        val isOn = tab?.readerMode == true
        item?.isChecked = isOn
        // Show explicit On/Off in the title for clarity (like Chrome's
        // "Request desktop site" which shows a checkbox when active).
        item?.title = if (isOn) {
            getString(R.string.action_reader_mode) + " ✓"
        } else {
            getString(R.string.action_reader_mode)
        }
    }

    /* ============================ reader mode ============================ */

    /**
     * Toggles the current tab between normal rendering and a clean
     * "reader mode" view.
     *
     * Reader mode is implemented purely via injected JavaScript that
     * restructures the page's DOM (replaces `document.body.innerHTML` with a
     * narrow, serif-styled container holding the extracted article content).
     * It does NOT navigate — `onPageFinished` is never re-triggered, so the
     * page's history entry is neither duplicated nor replaced. The original
     * body HTML is stashed on `window.__pbOriginalBody` so toggling OFF
     * restores the page exactly.
     */
    /**
     * Toggles the current tab's reader mode. Returns the new state (true = on).
     */
    private fun toggleReaderMode(): Boolean {
        val tab = currentTab() ?: return false
        tab.readerMode = !tab.readerMode
        if (tab.readerMode) {
            // Extract the page HTML via JS and apply reader styling.
            val extractJs = """
            (function() {
                if (!window.__pbOriginalBody) {
                    window.__pbOriginalBody = document.body.innerHTML;
                }
                var candidates = [].slice.call(document.querySelectorAll('article, main, div'));
                var best = null;
                var bestScore = -1;
                candidates.forEach(function(el) {
                    var pCount = el.getElementsByTagName('p').length;
                    if (pCount > bestScore) {
                        bestScore = pCount;
                        best = el;
                    }
                });
                if (!best || bestScore < 1) {
                    best = document.body;
                }
                var inner = best.innerHTML || '';
                document.body.innerHTML =
                    '<div class="__pb_reader_content">' + inner + '</div>';
                document.body.style.background = '#fdf6e3';
                document.body.style.color = '#333';
                document.body.style.fontFamily = "Georgia, serif";
                document.body.style.fontSize = '18px';
                document.body.style.lineHeight = '1.8';
                document.body.style.padding = '16px';
                document.body.style.margin = '0';
                document.body.style.maxWidth = '65ch';
                document.body.style.marginLeft = 'auto';
                document.body.style.marginRight = 'auto';
            })();
            """.trimIndent()
            currentTab()?.webView?.evaluateJavascript(extractJs, null)
            ReaderMode.applyReaderCss(tab.webView)
        } else {
            // Turning OFF: restore the original body snapshot.
            val restoreJs = """
            (function() {
                if (window.__pbOriginalBody !== undefined) {
                    document.body.innerHTML = window.__pbOriginalBody;
                    document.body.style.background = '';
                    document.body.style.color = '';
                    document.body.style.fontFamily = '';
                    document.body.style.fontSize = '';
                    document.body.style.lineHeight = '';
                    document.body.style.padding = '';
                    document.body.style.margin = '';
                    document.body.style.maxWidth = '';
                    document.body.style.marginLeft = '';
                    document.body.style.marginRight = '';
                }
            })();
            """.trimIndent()
            currentTab()?.webView?.evaluateJavascript(restoreJs, null)
            ReaderMode.removeReaderCss(tab.webView)
        }
        Toast.makeText(
            this,
            if (tab.readerMode) R.string.reader_mode_enabled else R.string.reader_mode_disabled,
            Toast.LENGTH_SHORT
        ).show()
        return tab.readerMode
    }

    /* ============================ per-site JavaScript toggle =============== */

    /**
     * Shows a Material dialog with a Switch widget to toggle JavaScript
     * for the current page's origin. Persists via PermissionManager.
     */
    private fun showJavaScriptToggleDialog() {
        val tab = currentTab() ?: return
        val url = tab.webView.url ?: tab.url
        val origin = PermissionManager.normalizeOrigin(url) ?: run {
            Toast.makeText(this, R.string.action_site_javascript, Toast.LENGTH_SHORT).show()
            return
        }
        val currentEnabled = PermissionManager.isJavaScriptEnabled(this, origin)

        // Build dialog view with a Switch widget.
        val switchWidget = Switch(this).apply {
            text = getString(R.string.js_toggle_summary)
            isChecked = currentEnabled
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.js_toggle_title))
            .setView(switchWidget)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newState = switchWidget.isChecked
                PermissionManager.setJavaScriptEnabled(this, origin, newState)
                // Apply immediately and reload so the page respects the new setting.
                tab.webView.settings.javaScriptEnabled = newState
                tab.webView.reload()
                Toast.makeText(
                    this,
                    if (newState) R.string.js_toggle_on else R.string.js_toggle_off,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /* ============================ tab management ========================== */

    private fun newTabId(): Long = tabIdCounter++

    /**
     * Creates and configures a fresh WebView. Used both for user-initiated
     * new tabs and for onCreateWindow (target="_blank" / window.open).
     */
    private fun createConfiguredWebView(): WebView {
        val wv = WebView(this)
        configureWebView(wv)
        return wv
    }

    private fun configureWebView(wv: WebView) {
        val settings = wv.settings
        settings.javaScriptEnabled = Prefs.getJavaScriptEnabled(this)
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        // Multi-window must be ON so target="_blank" / window.open() route
        // through onCreateWindow, where we create a new normal tab.
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.allowFileAccess = false
        settings.allowContentAccess = true

        // Third-party cookie blocking (when ad-block is enabled)
        if (AdBlocker.isEnabled(this)) {
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(wv, false)
        }
        // Mixed content mode: never allow insecure content on secure origins
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        wv.webViewClient = HistoryRecordingWebViewClient(
            onUrlChanged = { url ->
                runOnUiThread {
                    val tab = tabForWebView(wv) ?: return@runOnUiThread
                    tab.url = url
                    if (tab.id == currentTabId) updateOmnibox(url, isSecure(url))
                    // Apply per-site JavaScript setting for the new origin.
                    val origin = PermissionManager.normalizeOrigin(url)
                    if (origin != null) {
                        wv.settings.javaScriptEnabled =
                            PermissionManager.isJavaScriptEnabled(this, origin)
                    }
                }
            },
            onTitleChanged = { title ->
                runOnUiThread {
                    val tab = tabForWebView(wv) ?: return@runOnUiThread
                    tab.title = title
                    refreshTabSwitcher()
                }
            },
            onHistoryRecord = { url, title ->
                recordHistory(url, title)
            },
            // Cosmetic filtering: inject CSS to hide ad/tracker DOM elements after page load
            onCosmeticFilter = { pageUrl ->
                // Must run on the UI thread for evaluateJavascript
                runOnUiThread {
                    if (AdBlocker.isEnabled(this) && Prefs.getCosmeticFiltersEnabled(this)) {
                        val js = AdBlocker.getCosmeticFilterJs(pageUrl)
                        if (js.isNotBlank()) {
                            wv.evaluateJavascript(js, null)
                        }
                    }
                }
            },
            // Ad-blocker: blocks sub-resource requests only. Main-frame
            // navigations are short-circuited inside the WebViewClient BEFORE
            // this lambda is consulted, so every page visit still records.
            shouldBlock = { url, _ -> AdBlocker.shouldBlock(this, url) }
        )

        wv.webChromeClient = TabSupportingChromeClient(
            context = this,
            onProgress = { progress ->
                runOnUiThread {
                    val tab = tabForWebView(wv) ?: return@runOnUiThread
                    tab.isLoading = progress in 1..99
                    if (tab.id == currentTabId) updateProgress(progress)
                }
            },
            onTitleChanged = { title ->
                runOnUiThread {
                    val tab = tabForWebView(wv) ?: return@runOnUiThread
                    tab.title = title
                    if (tab.id == currentTabId) {
                        // title-only update; URL handled by WebViewClient
                    }
                    refreshTabSwitcher()
                }
            },
            onCreateNewWindow = {
                // A page requested a new window (target="_blank" / window.open).
                // onCreateWindow runs on the UI thread, so we can add the tab
                // synchronously and return its WebView for the transport.
                // We create a new NORMAL tab — never a private one.
                val newTab = addTab(url = null, preBuiltWebView = null, switchTo = true, createdByWindow = true)
                newTab.webView
            },
            onFileChooserNeeded = {
                openFileChooser()
            }
        )

        // Long-press link handling → "Open in new tab" context menu.
        wv.setOnLongClickListener {
            val hit = wv.hitTestResult
            val url = hit.extra
            if (url != null && url.startsWith("http")) {
                showLinkContextMenu(url)
                wv.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                true
            } else false
        }

        // Per-tab find-in-page result listener.
        installFindListener(wv)
    }

    private fun tabForWebView(wv: WebView): Tab? = tabs.firstOrNull { it.webView === wv }

    /**
     * Adds a new tab. If [preBuiltWebView] is null a fresh WebView is created.
     * If [url] is non-null it is loaded immediately.
     *
     * New-tab behavior:
     * - External intent (`createdByWindow == false` && `url != null`): load URL.
     * - User-initiated new tab with no URL (`createdByWindow == false` && `url == null`):
     *   load the user's configured New Tab Page ([Prefs.getNewTabPage]).
     * - `onCreateWindow`-created tab (`createdByWindow == true`): do NOT load
     *   anything — the WebView will be used as the transport target for the
     *   pending `window.open()` navigation, which loads the URL itself.
     */
    private fun addTab(
        url: String?,
        preBuiltWebView: WebView? = null,
        switchTo: Boolean = true,
        createdByWindow: Boolean = false
    ): Tab {
        val wv = preBuiltWebView ?: createConfiguredWebView()
        val tab = Tab(id = newTabId(), webView = wv, title = "", url = url ?: "")
        tabs.add(tab)
        webviewContainer.addView(wv)
        if (!createdByWindow && url != null) {
            wv.loadUrl(url)
        } else if (!createdByWindow && url == null) {
            wv.loadUrl(Prefs.getNewTabPage(this))
        }
        if (switchTo) switchToTab(tab.id)
        updateTabCountBadge()
        refreshTabSwitcher()
        return tab
    }

    private fun switchToTab(id: Long) {
        currentTabId = id
        tabs.forEach { it.webView.visibility = if (it.id == id) View.VISIBLE else View.GONE }
        val tab = tabs.firstOrNull { it.id == id } ?: return
        updateOmnibox(tab.url, isSecure(tab.url))
        updateProgress(if (tab.isLoading) progressBar.progress else 0)
        updateNavButtons()
        // Refresh the bookmark star for the newly-active tab.
        updateBookmarkStarState()
    }

    private fun closeTab(id: Long) {
        val idx = tabs.indexOfFirst { it.id == id }
        if (idx == -1) return
        val tab = tabs[idx]
        webviewContainer.removeView(tab.webView)
        tab.webView.destroy()
        tabs.removeAt(idx)
        if (tabs.isEmpty()) {
            addTab(url = Prefs.getHomepage(this))
            hideTabSwitcher()
            return
        }
        if (currentTabId == id) {
            switchToTab(tabs[minOf(idx, tabs.lastIndex)].id)
        }
        updateTabCountBadge()
        refreshTabSwitcher()
    }

    private fun currentTab(): Tab? = tabs.firstOrNull { it.id == currentTabId }

    private fun updateTabCountBadge() {
        val n = tabs.size
        tabCountBadge.text = if (n > 99) "99+" else n.toString()
    }

    private fun updateNavButtons() {
        val wv = currentTab()?.webView
        btnBack.isEnabled = wv?.canGoBack() == true
        btnForward.isEnabled = wv?.canGoForward() == true
        btnBack.alpha = if (btnBack.isEnabled) 1f else 0.35f
        btnForward.alpha = if (btnForward.isEnabled) 1f else 0.35f
    }

    /* ============================ omnibox / progress ====================== */

    private fun updateOmnibox(url: String, secure: Boolean) {
        if (!urlBar.isFocused) {
            urlBar.setText(url)
        }
        omniboxSecurityIcon.isVisible = secure && url.startsWith("https")
        omniboxSearchIcon.isVisible = !secure && url.isBlank()
        updateNavButtons()
    }

    private fun updateProgress(progress: Int) {
        if (progress in 1..99) {
            progressBar.visibility = View.VISIBLE
            progressBar.progress = progress
        } else {
            progressBar.visibility = View.GONE
        }
        if (progress == 100) {
            updateNavButtons()
            // Refresh the bookmark-star state for the newly-loaded page.
            updateBookmarkStarState()
            // Update shield badge with latest blocked count
            updateShieldBadge()
        }
    }

    private fun isSecure(url: String): Boolean = url.startsWith("https://")

    /* ============================ tab switcher ============================ */

    private fun showTabSwitcher() {
        refreshTabSwitcher()
        tabSwitcherOverlay.visibility = View.VISIBLE
        tabSwitcherOverlay.animate().alpha(1f).setDuration(150).start()
    }

    private fun hideTabSwitcher() {
        tabSwitcherOverlay.animate().alpha(0f).setDuration(150).withEndAction {
            tabSwitcherOverlay.visibility = View.GONE
        }.start()
    }

    private fun refreshTabSwitcher() {
        if (!::tabCardsList.isInitialized) return
        tabAdapter.submit(tabs.toList())
        val n = tabs.size
        tabsCountLabel.text = resources.getQuantityString(R.plurals.open_tabs_plural, n, n)
    }

    /* ============================ overflow menu =========================== */

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        // Reflect current tab's desktop-mode + reader-mode state in checkboxes.
        updateDesktopModeMenuCheck(popup)
        updateReaderModeMenuCheck(popup)
        // Force-show icons in the overflow menu (Chrome-style).
        try {
            val fieldMPopup = PopupMenu::class.java.getDeclaredField("mPopup")
            fieldMPopup.isAccessible = true
            val mPopup = fieldMPopup.get(popup)
            mPopup.javaClass
                .getDeclaredMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                .invoke(mPopup, true)
        } catch (_: Exception) {
            // If reflection fails (some OEM ROMs), icons simply won't show — menu still works.
        }
        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.opt_new_tab -> {
                    addTab(url = Prefs.getHomepage(this), switchTo = true)
                    true
                }
                R.id.opt_history -> {
                    startActivity(Intent(this, HistoryActivity::class.java))
                    true
                }
                R.id.opt_downloads -> {
                    openSystemDownloads()
                    true
                }
                R.id.opt_bookmarks -> {
                    startActivity(Intent(this, BookmarksActivity::class.java))
                    true
                }
                R.id.opt_find_in_page -> {
                    showFindInPage()
                    true
                }
                R.id.opt_reader_mode -> {
                    val toggled = toggleReaderMode()
                    item.isChecked = toggled
                    true
                }
                R.id.opt_desktop_mode -> {
                    toggleDesktopMode()
                    true
                }
                R.id.opt_share -> {
                    shareCurrentPage()
                    true
                }
                R.id.opt_permissions -> {
                    startActivity(Intent(this, PermissionsActivity::class.java))
                    true
                }
                R.id.opt_javascript_toggle -> {
                    showJavaScriptToggleDialog()
                    true
                }
                R.id.opt_download_page -> {
                    downloadCurrentPage()
                    true
                }
                R.id.opt_translate -> {
                    val url = currentTab()?.webView?.url ?: currentTab()?.url ?: ""
                    PageTranslator.showTranslateDialog(this, url) { translatedUrl ->
                        // Open the translated URL in a new tab — this is a
                        // normal navigation that records history normally.
                        addTab(url = translatedUrl, switchTo = true)
                    }
                    true
                }
                R.id.opt_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    /**
     * Opens the system Downloads app (Chrome-style "Downloads" menu item).
     */
    private fun openSystemDownloads() {
        try {
            val intent = Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, R.string.action_downloads, Toast.LENGTH_SHORT).show()
        }
    }

    /* ====================== file chooser (upload) ================= */

    /**
     * Opens the system file picker so the user can choose a file to upload.
     *
     * Uses ACTION_GET_CONTENT which shows the standard Android file manager /
     * document picker (including Gallery for images, file manager for docs, etc.).
     * Supports all MIME types so the user can upload images, PDFs, documents, etc.
     */
    private fun openFileChooser() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "image/*",
                    "video/*",
                    "audio/*",
                    "application/pdf",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.*",
                    "text/plain",
                    "text/csv"
                )
            )
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, FILE_CHOOSER_REQUEST_CODE)
    }

    /**
     * Handles the file chooser result. Delivers the selected file URI(s)
     * back to [TabSupportingChromeClient.filePathCallback] so the WebView
     * can complete the file upload.
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            val callback = TabSupportingChromeClient.filePathCallback
            TabSupportingChromeClient.filePathCallback = null
            if (callback == null) return
            if (resultCode == RESULT_OK && data != null) {
                val result = data.data?.let { arrayOf(it) } ?: arrayOf()
                callback.onReceiveValue(result)
            } else {
                callback.onReceiveValue(arrayOf())
            }
        }
    }

    /* ====================== link context menu (long press) ================= */

    private fun showLinkContextMenu(url: String) {
        val options = arrayOf(
            getString(R.string.link_open_new_tab),
            getString(R.string.link_open_bg_tab),
            getString(R.string.link_copy),
            getString(R.string.link_share),
            getString(R.string.action_download_link)
        )
        AlertDialog.Builder(this)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> { addTab(url = url, switchTo = true); hideKeyboard() }
                    1 -> { addTab(url = url, switchTo = false); Toast.makeText(this, R.string.link_open_bg_tab, Toast.LENGTH_SHORT).show() }
                    2 -> copyLink(url)
                    3 -> shareUrl(url)
                    4 -> downloadLink(url)
                }
            }
            .show()
    }

    /**
     * Enqueues a download of [url] via Android's DownloadManager.
     * Requires no extra permission for http/https URLs on API 24+.
     */
    private fun downloadLink(url: String) {
        try {
            val request = android.app.DownloadManager.Request(Uri.parse(url))
                .setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
            request.setDestinationInExternalPublicDir(
                android.os.Environment.DIRECTORY_DOWNLOADS,
                Uri.parse(url).lastPathSegment ?: "download"
            )
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun copyLink(url: String) {
        val clip = ClipData.newPlainText("url", url)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        Toast.makeText(this, R.string.link_copy, Toast.LENGTH_SHORT).show()
    }

    /* ============================ helpers ================================= */

    private fun normalizeInput(input: String): String {
        return when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(" ") || !input.contains(".") ->
                Prefs.buildSearchUrl(this, input)
            else -> "https://$input"
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        try {
            imm.hideSoftInputFromWindow(urlBar.windowToken, 0)
        } catch (_: Exception) {}
    }

    private fun recordHistory(url: String, title: String) {
        ioScope.launch {
            HistoryDatabase.getInstance(applicationContext)
                .historyDao()
                .insert(HistoryEntry(url = url, title = title))
        }
    }

    /**
     * Toggles the bookmark state for the current page.
     * If not bookmarked → add bookmark. If already bookmarked → remove it.
     */
    private fun bookmarkCurrentPage() {
        val wv = currentTab()?.webView ?: return
        val url = wv.url ?: return
        val title = wv.title ?: url
        ioScope.launch {
            val dao = BookmarkDatabase.getInstance(applicationContext).bookmarkDao()
            if (!dao.exists(url)) {
                dao.insert(Bookmark(title = title, url = url))
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.bookmark_added, Toast.LENGTH_SHORT).show()
                    updateBookmarkStarState()
                }
            } else {
                dao.deleteByUrl(url)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, R.string.bookmark_removed, Toast.LENGTH_SHORT).show()
                    updateBookmarkStarState()
                }
            }
        }
    }

    /**
     * Downloads the current page using Android's DownloadManager.
     * Equivalent to Chrome's "Download page" / "Save page as" feature.
     */
    private fun downloadCurrentPage() {
        val url = currentTab()?.webView?.url
        if (url.isNullOrBlank()) {
            Toast.makeText(this, R.string.download_page_no_url, Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val title = currentTab()?.title ?: url
            val fileName = title
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .take(200) + ".html"
            val request = android.app.DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription(url)
                .setNotificationVisibility(
                    android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            Toast.makeText(this, R.string.download_page_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, R.string.download_page_failed, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Updates the bookmark toolbar icon to a filled star when the current
     * page is already bookmarked, and an outline star otherwise. Called
     * on page load completion and on tab switch.
     */
    private fun updateBookmarkStarState() {
        val url = currentTab()?.webView?.url ?: run {
            btnBookmark.setImageResource(R.drawable.ic_bookmark)
            return
        }
        ioScope.launch {
            val exists = BookmarkDatabase.getInstance(applicationContext)
                .bookmarkDao()
                .exists(url)
            withContext(Dispatchers.Main) {
                // Reuse the same drawable; tint indicates state.
                btnBookmark.setImageResource(R.drawable.ic_bookmark)
                btnBookmark.setColorFilter(
                    if (exists) getColor(R.color.accent_blue)
                    else getColor(R.color.icon_default)
                )
            }
        }
    }

    private fun shareCurrentPage() {
        val url = currentTab()?.webView?.url ?: return
        shareUrl(url)
    }

    private fun shareUrl(url: String) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, currentTab()?.title ?: url)
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(share, getString(R.string.action_share)))
    }

    /* ============================ lifecycle =============================== */

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (suggestionList.isVisible) {
            urlBar.clearFocus()
            hideSuggestions()
            return
        }
        if (findActive) {
            hideFindInPage()
            return
        }
        if (tabSwitcherOverlay.isVisible) {
            hideTabSwitcher()
            return
        }
        if (urlBar.isFocused) {
            urlBar.clearFocus()
            hideKeyboard()
            return
        }
        val wv = currentTab()?.webView
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else if (tabs.size > 1) {
            closeTab(currentTabId)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply JS setting in case the user toggled it in Settings.
        tabs.forEach {
            val url = it.webView.url ?: it.url
            val origin = PermissionManager.normalizeOrigin(url)
            it.webView.settings.javaScriptEnabled = if (origin != null) {
                PermissionManager.isJavaScriptEnabled(this, origin)
            } else {
                Prefs.getJavaScriptEnabled(this)
            }
        }
        // Update shield badge state
        updateShieldBadge()
    }

    override fun onPause() {
        super.onPause()
        // Persist the current tab URLs so they can be restored on next launch
        // (Chrome-style "continue where you left off").
        saveCurrentTabs()
        // Persist ad-block stats for this session
        Prefs.setBlockStatsSession(this, AdBlocker.totalBlocked())
    }

    override fun onDestroy() {
        // Save once more in case onPause was skipped (e.g. system kill).
        saveCurrentTabs()
        // Persist final ad-block stats
        Prefs.persistBlockStats(this, AdBlocker.totalBlocked())
        tabs.forEach { it.webView.destroy() }
        tabs.clear()
        super.onDestroy()
    }

    /**
     * Shows a Brave-style shield dialog with blocking stats when the user
     * taps the shield icon in the bottom toolbar.
     */
    private fun showShieldInfo() {
        val blocked = AdBlocker.totalBlocked()
        val lifetime = Prefs.getBlockStatsTotal(this) + blocked
        val enabled = AdBlocker.isEnabled(this)
        val categories = AdBlocker.blockedByCategory()

        val categoryText = categories.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .joinToString("\n") { (cat, count) ->
                "• ${cat.label}: ${count}"
            }

        val message = buildString {
            appendln("Shield: ${if (enabled) "ON" else "OFF"}")
            appendln()
            if (enabled) {
                appendln("Blocked this session: ${formatShieldCount(blocked)}")
                appendln("Lifetime total: ${formatShieldCount(lifetime)}")
                if (categoryText.isNotBlank()) {
                    appendln()
                    appendln("By category:")
                    appendln(categoryText)
                }
                appendln()
                appendln("~2,500 domains + URL pattern matching")
                appendln("Cosmetic filters: ${if (Prefs.getCosmeticFiltersEnabled(this@MainActivity)) "ON" else "OFF"}")
                appendln("HTTPS upgrade: ${if (Prefs.getHttpsUpgradeEnabled(this@MainActivity)) "ON" else "OFF"}")
            } else {
                appendln("Enable ad blocking in Settings → Privacy to activate the shield.")
            }
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_ad_block)
            .setMessage(message.trim())
            .setPositiveButton(android.R.string.ok, null)
            .setNeutralButton("Settings") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .show()
    }

    /**
     * Updates the shield badge to show the blocked count.
     * Called on page load and periodically.
     */
    private fun updateShieldBadge() {
        val enabled = AdBlocker.isEnabled(this)
        val blocked = AdBlocker.totalBlocked()

        if (enabled && blocked > 0) {
            shieldBadge.visibility = View.VISIBLE
            shieldBadge.text = formatShieldCountShort(blocked)
            btnShield.setColorFilter(getColor(R.color.secure_green))
        } else if (enabled) {
            shieldBadge.visibility = View.GONE
            btnShield.setColorFilter(getColor(R.color.secure_green))
        } else {
            shieldBadge.visibility = View.GONE
            btnShield.setColorFilter(getColor(R.color.text_hint))
        }
    }

    private fun formatShieldCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    private fun formatShieldCountShort(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format("%.0fM", count / 1_000_000.0)
            count >= 10_000 -> String.format("%.0fK", count / 1_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    /**
     * Serializes the current tab URLs + active index + desktop flag to
     * SharedPreferences. Called from onPause/onDestroy.
     */
    private fun saveCurrentTabs() {
        if (tabs.isEmpty()) return
        val urls = tabs.map { it.webView.url ?: it.url }
        val idx = tabs.indexOfFirst { it.id == currentTabId }.coerceAtLeast(0)
        val desktop = currentTab()?.desktopMode == true
        Prefs.saveTabs(this, urls, idx, desktop)
    }
}
