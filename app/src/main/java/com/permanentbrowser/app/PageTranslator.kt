package com.permanentbrowser.app

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import java.net.URLEncoder

/**
 * Translates the current page by opening Google Translate's URL-translation
 * endpoint in a new browser tab. The translation runs entirely inside
 * Google Translate's web UI — no in-app parsing of the page happens.
 *
 * A network pre-flight check is performed before showing the language
 * picker dialog. If the device is offline, a toast is shown and the
 * function returns early — since Google Translate is a remote service,
 * attempting to load the translate URL without connectivity would only
 * show an error page.
 *
 * GUARANTEE: this class only constructs a URL and asks the activity to open
 * it in a new tab via [onTranslate]. The new tab loads the Google Translate
 * URL using the same WebViewClient that records every navigation — so the
 * translation URL itself becomes a permanent history entry, exactly like
 * any other page visit. Nothing about the original page is modified,
 * cleared, or duplicated.
 *
 * Google Translate's URL form is:
 *   https://translate.google.com/translate?sl=auto&tl=<lang>&u=<encoded-url>
 *
 * The `sl=auto` asks Google to auto-detect the source language; `tl` is
 * the target language code (e.g. "en", "es", "fr").
 */
object PageTranslator {

    /** Display name → ISO 639-1 code. */
    private val LANGUAGES = linkedMapOf(
        "English" to "en",
        "Spanish" to "es",
        "French" to "fr",
        "German" to "de",
        "Chinese" to "zh",
        "Japanese" to "ja",
        "Korean" to "ko",
        "Russian" to "ru",
        "Portuguese" to "pt",
        "Italian" to "it"
    )

    /**
     * Builds the Google Translate URL for the given [sourceUrl] and
     * [targetLang] code. [sourceUrl] is URL-encoded so query-string
     * parameters survive intact.
     */
    fun buildTranslateUrl(sourceUrl: String, targetLang: String = "en"): String {
        val encoded = URLEncoder.encode(sourceUrl, "UTF-8")
        return "https://translate.google.com/translate?sl=auto&tl=$targetLang&u=$encoded"
    }

    /**
     * Checks whether the device currently has an active network connection.
     *
     * On API 23+ uses [ConnectivityManager.getActiveNetwork] +
     * [ConnectivityManager.getNetworkCapabilities]. On older APIs falls
     * back to the deprecated [ConnectivityManager.getActiveNetworkInfo].
     *
     * @return `true` if a network with internet capability is available.
     */
    fun isNetworkAvailable(activity: Activity): Boolean {
        val cm = activity.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            return info != null && info.isConnected
        }
    }

    /**
     * Shows a Material dialog with a language picker. On confirm, calls
     * [onTranslate] with the constructed translate URL — the activity is
     * responsible for opening it in a new tab.
     *
     * A network pre-flight check is performed first. If [currentUrl] is
     * blank or not an http(s) URL, the dialog is skipped and a toast is
     * shown instead. If no network is available, a toast is shown and the
     * function returns early.
     */
    fun showTranslateDialog(
        activity: Activity,
        currentUrl: String,
        onTranslate: (String) -> Unit
    ) {
        if (currentUrl.isBlank() || !currentUrl.startsWith("http")) {
            Toast.makeText(
                activity,
                activity.getString(R.string.translate_no_url),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (!isNetworkAvailable(activity)) {
            Toast.makeText(
                activity,
                activity.getString(R.string.translate_no_network),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val langNames = LANGUAGES.keys.toTypedArray()
        val spinner = Spinner(activity).apply {
            adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_item,
                langNames
            ).also {
                it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        // Pad the spinner so the dialog doesn't feel cramped.
        val pad = (16 * activity.resources.displayMetrics.density).toInt()
        spinner.setPadding(pad, pad, pad, 0)

        AlertDialog.Builder(activity)
            .setTitle(R.string.translate_title)
            .setMessage(R.string.translate_select_lang)
            .setView(spinner)
            .setPositiveButton(R.string.translate_confirm) { _, _ ->
                val selectedLang = LANGUAGES[spinner.selectedItem as String] ?: "en"
                onTranslate(buildTranslateUrl(currentUrl, selectedLang))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
