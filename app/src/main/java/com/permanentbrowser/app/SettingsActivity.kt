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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible

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

    // Ad-block main switch
    private lateinit var switchAdBlock: SwitchCompat
    private lateinit var textAdBlockSummary: TextView

    // Ad-block stats
    private lateinit var textAdBlockStats: TextView

    // Advanced ad-block section
    private lateinit var rowAdBlockAdvanced: View
    private lateinit var layoutAdBlockAdvanced: LinearLayout
    private lateinit var imgAdBlockExpand: ImageView
    private var advancedExpanded = false

    // Advanced toggles
    private lateinit var switchHttpsUpgrade: SwitchCompat
    private lateinit var switchCosmeticFilters: SwitchCompat

    // Category switches
    private lateinit var switchCatAds: SwitchCompat
    private lateinit var switchCatTrackers: SwitchCompat
    private lateinit var switchCatAnalytics: SwitchCompat
    private lateinit var switchCatSocial: SwitchCompat
    private lateinit var switchCatFingerprint: SwitchCompat
    private lateinit var switchCatCrypto: SwitchCompat
    private lateinit var switchCatPopups: SwitchCompat
    private lateinit var switchCatAnnoyances: SwitchCompat
    private lateinit var switchCatMalware: SwitchCompat
    private lateinit var switchCatContentFarm: SwitchCompat
    private lateinit var switchCatOther: SwitchCompat

    // Stats update job
    private var statsUpdateJob: kotlinx.coroutines.Job? = null

    /**
     * Result launcher for the system "Set as default browser?" dialog (Android 10+).
     */
    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
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
        textAdBlockSummary = findViewById(R.id.text_ad_block_summary)
        textAdBlockStats = findViewById(R.id.text_ad_block_stats)

        // Advanced ad-block
        rowAdBlockAdvanced = findViewById(R.id.row_ad_block_advanced)
        layoutAdBlockAdvanced = findViewById(R.id.layout_ad_block_advanced)
        imgAdBlockExpand = findViewById(R.id.img_ad_block_expand)

        switchHttpsUpgrade = findViewById(R.id.switch_https_upgrade)
        switchCosmeticFilters = findViewById(R.id.switch_cosmetic_filters)

        // Category switches
        switchCatAds = findViewById(R.id.switch_cat_ads)
        switchCatTrackers = findViewById(R.id.switch_cat_trackers)
        switchCatAnalytics = findViewById(R.id.switch_cat_analytics)
        switchCatSocial = findViewById(R.id.switch_cat_social)
        switchCatFingerprint = findViewById(R.id.switch_cat_fingerprint)
        switchCatCrypto = findViewById(R.id.switch_cat_crypto)
        switchCatPopups = findViewById(R.id.switch_cat_popups)
        switchCatAnnoyances = findViewById(R.id.switch_cat_annoyances)
        switchCatMalware = findViewById(R.id.switch_cat_malware)
        switchCatContentFarm = findViewById(R.id.switch_cat_content_farm)
        switchCatOther = findViewById(R.id.switch_cat_other)

        // Load current values
        editHomepage.setText(Prefs.getHomepage(this))
        switchJs.isChecked = Prefs.getJavaScriptEnabled(this)
        switchPopups.isChecked = Prefs.getBlockPopups(this)
        textSearchEngineValue.text = Prefs.getSearchEngine(this).displayName
        textNewTabPageValue.text = Prefs.getNewTabPage(this)
        switchAdBlock.isChecked = Prefs.getAdBlockEnabled(this)

        // Load advanced toggles
        switchHttpsUpgrade.isChecked = Prefs.getHttpsUpgradeEnabled(this)
        switchCosmeticFilters.isChecked = Prefs.getCosmeticFiltersEnabled(this)

        // Load category toggles
        switchCatAds.isChecked = Prefs.getAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.ADVERTISING)
        switchCatTrackers.isChecked = Prefs.getAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.TRACKERS)
        switchCatAnalytics.isChecked = Prefs.getAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.ANALYTICS)
        switchCatSocial.isChecked = Prefs.getAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.SOCIAL)
        switchCatFingerprint.isChecked = Prefs.getAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.FINGERPRINTING)
        switchCatCrypto.isChecked = Prefs.getAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.CRYPTO_MINING)
        switchCatPopups.isChecked = Prefs.getAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.POPUPS)
        switchCatAnnoyances.isChecked = Prefs.getAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.ANNOYANCES)
        switchCatMalware.isChecked = Prefs.getAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.MALWARE)
        switchCatContentFarm.isChecked = Prefs.getAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.CONTENT_FARMING)
        switchCatOther.isChecked = Prefs.getAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.OTHER)

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
            updateAdBlockSummary()
            updateAdvancedVisibility()
        }

        // Wire advanced toggle
        rowAdBlockAdvanced.setOnClickListener {
            advancedExpanded = !advancedExpanded
            layoutAdBlockAdvanced.isVisible = advancedExpanded
            imgAdBlockExpand.animate().rotation(if (advancedExpanded) 90f else 0f).setDuration(200).start()
        }

        // Wire advanced switches
        switchHttpsUpgrade.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setHttpsUpgradeEnabled(this, isChecked)
        }
        switchCosmeticFilters.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setCosmeticFiltersEnabled(this, isChecked)
        }

        // Wire category switches
        switchCatAds.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.ADVERTISING, isChecked)
        }
        switchCatTrackers.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.TRACKERS, isChecked)
        }
        switchCatAnalytics.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.ANALYTICS, isChecked)
        }
        switchCatSocial.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.SOCIAL, isChecked)
        }
        switchCatFingerprint.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.FINGERPRINTING, isChecked)
        }
        switchCatCrypto.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.CRYPTO_MINING, isChecked)
        }
        switchCatPopups.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.POPUPS, isChecked)
        }
        switchCatAnnoyances.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.ANNOYANCES, isChecked)
        }
        switchCatMalware.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.MALWARE, isChecked)
        }
        switchCatContentFarm.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.CONTENT_FARMING, isChecked)
        }
        switchCatOther.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAdBlockCategoryEnabled(this, AdBlocker.BlockCategory.OTHER, isChecked)
        }

        // Reset stats
        findViewById<View>(R.id.row_ad_block_reset_stats).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.settings_ad_block_reset_stats)
                .setMessage(R.string.settings_ad_block_reset_stats_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    AdBlocker.resetStats()
                    Prefs.resetBlockStats(this)
                    updateBlockStats()
                    Toast.makeText(this, "Stats reset", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        // Initial UI state
        updateAdBlockSummary()
        updateAdvancedVisibility()
        updateBlockStats()
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultBrowserStatus()
        updateBlockStats()
    }

    override fun onPause() {
        super.onPause()
        // Persist the homepage EditText
        val hp = editHomepage.text.toString().trim()
        if (hp.isNotEmpty()) Prefs.setHomepage(this, hp)

        // Persist current session stats
        Prefs.setBlockStatsSession(this, AdBlocker.totalBlocked())
    }

    /* ==================== Ad-block UI helpers ==================== */

    private fun updateAdBlockSummary() {
        val enabled = switchAdBlock.isChecked
        textAdBlockSummary.text = if (enabled) {
            getString(R.string.settings_ad_block_summary_on)
        } else {
            getString(R.string.settings_ad_block_summary_off)
        }
    }

    private fun updateAdvancedVisibility() {
        layoutAdBlockAdvanced.isVisible = advancedExpanded && switchAdBlock.isChecked
        findViewById<View>(R.id.row_ad_block_stats).isVisible = switchAdBlock.isChecked
    }

    private fun updateBlockStats() {
        val sessionCount = AdBlocker.totalBlocked()
        val lifetimeCount = Prefs.getBlockStatsTotal(this) + sessionCount

        textAdBlockStats.text = if (sessionCount > 0) {
            getString(R.string.settings_ad_block_stats_session, formatCount(sessionCount))
        } else {
            getString(R.string.settings_ad_block_stats, formatCount(lifetimeCount))
        }
    }

    private fun formatCount(count: Long): String {
        return when {
            count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
            count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
            else -> count.toString()
        }
    }

    /* ==================== default browser ==================== */

    private fun onSetAsDefaultBrowserClicked() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (rm.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                val intent = rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
                roleRequestLauncher.launch(intent)
                return
            }
        }
        openSystemDefaultAppsSettings()
        Toast.makeText(this, R.string.default_browser_unsupported, Toast.LENGTH_LONG).show()
    }

    private fun openSystemDefaultAppsSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

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
