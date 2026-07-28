package com.permanentbrowser.app

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var editHomepage: EditText
    private lateinit var switchJs: SwitchCompat
    private lateinit var switchPopups: SwitchCompat
    private lateinit var textVersion: TextView

    // Default browser row
    private lateinit var rowDefaultBrowser: View
    private lateinit var textDefaultBrowserStatus: TextView
    private lateinit var textDefaultBrowserSummary: TextView

    // Search engine row
    private lateinit var rowSearchEngine: View
    private lateinit var textSearchEngineValue: TextView

    // New tab page row
    private lateinit var rowNewTabPage: View
    private lateinit var textNewTabPageValue: TextView

    // Ad-block switch
    private lateinit var switchAdBlock: SwitchCompat

    /**
     * Result launcher for the system "Set as default browser?" dialog (Android 10+).
     * After it returns, we refresh the status pill.
     */
    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // The result code is unreliable across OEMs; always re-query the role.
        refreshDefaultBrowserStatus()
        if (Prefs.isDefaultBrowser(this)) {
            Toast.makeText(this, R.string.settings_default_browser_status_default, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<ImageButton>(R.id.btn_back_nav).setOnClickListener { finish() }

        editHomepage = findViewById(R.id.edit_homepage)
        switchJs = findViewById(R.id.switch_javascript)
        switchPopups = findViewById(R.id.switch_block_popups)
        textVersion = findViewById(R.id.text_version)

        rowDefaultBrowser = findViewById(R.id.row_default_browser)
        textDefaultBrowserStatus = findViewById(R.id.text_default_browser_status)
        textDefaultBrowserSummary = findViewById(R.id.text_default_browser_summary)

        rowSearchEngine = findViewById(R.id.row_search_engine)
        textSearchEngineValue = findViewById(R.id.text_search_engine_value)

        rowNewTabPage = findViewById(R.id.row_new_tab_page)
        textNewTabPageValue = findViewById(R.id.text_new_tab_page_value)

        switchAdBlock = findViewById(R.id.switch_ad_block)

        // Load current values
        editHomepage.setText(Prefs.getHomepage(this))
        switchJs.isChecked = Prefs.getJavaScriptEnabled(this)
        switchPopups.isChecked = Prefs.getBlockPopups(this)
        textSearchEngineValue.text = Prefs.getSearchEngine(this).displayName
        textNewTabPageValue.text = Prefs.getNewTabPage(this)
        switchAdBlock.isChecked = Prefs.getAdBlockEnabled(this)

        try {
            val pkg = packageManager.getPackageInfo(packageName, 0)
            textVersion.text = pkg.versionName
        } catch (_: Exception) {
            textVersion.text = "1.0"
        }

        // Wire the default browser row
        rowDefaultBrowser.setOnClickListener { onSetAsDefaultBrowserClicked() }

        // Wire the search engine picker
        rowSearchEngine.setOnClickListener { showSearchEnginePicker() }

        // Wire the new tab page editor
        rowNewTabPage.setOnClickListener { showNewTabPageDialog() }

        // Wire switches (persist immediately, Chrome-style)
        switchJs.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setJavaScriptEnabled(this, isChecked)
        }
        switchPopups.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setBlockPopups(this, isChecked)
        }
        switchAdBlock.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockEnabled(this, isChecked)
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have changed the default browser from outside the app
        // (e.g. system settings). Refresh the status pill.
        refreshDefaultBrowserStatus()
    }

    override fun onPause() {
        super.onPause()
        // Persist the homepage EditText (the only field not auto-persisted by switch listeners).
        val hp = editHomepage.text.toString().trim()
        if (hp.isNotEmpty()) Prefs.setHomepage(this, hp)
    }

    /* ==================== default browser ==================== */

    /**
     * Called when the user taps "Set as default browser".
     *
     * - Android 10+ (API 29+): uses RoleManager.isRoleAvailable(ROLE_BROWSER).
     *   If the role is available, we launch the system "Make [App] the default
     *   browser?" dialog via roleRequestLauncher.
     *   If the role isn't available (no other browsers installed, etc.),
     *   we fall back to the system default-apps settings page.
     * - Android 9 and below: RoleManager doesn't exist. We open the system
     *   "Default apps" settings page so the user can pick manually.
     */
    private fun onSetAsDefaultBrowserClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (rm.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                val intent = rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                roleRequestLauncher.launch(intent)
                return
            }
        }
        // Fallback: open the system default-apps settings.
        openSystemDefaultAppsSettings()
        Toast.makeText(this, R.string.default_browser_unsupported, Toast.LENGTH_LONG).show()
    }

    /**
     * Opens the system "Default apps" settings screen. Used as a fallback
     * on pre-Q Android or when RoleManager.ROLE_BROWSER is not available.
     */
    private fun openSystemDefaultAppsSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            // Pre-N: there is no dedicated default apps screen. Open the
            // generic app details for this package so the user can at least
            // navigate to "Open by default".
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            // Last-ditch fallback: open the main settings.
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /**
     * Updates the status pill ("Default" / "Not set") and summary text
     * to reflect the current state of whether this app holds the browser role.
     */
    private fun refreshDefaultBrowserStatus() {
        val isDefault = Prefs.isDefaultBrowser(this)
        if (isDefault) {
            textDefaultBrowserStatus.text = getString(R.string.settings_default_browser_status_default)
            textDefaultBrowserStatus.setTextColor(getColorCompat(R.color.status_default_text))
            textDefaultBrowserStatus.setBackgroundResource(R.drawable.bg_status_default)
            textDefaultBrowserSummary.text = getString(R.string.settings_default_browser_summary_default)
        } else {
            textDefaultBrowserStatus.text = getString(R.string.settings_default_browser_status_not_default)
            textDefaultBrowserStatus.setTextColor(getColorCompat(R.color.status_notset_text))
            textDefaultBrowserStatus.setBackgroundResource(R.drawable.bg_status_notset)
            textDefaultBrowserSummary.text = getString(R.string.settings_default_browser_summary_not_default)
        }
    }

    /* ==================== search engine picker ==================== */

    private fun showSearchEnginePicker() {
        val engines = Prefs.SEARCH_ENGINES
        val labels = engines.map { it.displayName }.toTypedArray()
        val currentId = Prefs.getSearchEngineId(this)
        val checked = engines.indexOfFirst { it.id == currentId }

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_search_engine)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val chosen = engines[which]
                Prefs.setSearchEngineId(this, chosen.id)
                textSearchEngineValue.text = chosen.displayName
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /* ==================== new tab page editor ==================== */

    /**
     * Shows a dialog with a single EditText pre-filled with the current
     * New Tab Page URL. On OK the pref is persisted and the row subtitle
     * is updated. Empty input is rejected (falls back to homepage via
     * [Prefs.getNewTabPage] semantics — but we explicitly guard here so
     * the displayed value matches what was actually saved).
     */
    private fun showNewTabPageDialog() {
        val current = Prefs.getNewTabPage(this)
        val input = EditText(this).apply {
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(current)
            setSelection(current.length)
            setSingleLine(true)
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, 0)

        AlertDialog.Builder(this)
            .setTitle(R.string.settings_new_tab_page_dialog_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val value = input.text.toString().trim()
                if (value.isNotEmpty()) {
                    Prefs.setNewTabPage(this, value)
                    textNewTabPageValue.text = value
                } else {
                    // Empty input → clear the override so getNewTabPage falls
                    // back to the homepage, and reflect that in the UI.
                    Prefs.setNewTabPage(this, "")
                    textNewTabPageValue.text = Prefs.getNewTabPage(this)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /* ==================== helpers ==================== */

    private fun Context.getColorCompat(colorRes: Int): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getColor(colorRes)
        } else {
            @Suppress("DEPRECATION")
            resources.getColor(colorRes)
        }
    }
}
