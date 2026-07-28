package com.permanentbrowser.app

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A lightweight, Chrome-style search Activity.
 *
 * This is the equivalent of Chrome's `BrowserSearchWidgetSearchActivity`.
 * It can be launched by:
 *   - The home screen search widget
 *   - Third-party widget makers (KWGT, Tasker, MacroDroid) via
 *     `com.permanentbrowser.app.ACTION_SEARCH`
 *   - Android global search (`ACTION_WEB_SEARCH`, `ACTION_SEARCH`)
 *   - Text sharing (`ACTION_SEND` with `text/plain`)
 *   - Direct intent with `EXTRA_SEARCH_QUERY` or `SearchManager.QUERY`
 *
 * Incoming extras for pre-filling:
 *   - `EXTRA_SEARCH_QUERY` ("com.permanentbrowser.app.SEARCH_QUERY")
 *   - `SearchManager.QUERY`
 *   - "query" (string extra)
 *   - `Intent.EXTRA_TEXT` (from ACTION_SEND)
 *
 * All other apps can launch this with:
 * ```
 *   Intent("com.permanentbrowser.app.ACTION_SEARCH")
 *     .setPackage("com.permanentbrowser.app")
 *     .putExtra("query", "hello world")
 * ```
 */
class SearchActivity : AppCompatActivity() {

    companion object {
        /** The search query string passed back to MainActivity. */
        const val EXTRA_SEARCH_QUERY = "com.permanentbrowser.app.SEARCH_QUERY"
        /** Custom action for third-party widgets and shortcuts. */
        const val ACTION_SEARCH = "com.permanentbrowser.app.ACTION_SEARCH"
    }

    private lateinit var searchInput: EditText
    private lateinit var suggestionList: RecyclerView
    private lateinit var clearButton: ImageView
    private val suggestionAdapter = SuggestionAdapter(
        onSuggestionClick = { item -> onSuggestionClicked(item) }
    )
    private var suggestionJob: Job? = null
    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = getColor(R.color.browser_bg)
        setContentView(R.layout.activity_search)

        // Transparent nav bar
        window.navigationBarColor = Color.TRANSPARENT

        searchInput = findViewById(R.id.search_input)
        suggestionList = findViewById(R.id.search_suggestion_list)
        clearButton = findViewById(R.id.search_clear_btn)

        suggestionList.layoutManager = LinearLayoutManager(this)
        suggestionList.adapter = suggestionAdapter

        wireSearchInput()
        wireClearButton()

        // Extract the incoming query from whatever source it came from.
        val prefill = extractQueryFromIntent(intent)
        if (!prefill.isNullOrBlank()) {
            searchInput.setText(prefill)
            searchInput.setSelection(prefill.length)
            // Trigger autocomplete immediately for the pre-filled text
            onSearchTextChanged()
        } else {
            // No pre-fill — show keyboard immediately
            searchInput.post {
                searchInput.requestFocus()
                showKeyboard(searchInput)
            }
        }
    }

    /**
     * Handles NEW intents delivered while the activity is already running
     * (e.g. the user taps a shortcut while search is open).
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val prefill = extractQueryFromIntent(intent)
        if (!prefill.isNullOrBlank()) {
            searchInput.setText(prefill)
            searchInput.setSelection(prefill.length)
            onSearchTextChanged()
        }
    }

    /**
     * Extracts a search query from the incoming intent.
     *
     * Checks (in priority order):
     *   1. EXTRA_SEARCH_QUERY (our own constant)
     *   2. SearchManager.QUERY (Android standard)
     *   3. "query" extra (for simple third-party integrations)
     *   4. Intent.EXTRA_TEXT (from ACTION_SEND / share)
     */
    private fun extractQueryFromIntent(intent: Intent?): String? {
        if (intent == null) return null

        // 1. Our own constant
        val ourQuery = intent.getStringExtra(EXTRA_SEARCH_QUERY)
        if (!ourQuery.isNullOrBlank()) return ourQuery

        // 2. Android SearchManager standard
        val smQuery = intent.getStringExtra(SearchManager.QUERY)
        if (!smQuery.isNullOrBlank()) return smQuery

        // 3. Simple "query" extra for easy third-party integration
        val simpleQuery = intent.getStringExtra("query")
        if (!simpleQuery.isNullOrBlank()) return simpleQuery

        // 4. Shared text (ACTION_SEND)
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (!sharedText.isNullOrBlank()) return sharedText

        return null
    }

    private fun wireSearchInput() {
        // Keyboard "Go" / Enter submits the search
        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            ) {
                submitSearch()
                true
            } else false
        }

        // Show/hide clear button and trigger autocomplete
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                clearButton.isVisible = s?.isNotEmpty() == true
            }
            override fun afterTextChanged(s: Editable?) {
                onSearchTextChanged()
            }
        })
    }

    private fun wireClearButton() {
        clearButton.setOnClickListener {
            searchInput.text.clear()
            searchInput.requestFocus()
            showKeyboard(searchInput)
        }
    }

    /* ============================ autocomplete ============================ */

    private fun onSearchTextChanged() {
        val query = searchInput.text.toString().trim()
        if (query.isBlank()) {
            hideSuggestions()
            return
        }

        suggestionJob?.cancel()

        suggestionJob = ioScope.launch {
            delay(150) // debounce

            val items = mutableListOf<SuggestionItem>()
            val lowerQuery = query.lowercase()

            // 1. Bookmarks
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

            // 2. History (deduplicate bookmarks)
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
                val engineName = Prefs.getSearchEngine(this@SearchActivity).displayName
                val searchSuggestions = SearchSuggestionProvider.fetchSuggestions(this@SearchActivity, query, 5)
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

    private fun onSuggestionClicked(item: SuggestionItem) {
        val url = if (item.url.isNotBlank()) item.url else Prefs.buildSearchUrl(this, item.title)
        launchBrowser(url)
    }

    /* ============================ navigation ============================== */

    private fun submitSearch() {
        val text = searchInput.text.toString().trim()
        if (text.isEmpty()) return
        val url = normalizeInput(text)
        launchBrowser(url)
    }

    private fun normalizeInput(input: String): String {
        return when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(" ") || !input.contains(".") ->
                Prefs.buildSearchUrl(this, input)
            else -> "https://$input"
        }
    }

    private fun launchBrowser(url: String) {
        hideKeyboard()
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = android.net.Uri.parse(url)
            // Use NEW_TASK so it opens in the existing MainActivity task.
            // Do NOT use CLEAR_TOP — that would destroy all open tabs.
            // singleTop on MainActivity ensures onNewIntent() is called.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    /* ============================ back press ============================== */

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (suggestionList.isVisible) {
            hideSuggestions()
            return
        }
        hideKeyboard()
        super.onBackPressed()
    }

    /* ============================ helpers ================================ */

    private fun showKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        try {
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        } catch (_: Exception) {}
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        try {
            imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
        } catch (_: Exception) {}
    }
}
