package com.permanentbrowser.app

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Per-site permission manager.
 *
 * Chrome-style "Site permissions" screen: list every origin that has at
 * least one web permission granted, with per-permission toggle chips.
 *
 * Layout: activity_permissions.xml
 * Adapter: bound inline (small list, no separate adapter file).
 */
class PermissionsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PermissionsActivity"
    }

    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var summaryText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_permissions)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set content view", e)
            finish()
            return
        }

        try {
            val toolbarBack = findViewById<ImageButton>(R.id.toolbar)
            toolbarBack.setOnClickListener { finish() }

            recycler = findViewById(R.id.recycler_perms)
            emptyState = findViewById(R.id.empty_state)
            summaryText = findViewById(R.id.text_summary)

            recycler.layoutManager = LinearLayoutManager(this)

            findViewById<View>(R.id.btn_clear_all).setOnClickListener {
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.perms_clear_all_title)
                    .setMessage(R.string.perms_clear_all_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.perms_clear_all_confirm) { _, _ ->
                        try {
                            val origins = PermissionManager.listOrigins(this)
                            origins.forEach { PermissionManager.revokeOrigin(this, it.origin) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to revoke all origins", e)
                        }
                        refresh()
                    }
                    .show()
            }

            refresh()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PermissionsActivity", e)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        try {
            val origins = PermissionManager.listOrigins(this)
            if (origins.isEmpty()) {
                recycler.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
                summaryText.text = getString(R.string.perms_summary_empty)
            } else {
                recycler.visibility = View.VISIBLE
                emptyState.visibility = View.GONE
                val grantedCount = origins.count {
                    it.camera || it.mic || it.geo || it.notif
                }
                summaryText.text = getString(
                    R.string.perms_summary_count,
                    grantedCount,
                    origins.size
                )
            }
            recycler.adapter = OriginAdapter(origins) { origin, perm ->
                try {
                    val current = PermissionManager.isGranted(this, origin, perm)
                    PermissionManager.setGranted(this, origin, perm, !current)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to toggle permission for $origin", e)
                }
                refresh()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh permissions", e)
            // Show empty state as fallback
            try {
                recycler.visibility = View.GONE
                emptyState.visibility = View.VISIBLE
                summaryText.text = getString(R.string.perms_summary_empty)
            } catch (_: Exception) {}
        }
    }

    /** Lightweight in-file adapter — list is small. */
    private inner class OriginAdapter(
        private val items: List<PermissionManager.OriginGrant>,
        private val onToggle: (String, PermissionManager.WebPermission) -> Unit,
    ) : RecyclerView.Adapter<OriginAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val originText: TextView = v.findViewById(R.id.text_origin)
            val chipGroup: ChipGroup = v.findViewById(R.id.chip_group)
            val revokeBtn: View = v.findViewById(R.id.btn_revoke)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_permission_origin, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            try {
                val item = items[position]
                holder.originText.text = item.origin
                holder.chipGroup.removeAllViews()
                // Use values() for maximum compatibility; both values() and
                // entries() work on Kotlin 1.9.24, but values() never fails.
                @Suppress("DEPRECATION")
                PermissionManager.WebPermission.values().forEach { perm ->
                    val chip = Chip(holder.itemView.context).apply {
                        text = perm.label
                        isCheckable = true
                        isChecked = item.isGranted(perm)
                        setOnClickListener { onToggle(item.origin, perm) }
                    }
                    holder.chipGroup.addView(chip)
                }
                holder.revokeBtn.setOnClickListener {
                    MaterialAlertDialogBuilder(holder.itemView.context)
                        .setTitle(R.string.perms_revoke_origin_title)
                        .setMessage(getString(R.string.perms_revoke_origin_message, item.origin))
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.perms_revoke_origin_confirm) { _, _ ->
                            try {
                                PermissionManager.revokeOrigin(
                                    holder.itemView.context,
                                    item.origin
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to revoke origin: ${item.origin}", e)
                            }
                            refresh()
                        }
                        .show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind view at position $position", e)
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
