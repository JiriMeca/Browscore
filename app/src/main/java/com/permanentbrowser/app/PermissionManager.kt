package com.permanentbrowser.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-site permission manager.
 *
 * Tracks per-origin grants for web-facing permission categories:
 *   - Camera         → web permission RESOURCE_VIDEO_CAPTURE
 *   - Microphone     → RESOURCE_AUDIO_CAPTURE
 *   - Geolocation    → onGeolocationPermissionsShowPrompt
 *   - Notifications  → WebChromeClient PermissionRequest (API 33+)
 *   - JavaScript     → per-site JS toggle (default: enabled)
 *
 * Storage model: a single SharedPreferences JSON blob keyed by origin.
 *
 *   { "https://example.com": { "camera": true, "mic": false, "geo": false, "notif": false, "js": true }, ... }
 *
 * CRITICAL: this class only GRANTS or DENIES web-level permission requests.
 * It does NOT touch history, cookies, or any browsing data — history stays
 * permanent regardless of how permissions are toggled here.
 *
 * @see <a href="https://developer.android.com/reference/android/webkit/PermissionRequest">PermissionRequest</a>
 */
object PermissionManager {

    private const val FILE = "permanent_browser_perms"
    private const val KEY_PERMS = "per_origin_perms"

    enum class WebPermission(val key: String, val label: String) {
        Camera("camera", "Camera"),
        Microphone("mic", "Microphone"),
        Geolocation("geo", "Location"),
        Notifications("notif", "Notifications"),
        JavaScript("js", "JavaScript");

        companion object {
            fun fromKey(k: String): WebPermission? = entries.firstOrNull { it.key == k }
        }
    }

    data class OriginGrant(
        val origin: String,
        val camera: Boolean,
        val mic: Boolean,
        val geo: Boolean,
        val notif: Boolean,
        val js: Boolean
    ) {
        fun isGranted(p: WebPermission): Boolean = when (p) {
            WebPermission.Camera -> camera
            WebPermission.Microphone -> mic
            WebPermission.Geolocation -> geo
            WebPermission.Notifications -> notif
            WebPermission.JavaScript -> js
        }
    }

    /* ---------------- load / save ---------------- */

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun readAll(ctx: Context): JSONObject {
        val raw = prefs(ctx).getString(KEY_PERMS, null) ?: return JSONObject()
        return try { JSONObject(raw) } catch (_: Throwable) { JSONObject() }
    }

    private fun writeAll(ctx: Context, obj: JSONObject) {
        prefs(ctx).edit().putString(KEY_PERMS, obj.toString()).apply()
    }

    /* ---------------- public API ---------------- */

    /** Normalize an origin: strip path, return scheme://host[:port] form. */
    fun normalizeOrigin(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        // Determine the scheme from the URL; default to https if missing.
        val hasHttp = trimmed.startsWith("http://")
        val hasHttps = trimmed.startsWith("https://")
        val withScheme = when {
            hasHttp || hasHttps -> trimmed
            else -> "https://$trimmed"
        }
        return try {
            val u = java.net.URI(withScheme)
            val host = u.host ?: return null
            val scheme = if (hasHttp) "http" else "https"
            val port = u.port.takeIf { it > 0 && it != 443 && it != 80 }
            if (port != null) "$scheme://$host:$port" else "$scheme://$host"
        } catch (_: Throwable) {
            null
        }
    }

    fun isGranted(ctx: Context, origin: String, perm: WebPermission): Boolean {
        val o = normalizeOrigin(origin) ?: return false
        val all = readAll(ctx)
        val perOrigin = all.optJSONObject(o) ?: return false
        return perOrigin.optBoolean(perm.key, false)
    }

    fun setGranted(ctx: Context, origin: String, perm: WebPermission, granted: Boolean) {
        val o = normalizeOrigin(origin) ?: return
        val all = readAll(ctx)
        val perOrigin = all.optJSONObject(o) ?: JSONObject()
        perOrigin.put(perm.key, granted)
        all.put(o, perOrigin)
        writeAll(ctx, all)
    }

    /**
     * Checks whether JavaScript is enabled for the given origin.
     * Returns true by default (JS is on unless explicitly disabled).
     */
    fun isJavaScriptEnabled(ctx: Context, origin: String): Boolean {
        val o = normalizeOrigin(origin) ?: return true
        val all = readAll(ctx)
        val perOrigin = all.optJSONObject(o) ?: return true
        // If the key exists, use its value; otherwise default to true.
        return if (perOrigin.has(WebPermission.JavaScript.key)) {
            perOrigin.optBoolean(WebPermission.JavaScript.key, true)
        } else {
            true
        }
    }

    /**
     * Sets whether JavaScript is enabled for the given origin.
     * Persists in the same SharedPreferences JSON blob as other permissions.
     */
    fun setJavaScriptEnabled(ctx: Context, origin: String, enabled: Boolean) {
        setGranted(ctx, origin, WebPermission.JavaScript, enabled)
    }

    /** Revoke every web permission for an origin (e.g. "forget this site's grants"). */
    fun revokeOrigin(ctx: Context, origin: String) {
        val o = normalizeOrigin(origin) ?: return
        val all = readAll(ctx)
        all.remove(o)
        writeAll(ctx, all)
    }

    /** List every origin that has at least one grant recorded. */
    fun listOrigins(ctx: Context): List<OriginGrant> {
        val all = readAll(ctx)
        val out = mutableListOf<OriginGrant>()
        val keys = all.keys()
        while (keys.hasNext()) {
            val o = keys.next()
            val perOrigin = all.optJSONObject(o) ?: continue
            out += OriginGrant(
                origin = o,
                camera = perOrigin.optBoolean(WebPermission.Camera.key, false),
                mic = perOrigin.optBoolean(WebPermission.Microphone.key, false),
                geo = perOrigin.optBoolean(WebPermission.Geolocation.key, false),
                notif = perOrigin.optBoolean(WebPermission.Notifications.key, false),
                js = if (perOrigin.has(WebPermission.JavaScript.key)) {
                    perOrigin.optBoolean(WebPermission.JavaScript.key, true)
                } else {
                    true
                }
            )
        }
        return out.sortedBy { it.origin }
    }

    /** True if at least one origin currently has the given permission granted. */
    fun hasAnyGrant(ctx: Context, perm: WebPermission): Boolean =
        listOrigins(ctx).any { it.isGranted(perm) }
}
