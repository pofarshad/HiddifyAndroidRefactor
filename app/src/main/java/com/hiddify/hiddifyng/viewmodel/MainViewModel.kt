package com.hiddify.hiddifyng.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.hiddify.hiddifyng.utils.TrafficStats
import com.hiddify.hiddifyng.utils.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Timer
import java.util.TimerTask

/**
 * ViewModel for the main activity
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    // Traffic statistics
    private val _trafficStats = MutableLiveData<TrafficStats>()
    val trafficStats: LiveData<TrafficStats> = _trafficStats
    
    // Connection state
    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: LiveData<ConnectionState> = _connectionState
    
    // Update manager
    private val updateManager = UpdateManager(application)
    
    // Timer for refreshing traffic stats
    private var trafficStatsTimer: Timer? = null
    
    init {
        // Initialize traffic stats
        _trafficStats.value = TrafficStats(application)
        
        // Set initial connection state
        _connectionState.value = ConnectionState.DISCONNECTED
    }
    
    /**
     * Start monitoring traffic statistics
     */
    fun startTrafficStatsMonitoring() {
        stopTrafficStatsMonitoring()
        
        trafficStatsTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    refreshTrafficStats()
                }
            }, 0, 1000) // Update every second
        }
    }
    
    /**
     * Stop monitoring traffic statistics
     */
    fun stopTrafficStatsMonitoring() {
        trafficStatsTimer?.cancel()
        trafficStatsTimer = null
    }
    
    /**
     * Refresh traffic statistics
     */
    fun refreshTrafficStats() {
        viewModelScope.launch(Dispatchers.IO) {
            // In a real implementation, this would get stats from Xray
            // For now, we'll just use random values for demonstration
            _trafficStats.value?.let { stats ->
                val upload = (Math.random() * 1024 * 1024).toLong()
                val download = (Math.random() * 1024 * 1024).toLong()
                stats.updateStats(upload, download)
                
                withContext(Dispatchers.Main) {
                    _trafficStats.value = stats
                }
            }
        }
    }
    
    /**
     * Check for app updates
     */
    suspend fun checkForAppUpdates(): Boolean = withContext(Dispatchers.IO) {
        return@withContext updateManager.checkForAppUpdates()
    }
    
    /**
     * Check for routing updates
     */
    suspend fun checkForRoutingUpdates(): Boolean = withContext(Dispatchers.IO) {
        return@withContext updateManager.checkForRoutingUpdates()
    }
    
    /**
     * Connection states
     */
    enum class ConnectionState {
        CONNECTING,
        CONNECTED,
        DISCONNECTED,
        ERROR
    }
}