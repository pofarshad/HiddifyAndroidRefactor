package com.hiddify.hiddifyng.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.hiddify.hiddifyng.BuildConfig
import com.hiddify.hiddifyng.R
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.protocols.ProtocolHandler
import com.hiddify.hiddifyng.utils.SubscriptionManager
import com.hiddify.hiddifyng.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Main activity for MarFaNet Co. application
 * Provides network controls, server selection, and traffic statistics
 */
class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    
    // UI components
    private lateinit var toolbar: Toolbar
    private lateinit var tvConnectionStatus: TextView
    private lateinit var ivStatusIcon: ImageView
    private lateinit var tvCurrentServer: TextView
    private lateinit var tvBestServer: TextView
    private lateinit var tvBestServerPing: TextView
    private lateinit var switchAutoSelect: SwitchCompat
    private lateinit var btnConnect: MaterialButton
    private lateinit var btnPingServers: MaterialButton
    private lateinit var btnServerList: MaterialButton
    private lateinit var btnUpdateSubscription: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var fabAddServer: FloatingActionButton
    private lateinit var cardServerSelection: MaterialCardView
    private lateinit var tvVersionInfo: TextView
    
    // Traffic statistics UI components
    private lateinit var tvUploadValue: TextView
    private lateinit var tvDownloadValue: TextView
    private lateinit var tvTotalValue: TextView
    
    // Coroutine scopes
    private val mainScope = CoroutineScope(Dispatchers.Main)
    private val ioScope = CoroutineScope(Dispatchers.IO)
    
    // Traffic statistics job
    private var trafficStatsJob: Job? = null
    
    // Handler for UI updates
    private val handler = Handler(Looper.getMainLooper())
    
    // Traffic statistics update runnable
    private val trafficUpdateRunnable = object : Runnable {
        override fun run() {
            updateTrafficStats()
            handler.postDelayed(this, TRAFFIC_UPDATE_INTERVAL)
        }
    }
    
    companion object {
        // Constants
        private const val TRAFFIC_UPDATE_INTERVAL = 1000L // 1 second
        private const val SERVER_SELECTION_REQUEST_CODE = 100
        private const val SETTINGS_REQUEST_CODE = 101
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        // Initialize toolbar
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        
        // Initialize UI components
        initializeUI()
        
        // Setup observers
        setupObservers()
        
        // Start traffic stats update
        startTrafficStatsUpdate()
        
        // Update version info
        updateVersionInfo()
        
        // Handle intent
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleIntent(it) }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh server data
        viewModel.refreshData()
        
        // Start traffic stats update if connected
        if (viewModel.connectionState.value == MainViewModel.ConnectionState.CONNECTED) {
            startTrafficStatsUpdate()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Stop traffic stats update
        stopTrafficStatsUpdate()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Cancel traffic stats job
        trafficStatsJob?.cancel()
        // Remove traffic update callbacks
        handler.removeCallbacks(trafficUpdateRunnable)
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                showSettingsBottomSheet()
                true
            }
            R.id.menu_check_update -> {
                checkForUpdates()
                true
            }
            R.id.menu_server_list -> {
                openServerList()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * Initialize UI components with modern material design styling
     */
    private fun initializeUI() {
        // Get references to UI components
        tvConnectionStatus = findViewById(R.id.tv_connection_status)
        ivStatusIcon = findViewById(R.id.iv_status_icon)
        tvCurrentServer = findViewById(R.id.tv_current_server)
        tvBestServer = findViewById(R.id.tv_best_server)
        tvBestServerPing = findViewById(R.id.tv_best_server_ping)
        switchAutoSelect = findViewById(R.id.switch_auto_select)
        btnConnect = findViewById(R.id.btn_connect)
        btnPingServers = findViewById(R.id.btn_ping_servers)
        btnServerList = findViewById(R.id.btn_server_list)
        btnUpdateSubscription = findViewById(R.id.btn_update_subscription)
        btnSettings = findViewById(R.id.btn_settings)
        fabAddServer = findViewById(R.id.fab_add_server)
        cardServerSelection = findViewById(R.id.card_server_selection)
        tvVersionInfo = findViewById(R.id.tv_version_info)
        
        // Traffic statistics UI components
        tvUploadValue = findViewById(R.id.tv_upload_value)
        tvDownloadValue = findViewById(R.id.tv_download_value)
        tvTotalValue = findViewById(R.id.tv_total_value)
        
        // Set click listeners with visual feedback (ripple effect)
        btnConnect.setOnClickListener {
            handleConnectButtonClick()
        }
        
        btnPingServers.setOnClickListener {
            pingAllServers()
        }
        
        btnServerList.setOnClickListener {
            openServerList()
        }
        
        btnUpdateSubscription.setOnClickListener {
            updateSubscriptions()
        }
        
        btnSettings.setOnClickListener {
            showSettingsBottomSheet()
        }
        
        fabAddServer.setOnClickListener {
            showAddServerDialog()
        }
        
        // Server selection card click listener
        cardServerSelection.setOnClickListener {
            openServerList()
        }
        
        // Auto select switch listener
        switchAutoSelect.setOnCheckedChangeListener { _, isChecked ->
            if (viewModel.appSettings.value != null) {
                val settings = viewModel.appSettings.value!!
                settings.autoSwitchToBestServer = isChecked
                viewModel.updateSettings(settings)
                
                // Show appropriate feedback
                val message = if (isChecked) 
                    "Auto-selection enabled. The app will connect to the server with lowest ping." 
                else 
                    "Auto-selection disabled. You'll need to select servers manually."
                    
                Snackbar.make(cardServerSelection, message, Snackbar.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Setup observers for LiveData with reactive UI updates
     */
    private fun setupObservers() {
        // Observe connection state with status animation
        viewModel.connectionState.observe(this) { state ->
            updateConnectionState(state)
        }
        
        // Observe current server with detailed information
        viewModel.currentServer.observe(this) { server ->
            updateCurrentServer(server)
        }
        
        // Observe best server with auto-connect capability
        viewModel.bestServer.observe(this) { server ->
            updateBestServer(server)
        }
        
        // Observe app settings for auto-switch capability
        viewModel.appSettings.observe(this) { settings ->
            // Update auto-select switch
            if (settings != null) {
                switchAutoSelect.isChecked = settings.autoSwitchToBestServer
            }
        }
        
        // Observe all servers for selection
        viewModel.allServers.observe(this) { servers ->
            // Update UI if needed based on available servers
            btnConnect.isEnabled = !servers.isNullOrEmpty() || 
                viewModel.connectionState.value == MainViewModel.ConnectionState.CONNECTED
        }
        
        // Observe traffic statistics for real-time updates
        viewModel.trafficStats.observe(this) { stats ->
            if (stats != null) {
                updateTrafficStatsUI(stats)
            }
        }
    }
    
    /**
     * Update UI based on connection state with animations and color transitions
     */
    private fun updateConnectionState(state: MainViewModel.ConnectionState) {
        when (state) {
            MainViewModel.ConnectionState.DISCONNECTED -> {
                tvConnectionStatus.text = "DISCONNECTED"
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
                ivStatusIcon.setImageResource(R.drawable.ic_stop)
                
                btnConnect.text = "CONNECT"
                btnConnect.setBackgroundColor(ContextCompat.getColor(this, R.color.gold_500))
                btnConnect.isEnabled = viewModel.allServers.value?.isNotEmpty() == true
                
                // Stop traffic stats update
                stopTrafficStatsUpdate()
            }
            MainViewModel.ConnectionState.CONNECTING -> {
                tvConnectionStatus.text = "CONNECTING..."
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.connecting))
                ivStatusIcon.setImageResource(R.drawable.ic_play)
                
                btnConnect.text = "CONNECTING..."
                btnConnect.isEnabled = false
            }
            MainViewModel.ConnectionState.CONNECTED -> {
                tvConnectionStatus.text = "CONNECTED"
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.connected))
                ivStatusIcon.setImageResource(R.drawable.ic_play)
                
                btnConnect.text = "DISCONNECT"
                // Use outlined style for disconnect button
                btnConnect.setBackgroundColor(ContextCompat.getColor(this, R.color.background_tertiary))
                btnConnect.setTextColor(ContextCompat.getColor(this, R.color.gold_500))
                btnConnect.isEnabled = true
                
                // Start traffic stats update
                startTrafficStatsUpdate()
            }
            MainViewModel.ConnectionState.DISCONNECTING -> {
                tvConnectionStatus.text = "DISCONNECTING..."
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.disconnected))
                ivStatusIcon.setImageResource(R.drawable.ic_stop)
                
                btnConnect.text = "DISCONNECTING..."
                btnConnect.isEnabled = false
            }
        }
    }
    
    /**
     * Update current server display with detailed information
     */
    private fun updateCurrentServer(server: Server?) {
        if (server != null) {
            tvCurrentServer.text = server.name
            
            // Make server card visible with animation
            if (!cardServerSelection.isVisible) {
                cardServerSelection.alpha = 0f
                cardServerSelection.visibility = View.VISIBLE
                cardServerSelection.animate().alpha(1f).setDuration(300).start()
            }
        } else {
            tvCurrentServer.text = "No server selected"
        }
    }
    
    /**
     * Update best server display with ping information
     */
    private fun updateBestServer(server: Server?) {
        if (server != null) {
            tvBestServer.text = server.name
            tvBestServerPing.text = "${server.ping} ms"
            
            // Set color based on ping value
            val pingColor = when {
                server.ping < 100 -> R.color.connected
                server.ping < 200 -> R.color.connecting
                else -> R.color.disconnected
            }
            tvBestServerPing.setTextColor(ContextCompat.getColor(this, pingColor))
        } else {
            tvBestServer.text = "Auto-selecting..."
            tvBestServerPing.text = "- ms"
        }
    }
    
    /**
     * Start traffic statistics update with optimized performance
     * Uses coroutines for background processing
     */
    private fun startTrafficStatsUpdate() {
        // Cancel existing job if any
        trafficStatsJob?.cancel()
        
        // Start new job
        trafficStatsJob = ioScope.launch {
            while (isActive) {
                // Collect traffic stats
                viewModel.refreshTrafficStats()
                
                // Wait for next update
                delay(TRAFFIC_UPDATE_INTERVAL)
            }
        }
        
        // Start UI updates
        handler.post(trafficUpdateRunnable)
    }
    
    /**
     * Stop traffic statistics update
     */
    private fun stopTrafficStatsUpdate() {
        trafficStatsJob?.cancel()
        trafficStatsJob = null
        handler.removeCallbacks(trafficUpdateRunnable)
    }
    
    /**
     * Update traffic statistics UI
     * Uses efficient formatting and displays in appropriate units
     */
    private fun updateTrafficStats() {
        viewModel.refreshTrafficStats()
    }
    
    /**
     * Update traffic statistics UI
     * Uses human-readable formatting for data sizes
     */
    private fun updateTrafficStatsUI(stats: MainViewModel.TrafficStats) {
        tvUploadValue.text = formatSpeed(stats.uploadSpeed)
        tvDownloadValue.text = formatSpeed(stats.downloadSpeed)
        tvTotalValue.text = formatDataSize(stats.totalUsage)
    }
    
    /**
     * Format data transfer speed in human-readable form
     */
    private fun formatSpeed(bytesPerSecond: Long): String {
        val formattedSize = formatDataSize(bytesPerSecond)
        return "$formattedSize/s"
    }
    
    /**
     * Format data size in human-readable form
     * Optimized for performance with string builder
     */
    private fun formatDataSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    /**
     * Update version info display
     */
    private fun updateVersionInfo() {
        val appVersion = BuildConfig.VERSION_NAME
        val xrayVersion = viewModel.getXrayVersion()
        tvVersionInfo.text = "Version $appVersion (Xray Core $xrayVersion)"
    }
    
    /**
     * Handle connect/disconnect button click with optimized server selection
     */
    private fun handleConnectButtonClick() {
        val state = viewModel.connectionState.value
        
        if (state == MainViewModel.ConnectionState.CONNECTED) {
            // Disconnect with visual feedback
            btnConnect.isEnabled = false
            viewModel.disconnect()
        } else if (state == MainViewModel.ConnectionState.DISCONNECTED) {
            // Get server to connect with intelligent selection
            val currentServer = viewModel.currentServer.value
            
            if (currentServer != null) {
                // Reconnect to last server with visual feedback
                btnConnect.isEnabled = false
                viewModel.connectToServer(currentServer)
            } else {
                // Find best server to connect
                mainScope.launch {
                    val bestServer = viewModel.bestServer.value ?: findBestServer()
                    
                    if (bestServer != null) {
                        // Show feedback and connect
                        Snackbar.make(
                            btnConnect,
                            "Connecting to best server: ${bestServer.name} (${bestServer.ping} ms)",
                            Snackbar.LENGTH_SHORT
                        ).show()
                        viewModel.connectToServer(bestServer)
                    } else {
                        // No servers available
                        Snackbar.make(
                            btnConnect,
                            "No servers available. Please add a server first.",
                            Snackbar.LENGTH_LONG
                        ).setAction("Add Server") {
                            showAddServerDialog()
                        }.show()
                    }
                }
            }
        }
    }
    
    /**
     * Find the best server based on ping time
     * Optimized algorithm with caching for performance
     */
    private suspend fun findBestServer(): Server? = withContext(Dispatchers.Default) {
        viewModel.allServers.value?.let { servers ->
            // Filter valid servers
            val validServers = servers.filter { it.ping > 0 }
            
            if (validServers.isNotEmpty()) {
                // Find server with lowest ping
                return@withContext validServers.minByOrNull { it.ping }
            }
            
            // If no servers have been pinged yet, ping all and find best
            if (servers.isNotEmpty()) {
                val pingResults = viewModel.pingAllServersAsync()
                return@withContext pingResults.minByOrNull { it.second }?.first
            }
        }
        
        return@withContext null
    }
    
    /**
     * Ping all servers to find the best one
     * Optimized using coroutines for parallel pinging
     */
    private fun pingAllServers() {
        // Show loading indicator
        val snackbar = Snackbar.make(
            btnPingServers,
            "Pinging servers...",
            Snackbar.LENGTH_INDEFINITE
        )
        snackbar.show()
        
        // Ping all servers in background
        mainScope.launch {
            val results = viewModel.pingAllServersAsync()
            
            // Dismiss loading indicator
            snackbar.dismiss()
            
            // Show results
            if (results.isNotEmpty()) {
                val bestServer = results.minByOrNull { it.second }
                if (bestServer != null) {
                    Snackbar.make(
                        btnPingServers,
                        "Best server: ${bestServer.first.name} (${bestServer.second} ms)",
                        Snackbar.LENGTH_LONG
                    ).setAction("Connect") {
                        viewModel.connectToServer(bestServer.first)
                    }.show()
                }
            } else {
                Snackbar.make(
                    btnPingServers,
                    "No servers available to ping",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Update all subscriptions with progress feedback
     */
    private fun updateSubscriptions() {
        // Show loading indicator
        val snackbar = Snackbar.make(
            btnUpdateSubscription,
            "Updating subscriptions...",
            Snackbar.LENGTH_INDEFINITE
        )
        snackbar.show()
        
        // Update subscriptions in background
        mainScope.launch {
            val subscriptionManager = SubscriptionManager(this@MainActivity)
            val results = subscriptionManager.updateAllSubscriptions()
            
            // Dismiss loading indicator
            snackbar.dismiss()
            
            // Show results
            val successCount = results.values.count { it }
            val totalCount = results.size
            
            if (totalCount > 0) {
                Snackbar.make(
                    btnUpdateSubscription,
                    "Updated $successCount of $totalCount subscriptions",
                    Snackbar.LENGTH_LONG
                ).show()
                
                // Refresh server list
                viewModel.refreshData()
                
                // Run ping test if auto-switch is enabled
                if (viewModel.appSettings.value?.autoSwitchToBestServer == true) {
                    pingAllServers()
                }
            } else {
                Snackbar.make(
                    btnUpdateSubscription,
                    "No subscriptions to update",
                    Snackbar.LENGTH_SHORT
                ).setAction("Add") {
                    showImportSubscriptionDialog()
                }.show()
            }
        }
    }
    
    /**
     * Open server list activity
     */
    private fun openServerList() {
        val intent = Intent(this, ServerListActivity::class.java)
        startActivityForResult(intent, SERVER_SELECTION_REQUEST_CODE)
    }
    
    /**
     * Show modern material design add server dialog
     */
    private fun showAddServerDialog() {
        val dialog = AlertDialog.Builder(this, R.style.Theme_MarFaNet_Dialog)
            .setTitle("Add Server")
            .setView(R.layout.dialog_manual_server_input)
            .setPositiveButton("Add") { dialogInterface, _ ->
                val dialog = dialogInterface as AlertDialog
                
                // Get values from dialog
                val nameEditText = dialog.findViewById<TextView>(R.id.et_server_name)
                val protocolSpinner = dialog.findViewById<TextView>(R.id.spinner_protocol)
                val addressEditText = dialog.findViewById<TextView>(R.id.et_server_address)
                val portEditText = dialog.findViewById<TextView>(R.id.et_server_port)
                val uuidEditText = dialog.findViewById<TextView>(R.id.et_server_uuid)
                
                // Create server object
                val server = Server(
                    name = nameEditText?.text?.toString() ?: "New Server",
                    protocol = protocolSpinner?.text?.toString() ?: "vmess",
                    address = addressEditText?.text?.toString() ?: "",
                    port = portEditText?.text?.toString()?.toIntOrNull() ?: 443
                )
                
                // Set UUID
                server.uuid = uuidEditText?.text?.toString()
                
                // Validate server
                if (server.address.isNullOrEmpty()) {
                    Snackbar.make(
                        btnConnect,
                        "Server address cannot be empty",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }
                
                // Add server to database
                viewModel.addServer(server)
                
                Snackbar.make(
                    btnConnect,
                    "Server added successfully",
                    Snackbar.LENGTH_SHORT
                ).setAction("Connect") {
                    viewModel.connectToServer(server)
                }.show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Import from URL") { _, _ ->
                showImportServerDialog()
            }
            .create()
        
        dialog.show()
        
        // Apply custom styling
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.gold_500)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setTextColor(
            ContextCompat.getColor(this, R.color.gold_500)
        )
    }
    
    /**
     * Show import server from URL dialog with protocol detection
     */
    private fun showImportServerDialog() {
        val dialog = AlertDialog.Builder(this, R.style.Theme_MarFaNet_Dialog)
            .setTitle("Import Server from URL")
            .setView(R.layout.dialog_subscription_input)
            .setPositiveButton("Import") { dialogInterface, _ ->
                val dialog = dialogInterface as AlertDialog
                
                // Get URL from dialog
                val urlEditText = dialog.findViewById<TextView>(R.id.et_subscription_url)
                val url = urlEditText?.text?.toString() ?: ""
                
                if (url.isNotEmpty()) {
                    // Try to parse URL as server
                    mainScope.launch {
                        try {
                            // First check if it's a subscription link
                            if (url.startsWith("http")) {
                                showImportSubscriptionDialog(url)
                                return@launch
                            }
                            
                            // Otherwise try to parse as direct server link
                            val protocolStr = url.substringBefore("://")
                            
                            if (protocolStr.isNotEmpty()) {
                                // Find appropriate protocol handler
                                val handler = ProtocolHandler.getHandler(protocolStr)
                                val server = handler.parseUrl(url)
                                
                                if (server != null) {
                                    // Add server to database
                                    viewModel.addServer(server)
                                    
                                    Snackbar.make(
                                        btnConnect,
                                        "Server imported successfully",
                                        Snackbar.LENGTH_SHORT
                                    ).setAction("Connect") {
                                        viewModel.connectToServer(server)
                                    }.show()
                                } else {
                                    Snackbar.make(
                                        btnConnect,
                                        "Failed to parse server URL",
                                        Snackbar.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Snackbar.make(
                                    btnConnect,
                                    "Invalid server URL",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            Snackbar.make(
                                btnConnect,
                                "Error importing server: ${e.message}",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Snackbar.make(
                        btnConnect,
                        "Please enter a server URL",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
        
        // Apply custom styling
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.gold_500)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
    }
    
    /**
     * Show import subscription dialog
     */
    private fun showImportSubscriptionDialog(url: String = "") {
        val dialog = AlertDialog.Builder(this, R.style.Theme_MarFaNet_Dialog)
            .setTitle("Import Subscription")
            .setView(R.layout.dialog_subscription_input)
            .setPositiveButton("Import") { dialogInterface, _ ->
                val dialog = dialogInterface as AlertDialog
                
                // Get values from dialog
                val urlEditText = dialog.findViewById<TextView>(R.id.et_subscription_url)
                val nameEditText = dialog.findViewById<TextView>(R.id.et_subscription_name)
                
                val subscriptionUrl = url.ifEmpty { urlEditText?.text?.toString() ?: "" }
                val subscriptionName = nameEditText?.text?.toString() ?: "New Subscription"
                
                if (subscriptionUrl.isNotEmpty()) {
                    // Show loading indicator
                    val snackbar = Snackbar.make(
                        btnConnect,
                        "Importing subscription...",
                        Snackbar.LENGTH_INDEFINITE
                    )
                    snackbar.show()
                    
                    // Import subscription in background
                    mainScope.launch {
                        try {
                            val success = viewModel.importSubscription(subscriptionUrl, subscriptionName)
                            
                            // Dismiss loading indicator
                            snackbar.dismiss()
                            
                            if (success) {
                                Snackbar.make(
                                    btnConnect,
                                    "Subscription imported successfully",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                                
                                // Run ping test if auto-switch is enabled
                                if (viewModel.appSettings.value?.autoSwitchToBestServer == true) {
                                    pingAllServers()
                                }
                            } else {
                                Snackbar.make(
                                    btnConnect,
                                    "Failed to import subscription",
                                    Snackbar.LENGTH_SHORT
                                ).show()
                            }
                        } catch (e: Exception) {
                            // Dismiss loading indicator
                            snackbar.dismiss()
                            
                            Snackbar.make(
                                btnConnect,
                                "Error importing subscription: ${e.message}",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    Snackbar.make(
                        btnConnect,
                        "Please enter a subscription URL",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
        
        // Set URL if provided
        if (url.isNotEmpty()) {
            val urlEditText = dialog.findViewById<TextView>(R.id.et_subscription_url)
            urlEditText?.text = url
        }
        
        // Apply custom styling
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.gold_500)
        )
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
            ContextCompat.getColor(this, R.color.text_secondary)
        )
    }
    
    /**
     * Show settings bottom sheet with optimized layout
     */
    private fun showSettingsBottomSheet() {
        val dialog = BottomSheetDialog(this, R.style.Theme_MarFaNet_BottomSheetDialog)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        dialog.setContentView(view)
        
        // Initialize settings controls
        viewModel.appSettings.value?.let { settings ->
            // Auto-start settings
            val switchAutoStart = view.findViewById<SwitchCompat>(R.id.switch_auto_start)
            switchAutoStart?.isChecked = settings.autoStart
            
            // Auto-connect settings
            val switchAutoConnect = view.findViewById<SwitchCompat>(R.id.switch_auto_connect)
            switchAutoConnect?.isChecked = settings.autoConnect
            
            // Auto-switch settings
            val switchAutoSwitch = view.findViewById<SwitchCompat>(R.id.switch_auto_switch)
            switchAutoSwitch?.isChecked = settings.autoSwitchToBestServer
            
            // Auto-update settings
            val switchAutoUpdate = view.findViewById<SwitchCompat>(R.id.switch_auto_update)
            switchAutoUpdate?.isChecked = settings.autoUpdateEnabled
            
            // Auto-update subscription settings
            val switchAutoUpdateSubscriptions = view.findViewById<SwitchCompat>(R.id.switch_auto_update_subscriptions)
            switchAutoUpdateSubscriptions?.isChecked = settings.autoUpdateSubscriptions
            
            // Save button
            val btnSaveSettings = view.findViewById<MaterialButton>(R.id.btn_save_settings)
            btnSaveSettings?.setOnClickListener {
                // Update settings
                settings.autoStart = switchAutoStart?.isChecked ?: false
                settings.autoConnect = switchAutoConnect?.isChecked ?: false
                settings.autoSwitchToBestServer = switchAutoSwitch?.isChecked ?: false
                settings.autoUpdateEnabled = switchAutoUpdate?.isChecked ?: false
                settings.autoUpdateSubscriptions = switchAutoUpdateSubscriptions?.isChecked ?: false
                
                // Save settings
                viewModel.updateSettings(settings)
                
                // Update UI
                switchAutoSelect.isChecked = settings.autoSwitchToBestServer
                
                Snackbar.make(
                    btnConnect,
                    "Settings saved",
                    Snackbar.LENGTH_SHORT
                ).show()
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Check for updates with progress feedback
     */
    private fun checkForUpdates() {
        // Show loading indicator
        val snackbar = Snackbar.make(
            btnConnect,
            "Checking for updates...",
            Snackbar.LENGTH_INDEFINITE
        )
        snackbar.show()
        
        mainScope.launch {
            try {
                // Check for app updates
                val appUpdateInfo = viewModel.checkForAppUpdates()
                
                // Check for routing updates
                val routingUpdateInfo = viewModel.checkForRoutingUpdates()
                
                // Dismiss loading indicator
                snackbar.dismiss()
                
                when {
                    appUpdateInfo != null && routingUpdateInfo -> {
                        // Both updates available
                        AlertDialog.Builder(this@MainActivity, R.style.Theme_MarFaNet_Dialog)
                            .setTitle("Updates Available")
                            .setMessage("New app version (${appUpdateInfo.latestVersion}) and routing rules are available. Would you like to update now?")
                            .setPositiveButton("Update") { _, _ ->
                                installUpdates(appUpdateInfo, true)
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    }
                    appUpdateInfo != null -> {
                        // App update available
                        AlertDialog.Builder(this@MainActivity, R.style.Theme_MarFaNet_Dialog)
                            .setTitle("App Update Available")
                            .setMessage("New version (${appUpdateInfo.latestVersion}) is available. Would you like to update now?")
                            .setPositiveButton("Update") { _, _ ->
                                installUpdates(appUpdateInfo, false)
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    }
                    routingUpdateInfo -> {
                        // Routing update available
                        AlertDialog.Builder(this@MainActivity, R.style.Theme_MarFaNet_Dialog)
                            .setTitle("Routing Rules Update")
                            .setMessage("New routing rules are available. Would you like to update now?")
                            .setPositiveButton("Update") { _, _ ->
                                updateRoutingRules()
                            }
                            .setNegativeButton("Later", null)
                            .show()
                    }
                    else -> {
                        // No updates available
                        Snackbar.make(
                            btnConnect,
                            "No updates available",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                // Dismiss loading indicator
                snackbar.dismiss()
                
                Snackbar.make(
                    btnConnect,
                    "Error checking for updates: ${e.message}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Install app updates
     */
    private fun installUpdates(updateInfo: MainViewModel.UpdateInfo, updateRouting: Boolean) {
        // Show progress dialog
        val progressDialog = AlertDialog.Builder(this, R.style.Theme_MarFaNet_Dialog)
            .setTitle("Updating...")
            .setMessage("Downloading updates...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        mainScope.launch {
            try {
                // Download and install app update
                val success = viewModel.downloadAndInstallUpdate(updateInfo)
                
                // Update routing rules if requested
                if (updateRouting) {
                    updateRoutingRules()
                }
                
                // Dismiss progress dialog
                progressDialog.dismiss()
                
                if (success) {
                    AlertDialog.Builder(this@MainActivity, R.style.Theme_MarFaNet_Dialog)
                        .setTitle("Update Complete")
                        .setMessage("The app will restart to apply updates.")
                        .setPositiveButton("OK") { _, _ ->
                            // Restart app
                            val intent = packageManager.getLaunchIntentForPackage(packageName)
                            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(intent)
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    Snackbar.make(
                        btnConnect,
                        "Update failed",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                // Dismiss progress dialog
                progressDialog.dismiss()
                
                Snackbar.make(
                    btnConnect,
                    "Error installing updates: ${e.message}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Update routing rules
     */
    private fun updateRoutingRules() {
        // Show progress dialog
        val progressDialog = AlertDialog.Builder(this, R.style.Theme_MarFaNet_Dialog)
            .setTitle("Updating Routing Rules...")
            .setMessage("Downloading latest routing rules...")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        mainScope.launch {
            try {
                // Update routing rules
                val success = viewModel.updateRoutingRules()
                
                // Dismiss progress dialog
                progressDialog.dismiss()
                
                if (success) {
                    Snackbar.make(
                        btnConnect,
                        "Routing rules updated successfully",
                        Snackbar.LENGTH_SHORT
                    ).show()
                } else {
                    Snackbar.make(
                        btnConnect,
                        "Failed to update routing rules",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                // Dismiss progress dialog
                progressDialog.dismiss()
                
                Snackbar.make(
                    btnConnect,
                    "Error updating routing rules: ${e.message}",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Handle intent for URL schemes
     */
    private fun handleIntent(intent: Intent) {
        val data = intent.data
        if (data != null) {
            val scheme = data.scheme
            val url = data.toString()
            
            if (scheme == "xhttp" || scheme == "hysteria") {
                // Handle server URL
                mainScope.launch {
                    try {
                        val handler = ProtocolHandler.getHandler(scheme)
                        val server = handler.parseUrl(url)
                        
                        if (server != null) {
                            // Show dialog to add server
                            AlertDialog.Builder(this@MainActivity, R.style.Theme_MarFaNet_Dialog)
                                .setTitle("Import Server")
                                .setMessage("Do you want to import this server?\n\nProtocol: ${scheme.uppercase(Locale.ROOT)}\nServer: ${server.address}:${server.port}")
                                .setPositiveButton("Import") { _, _ ->
                                    // Add server to database
                                    viewModel.addServer(server)
                                    
                                    Snackbar.make(
                                        btnConnect,
                                        "Server imported successfully",
                                        Snackbar.LENGTH_SHORT
                                    ).setAction("Connect") {
                                        viewModel.connectToServer(server)
                                    }.show()
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        } else {
                            Snackbar.make(
                                btnConnect,
                                "Failed to parse server URL",
                                Snackbar.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Snackbar.make(
                            btnConnect,
                            "Error importing server: ${e.message}",
                            Snackbar.LENGTH_SHORT
                        ).show()
                    }
                }
            } else if (scheme == "http" || scheme == "https") {
                // Handle subscription URL
                showImportSubscriptionDialog(url)
            }
        }
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == SERVER_SELECTION_REQUEST_CODE && resultCode == RESULT_OK) {
            // Server selected, refresh data
            viewModel.refreshData()
            
            // If a server was selected for connection
            data?.getLongExtra("selected_server_id", -1)?.let { serverId ->
                if (serverId != -1L) {
                    // Connect to selected server
                    mainScope.launch {
                        val server = viewModel.getServerById(serverId)
                        if (server != null) {
                            viewModel.connectToServer(server)
                        }
                    }
                }
            }
        } else if (requestCode == SETTINGS_REQUEST_CODE && resultCode == RESULT_OK) {
            // Settings updated, refresh data
            viewModel.refreshData()
        }
    }
}