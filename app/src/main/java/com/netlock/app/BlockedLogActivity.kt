package com.netlock.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.netlock.app.data.AppDatabase
import com.netlock.app.data.DomainEntity
import com.netlock.app.data.ListType
import kotlinx.coroutines.launch

/**
 * Diagnostic screen: recent DNS lookups that got blocked while protection was
 * running. The most common reason a whitelisted study site "breaks" (video
 * won't play, embeds don't load) is that the content is actually served from
 * a separate domain the site depends on - a CDN, video-delivery, or fonts/
 * script host - which isn't automatically covered by whitelisting the site's
 * own domain. This lets the user see exactly what was refused, right when it
 * happened, instead of guessing.
 */
class BlockedLogActivity : AppCompatActivity() {

    private lateinit var db: AppDatabase
    private lateinit var adapter: BlockedLogAdapter
    private var allSelected = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_blocked_log)

        db = AppDatabase.getInstance(this)
        setSupportActionBar(findViewById(R.id.toolbar))

        val btnWhitelistSelected = findViewById<Button>(R.id.btnWhitelistSelected)
        val btnSelectToggle = findViewById<Button>(R.id.btnSelectToggle)

        adapter = BlockedLogAdapter(
            onWhitelist = { entry ->
                lifecycleScope.launch {
                    db.domainDao().insert(DomainEntity(domain = entry.domain, listType = ListType.WHITELIST))
                    Toast.makeText(this@BlockedLogActivity, "${entry.domain} added to whitelist", Toast.LENGTH_SHORT).show()
                }
            },
            onSelectionChanged = { checkedCount ->
                btnWhitelistSelected.text = "Whitelist selected ($checkedCount)"
                btnWhitelistSelected.isEnabled = checkedCount > 0
            }
        )

        findViewById<RecyclerView>(R.id.rvBlocked).apply {
            layoutManager = LinearLayoutManager(this@BlockedLogActivity)
            adapter = this@BlockedLogActivity.adapter
        }

        btnSelectToggle.setOnClickListener {
            allSelected = !allSelected
            if (allSelected) adapter.selectAll() else adapter.selectNone()
            btnSelectToggle.text = if (allSelected) "None" else "All"
        }

        btnWhitelistSelected.setOnClickListener {
            val domains = adapter.checkedDomains()
            if (domains.isEmpty()) return@setOnClickListener
            lifecycleScope.launch {
                db.domainDao().insertAll(domains.map { DomainEntity(domain = it, listType = ListType.WHITELIST) })
                Toast.makeText(
                    this@BlockedLogActivity,
                    "${domains.size} domain${if (domains.size == 1) "" else "s"} added to whitelist",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            lifecycleScope.launch { db.domainDao().clearBlockedLog() }
        }

        lifecycleScope.launch {
            db.domainDao().observeRecentBlocked().collect { list ->
                adapter.submitList(list)
                findViewById<TextView>(R.id.tvEmpty).visibility =
                    if (list.isEmpty()) View.VISIBLE else View.GONE
                findViewById<View>(R.id.selectionBar).visibility =
                    if (list.isEmpty()) View.GONE else View.VISIBLE
                btnWhitelistSelected.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}
