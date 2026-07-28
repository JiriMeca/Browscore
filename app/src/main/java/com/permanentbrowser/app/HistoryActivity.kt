package com.permanentbrowser.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryActivity : AppCompatActivity() {

    private lateinit var list: RecyclerView
    private lateinit var emptyView: View
    private lateinit var searchInput: EditText
    private lateinit var btnClearSearch: ImageButton
    private val adapter = HistoryAdapter { entry ->
        // Tapping a history entry loads it back in the browser as a new tab.
        val intent = Intent(this, MainActivity::class.java).apply {
            data = android.net.Uri.parse(entry.url)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
    }

    private val ioScope = CoroutineScope(Dispatchers.IO + Job())
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<ImageButton>(R.id.btn_back_nav).setOnClickListener { finish() }
        list = findViewById(R.id.history_list)
        emptyView = findViewById(R.id.empty_history)
        searchInput = findViewById(R.id.history_search)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        // Live search: debounced via coroutines.
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClearSearch.isVisible = !s.isNullOrBlank()
            }
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty().trim()
                searchJob?.cancel()
                searchJob = ioScope.launch {
                    kotlinx.coroutines.delay(200) // debounce 200ms
                    val entries = if (q.isEmpty()) {
                        HistoryDatabase.getInstance(applicationContext).historyDao().getAll()
                    } else {
                        HistoryDatabase.getInstance(applicationContext).historyDao().search(q)
                    }
                    withContext(Dispatchers.Main) { showEntries(entries) }
                }
            }
        })

        btnClearSearch.setOnClickListener {
            searchInput.setText("")
            searchInput.requestFocus()
        }

        loadHistory()
    }

    private fun loadHistory() {
        searchJob?.cancel()
        searchJob = ioScope.launch {
            val entries = HistoryDatabase.getInstance(applicationContext)
                .historyDao()
                .getAll()
            withContext(Dispatchers.Main) { showEntries(entries) }
        }
    }

    private fun showEntries(entries: List<HistoryEntry>) {
        if (entries.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            list.visibility = View.GONE
        } else {
            emptyView.visibility = View.GONE
            list.visibility = View.VISIBLE
            adapter.submit(entries)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ioScope.cancel()
    }
}
