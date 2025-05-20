package com.hiddify.hiddifyng.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.service.V2RayServiceManager
import com.hiddify.hiddifyng.utils.PingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker that performs periodic ping tests on servers
 * and optionally switches to the best server based on user settings
 */
class PingWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val TAG = "PingWorker"
    
    private val database = AppDatabase.getInstance(context)
    private val pingUtils = PingUtils(context)
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting ping testing")
        
        try {
            // Get app settings
            val settings = database.appSettingsDao().getSettings() ?: return@withContext Result.success()
            
            // Get all servers
            val servers = database.serverDao().getAllServers()
            if (servers.isEmpty()) {
                Log.i(TAG, "No servers to ping")
                return@withContext Result.success()
            }
            
            // Track current connection state
            val isRunning = isVpnRunning()
            var currentServerId: Long? = null
            
            // Find current server if VPN is running
            if (isRunning) {
                currentServerId = withContext(Dispatchers.Main) {
                    // This should be done with a better way to query service state
                    // For now, we'll assume if we can find a running service, we'll get the current server ID
                    // In a real implementation, you'd have a way to query the service for its state
                    -1L // Placeholder
                }
            }
            
            // Ping all servers and update database
            var bestServerId: Long? = null
            var bestPing = Int.MAX_VALUE
            
            for (server in servers) {
                val ping = pingUtils.measurePing(server.address)
                Log.d(TAG, "Server ${server.name} ping: $ping ms")
                
                // Update server ping in database
                server.ping = ping
                database.serverDao().updateServer(server)
                
                // Track best server
                if (ping > 0 && ping < bestPing) {
                    bestPing = ping
                    bestServerId = server.id
                }
            }
            
            // If auto-switch is enabled and we're connected, check if we should switch servers
            if (isRunning && settings.autoSwitchToBestServer && bestServerId != null && 
                currentServerId != null && currentServerId != bestServerId) {
                
                // Get current server ping
                val currentServer = database.serverDao().getServerById(currentServerId)
                val currentPing = currentServer?.ping ?: Int.MAX_VALUE
                
                // Check if the best server's ping is significantly better
                if (bestPing < currentPing - settings.minPingThreshold) {
                    Log.i(TAG, "Switching to better server, current: $currentPing ms, best: $bestPing ms")
                    
                    // Restart VPN with best server
                    V2RayServiceManager.stopService(applicationContext)
                    V2RayServiceManager.startService(applicationContext, bestServerId)
                }
            }
            
            return@withContext Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during ping testing", e)
            return@withContext Result.retry()
        }
    }
    
    /**
     * Check if VPN service is running
     * This is a placeholder - in a real app you'd query the service state
     */
    private fun isVpnRunning(): Boolean {
        // This would be implemented with a proper service query mechanism
        return false
    }
}
