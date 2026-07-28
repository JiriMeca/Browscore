package com.permanentbrowser.app

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON export / import for bookmarks.
 *
 * ## Format (v2)
 *
 * The exported file is a JSON object of the shape:
 *
 * ```json
 * {
 *   "version": 2,
 *   "exportedAt": 1717320000000,
 *   "bookmarks": [
 *     {
 *       "title": "DuckDuckGo",
 *       "url": "https://duckduckgo.com/",
 *       "folder": "Search",
 *       "createdAt": 1717320000000,
 *       "sortOrder": 0
 *     },
 *     ...
 *   ]
 * }
 * ```
 *
 * - `version` is the schema version of the EXPORT FORMAT (independent of the
 *   Room DB version). Currently 2. Bumping it requires a migration step in
 *   [importFromJson]. Old exports with `version: 1` will always be readable
 *   by future app versions (v1 files lack `sortOrder` — defaults to 0).
 * - `exportedAt` is epoch milliseconds — informational only.
 * - Each bookmark entry stores `title`, `url`, `folder`, `createdAt`, and
 *   `sortOrder`. The primary key `id` is deliberately NOT exported: on import,
 *   each bookmark is inserted with `id = 0` so Room assigns a fresh PK. This
 *   avoids collisions with already-existing bookmarks and makes import
 *   idempotent across devices.
 *
 * ## Why org.json?
 *
 * The project already uses `org.json.JSONArray` / `JSONObject` in
 * [Prefs] for tab persistence — re-using it here means no new Gradle
 * dependency (no Gson, no kotlinx-serialization).
 *
 * ## Throws
 *
 * [importFromJson] throws [IllegalArgumentException] with a clear message
 * if the JSON is malformed, the top-level object is missing required keys,
 * or the schema `version` is unsupported. Callers should catch and surface
 * the message to the user (e.g. in a Toast).
 */
object BookmarkIO {

    /** Current schema version of the export format. */
    const val FORMAT_VERSION = 2

    /**
     * Serializes a list of bookmarks to a JSON string suitable for
     * saving to a file or sharing.
     *
     * The output is pretty-printed (indent 2) for human readability.
     * Includes the `sortOrder` field (v2 format) so manual drag-reorder
     * positions survive export/import round-trips.
     */
    fun exportToJson(bookmarks: List<Bookmark>): String {
        val arr = JSONArray()
        for (b in bookmarks) {
            val obj = JSONObject()
            obj.put("title", b.title)
            obj.put("url", b.url)
            obj.put("folder", b.folder)
            obj.put("createdAt", b.createdAt)
            obj.put("sortOrder", b.sortOrder)
            arr.put(obj)
        }
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("bookmarks", arr)
        return root.toString(2)
    }

    /**
     * Parses a JSON string produced by [exportToJson] back into a list of
     * [Bookmark] objects.
     *
     * Accepts both v1 (no `sortOrder` — defaults to 0) and v2 formats.
     * Rejects v3+ with an error message.
     *
     * Each returned bookmark has `id = 0` so Room reassigns a fresh
     * primary key on insert (see [BookmarkDao.insert]).
     *
     * @throws IllegalArgumentException if the JSON is malformed, the
     *   schema version is unsupported, or a required field is missing.
     */
    fun importFromJson(json: String): List<Bookmark> {
        val root: JSONObject = try {
            JSONObject(json)
        } catch (e: JSONException) {
            throw IllegalArgumentException("Not valid JSON: ${e.message}")
        }

        val version = root.optInt("version", -1)
        if (version < 1) {
            throw IllegalArgumentException("Missing or invalid 'version' field")
        }
        if (version > FORMAT_VERSION) {
            throw IllegalArgumentException(
                "Export format version $version is newer than this app supports ($FORMAT_VERSION)"
            )
        }

        val arr: JSONArray = root.optJSONArray("bookmarks")
            ?: throw IllegalArgumentException("Missing 'bookmarks' array")

        val out = mutableListOf<Bookmark>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i)
                ?: throw IllegalArgumentException("Bookmark entry #$i is not an object")
            val title = item.optString("title")
            val url = item.optString("url")
            if (url.isBlank()) {
                // Skip silently — a bookmark without a URL is useless.
                // (We deliberately don't throw here so a single bad entry
                // doesn't abort an otherwise-valid import.)
                continue
            }
            val folder = item.optString("folder")
            val createdAt = item.optLong("createdAt", System.currentTimeMillis())
            // v1 files have no "sortOrder" field; optInt returns 0 by default.
            val sortOrder = item.optInt("sortOrder", 0)
            out.add(
                Bookmark(
                    id = 0,
                    title = title,
                    url = url,
                    folder = folder,
                    createdAt = createdAt,
                    sortOrder = sortOrder
                )
            )
        }
        return out
    }
}
