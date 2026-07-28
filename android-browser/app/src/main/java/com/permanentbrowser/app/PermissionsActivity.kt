package com.permanentbrowser.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Round 10 — Per-site permission manager.
 *
 * Chrome-style "Site permissions" screen: list every origin that has at
 * least one web permission granted, with per-permission toggle chips.
 *
 * Layout: activity_permissions.xml
 * Adapter: bound inline (small list, no separate adapter file).
 *
 * Security notes:
 *   - This screen only modifies web-level permission grants stored in
 *     SharedPreferences. It does NOT clear cookies, cache, or history.
 *   - The "Revoke all" action removes the origin's grants but leaves
 *     its history entries intact — permanent history is never touched.
 *   - System-level runtime permissions (CAMERA, RECORD_AUDIO, etc.) must
 *     also be granted at the OS level for the web grant to take effect;
 *     the user is prompted at runtime via ActivityCompat.requestPermissions.
 */
class PermissionsActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: View
    private lateinit var summaryText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permissions)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationIcon(R.drawable.ic_back)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = getString(R.string.title_permissions)

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
                    val origins = PermissionManager.listOrigins(this)
                    origins.forEach { PermissionManager.revokeOrigin(this, it.origin) }
                    refresh()
                }
                .show()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
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
            // Toggle handler — flip the grant and refresh
            val current = PermissionManager.isGranted(this, origin, perm)
            PermissionManager.setGranted(this, origin, perm, !current)
            refresh()
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
            val item = items[position]
            holder.originText.text = item.origin
            holder.chipGroup.removeAllViews()
            PermissionManager.WebPermission.entries.forEach { perm ->
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
                        PermissionManager.revokeOrigin(
                            holder.itemView.context,
                            item.origin
                        )
                        refresh()
                    }
                    .show()
            }
        }

        override fun getItemCount(): Int = items.size
    }
}
