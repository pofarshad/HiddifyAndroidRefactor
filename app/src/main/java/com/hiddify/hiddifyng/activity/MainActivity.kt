package com.hiddify.hiddifyng.activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.protocols.ProtocolHandler
import com.hiddify.hiddifyng.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main activity for the application
 * Shows server list, connection status, and provides VPN controls
 */
class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: MainViewModel
    
    // UI components
    private lateinit var btnConnect: Button
    private lateinit var btnPingServers: Button
    private lateinit var tvConnectionStatus: TextView
    private lateinit var tvCurrentServer: TextView
    private lateinit var fabAddServer: FloatingActionButton
    
    // Coroutine scope
    private val mainScope = CoroutineScope(Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        // Initialize UI components
        initializeUI()
        
        // Setup observers
        setupObservers()
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
                // Open server list activity
                val intent = Intent(this, ServerListActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * Initialize UI components
     */
    private fun initializeUI() {
        // Get references to UI components
        btnConnect = findViewById(R.id.btn_connect)
        btnPingServers = findViewById(R.id.btn_ping_servers)
        tvConnectionStatus = findViewById(R.id.tv_connection_status)
        tvCurrentServer = findViewById(R.id.tv_current_server)
        fabAddServer = findViewById(R.id.fab_add_server)
        
        // Setup click listeners
        btnConnect.setOnClickListener {
            handleConnectButtonClick()
        }
        
        btnPingServers.setOnClickListener {
            pingAllServers()
        }
        
        fabAddServer.setOnClickListener {
            showAddServerDialog()
        }
    }
    
    /**
     * Setup observers for LiveData
     */
    private fun setupObservers() {
        // Observe connection state
        viewModel.connectionState.observe(this) { state ->
            updateConnectionState(state)
        }
        
        // Observe current server
        viewModel.currentServer.observe(this) { server ->
            updateCurrentServer(server)
        }
        
        // Observe app settings
        viewModel.appSettings.observe(this) { settings ->
            // Update UI based on settings if needed
        }
    }
    
    /**
     * Update UI based on connection state
     */
    private fun updateConnectionState(state: MainViewModel.ConnectionState) {
        when (state) {
            MainViewModel.ConnectionState.DISCONNECTED -> {
                tvConnectionStatus.text = "Disconnected"
                btnConnect.text = "Connect"
                btnConnect.isEnabled = true
            }
            MainViewModel.ConnectionState.CONNECTING -> {
                tvConnectionStatus.text = "Connecting..."
                btnConnect.text = "Connecting..."
                btnConnect.isEnabled = false
            }
            MainViewModel.ConnectionState.CONNECTED -> {
                tvConnectionStatus.text = "Connected"
                btnConnect.text = "Disconnect"
                btnConnect.isEnabled = true
            }
            MainViewModel.ConnectionState.DISCONNECTING -> {
                tvConnectionStatus.text = "Disconnecting..."
                btnConnect.text = "Disconnecting..."
                btnConnect.isEnabled = false
            }
        }
    }
    
    /**
     * Update current server display
     */
    private fun updateCurrentServer(server: Server?) {
        if (server != null) {
            tvCurrentServer.text = "Connected to: ${server.name}"
            tvCurrentServer.visibility = View.VISIBLE
        } else {
            tvCurrentServer.visibility = View.GONE
        }
    }
    
    /**
     * Handle connect/disconnect button click
     */
    private fun handleConnectButtonClick() {
        val state = viewModel.connectionState.value
        
        if (state == MainViewModel.ConnectionState.CONNECTED) {
            // Disconnect
            viewModel.disconnect()
        } else if (state == MainViewModel.ConnectionState.DISCONNECTED) {
            // Get server to connect
            val currentServer = viewModel.currentServer.value
            
            if (currentServer != null) {
                // Reconnect to last server
                viewModel.connectToServer(currentServer)
            } else {
                // Find best server to connect
                mainScope.launch {
                    val bestServer = viewModel.allServers.value?.minByOrNull { it.ping }
                    
                    if (bestServer != null) {
                        viewModel.connectToServer(bestServer)
                    } else {
                        // No servers available
                        Toast.makeText(
                            this@MainActivity,
                            "No servers available. Please add a server first.",
                            Toast.LENGTH_SHORT
                        ).show()
                        
                        // Show add server dialog
                        showAddServerDialog()
                    }
                }
            }
        }
    }
    
    /**
     * Ping all servers to find the best one
     */
    private fun pingAllServers() {
        Toast.makeText(this, "Pinging servers...", Toast.LENGTH_SHORT).show()
        viewModel.pingAllServers()
    }
    
    /**
     * Show add server dialog
     */
    private fun showAddServerDialog() {
        val dialog = AlertDialog.Builder(this)
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
                
                // Add server to database
                viewModel.addServer(server)
                
                Toast.makeText(this, "Server added successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Import from URL") { _, _ ->
                showImportServerDialog()
            }
            .create()
        
        dialog.show()
    }
    
    /**
     * Show import server from URL dialog
     */
    private fun showImportServerDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Import Server from URL")
            .setView(R.layout.dialog_subscription_input)
            .setPositiveButton("Import") { dialogInterface, _ ->
                val dialog = dialogInterface as AlertDialog
                
                // Get URL from dialog
                val urlEditText = dialog.findViewById<TextView>(R.id.et_subscription_url)
                val url = urlEditText?.text?.toString() ?: ""
                
                if (url.isNotEmpty()) {
                    // Try to parse URL as server
                    try {
                        // Extract protocol
                        val protocolStr = url.substringBefore("://")
                        
                        if (protocolStr.isNotEmpty()) {
                            val handler = ProtocolHandler.getHandler(protocolStr)
                            val server = handler.parseUrl(url)
                            
                            if (server != null) {
                                // Add server to database
                                viewModel.addServer(server)
                                
                                Toast.makeText(
                                    this,
                                    "Server imported successfully",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this,
                                    "Failed to parse server URL",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            Toast.makeText(
                                this,
                                "Invalid server URL",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            this,
                            "Error importing server: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Please enter a server URL",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.show()
    }
    
    /**
     * Show settings bottom sheet
     */
    private fun showSettingsBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_settings, null)
        dialog.setContentView(view)
        
        // Initialize settings controls
        viewModel.appSettings.value?.let { settings ->
            // Auto-start settings
            val switchAutoStart = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_auto_start)
            switchAutoStart?.isChecked = settings.autoStart
            
            // Auto-connect settings
            val switchAutoConnect = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_auto_connect)
            switchAutoConnect?.isChecked = settings.autoConnect
            
            // Auto-switch settings
            val switchAutoSwitch = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_auto_switch)
            switchAutoSwitch?.isChecked = settings.autoSwitchToBestServer
            
            // Auto-update settings
            val switchAutoUpdate = view.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.switch_auto_update)
            switchAutoUpdate?.isChecked = settings.autoUpdateEnabled
            
            // Save button
            val btnSaveSettings = view.findViewById<Button>(R.id.btn_save_settings)
            btnSaveSettings?.setOnClickListener {
                // Update settings
                settings.autoStart = switchAutoStart?.isChecked ?: false
                settings.autoConnect = switchAutoConnect?.isChecked ?: false
                settings.autoSwitchToBestServer = switchAutoSwitch?.isChecked ?: false
                settings.autoUpdateEnabled = switchAutoUpdate?.isChecked ?: false
                
                // Save settings
                viewModel.updateSettings(settings)
                
                Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }
    
    /**
     * Check for updates
     */
    private fun checkForUpdates() {
        Toast.makeText(this, "Checking for updates...", Toast.LENGTH_SHORT).show()
        
        mainScope.launch {
            val hasUpdates = viewModel.checkForUpdates()
            
            if (hasUpdates) {
                Toast.makeText(
                    this@MainActivity,
                    "New updates available. Updates will be installed automatically.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "No updates available",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}