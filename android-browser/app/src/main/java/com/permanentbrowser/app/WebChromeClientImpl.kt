package com.permanentbrowser.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * A WebChromeClient attached to EVERY tab's WebView.
 *
 * Responsibilities:
 *   - Update the shared progress bar as each tab loads.
 *   - Handle onCreateWindow: when a page calls window.open() or a link with
 *     target="_blank" is tapped, create a NEW TAB (a normal-profile WebView)
 *     and hand it to the transport. The new tab records history just like
 *     every other tab — there is no private browsing path.
 *   - Handle onPermissionRequest (Round 10): when a page requests camera /
 *     microphone / etc., consult [PermissionManager] for the per-origin grant.
 *     If no grant is recorded, show a Material dialog asking the user.
 *
 * NOTE: multi-window support is required for "Open in new tab" and for
 * target="_blank" links to work. It does NOT enable incognito — every new
 * window is a normal WebView using the same shared profile.
 */
class TabSupportingChromeClient(
    private val context: Context,
    private val onProgress: (Int) -> Unit,
    private val onTitleChanged: (String) -> Unit,
    private val onCreateNewWindow: () -> WebView
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgress(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        if (!title.isNullOrBlank()) onTitleChanged(title)
    }

    override fun getVisitedHistory(callback: ValueCallback<Array<String>>?) {
        // We keep our own history database; we do not feed WebView's internal
        // history. This is intentional — our history is the permanent record.
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        val newWebView = onCreateNewWindow()
        val transport = resultMsg?.obj as? WebView.WebViewTransport
        transport?.webView = newWebView
        resultMsg?.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView?) {
        // window.close() from JS — handled by the tab manager via the
        // MainActivity reference. Here we just no-op; tab closing is done
        // explicitly by the user via the tab switcher.
    }

    /**
     * Round 10 — Per-site permission manager entry point.
     *
     * When a web page requests camera/microphone/etc. via the W3C Permissions API,
     * consult [PermissionManager] for the saved per-origin grant. If no grant is
     * recorded, prompt the user with a Material dialog and persist their choice.
     *
     * IMPORTANT: this only grants/denies the WEB-level permission. The
     * corresponding Android runtime permission (e.g. CAMERA) must also be
     * granted at the OS level — that's handled separately by MainActivity
     * before we get here.
     */
    override fun onPermissionRequest(request: PermissionRequest?) {
        request ?: return
        val origin = request.origin?.toString() ?: return
        val normalizedOrigin = PermissionManager.normalizeOrigin(origin) ?: origin
        val resources = request.resources

        // Map web PermissionRequest resources to WebPermission enum
        val requested = mutableListOf<PermissionManager.WebPermission>()
        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in resources) {
            requested += PermissionManager.WebPermission.Camera
        }
        if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in resources) {
            requested += PermissionManager.WebPermission.Microphone
        }
        // PROTECTED_MEDIA_ID is API 21+ — we don't expose it in the UI.
        // MIDDLEROOT and other advanced resources are denied by default.

        if (requested.isEmpty()) {
            // No supported resources — deny silently.
            request.deny()
            return
        }

        // Check OS-level permission first. If the OS hasn't granted it,
        // the web grant is meaningless — deny the web request and let
        // MainActivity prompt for the OS-level permission next time.
        val missingOsPermission = requested.any { perm ->
            !hasOsPermission(perm)
        }
        if (missingOsPermission) {
            request.deny()
            return
        }

        // Check stored grants. If all requested perms are already granted
        // for this origin, allow without prompting.
        val allGranted = requested.all { perm ->
            PermissionManager.isGranted(context, normalizedOrigin, perm)
        }
        if (allGranted) {
            request.grant(requested.toResourceArray())
            return
        }

        // Need to prompt — show a Material dialog. Persist choice.
        val permLabels = requested.joinToString(", ") { it.label }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.perms_dialog_title))
            .setMessage(context.getString(R.string.perms_dialog_message, normalizedOrigin, permLabels))
            .setNegativeButton(context.getString(R.string.perms_dialog_deny)) { _, _ ->
                requested.forEach { perm ->
                    PermissionManager.setGranted(context, normalizedOrigin, perm, false)
                }
                request.deny()
            }
            .setPositiveButton(context.getString(R.string.perms_dialog_allow)) { _, _ ->
                requested.forEach { perm ->
                    PermissionManager.setGranted(context, normalizedOrigin, perm, true)
                }
                request.grant(requested.toResourceArray())
            }
            .setOnCancelListener {
                request.deny()
            }
            .show()
    }

    private fun hasOsPermission(perm: PermissionManager.WebPermission): Boolean {
        val osPerm = when (perm) {
            PermissionManager.WebPermission.Camera -> Manifest.permission.CAMERA
            PermissionManager.WebPermission.Microphone -> Manifest.permission.RECORD_AUDIO
            PermissionManager.WebPermission.Geolocation -> Manifest.permission.ACCESS_FINE_LOCATION
            PermissionManager.WebPermission.Notifications ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    Manifest.permission.POST_NOTIFICATIONS
                else return true  // notifications don't need runtime perm below API 33
        }
        return ContextCompat.checkSelfPermission(context, osPerm) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun List<PermissionManager.WebPermission>.toResourceArray(): Array<String> {
        val out = mutableListOf<String>()
        if (PermissionManager.WebPermission.Camera in this) {
            out += PermissionRequest.RESOURCE_VIDEO_CAPTURE
        }
        if (PermissionManager.WebPermission.Microphone in this) {
            out += PermissionRequest.RESOURCE_AUDIO_CAPTURE
        }
        return out.toTypedArray()
    }

    /**
     * Round 10 — Geolocation permission hook.
     * WebView calls this separately for navigator.geolocation.getCurrentPosition().
     * Consult PermissionManager — if no grant recorded, prompt.
     */
    override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        val o = origin ?: return
        val normalized = PermissionManager.normalizeOrigin(o) ?: o
        val granted = PermissionManager.isGranted(
            context, normalized, PermissionManager.WebPermission.Geolocation
        )
        if (granted) {
            callback?.invoke(origin, true, false)
            return
        }
        // Check OS-level location permission
        val hasOsLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasOsLocation) {
            callback?.invoke(origin, false, false)
            return
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.perms_dialog_title))
            .setMessage(context.getString(
                R.string.perms_dialog_message,
                normalized,
                PermissionManager.WebPermission.Geolocation.label
            ))
            .setNegativeButton(context.getString(R.string.perms_dialog_deny)) { _, _ ->
                PermissionManager.setGranted(
                    context, normalized, PermissionManager.WebPermission.Geolocation, false
                )
                callback?.invoke(origin, false, false)
            }
            .setPositiveButton(context.getString(R.string.perms_dialog_allow)) { _, _ ->
                PermissionManager.setGranted(
                    context, normalized, PermissionManager.WebPermission.Geolocation, true
                )
                callback?.invoke(origin, true, false)
            }
            .setOnCancelListener { callback?.invoke(origin, false, false) }
            .show()
    }
}
