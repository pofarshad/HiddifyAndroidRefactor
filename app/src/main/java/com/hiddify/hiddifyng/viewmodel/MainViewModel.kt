package com.hiddify.hiddifyng.viewmodel

import android.app.Application
import android.content.Intent
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val xrayManager = XrayManager(application)
    private val routingManager = RoutingManager(application)
    private val pingUtils = PingUtils(application)
    private val autoUpdateManager = AutoUpdateManager(application)
    
    // Service state
    private val _isRunning = MutableLiveData<Boolean>(false)
    val isRunning: LiveData<Boolean> = _isRunning
    
    // Current connected server
    private val _currentServer = MutableLiveData<Server?>(null)
    val currentServer: LiveData<Server?> = _currentServer
    
    // Server list
    val allServers = database.serverDao().getAllServersLive()
    
    // App settings
    val appSettings = MutableStateFlow<AppSettings?>(null)
    
    // Connection statistics
    private val _uploadSpeed = MutableLiveData<Long>(0)
    val uploadSpeed: LiveData<Long> = _uploadSpeed
    
    private val _downloadSpeed = MutableLiveData<Long>(0)
    val downloadSpeed: LiveData<Long> = _downloadSpeed
    
    // Update info
    private val _updateAvailable = MutableLiveData<Boolean>(false)
    val updateAvailable: LiveData<Boolean> = _updateAvailable
    
    private val _updateInfo = MutableStateFlow<AutoUpdateManager.UpdateInfo?>(null)
    val updateInfo: StateFlow<AutoUpdateManager.UpdateInfo?> = _updateInfo
    
    // Action status
    private val _actionStatus = MutableLiveData<ActionStatus>()
    val actionStatus: LiveData<ActionStatus> = _actionStatus
    
    init {
        // Load app settings
        viewModelScope.launch(Dispatchers.IO) {
            appSettings.value = database.appSettingsDao().getSettings()
            
            // Initialize routing files if needed
            routingManager.initializeRoutingFiles()
            
            // Check for app updates
            checkForAppUpdates()
        }
    }
    
    /**
     * Start the VPN service with the selected server
     */
    fun startVpn(serverId: Long? = null) {
        viewModelScope.launch {
            try {
                val serverToUse = if (serverId != null) {
                    // Use specified server
                    database.serverDao().getServerById(serverId)
                } else {
                    // Use preferred server from settings, or best by ping, or first in list
                    val settings = database.appSettingsDao().getSettings()
                    if (settings?.preferredServerId != null) {
                        database.serverDao().getServerById(settings.preferredServerId!!)
                    } else {
                        val bestServer = database.serverDao().getBestServerByPing()
                        bestServer ?: database.serverDao().getAllServers().firstOrNull()
                    }
                }
                
                if (serverToUse != null) {
                    val ctx = getApplication<Application>()
                    V2RayServiceManager.startService(ctx, serverToUse.id)
                    _isRunning.postValue(true)
                    _currentServer.postValue(serverToUse)
                    _actionStatus.postValue(ActionStatus.SUCCESS)
                } else {
                    _actionStatus.postValue(ActionStatus.ERROR("No server available"))
                }
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Unknown error"))
            }
        }
    }
    
    /**
     * Stop the VPN service
     */
    fun stopVpn() {
        viewModelScope.launch {
            try {
                val ctx = getApplication<Application>()
                V2RayServiceManager.stopService(ctx)
                _isRunning.postValue(false)
                _currentServer.postValue(null)
                _actionStatus.postValue(ActionStatus.SUCCESS)
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Unknown error"))
            }
        }
    }
    
    /**
     * Run ping test on all servers
     */
    fun pingAllServers() {
        viewModelScope.launch {
            _actionStatus.postValue(ActionStatus.LOADING("Pinging servers..."))
            
            try {
                val servers = database.serverDao().getAllServers()
                
                for (server in servers) {
                    val ping = pingUtils.measurePing(server.address)
                    server.ping = ping
                    database.serverDao().updateServer(server)
                }
                
                _actionStatus.postValue(ActionStatus.SUCCESS)
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error pinging servers"))
            }
        }
    }
    
    /**
     * Add a new server
     */
    fun addServer(server: Server) {
        viewModelScope.launch {
            _actionStatus.postValue(ActionStatus.LOADING("Adding server..."))
            
            try {
                val newId = database.serverDao().insert(server)
                _actionStatus.postValue(ActionStatus.SUCCESS)
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error adding server"))
            }
        }
    }
    
    /**
     * Update an existing server
     */
    fun updateServer(server: Server) {
        viewModelScope.launch {
            _actionStatus.postValue(ActionStatus.LOADING("Updating server..."))
            
            try {
                database.serverDao().updateServer(server)
                _actionStatus.postValue(ActionStatus.SUCCESS)
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error updating server"))
            }
        }
    }
    
    /**
     * Delete a server
     */
    fun deleteServer(server: Server) {
        viewModelScope.launch {
            _actionStatus.postValue(ActionStatus.LOADING("Deleting server..."))
            
            try {
                database.serverDao().deleteServer(server)
                _actionStatus.postValue(ActionStatus.SUCCESS)
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error deleting server"))
            }
        }
    }
    
    /**
     * Import servers from subscription URL
     */
    fun importFromUrl(url: String, groupId: Long?) {
        viewModelScope.launch {
            _actionStatus.postValue(ActionStatus.LOADING("Importing servers..."))
            
            try {
                val group = groupId ?: withContext(Dispatchers.IO) {
                    // Create default group if none specified
                    val defaultGroupName = "Imported ${System.currentTimeMillis()}"
                    val newGroup = ServerGroup(name = defaultGroupName)
                    database.serverGroupDao().insert(newGroup)
                }
                
                val count = database.serverDao().importFromUrl(url, group)
                _actionStatus.postValue(ActionStatus.SUCCESS_WITH_DATA("Imported $count servers", count))
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error importing servers"))
            }
        }
    }
    
    /**
     * Parse and import server from URL
     */
    fun importFromServerUrl(url: String) {
        viewModelScope.launch {
            _actionStatus.postValue(ActionStatus.LOADING("Importing server..."))
            
            try {
                val server = database.serverDao().parseServerFromUrl(url)
                
                if (server != null) {
                    val newId = database.serverDao().insert(server)
                    _actionStatus.postValue(ActionStatus.SUCCESS)
                } else {
                    _actionStatus.postValue(ActionStatus.ERROR("Invalid server URL"))
                }
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error importing server"))
            }
        }
    }
    
    /**
     * Connect to the best server based on ping
     */
    fun connectToBestServer() {
        viewModelScope.launch {
            _actionStatus.postValue(ActionStatus.LOADING("Finding best server..."))
            
            try {
                val bestServer = database.serverDao().getBestServerByPing()
                
                if (bestServer != null) {
                    startVpn(bestServer.id)
                } else {
                    _actionStatus.postValue(ActionStatus.ERROR("No server available"))
                }
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error connecting to best server"))
            }
        }
    }
    
    /**
     * Update app settings
     */
    fun updateAppSettings(settings: AppSettings) {
        viewModelScope.launch {
            try {
                database.appSettingsDao().update(settings)
                appSettings.value = settings
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error updating settings"))
            }
        }
    }
    
    /**
     * Check for app updates
     */
    fun checkForAppUpdates() {
        viewModelScope.launch {
            _actionStatus.postValue(ActionStatus.LOADING("Checking for updates..."))
            
            try {
                val updateInfo = autoUpdateManager.checkForAppUpdates()
                _updateInfo.value = updateInfo
                _updateAvailable.postValue(updateInfo.hasUpdate)
                
                if (updateInfo.hasUpdate) {
                    _actionStatus.postValue(ActionStatus.SUCCESS_WITH_DATA("Update available", updateInfo))
                } else {
                    _actionStatus.postValue(ActionStatus.SUCCESS)
                }
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error checking for updates"))
            }
        }
    }
    
    /**
     * Download and install app update
     */
    fun downloadAndInstallUpdate() {
        viewModelScope.launch {
            _actionStatus.postValue(ActionStatus.LOADING("Downloading update..."))
            
            try {
                val updateInfo = _updateInfo.value
                
                if (updateInfo != null && updateInfo.hasUpdate && updateInfo.downloadUrl != null) {
                    val success = autoUpdateManager.downloadAndInstallUpdate(updateInfo.downloadUrl)
                    
                    if (success) {
                        _actionStatus.postValue(ActionStatus.SUCCESS)
                    } else {
                        _actionStatus.postValue(ActionStatus.ERROR("Failed to download update"))
                    }
                } else {
                    _actionStatus.postValue(ActionStatus.ERROR("No update available"))
                }
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error downloading update"))
            }
        }
    }
    
    /**
     * Update Xray core
     */
    fun updateXrayCore() {
        viewModelScope.launch {
            _actionStatus.postValue(ActionStatus.LOADING("Updating Xray core..."))
            
            try {
                val success = autoUpdateManager.checkForXrayCoreUpdates(xrayManager)
                
                if (success) {
                    _actionStatus.postValue(ActionStatus.SUCCESS)
                } else {
                    _actionStatus.postValue(ActionStatus.ERROR("Failed to update Xray core"))
                }
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error updating Xray core"))
            }
        }
    }
    
    /**
     * Update routing files
     */
    fun updateRoutingFiles() {
        viewModelScope.launch {
            _actionStatus.postValue(ActionStatus.LOADING("Updating routing files..."))
            
            try {
                val success = routingManager.updateRoutingFiles(true)
                
                if (success) {
                    _actionStatus.postValue(ActionStatus.SUCCESS)
                } else {
                    _actionStatus.postValue(ActionStatus.ERROR("Failed to update routing files"))
                }
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error updating routing files"))
            }
        }
    }
    
    /**
     * Set automatic update preferences
     */
    fun setAutoUpdatePreferences(enabled: Boolean, frequencyHours: Int) {
        viewModelScope.launch {
            try {
                autoUpdateManager.setAutoUpdateEnabled(enabled)
                autoUpdateManager.scheduleUpdateChecks(frequencyHours)
                
                // Update settings in database
                val settings = database.appSettingsDao().getSettings() ?: AppSettings(id = 1)
                settings.autoUpdateEnabled = enabled
                settings.updateFrequencyHours = frequencyHours
                database.appSettingsDao().update(settings)
                
                appSettings.value = settings
                _actionStatus.postValue(ActionStatus.SUCCESS)
            } catch (e: Exception) {
                _actionStatus.postValue(ActionStatus.ERROR(e.message ?: "Error setting auto-update preferences"))
            }
        }
    }
    
    /**
     * Action status sealed class for UI state handling
     */
    sealed class ActionStatus {
        object SUCCESS : ActionStatus()
        data class SUCCESS_WITH_DATA(val message: String, val data: Any) : ActionStatus()
        data class LOADING(val message: String) : ActionStatus()
        data class ERROR(val message: String) : ActionStatus()
    }
}
