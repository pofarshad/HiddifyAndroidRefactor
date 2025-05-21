package com.hiddify.hiddifyng.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Adaptive connection manager that monitors network quality and adjusts app behavior
 * This improves performance by optimizing network requests based on current conditions
 */
class AdaptiveConnectionManager(private val context: Context) {
    companion object {
        private const val TAG = "AdaptiveConnectionManager"
        
        // Connection quality levels
        const val QUALITY_POOR = 0
        const val QUALITY_MODERATE = 1
        const val QUALITY_GOOD = 2
        const val QUALITY_EXCELLENT = 3
        
        // Network types
        const val TYPE_NONE = 0
        const val TYPE_WIFI = 1
        const val TYPE_MOBILE = 2
        const val TYPE_ETHERNET = 3
        const val TYPE_OTHER = 4
    }
    
    // The connectivity manager service
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    // Observable connection state
    private val _connectionQuality = MutableStateFlow(QUALITY_MODERATE)
    val connectionQuality: StateFlow<Int> = _connectionQuality.asStateFlow()
    
    private val _connectionType = MutableStateFlow(TYPE_NONE)
    val connectionType: StateFlow<Int> = _connectionType.asStateFlow()
    
    private val _isMetered = MutableStateFlow(false)
    val isMetered: StateFlow<Boolean> = _isMetered.asStateFlow()
    
    private val _isVpnActive = MutableStateFlow(false)
    val isVpnActive: StateFlow<Boolean> = _isVpnActive.asStateFlow()
    
    // Network callback for monitoring changes
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateNetworkStatus(network)
        }
        
        override fun onLost(network: Network) {
            updateNetworkInfo()
        }
        
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            updateNetworkCapabilities(network, networkCapabilities)
        }
    }
    
    /**
     * Start monitoring network status
     */
    fun startMonitoring() {
        Log.d(TAG, "Starting network monitoring")
        
        try {
            // Initial update
            updateNetworkInfo()
            
            // Register for all network changes
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting network monitoring", e)
        }
    }
    
    /**
     * Stop monitoring network status
     */
    fun stopMonitoring() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping network monitoring", e)
        }
    }
    
    /**
     * Update current network information
     */
    private fun updateNetworkInfo() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            connectivityManager.activeNetwork?.let { network ->
                updateNetworkStatus(network)
            } ?: run {
                // No active network
                _connectionType.value = TYPE_NONE
                _connectionQuality.value = QUALITY_POOR
                _isMetered.value = true
                _isVpnActive.value = false
            }
        } else {
            // Legacy approach for older Android versions
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            if (activeNetworkInfo != null && activeNetworkInfo.isConnected) {
                when (activeNetworkInfo.type) {
                    ConnectivityManager.TYPE_WIFI -> {
                        _connectionType.value = TYPE_WIFI
                        _connectionQuality.value = QUALITY_GOOD
                        _isMetered.value = false
                    }
                    ConnectivityManager.TYPE_MOBILE -> {
                        _connectionType.value = TYPE_MOBILE
                        _connectionQuality.value = QUALITY_MODERATE
                        _isMetered.value = true
                    }
                    ConnectivityManager.TYPE_ETHERNET -> {
                        _connectionType.value = TYPE_ETHERNET
                        _connectionQuality.value = QUALITY_EXCELLENT
                        _isMetered.value = false
                    }
                    else -> {
                        _connectionType.value = TYPE_OTHER
                        _connectionQuality.value = QUALITY_MODERATE
                        _isMetered.value = true
                    }
                }
            } else {
                _connectionType.value = TYPE_NONE
                _connectionQuality.value = QUALITY_POOR
                _isMetered.value = true
            }
            
            // Check VPN status (limited on older Android versions)
            _isVpnActive.value = false
        }
    }
    
    /**
     * Update network status based on current network
     */
    private fun updateNetworkStatus(network: Network) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null) {
                updateNetworkCapabilities(network, capabilities)
            }
        } else {
            updateNetworkInfo()
        }
    }
    
    /**
     * Update network capabilities when they change
     */
    private fun updateNetworkCapabilities(network: Network, capabilities: NetworkCapabilities) {
        // Determine connection type
        when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                _connectionType.value = TYPE_WIFI
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                _connectionType.value = TYPE_MOBILE
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                _connectionType.value = TYPE_ETHERNET
            }
            else -> {
                _connectionType.value = TYPE_OTHER
            }
        }
        
        // Determine if VPN is active
        _isVpnActive.value = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        
        // Check if connection is metered
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            _isMetered.value = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        
        // Estimate connection quality based on capabilities
        _connectionQuality.value = when {
            // Check for high bandwidth as an indicator of good connection
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) &&
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && 
             capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)) -> {
                QUALITY_EXCELLENT
            }
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED) -> {
                QUALITY_GOOD
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                QUALITY_GOOD
            }
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                QUALITY_MODERATE
            }
            else -> {
                QUALITY_POOR
            }
        }
        
        Log.d(TAG, "Network updated: type=${_connectionType.value}, " +
                  "quality=${_connectionQuality.value}, " +
                  "metered=${_isMetered.value}, " +
                  "vpn=${_isVpnActive.value}")
    }
    
    /**
     * Get maximum recommended packet size based on connection quality
     * Helps optimize data transfers on poor connections
     */
    fun getRecommendedPacketSize(): Int {
        return when (_connectionQuality.value) {
            QUALITY_EXCELLENT -> 32768 // 32KB
            QUALITY_GOOD -> 16384      // 16KB
            QUALITY_MODERATE -> 8192   // 8KB
            else -> 4096               // 4KB
        }
    }
    
    /**
     * Get recommended image quality based on connection
     * Helps save data on metered connections
     */
    fun getRecommendedImageQuality(): Int {
        return when {
            _isMetered.value && _connectionQuality.value <= QUALITY_MODERATE -> 70
            _isMetered.value -> 85
            else -> 95
        }
    }
    
    /**
     * Get recommended timeout in milliseconds based on connection quality
     */
    fun getRecommendedTimeout(): Int {
        return when (_connectionQuality.value) {
            QUALITY_EXCELLENT -> 10000  // 10 seconds
            QUALITY_GOOD -> 15000       // 15 seconds
            QUALITY_MODERATE -> 20000   // 20 seconds
            else -> 30000               // 30 seconds
        }
    }
}