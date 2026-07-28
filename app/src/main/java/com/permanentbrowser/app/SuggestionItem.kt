package com.permanentbrowser.app

/**
 * A single autocomplete suggestion shown in the omnibox dropdown.
 *
 * Each suggestion has a display title, a subtitle (URL or extra context),
 * and a type that determines the icon shown alongside it.
 */
data class SuggestionItem(
    val title: String,
    val subtitle: String,
    val type: SuggestionType,
    /** The full URL to navigate to (empty for search suggestions — those use the title). */
    val url: String = ""
)

enum class SuggestionType {
    /** A bookmark matching the query. */
    BOOKMARK,
    /** A history entry matching the query. */
    HISTORY,
    /** A suggestion from the search engine autocomplete API. */
    SEARCH,
}
