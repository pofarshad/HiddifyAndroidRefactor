package com.hiddify.hiddifyng.activity

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.hiddify.hiddifyng.R
import com.hiddify.hiddifyng.adapter.ServerAdapter
import com.hiddify.hiddifyng.core.XrayManager
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.databinding.ActivityMainBinding
import com.hiddify.hiddifyng.utils.ServerComparator
import com.hiddify.hiddifyng.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main activity for the MarFaNet app
 * Controls VPN connection, server selection, and shows traffic statistics
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_VPN_PERMISSION = 1
    }
    
    // View binding
    private lateinit var binding: ActivityMainBinding
    
    // ViewModel
    private lateinit var viewModel: MainViewModel
    
    // XrayManager
    private lateinit var xrayManager: XrayManager
    
    // Server adapter
    private lateinit var serverAdapter: ServerAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize view binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        
        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        
        // Initialize XrayManager
        xrayManager = XrayManager.getInstance(this)
        
        // Set up server list
        setupServerList()
        
        // Set up listeners
        setupListeners()
        
        // Observe connection state
        observeConnectionState()
        
        // Observe traffic stats
        observeTrafficStats()
        
        // Observe workers
        observeWorkers()
        
        // Check for updates
        checkForUpdates()
        
        // Process intent
        processIntent(intent)
    }
    
    /**
     * Set up server list with RecyclerView
     */
    private fun setupServerList() {
        serverAdapter = ServerAdapter { server ->
            onServerSelected(server)
        }
        
        binding.serverRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = serverAdapter
        }
        
        // Load servers
        loadServers()
    }
    
    /**
     * Load servers from database
     */
    private fun loadServers() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val servers = getServersFromDatabase()
                
                withContext(Dispatchers.Main) {
                    if (servers.isEmpty()) {
                        // No servers, show empty state
                        binding.serverRecyclerView.visibility = View.GONE
                        // Add empty state view if needed
                    } else {
                        binding.serverRecyclerView.visibility = View.VISIBLE
                        serverAdapter.submitList(servers)
                        
                        // Update selected state
                        val currentServerId = xrayManager.getCurrentServerId()
                        if (currentServerId > 0) {
                            val selectedServer = servers.find { it.id == currentServerId }
                            selectedServer?.let {
                                serverAdapter.setSelectedServer(it)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading servers", e)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error loading servers", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Get servers from database
     * @return List of servers
     */
    private suspend fun getServersFromDatabase(): List<Server> = withContext(Dispatchers.IO) {
        // This would be implemented to get servers from database
        // For now, we'll just create some dummy servers
        val dummyServers = listOf(
            Server(
                id = 1,
                name = "Singapore Server",
                protocol = "vless",
                address = "sg.example.com:443",
                port = 443,
                avgPing = 120
            ),
            Server(
                id = 2,
                name = "Japan Server",
                protocol = "vmess",
                address = "jp.example.com:443",
                port = 443,
                avgPing = 78
            ),
            Server(
                id = 3,
                name = "Germany Server",
                protocol = "trojan",
                address = "de.example.com:443",
                port = 443,
                avgPing = 156
            ),
            Server(
                id = 4,
                name = "USA Server",
                protocol = "vless",
                address = "us.example.com:443",
                port = 443,
                avgPing = 230
            ),
            Server(
                id = 5,
                name = "Local Testing Server",
                protocol = "hysteria",
                address = "local.example.com:8443",
                port = 8443,
                avgPing = 5
            )
        )
        
        // Sort by ping (lowest first)
        return@withContext dummyServers.sortedWith(ServerComparator())
    }
    
    /**
     * Set up click listeners
     */
    private fun setupListeners() {
        // Connect/disconnect button
        binding.connectButton.setOnClickListener {
            if (xrayManager.isServiceRunning()) {
                disconnectVPN()
            } else {
                connectToBestServer()
            }
        }
        
        // Add server button
        binding.addServerFab.setOnClickListener {
            // Open add server activity
            Toast.makeText(this, "Add server (not yet implemented)", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Observe connection state changes
     */
    private fun observeConnectionState() {
        viewModel.connectionState.observe(this) { state ->
            when (state) {
                MainViewModel.ConnectionState.CONNECTING -> {
                    binding.statusText.text = getString(R.string.connecting)
                    binding.statusText.setTextColor(getColor(R.color.status_connecting))
                    binding.connectButton.isEnabled = false
                }
                MainViewModel.ConnectionState.CONNECTED -> {
                    binding.statusText.text = getString(R.string.connected)
                    binding.statusText.setTextColor(getColor(R.color.status_connected))
                    binding.connectButton.text = getString(R.string.disconnect)
                    binding.connectButton.isEnabled = true
                    
                    // Start traffic monitoring
                    viewModel.startTrafficStatsMonitoring()
                }
                MainViewModel.ConnectionState.DISCONNECTED -> {
                    binding.statusText.text = getString(R.string.disconnected)
                    binding.statusText.setTextColor(getColor(R.color.status_disconnected))
                    binding.connectButton.text = getString(R.string.connect)
                    binding.connectButton.isEnabled = true
                    
                    // Stop traffic monitoring
                    viewModel.stopTrafficStatsMonitoring()
                }
                MainViewModel.ConnectionState.ERROR -> {
                    binding.statusText.text = "Error"
                    binding.statusText.setTextColor(getColor(R.color.status_disconnected))
                    binding.connectButton.text = getString(R.string.connect)
                    binding.connectButton.isEnabled = true
                    
                    // Stop traffic monitoring
                    viewModel.stopTrafficStatsMonitoring()
                }
            }
        }
    }
    
    /**
     * Observe traffic statistics
     */
    private fun observeTrafficStats() {
        viewModel.trafficStats.observe(this) { stats ->
            binding.uploadText.text = stats.formatSpeed(stats.getUploadSpeed())
            binding.downloadText.text = stats.formatSpeed(stats.getDownloadSpeed())
        }
    }
    
    /**
     * Observe background worker states
     */
    private fun observeWorkers() {
        // Observe ping worker
        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData("ping_worker")
            .observe(this) { workInfos ->
                if (workInfos.isNullOrEmpty()) return@observe
                
                val workInfo = workInfos[0]
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    // Ping worker completed, refresh server list
                    loadServers()
                }
            }
            
        // Observe subscription worker
        WorkManager.getInstance(this)
            .getWorkInfosByTagLiveData("subscription_worker")
            .observe(this) { workInfos ->
                if (workInfos.isNullOrEmpty()) return@observe
                
                val workInfo = workInfos[0]
                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    // Subscription worker completed, refresh server list
                    loadServers()
                }
            }
    }
    
    /**
     * Check for app and routing updates
     */
    private fun checkForUpdates() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check for app updates
                val appUpdateAvailable = viewModel.checkForAppUpdates()
                
                // Check for routing updates
                val routingUpdateAvailable = viewModel.checkForRoutingUpdates()
                
                if (appUpdateAvailable) {
                    withContext(Dispatchers.Main) {
                        // Show app update notification
                        Toast.makeText(this@MainActivity, "App update available", Toast.LENGTH_SHORT).show()
                    }
                }
                
                if (routingUpdateAvailable) {
                    withContext(Dispatchers.Main) {
                        // Show routing update notification
                        Toast.makeText(this@MainActivity, "Routing rules updated", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
            }
        }
    }
    
    /**
     * Process intent (e.g., deeplinks, share intents)
     */
    private fun processIntent(intent: Intent?) {
        intent?.let {
            val action = it.action
            val data = it.data
            
            if (action == Intent.ACTION_VIEW && data != null) {
                // Handle deeplink (e.g., subscription URLs)
                val uri = data.toString()
                
                // Parse server URI or subscription URL
                Toast.makeText(this, "Received URL: $uri", Toast.LENGTH_SHORT).show()
                
                // Process URL in background
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Process URI
                        // This would be implemented to parse URI and add server/subscription
                        
                        withContext(Dispatchers.Main) {
                            // Refresh server list
                            loadServers()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing URI", e)
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Error processing URI", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Handle server selection
     */
    private fun onServerSelected(server: Server) {
        // Mark selected in UI
        serverAdapter.setSelectedServer(server)
        
        // Connect to server
        connectToServer(server.id)
    }
    
    /**
     * Connect to server
     */
    private fun connectToServer(serverId: Long) {
        // Update UI state
        viewModel.connectionState.value = MainViewModel.ConnectionState.CONNECTING
        
        // Start VPN
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = xrayManager.startXray(serverId)
                
                withContext(Dispatchers.Main) {
                    if (result) {
                        // Connection successful
                        viewModel.connectionState.value = MainViewModel.ConnectionState.CONNECTED
                    } else {
                        // Connection failed
                        viewModel.connectionState.value = MainViewModel.ConnectionState.ERROR
                        Toast.makeText(this@MainActivity, "Failed to connect", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to server", e)
                
                withContext(Dispatchers.Main) {
                    viewModel.connectionState.value = MainViewModel.ConnectionState.ERROR
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Connect to best server (lowest ping)
     */
    private fun connectToBestServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val servers = getServersFromDatabase()
                
                if (servers.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "No servers available", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Find server with lowest ping
                val bestServer = servers.minByOrNull { it.avgPing ?: Int.MAX_VALUE }
                
                bestServer?.let {
                    withContext(Dispatchers.Main) {
                        // Mark selected in UI
                        serverAdapter.setSelectedServer(it)
                        
                        // Connect to server
                        connectToServer(it.id)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error finding best server", e)
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error finding best server", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Disconnect VPN
     */
    private fun disconnectVPN() {
        viewModel.connectionState.value = MainViewModel.ConnectionState.CONNECTING
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = xrayManager.stopXray()
                
                withContext(Dispatchers.Main) {
                    if (result) {
                        // Disconnection successful
                        viewModel.connectionState.value = MainViewModel.ConnectionState.DISCONNECTED
                    } else {
                        // Disconnection failed
                        viewModel.connectionState.value = MainViewModel.ConnectionState.ERROR
                        Toast.makeText(this@MainActivity, "Failed to disconnect", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting", e)
                
                withContext(Dispatchers.Main) {
                    viewModel.connectionState.value = MainViewModel.ConnectionState.ERROR
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * Create options menu
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    /**
     * Handle options menu item selection
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_settings -> {
                // Open settings activity
                Toast.makeText(this, "Settings (not yet implemented)", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.menu_ping -> {
                // Manually trigger ping for all servers
                Toast.makeText(this, "Pinging servers...", Toast.LENGTH_SHORT).show()
                // Trigger ping worker
                true
            }
            R.id.menu_update -> {
                // Manually trigger subscription update
                Toast.makeText(this, "Updating subscriptions...", Toast.LENGTH_SHORT).show()
                // Trigger subscription worker
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * Handle new intent (e.g., deeplinks while app is running)
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        processIntent(intent)
    }
    
    /**
     * Handle activity result (e.g., VPN permission)
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_VPN_PERMISSION) {
            if (resultCode == RESULT_OK) {
                // VPN permission granted
                // reconnect to the server
                val currentServerId = xrayManager.getCurrentServerId()
                if (currentServerId > 0) {
                    connectToServer(currentServerId)
                } else {
                    connectToBestServer()
                }
            } else {
                // VPN permission denied
                Toast.makeText(this, "VPN permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }
}