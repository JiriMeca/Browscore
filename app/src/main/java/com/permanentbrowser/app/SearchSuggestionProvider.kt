package com.permanentbrowser.app

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches autocomplete suggestions from the user's selected search engine.
 *
 * Supported engines:
 *   - DuckDuckGo:  https://duckduckgo.com/ac/?q=…&type=list   → JSON array of strings
 *   - Google:       https://suggestqueries.google.com/complete/search?client=android&q=…  → JSON array
 *   - Bing:         https://www.bing.com/osjson.aspx?query=…   → JSON array
 *   - Startpage:    Falls back to Google's suggestion endpoint
 *
 * All network calls run on [Dispatchers.IO] and return an empty list on any
 * error (timeout, malformed response, etc.) so the UI never stalls.
 */
object SearchSuggestionProvider {

    private const val TAG = "SearchSuggestion"

    /**
     * Returns up to [limit] suggestion strings for [query] from the currently
     * selected search engine, or an empty list on error.
     */
    suspend fun fetchSuggestions(ctx: android.content.Context, query: String, limit: Int = 5): List<String> {
        if (query.isBlank()) return emptyList()
        val engineId = Prefs.getSearchEngineId(ctx)
        val encodedQuery = URLEncoder.encode(query, "UTF-8")

        val url = when (engineId) {
            "duckduckgo" -> "https://duckduckgo.com/ac/?q=$encodedQuery&type=list"
            "google" -> "https://suggestqueries.google.com/complete/search?client=android&q=$encodedQuery"
            "bing" -> "https://www.bing.com/osjson.aspx?query=$encodedQuery"
            // Startpage has no public suggestion API → use Google as fallback
            "startpage" -> "https://suggestqueries.google.com/complete/search?client=android&q=$encodedQuery"
            else -> "https://duckduckgo.com/ac/?q=$encodedQuery&type=list"
        }

        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 2000
                connection.readTimeout = 2000
                connection.requestMethod = "GET"
                connection.instanceFollowRedirects = true

                val responseCode = connection.responseCode
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Suggestion API returned $responseCode for $engineId")
                    return@withContext emptyList()
                }

                val body = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                parseSuggestions(body, engineId).take(limit)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch suggestions for '$query' ($engineId)", e)
                emptyList()
            }
        }
    }

    /**
     * Different engines format their JSON response differently:
     *   - DuckDuckGo returns a flat JSON array of strings: `["suggestion1", "suggestion2"]`
     *   - Google returns a 2-element array: `["query", ["suggestion1", "suggestion2"]]`
     *   - Bing returns a flat JSON array: `["query", "suggestion1", "suggestion2"]`
     */
    private fun parseSuggestions(body: String, engineId: String): List<String> {
        return try {
            val arr = JSONArray(body)
            when (engineId) {
                "duckduckgo" -> {
                    // Flat array: ["s1", "s2", ...]
                    (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
                }
                "google", "startpage" -> {
                    // ["query", ["s1", "s2", ...]]
                    if (arr.length() >= 2 && arr.optJSONArray(1) != null) {
                        val inner = arr.getJSONArray(1)
                        (0 until inner.length()).mapNotNull {
                            inner.optString(it).takeIf { s -> s.isNotBlank() }
                        }
                    } else emptyList()
                }
                "bing" -> {
                    // ["query", "s1", "s2", ...] — first element is the query itself, skip it
                    (1 until arr.length()).mapNotNull {
                        arr.optString(it).takeIf { s -> s.isNotBlank() }
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse suggestions JSON for $engineId", e)
            emptyList()
        }
    }
}
