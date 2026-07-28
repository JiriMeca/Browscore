package com.permanentbrowser.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.WebView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * Captures the content of a [WebView] as a PNG and saves it to both the
 * app cache and the public `Pictures/Screenshots` directory.
 *
 * Two capture modes are available:
 * - [captureWebView]: captures the visible viewport only (quick).
 * - [captureFullPage]: captures the entire scrollable content of the page
 *   by stitching viewport-sized chunks together as it scrolls through the
 *   page.
 *
 * GUARANTEE: this class only reads pixels from the WebView via [Canvas] —
 * it does NOT touch browsing history, cookies, cache, WebStorage, or any
 * other persistent browser state. Capturing a screenshot is a pure UI
 * operation; nothing is loaded, cleared, or modified. No calls to
 * `loadUrl`, `clearHistory`, `clearCache`, `clearCookies`, or
 * `WebStorage.deleteAllData` are ever made.
 *
 * Storage strategy:
 *  - API 29+ (scoped storage): uses [MediaStore.Images.Media] with
 *    [RELATIVE_PATH] = `Pictures/Screenshots`. No permission needed.
 *  - API < 29: writes directly to
 *    `Environment.getExternalStoragePublicDirectory(PICTURES)/Screenshots`.
 *    Requires `WRITE_EXTERNAL_STORAGE` (declared in the manifest with
 *    `maxSdkVersion=28`).
 */
object ScreenshotCapture {

    /**
     * Maximum bitmap dimension (pixels). Android's max texture size on most
     * devices is 16384. We use this as a hard cap for full-page capture.
     */
    private const val MAX_BITMAP_DIMENSION = 16384

    /**
     * Captures the visible viewport of [webView] as a PNG. Returns the
     * cache file on success, null on any failure. The caller is responsible
     * for showing a toast — see [showScreenshotSavedToast].
     */
    fun captureWebView(
        context: Context,
        webView: WebView,
        filename: String = "screenshot_${System.currentTimeMillis()}"
    ): File? {
        return try {
            val bitmap = captureViewportBitmap(webView) ?: return null
            val file = saveBitmapToPictures(context, bitmap, "$filename.png")
            try { bitmap.recycle() } catch (_: Exception) { /* already recycled */ }
            file
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Captures the entire scrollable content of [webView] as a tall PNG.
     * Returns the cache file on success, null on any failure.
     *
     * The bitmap height is computed as `(contentHeight * scale).toInt()`.
     * If the page is too tall (exceeds [MAX_BITMAP_DIMENSION]) or
     * contentHeight/scale are invalid, falls back to [captureWebView]
     * (viewport-only) and shows a hint toast.
     *
     * Implementation: scrolls through the page in viewport-sized chunks,
     * drawing each chunk onto a tall canvas. The canvas is translated
     * downward by `viewportHeight` after each draw so chunks stack
     * vertically. The original scroll position is restored at the end.
     *
     * GUARANTEE: only calls `View.draw(Canvas)`. Never touches history,
     * cookies, cache, or calls `loadUrl`.
     */
    fun captureFullPage(
        context: Context,
        webView: WebView,
        filename: String = "fullpage_${System.currentTimeMillis()}"
    ): File? {
        val contentHeight = webView.contentHeight
        val scale = webView.scale
        val width = webView.width
        val viewportHeight = webView.height

        // Validate dimensions — fall back to viewport if anything is off.
        if (contentHeight <= 0 || scale <= 0f || width <= 0 || viewportHeight <= 0) {
            Toast.makeText(context, R.string.screenshot_too_tall, Toast.LENGTH_SHORT).show()
            return captureWebView(context, webView, filename)
        }

        val tallHeight = (contentHeight * scale).toInt()

        // Cap at max texture size — fall back if the page is too tall.
        if (tallHeight > MAX_BITMAP_DIMENSION - width || tallHeight <= viewportHeight) {
            Toast.makeText(context, R.string.screenshot_too_tall, Toast.LENGTH_SHORT).show()
            return captureWebView(context, webView, filename)
        }

        return try {
            val bitmap = Bitmap.createBitmap(width, tallHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Save the current scroll position to restore afterwards.
            val prevScrollY = webView.scrollY

            // Stitch viewport-sized chunks by scrolling through the page.
            // Each chunk is drawn onto the canvas at its vertical offset,
            // then the canvas origin is translated down by viewportHeight.
            var yOffset = 0
            while (yOffset < tallHeight) {
                // Convert device-pixel y-offset back to CSS pixels for scrollTo.
                val scrollInCssPx = (yOffset / scale).toInt()
                webView.scrollTo(0, scrollInCssPx)
                // Force a synchronous invalidate so the WebView reflects the
                // new scroll position before we draw. (View.draw is itself
                // synchronous, so the next call paints the current state.)
                webView.invalidate()
                webView.draw(canvas)
                canvas.translate(0f, viewportHeight.toFloat())
                yOffset += viewportHeight
            }

            // Restore the original scroll position.
            webView.scrollTo(0, prevScrollY)

            val file = saveBitmapToPictures(context, bitmap, "$filename.png")
            try { bitmap.recycle() } catch (_: Exception) { /* already recycled */ }
            file
        } catch (_: Exception) {
            // OOM or other failure — fall back to viewport capture.
            captureWebView(context, webView, filename)
        }
    }

    /**
     * Draws the WebView's visible viewport onto a fresh ARGB_8888 bitmap.
     * Falls back to null if the WebView hasn't been laid out yet
     * (width/height <= 0).
     */
    private fun captureViewportBitmap(webView: WebView): Bitmap? {
        return try {
            val w = webView.width
            val h = webView.height
            if (w <= 0 || h <= 0) return null
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Saves [bitmap] as a PNG to both the app-private cache and the public
     * `Pictures/Screenshots` directory. Returns the cache [File] on success,
     * null on failure.
     *
     * On API 29+ this uses scoped storage via MediaStore — no permission
     * needed. On older APIs, it falls back to the legacy
     * `getExternalStoragePublicDirectory` path (requires
     * `WRITE_EXTERNAL_STORAGE`, declared with `maxSdkVersion=28`).
     */
    private fun saveBitmapToPictures(
        context: Context,
        bitmap: Bitmap,
        displayName: String
    ): File? {
        return try {
            // 1. Save to app-private cache for quick re-access (no permission).
            val cacheDir = File(context.cacheDir, "screenshots")
            cacheDir.mkdirs()
            val cacheFile = File(cacheDir, displayName)
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            // 2. Copy to public Pictures/Screenshots so the user can find it.
            writeToMediaStore(context, bitmap, displayName)
            cacheFile
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Writes [bitmap] to the public Pictures/Screenshots directory.
     *
     * On API 29+ this uses scoped storage via MediaStore — no permission
     * needed. On older APIs, it falls back to the legacy
     * `getExternalStoragePublicDirectory` path.
     */
    private fun writeToMediaStore(context: Context, bitmap: Bitmap, displayName: String) {
        val mimeType = "image/png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Screenshots")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
            ) ?: return
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            // Mark the entry as complete — visible to other apps.
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES
            )
            val screenshotsDir = File(dir, "Screenshots")
            screenshotsDir.mkdirs()
            val file = File(screenshotsDir, displayName)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        }
    }

    /**
     * Shows a toast confirming the screenshot result.
     *
     * @param context the context for showing the toast.
     * @param file the cache file returned by [captureWebView] or
     *   [captureFullPage] — null means capture failed.
     * @param fullPage if true, uses the full-page-specific success string.
     */
    fun showScreenshotSavedToast(context: Context, file: File?, fullPage: Boolean = false) {
        val resId = when {
            file == null -> R.string.screenshot_failed
            fullPage -> R.string.screenshot_fullpage_saved
            else -> R.string.screenshot_saved
        }
        Toast.makeText(context, resId, Toast.LENGTH_SHORT).show()
    }
}
