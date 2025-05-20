package com.hiddify.hiddifyng.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hiddify.hiddifyng.core.XrayManager
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.database.entity.AppSettings
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.service.V2RayServiceManager
import com.hiddify.hiddifyng.utils.AutoUpdateManager
import com.hiddify.hiddifyng.utils.PingUtils
import com.hiddify.hiddifyng.utils.RoutingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for MainActivity
 * Handles connection state, server management, and settings
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "MainViewModel"
    
    // Database references
    private val database = AppDatabase.getInstance(application)
    private val serverDao = database.serverDao()
    private val settingsDao = database.appSettingsDao()
    
    // Manager instances
    private val xrayManager = XrayManager(application)
    private val updateManager = AutoUpdateManager(application)
    private val routingManager = RoutingManager(application)
    
    // LiveData for UI
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState
    
    private val _connectionStats = MutableLiveData<ConnectionStats>()
    val connectionStats: LiveData<ConnectionStats> = _connectionStats
    
    private val _currentServer = MutableLiveData<Server?>()
    val currentServer: LiveData<Server?> = _currentServer
    
    // Get all servers from database
    val allServers = serverDao.getAllServers()
    
    // Get app settings from database
    val appSettings = settingsDao.getSettingsLive()
    
    // Initial state
    init {
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectionStats.value = ConnectionStats(0, 0, 0)
        
        // Check current connection state
        checkConnectionState()
        
        // Initialize routing files
        initializeRoutingFiles()
    }
    
    /**
     * Initialize routing files if needed
     */
    private fun initializeRoutingFiles() {
        viewModelScope.launch {
            try {
                val initialized = routingManager.initializeRoutingFiles()
                if (initialized) {
                    Log.i(TAG, "Routing files initialized successfully")
                } else {
                    Log.e(TAG, "Failed to initialize routing files")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing routing files", e)
            }
        }
    }
    
    /**
     * Check current connection state
     */
    private fun checkConnectionState() {
        viewModelScope.launch {
            try {
                if (xrayManager.isRunning()) {
                    _connectionState.value = ConnectionState.CONNECTED
                    
                    // Get current server
                    val serverId = xrayManager.getCurrentServerId()
                    if (serverId != -1L) {
                        val server = serverDao.getServerByIdSync(serverId)
                        _currentServer.value = server
                    }
                } else {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _currentServer.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking connection state", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                _currentServer.value = null
            }
        }
    }
    
    /**
     * Connect to a server
     * @param server Server to connect to
     */
    fun connectToServer(server: Server) {
        viewModelScope.launch {
            try {
                _connectionState.value = ConnectionState.CONNECTING
                
                // Start VPN service
                V2RayServiceManager.startService(getApplication(), server.id)
                
                // Update current server and state
                _currentServer.value = server
                _connectionState.value = ConnectionState.CONNECTED
                
                // Update preferred server ID in settings
                settingsDao.setPreferredServerId(server.id)
                
                Log.i(TAG, "Connected to server: ${server.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to server", e)
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }
    
    /**
     * Disconnect from current server
     */
    fun disconnect() {
        viewModelScope.launch {
            try {
                _connectionState.value = ConnectionState.DISCONNECTING
                
                // Stop VPN service
                V2RayServiceManager.stopService(getApplication())
                
                // Update state
                _currentServer.value = null
                _connectionState.value = ConnectionState.DISCONNECTED
                
                Log.i(TAG, "Disconnected from server")
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting", e)
            }
        }
    }
    
    /**
     * Ping all servers to find the best one
     */
    fun pingAllServers() {
        viewModelScope.launch {
            try {
                val servers = serverDao.getAllServers().value ?: return@launch
                
                for (server in servers) {
                    try {
                        val ping = PingUtils.pingServer(server)
                        
                        if (ping > 0) {
                            serverDao.updatePing(server.id, ping)
                        } else {
                            serverDao.updatePing(server.id, Int.MAX_VALUE)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pinging server: ${server.name}", e)
                    }
                }
                
                Log.i(TAG, "Finished pinging all servers")
            } catch (e: Exception) {
                Log.e(TAG, "Error during ping test", e)
            }
        }
    }
    
    /**
     * Connect to the server with lowest ping
     */
    fun connectToBestServer() {
        viewModelScope.launch {
            try {
                val bestServer = serverDao.getServerWithLowestPing()
                
                if (bestServer != null) {
                    Log.i(TAG, "Connecting to best server: ${bestServer.name} (${bestServer.ping} ms)")
                    connectToServer(bestServer)
                } else {
                    Log.e(TAG, "No server with valid ping found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error connecting to best server", e)
            }
        }
    }
    
    /**
     * Add a new server
     * @param server Server to add
     */
    fun addServer(server: Server) {
        viewModelScope.launch {
            try {
                val id = serverDao.insert(server)
                Log.i(TAG, "Added new server: ${server.name} with ID: $id")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding server", e)
            }
        }
    }
    
    /**
     * Update a server
     * @param server Server to update
     */
    fun updateServer(server: Server) {
        viewModelScope.launch {
            try {
                serverDao.update(server)
                Log.i(TAG, "Updated server: ${server.name}")
                
                // If this is the current server, update the current server LiveData
                if (_currentServer.value?.id == server.id) {
                    _currentServer.value = server
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating server", e)
            }
        }
    }
    
    /**
     * Delete a server
     * @param server Server to delete
     */
    fun deleteServer(server: Server) {
        viewModelScope.launch {
            try {
                // If we're connected to this server, disconnect first
                if (_currentServer.value?.id == server.id) {
                    disconnect()
                }
                
                // Delete the server
                serverDao.delete(server)
                Log.i(TAG, "Deleted server: ${server.name}")
                
                // If this was the preferred server, clear the preference
                val settings = settingsDao.getSettings()
                if (settings?.preferredServerId == server.id) {
                    settingsDao.setPreferredServerId(null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting server", e)
            }
        }
    }
    
    /**
     * Update app settings
     * @param settings Updated settings
     */
    fun updateSettings(settings: AppSettings) {
        viewModelScope.launch {
            try {
                settingsDao.update(settings)
                Log.i(TAG, "Updated app settings")
                
                // Update auto-update schedule if changed
                if (settings.autoUpdateEnabled) {
                    updateManager.setAutoUpdateEnabled(true)
                    updateManager.scheduleUpdateChecks(settings.updateFrequencyHours)
                } else {
                    updateManager.setAutoUpdateEnabled(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating settings", e)
            }
        }
    }
    
    /**
     * Check for updates manually
     * @return true if updates are available, false otherwise
     */
    suspend fun checkForUpdates(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Check for app updates
            val updateInfo = updateManager.checkForUpdates()
            
            if (updateInfo != null) {
                Log.i(TAG, "New app update available: ${updateInfo.latestVersion}")
                return@withContext true
            }
            
            // Check for routing updates
            val routingUpdates = routingManager.checkForRoutingUpdates()
            
            if (routingUpdates) {
                Log.i(TAG, "New routing updates available")
                return@withContext true
            }
            
            Log.i(TAG, "No updates available")
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return@withContext false
        }
    }
    
    /**
     * Get Xray version info
     * @return Xray version string
     */
    fun getXrayVersion(): String {
        return try {
            xrayManager.getXrayVersion()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting Xray version", e)
            "Unknown"
        }
    }
    
    /**
     * Enum class for connection states
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        DISCONNECTING
    }
    
    /**
     * Data class for connection statistics
     */
    data class ConnectionStats(
        val upSpeed: Long, // in bytes per second
        val downSpeed: Long, // in bytes per second
        val upTotal: Long // in bytes
    )
}