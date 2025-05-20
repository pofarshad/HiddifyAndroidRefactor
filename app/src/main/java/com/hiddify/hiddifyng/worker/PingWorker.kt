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
 * Background worker for pinging servers and auto-switching to best server
 */
class PingWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {
    private val TAG = "PingWorker"
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting ping test for servers")
                
                // Get app settings
                val database = AppDatabase.getInstance(context)
                val settings = database.appSettingsDao().getSettings()
                
                if (settings == null) {
                    Log.e(TAG, "App settings not found")
                    return@withContext Result.failure()
                }
                
                // Check if auto-switch is enabled
                if (!settings.autoSwitchToBestServer) {
                    Log.i(TAG, "Auto-switch to best server is disabled, skipping ping test")
                    return@withContext Result.success()
                }
                
                // Get all servers
                val serverDao = database.serverDao()
                val servers = serverDao.getAllServers().value ?: emptyList()
                
                if (servers.isEmpty()) {
                    Log.i(TAG, "No servers found, skipping ping test")
                    return@withContext Result.success()
                }
                
                Log.i(TAG, "Pinging ${servers.size} servers")
                
                // Ping each server and update database
                for (server in servers) {
                    try {
                        val pingTime = PingUtils.pingServer(server)
                        if (pingTime > 0) {
                            Log.d(TAG, "Server ${server.name} ping: $pingTime ms")
                            serverDao.updatePing(server.id, pingTime)
                        } else {
                            Log.d(TAG, "Server ${server.name} ping failed")
                            serverDao.updatePing(server.id, Int.MAX_VALUE)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error pinging server ${server.name}", e)
                    }
                }
                
                // Get current server ID from Xray Manager
                // For now, we'll use the preferred server ID from settings
                val currentServerId = settings.preferredServerId
                
                // Get server with lowest ping
                val bestServer = serverDao.getServerWithLowestPing()
                
                if (bestServer == null) {
                    Log.i(TAG, "No server with valid ping found")
                    return@withContext Result.success()
                }
                
                Log.i(TAG, "Best server: ${bestServer.name} (${bestServer.ping} ms)")
                
                // Check if we should switch to the best server
                if (currentServerId != null && currentServerId != bestServer.id) {
                    // Get current server
                    val currentServer = serverDao.getServerByIdSync(currentServerId)
                    
                    if (currentServer != null) {
                        val pingDifference = currentServer.ping - bestServer.ping
                        
                        // Switch only if ping difference is above threshold
                        if (pingDifference > settings.minPingThreshold) {
                            Log.i(TAG, "Switching to better server: ${bestServer.name} (${bestServer.ping} ms vs ${currentServer.ping} ms)")
                            
                            // Switch to best server
                            V2RayServiceManager.restartService(context, bestServer.id)
                        } else {
                            Log.i(TAG, "Not switching servers, ping difference below threshold: $pingDifference < ${settings.minPingThreshold}")
                        }
                    } else {
                        // Current server not found, switch to best server
                        Log.i(TAG, "Current server not found, switching to best server: ${bestServer.name}")
                        V2RayServiceManager.restartService(context, bestServer.id)
                    }
                } else if (currentServerId == null) {
                    // No current server, switch to best server
                    Log.i(TAG, "No current server, switching to best server: ${bestServer.name}")
                    V2RayServiceManager.startService(context, bestServer.id)
                } else {
                    // Already using best server
                    Log.i(TAG, "Already using best server: ${bestServer.name}")
                }
                
                return@withContext Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Error during ping worker execution", e)
                return@withContext Result.failure()
            }
        }
    }
}