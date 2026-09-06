package com.netlock.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.netlock.app.data.AppDatabase
import com.netlock.app.data.BlockMode
import com.netlock.app.data.DomainEntity
import com.netlock.app.data.ListType
import com.netlock.app.data.Prefs
import com.netlock.app.util.AppsHelper
import com.netlock.app.util.DomainImport
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var db: AppDatabase
    private lateinit var adapter: DomainAdapter

    private var currentTab: ListType = ListType.WHITELIST

    // What we want to do once the user has unlocked (if a password is set),
    // and what to do instead if they cancel (e.g. revert a UI toggle).
    private var pendingAction: (() -> Unit)? = null
    private var pendingCancelAction: (() -> Unit)? = null

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpnService() else
            Toast.makeText(this, "VPN permission is required to enable protection", Toast.LENGTH_LONG).show()
    }

    private val unlockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingAction?.invoke()
        } else {
            pendingCancelAction?.invoke()
        }
        pendingAction = null
        pendingCancelAction = null
    }

    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshSelectedAppsLabel() }

    private val setupPasswordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshPasswordStatus() }

    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            try {
                val domains = contentResolver.openInputStream(uri)?.use { stream ->
                    DomainImport.parseFile(stream)
                } ?: emptySet()

                val entities = domains.map { DomainEntity(domain = it, listType = currentTab) }
                db.domainDao().insertAll(entities)
                Toast.makeText(this@MainActivity, "Imported ${entities.size} domains", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op either way, notification is best-effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = Prefs.getInstance(this)
        db = AppDatabase.getInstance(this)

        setSupportActionBar(findViewById(R.id.toolbar))

        setupModeControls()
        setupListControls()
        setupAppPicker()
        setupPasswordControls()
        setupVpnToggle()

        requestNotificationPermissionIfNeeded()
        observeCurrentList()
        refreshSelectedAppsLabel()
        refreshPasswordStatus()
        refreshVpnStatusLabel()
    }

    override fun onResume() {
        super.onResume()
        refreshVpnStatusLabel()
    }

    // ---------- Password gate helper ----------

    /**
     * Runs [action] immediately if no password is set, otherwise prompts for it first.
     * If the user cancels the unlock prompt, [onCancel] runs instead (e.g. to revert a UI toggle).
     */
    private fun runProtected(onCancel: (() -> Unit)? = null, action: () -> Unit) {
        if (!prefs.hasPassword()) {
            action()
            return
        }
        pendingAction = action
        pendingCancelAction = onCancel
        unlockLauncher.launch(Intent(this, UnlockActivity::class.java))
    }

    // ---------- Mode ----------

    private fun setupModeControls() {
        val rgMode = findViewById<RadioGroup>(R.id.rgMode)
        val tvExplain = findViewById<TextView>(R.id.tvModeExplain)

        fun applyModeUi(mode: BlockMode) {
            rgMode.check(if (mode == BlockMode.WHITELIST) R.id.rbWhitelist else R.id.rbBlacklist)
            tvExplain.text = if (mode == BlockMode.WHITELIST)
                "Whitelist only: everything is blocked except the sites you add."
            else
                "Blacklist: everything is allowed except the sites you add."
        }

        applyModeUi(prefs.mode)

        rgMode.setOnCheckedChangeListener { _, checkedId ->
            val newMode = if (checkedId == R.id.rbWhitelist) BlockMode.WHITELIST else BlockMode.BLACKLIST
            if (newMode == prefs.mode) return@setOnCheckedChangeListener

            runProtected(
                onCancel = { applyModeUi(prefs.mode) },
                action = {
                    prefs.mode = newMode
                    applyModeUi(newMode)
                }
            )
        }
    }

    // ---------- Domain lists ----------

    private fun setupListControls() {
        adapter = DomainAdapter { entity ->
            runProtected {
                lifecycleScope.launch { db.domainDao().delete(entity) }
            }
        }
        findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvDomains).apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }

        val tvListTitle = findViewById<TextView>(R.id.tvListTitle)

        findViewById<Button>(R.id.btnTabWhitelist).setOnClickListener {
            currentTab = ListType.WHITELIST
            tvListTitle.text = "Editing: Whitelist"
            observeCurrentList()
        }
        findViewById<Button>(R.id.btnTabBlacklist).setOnClickListener {
            currentTab = ListType.BLACKLIST
            tvListTitle.text = "Editing: Blacklist"
            observeCurrentList()
        }

        findViewById<Button>(R.id.btnAddDomain).setOnClickListener {
            val et = findViewById<EditText>(R.id.etDomain)
            val normalized = DomainImport.normalize(et.text.toString())
            if (normalized == null) {
                Toast.makeText(this, "Enter a valid domain, e.g. example.com", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            runProtected {
                lifecycleScope.launch {
                    db.domainDao().insert(DomainEntity(domain = normalized, listType = currentTab))
                }
                et.text.clear()
            }
        }

        findViewById<Button>(R.id.btnImportFile).setOnClickListener {
            runProtected {
                importFileLauncher.launch(arrayOf("text/plain", "text/csv", "text/comma-separated-values", "*/*"))
            }
        }
    }

    private var listCollectJob: kotlinx.coroutines.Job? = null

    private fun observeCurrentList() {
        listCollectJob?.cancel()
        listCollectJob = lifecycleScope.launch {
            db.domainDao().observeByType(currentTab).collect { list ->
                adapter.submitList(list)
            }
        }
    }

    // ---------- App picker ----------

    private fun setupAppPicker() {
        findViewById<Button>(R.id.btnPickApps).setOnClickListener {
            runProtected {
                appPickerLauncher.launch(Intent(this, AppPickerActivity::class.java))
            }
        }
    }

    private fun refreshSelectedAppsLabel() {
        val tv = findViewById<TextView>(R.id.tvSelectedApps)
        val selected = prefs.selectedPackages
        if (selected.isEmpty()) {
            tv.text = "No apps selected yet - protection can't start until you choose at least one."
            return
        }
        val candidates = AppsHelper.listBrowserCandidates(packageManager).associateBy { it.packageName }
        val labels = selected.map { candidates[it]?.label ?: it }
        tv.text = labels.joinToString(", ")
    }

    // ---------- Password ----------

    private fun setupPasswordControls() {
        findViewById<Button>(R.id.btnSetPassword).setOnClickListener {
            runProtected {
                setupPasswordLauncher.launch(Intent(this, SetupPasswordActivity::class.java))
            }
        }
    }

    private fun refreshPasswordStatus() {
        findViewById<TextView>(R.id.tvPasswordStatus).text =
            if (prefs.hasPassword()) "Password protection is ON" else "No password set"
    }

    // ---------- VPN control ----------

    private fun setupVpnToggle() {
        findViewById<Button>(R.id.btnToggleVpn).setOnClickListener {
            if (prefs.vpnRunning) {
                runProtected { stopVpnService() }
            } else {
                startProtectionFlow()
            }
        }
    }

    private fun startProtectionFlow() {
        if (prefs.selectedPackages.isEmpty()) {
            Toast.makeText(this, "Choose at least one app to filter first", Toast.LENGTH_LONG).show()
            return
        }
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPrepareLauncher.launch(prepareIntent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, FilterVpnService::class.java)
        ContextCompat.startForegroundService(this, intent)
        // Give the service a brief moment to update prefs.vpnRunning before refreshing UI.
        findViewById<Button>(R.id.btnToggleVpn).postDelayed({ refreshVpnStatusLabel() }, 400)
    }

    private fun stopVpnService() {
        val intent = Intent(this, FilterVpnService::class.java).setAction(FilterVpnService.ACTION_STOP)
        startService(intent)
        findViewById<Button>(R.id.btnToggleVpn).postDelayed({ refreshVpnStatusLabel() }, 400)
    }

    private fun refreshVpnStatusLabel() {
        val running = prefs.vpnRunning
        findViewById<TextView>(R.id.tvStatus).text = if (running) "Protection: ON" else "Protection: OFF"
        findViewById<Button>(R.id.btnToggleVpn).text = if (running) "Stop Protection" else "Start Protection"
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
