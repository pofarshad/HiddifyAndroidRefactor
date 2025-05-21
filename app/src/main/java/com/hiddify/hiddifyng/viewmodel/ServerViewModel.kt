package com.hiddify.hiddifyng.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.utils.PingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for server management and interaction
 */
class ServerViewModel(application: Application) : AndroidViewModel(application) {
    
    private val database = AppDatabase.getInstance(application)
    private val serverDao = database.serverDao()
    
    // Current selected server
    private val _selectedServer = MutableLiveData<Server?>()
    val selectedServer: LiveData<Server?> = _selectedServer
    
    // List of all servers
    val allServers = serverDao.getAllServersLive()
    
    // Server with lowest ping
    private val _bestServer = MutableLiveData<Server?>()
    val bestServer: LiveData<Server?> = _bestServer
    
    init {
        refreshData()
    }
    
    /**
     * Refresh data from database
     */
    fun refreshData() {
        viewModelScope.launch {
            // Update best server
            updateBestServer()
        }
    }
    
    /**
     * Get server by ID
     */
    suspend fun getServerById(id: Long): Server? = withContext(Dispatchers.IO) {
        return@withContext serverDao.getServerByIdSync(id)
    }
    
    /**
     * Update the current best server (lowest ping)
     */
    private suspend fun updateBestServer() {
        withContext(Dispatchers.IO) {
            val best = serverDao.getServerWithLowestPing()
            _bestServer.postValue(best)
        }
    }
    
    /**
     * Ping all servers and update database
     */
    suspend fun pingAllServersAsync(): List<Pair<Server, Int>> = withContext(Dispatchers.IO) {
        val servers = serverDao.getAllServers().value ?: emptyList()
        val results = mutableListOf<Pair<Server, Int>>()
        
        for (server in servers) {
            try {
                val pingTime = PingUtils.pingServer(server)
                if (pingTime > 0) {
                    serverDao.updatePing(server.id, pingTime)
                    results.add(Pair(server, pingTime))
                } else {
                    serverDao.updatePing(server.id, Int.MAX_VALUE)
                    results.add(Pair(server, -1))
                }
            } catch (e: Exception) {
                results.add(Pair(server, -1))
            }
        }
        
        // Update best server after pinging
        updateBestServer()
        
        return@withContext results
    }
    
    /**
     * Connect to a server
     */
    fun connectToServer(server: Server) {
        viewModelScope.launch {
            _selectedServer.value = server
            // Actual connection logic would be implemented in a service
        }
    }
    
    /**
     * Import a subscription URL
     */
    suspend fun importSubscription(url: String, name: String): Boolean = withContext(Dispatchers.IO) {
        // This would be implemented with SubscriptionManager
        return@withContext false
    }
}