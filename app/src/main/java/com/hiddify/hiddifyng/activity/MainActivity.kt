package com.hiddify.hiddifyng.activity

import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import com.hiddify.hiddifyng.R
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.utils.AutoUpdateManager
import com.hiddify.hiddifyng.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    
    // UI components
    private lateinit var toolbar: Toolbar
    private lateinit var connectionCard: MaterialCardView
    private lateinit var serverNameText: MaterialTextView
    private lateinit var serverAddressText: MaterialTextView
    private lateinit var pingText: MaterialTextView
    private lateinit var connectionStatusText: MaterialTextView
    private lateinit var connectButton: FloatingActionButton
    private lateinit var statisticsCard: MaterialCardView
    private lateinit var uploadSpeedText: MaterialTextView
    private lateinit var downloadSpeedText: MaterialTextView
    
    // VPN permission request
    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Permission granted, start the service
            startVpnService()
        } else {
            // Permission denied
            showSnackbar("VPN permission denied")
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this).get(MainViewModel::class.java)
        
        // Set up UI components
        setupUI()
        
        // Set up observers
        setupObservers()
        
        // Handle intent (subscription or server import)
        handleIntent(intent)
        
        // Check for updates
        viewModel.checkForAppUpdates()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }
    
    private fun setupUI() {
        // Toolbar
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // Connection card
        connectionCard = findViewById(R.id.connection_card)
        serverNameText = findViewById(R.id.server_name)
        serverAddressText = findViewById(R.id.server_address)
        pingText = findViewById(R.id.ping)
        connectionStatusText = findViewById(R.id.connection_status)
        
        // Connect button
        connectButton = findViewById(R.id.connect_button)
        connectButton.setOnClickListener {
            if (viewModel.isRunning.value == true) {
                viewModel.stopVpn()
            } else {
                checkVpnPermissionAndConnect()
            }
        }
        
        // Server selection click
        connectionCard.setOnClickListener {
            val intent = Intent(this, ServerListActivity::class.java)
            startActivity(intent)
        }
        
        // Statistics card
        statisticsCard = findViewById(R.id.statistics_card)
        uploadSpeedText = findViewById(R.id.upload_speed)
        downloadSpeedText = findViewById(R.id.download_speed)
    }
    
    private fun setupObservers() {
        // Observe connection state
        viewModel.isRunning.observe(this) { isRunning ->
            updateConnectionUI(isRunning)
        }
        
        // Observe current server
        viewModel.currentServer.observe(this) { server ->
            updateServerInfo(server)
        }
        
        // Observe upload/download speeds
        viewModel.uploadSpeed.observe(this) { speed ->
            uploadSpeedText.text = formatSpeed(speed)
        }
        
        viewModel.downloadSpeed.observe(this) { speed ->
            downloadSpeedText.text = formatSpeed(speed)
        }
        
        // Observe action status
        viewModel.actionStatus.observe(this) { status ->
            handleActionStatus(status)
        }
        
        // Observe update availability
        viewModel.updateAvailable.observe(this) { available ->
            if (available) {
                showUpdateAvailableDialog()
            }
        }
        
        // Observe app settings
        lifecycleScope.launch {
            viewModel.appSettings.collectLatest { settings ->
                settings?.let {
                    // Apply settings (theme, language, etc.)
                    // This would be implemented in a real app
                }
            }
        }
    }
    
    private fun updateConnectionUI(isRunning: Boolean) {
        if (isRunning) {
            connectButton.setImageResource(R.drawable.ic_stop)
            connectionStatusText.text = getString(R.string.connected)
            connectionStatusText.setTextColor(getColor(R.color.success))
            statisticsCard.visibility = View.VISIBLE
        } else {
            connectButton.setImageResource(R.drawable.ic_play)
            connectionStatusText.text = getString(R.string.disconnected)
            connectionStatusText.setTextColor(getColor(R.color.error))
            statisticsCard.visibility = View.GONE
        }
    }
    
    private fun updateServerInfo(server: Server?) {
        if (server != null) {
            serverNameText.text = server.name
            serverAddressText.text = "${server.protocol}://${server.address}:${server.port}"
            
            if (server.ping > 0 && server.ping < Integer.MAX_VALUE) {
                pingText.text = "${server.ping} ms"
                pingText.visibility = View.VISIBLE
            } else {
                pingText.visibility = View.GONE
            }
            
            connectionCard.alpha = 1.0f
        } else {
            serverNameText.text = getString(R.string.no_server_selected)
            serverAddressText.text = getString(R.string.tap_to_select_server)
            pingText.visibility = View.GONE
            connectionCard.alpha = 0.8f
        }
    }
    
    private fun handleActionStatus(status: MainViewModel.ActionStatus) {
        when (status) {
            is MainViewModel.ActionStatus.LOADING -> {
                // Show loading indicator
                // This would be implemented in a real app
            }
            is MainViewModel.ActionStatus.SUCCESS -> {
                // Clear loading indicator
            }
            is MainViewModel.ActionStatus.SUCCESS_WITH_DATA -> {
                if (status.data is AutoUpdateManager.UpdateInfo) {
                    showUpdateInfoDialog(status.data)
                } else {
                    showSnackbar(status.message)
                }
            }
            is MainViewModel.ActionStatus.ERROR -> {
                showSnackbar(status.message)
            }
        }
    }
    
    private fun checkVpnPermissionAndConnect() {
        val vpnIntent = VpnService.prepare(this)
        if (vpnIntent != null) {
            vpnPermissionLauncher.launch(vpnIntent)
        } else {
            // Permission already granted
            startVpnService()
        }
    }
    
    private fun startVpnService() {
        val currentServer = viewModel.currentServer.value
        
        if (currentServer != null) {
            viewModel.startVpn(currentServer.id)
        } else {
            // No server selected, show server list or connect to best server
            val serverCount = viewModel.allServers.value?.size ?: 0
            
            if (serverCount > 0) {
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.no_server_selected))
                    .setMessage(getString(R.string.connect_to_best_server_prompt))
                    .setPositiveButton(getString(R.string.yes)) { _, _ ->
                        viewModel.connectToBestServer()
                    }
                    .setNegativeButton(getString(R.string.no)) { _, _ ->
                        val intent = Intent(this, ServerListActivity::class.java)
                        startActivity(intent)
                    }
                    .show()
            } else {
                // No servers available, show add server dialog
                showSnackbar(getString(R.string.no_servers_available))
                val intent = Intent(this, ServerListActivity::class.java)
                startActivity(intent)
            }
        }
    }
    
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) {
                    val scheme = uri.scheme
                    if (scheme == "vmess" || scheme == "vless" || scheme == "trojan" || 
                        scheme == "ss" || scheme == "hysteria") {
                        // Import server from URI
                        viewModel.importFromServerUrl(uri.toString())
                    } else if (scheme == "http" || scheme == "https") {
                        // Import subscription
                        showSubscriptionImportDialog(uri.toString())
                    }
                }
            }
        }
    }
    
    private fun showSubscriptionImportDialog(url: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.import_subscription))
            .setMessage(getString(R.string.import_subscription_prompt, url))
            .setPositiveButton(getString(R.string.import_)) { _, _ ->
                viewModel.importFromUrl(url, null)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        
        dialog.show()
    }
    
    private fun showUpdateAvailableDialog() {
        val updateInfo = viewModel.updateInfo.value ?: return
        
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available))
            .setMessage(getString(R.string.update_available_prompt, 
                updateInfo.currentVersion, updateInfo.latestVersion))
            .setPositiveButton(getString(R.string.update_now)) { _, _ ->
                viewModel.downloadAndInstallUpdate()
            }
            .setNegativeButton(getString(R.string.later), null)
            .create()
        
        dialog.show()
    }
    
    private fun showUpdateInfoDialog(updateInfo: AutoUpdateManager.UpdateInfo) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_info))
            .setMessage(
                """
                ${getString(R.string.current_version)}: ${updateInfo.currentVersion}
                ${getString(R.string.latest_version)}: ${updateInfo.latestVersion}
                
                ${getString(R.string.release_notes)}:
                ${updateInfo.releaseNotes}
                """.trimIndent()
            )
            .setPositiveButton(getString(if (updateInfo.hasUpdate) R.string.update_now else R.string.ok)) { _, _ ->
                if (updateInfo.hasUpdate) {
                    viewModel.downloadAndInstallUpdate()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        
        dialog.show()
    }
    
    private fun showSettingsBottomSheet() {
        val bottomSheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        
        // Initialize settings UI components and set current values
        
        bottomSheet.setContentView(view)
        bottomSheet.show()
    }
    
    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond < 1024 -> "$bytesPerSecond B/s"
            bytesPerSecond < 1024 * 1024 -> "${bytesPerSecond / 1024} KB/s"
            else -> String.format("%.2f MB/s", bytesPerSecond / (1024.0 * 1024.0))
        }
    }
    
    private fun showSnackbar(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_servers -> {
                val intent = Intent(this, ServerListActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_settings -> {
                showSettingsBottomSheet()
                true
            }
            R.id.action_ping_all -> {
                viewModel.pingAllServers()
                true
            }
            R.id.action_check_update -> {
                viewModel.checkForAppUpdates()
                true
            }
            R.id.action_update_core -> {
                viewModel.updateXrayCore()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
