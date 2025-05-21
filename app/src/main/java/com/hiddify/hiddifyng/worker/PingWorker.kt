package com.hiddify.hiddifyng.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiddify.hiddifyng.database.entity.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlin.math.min

/**
 * Worker for pinging servers and updating ping statistics
 * Runs every 10 minutes to find the best server for auto-connection
 */
class PingWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "PingWorker"
        private const val PING_COUNT = 3
        private const val SOCKET_TIMEOUT = 5000 // 5 seconds
    }
    
    // Store parameters for child worker creation
    private val workerParams = params
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting server ping operation")
            
            // Get all servers from database
            val servers = getAllServers()
            if (servers.isEmpty()) {
                Log.i(TAG, "No servers to ping")
                return@withContext Result.success()
            }
            
            // Ping each server
            var bestServer: Server? = null
            var bestPing = Int.MAX_VALUE
            
            for (server in servers) {
                val pingResult = pingServer(server)
                
                // Update server with ping result
                if (pingResult > 0) {
                    updateServerPing(server.id, pingResult)
                    
                    // Check if this is the best server
                    if (pingResult < bestPing) {
                        bestPing = pingResult
                        bestServer = server
                    }
                }
            }
            
            // If auto-connect is enabled and we found a best server, connect to it
            if (bestServer != null && isAutoConnectEnabled()) {
                Log.i(TAG, "Best server: ${bestServer.name} with ping $bestPing ms")
                connectToBestServer(bestServer.id)
            }
            
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in ping worker", e)
            return@withContext Result.failure()
        }
    }
    
    /**
     * Ping a server using multiple methods and return the best result
     * @param server Server to ping
     * @return Ping time in milliseconds, or -1 if failed
     */
    private suspend fun pingServer(server: Server): Int = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Pinging server: ${server.name}")
            
            // Try multiple ping methods and use the best result
            val tcpPing = tcpPing(server)
            val httpPing = httpPing(server)
            
            // Use the best ping result that was successful
            val pingResults = listOf(tcpPing, httpPing).filter { it > 0 }
            
            return@withContext if (pingResults.isNotEmpty()) {
                // Use the minimum ping value
                pingResults.minOrNull() ?: -1
            } else {
                -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error pinging server: ${server.name}", e)
            return@withContext -1
        }
    }
    
    /**
     * Ping a server using TCP socket
     * @param server Server to ping
     * @return Average ping time in milliseconds, or -1 if failed
     */
    private suspend fun tcpPing(server: Server): Int = withContext(Dispatchers.IO) {
        try {
            var totalTime = 0L
            var successCount = 0
            
            // Extract host and port from server address
            val host = server.address.split(":")[0]
            val port = server.port ?: 443 // Default to 443 if port not specified
            
            // Ping multiple times and calculate average
            for (i in 0 until PING_COUNT) {
                val socket = Socket()
                val startTime = System.currentTimeMillis()
                
                try {
                    socket.connect(InetSocketAddress(host, port), SOCKET_TIMEOUT)
                    val endTime = System.currentTimeMillis()
                    socket.close()
                    
                    val pingTime = endTime - startTime
                    totalTime += pingTime
                    successCount++
                    
                    Log.d(TAG, "TCP ping ${i + 1} to ${server.name}: $pingTime ms")
                } catch (e: IOException) {
                    Log.e(TAG, "TCP ping ${i + 1} to ${server.name} failed", e)
                }
            }
            
            return@withContext if (successCount > 0) {
                (totalTime / successCount).toInt()
            } else {
                -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in TCP ping to ${server.name}", e)
            return@withContext -1
        }
    }
    
    /**
     * Ping a server using HTTP request
     * @param server Server to ping
     * @return Average ping time in milliseconds, or -1 if failed
     */
    private suspend fun httpPing(server: Server): Int = withContext(Dispatchers.IO) {
        try {
            var totalTime = 0L
            var successCount = 0
            
            // Extract host from server address
            val host = server.address.split(":")[0]
            
            // Ping multiple times and calculate average
            for (i in 0 until PING_COUNT) {
                val startTime = System.currentTimeMillis()
                
                try {
                    val url = URL("https://$host")
                    val connection = url.openConnection()
                    connection.connectTimeout = SOCKET_TIMEOUT
                    connection.readTimeout = SOCKET_TIMEOUT
                    connection.getInputStream().close()
                    
                    val endTime = System.currentTimeMillis()
                    val pingTime = endTime - startTime
                    totalTime += pingTime
                    successCount++
                    
                    Log.d(TAG, "HTTP ping ${i + 1} to ${server.name}: $pingTime ms")
                } catch (e: Exception) {
                    Log.e(TAG, "HTTP ping ${i + 1} to ${server.name} failed", e)
                }
            }
            
            return@withContext if (successCount > 0) {
                (totalTime / successCount).toInt()
            } else {
                -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in HTTP ping to ${server.name}", e)
            return@withContext -1
        }
    }
    
    /**
     * Get all servers from database
     * @return List of servers
     */
    private suspend fun getAllServers(): List<Server> = withContext(Dispatchers.IO) {
        // This would be implemented to get servers from database
        // For now, we'll just return an empty list
        return@withContext emptyList<Server>()
    }
    
    /**
     * Update server ping result in database
     * @param serverId Server ID
     * @param pingResult Ping result in milliseconds
     */
    private suspend fun updateServerPing(serverId: Long, pingResult: Int) = withContext(Dispatchers.IO) {
        // This would be implemented to update server ping in database
        Log.d(TAG, "Updated server $serverId with ping $pingResult ms")
    }
    
    /**
     * Check if auto-connect is enabled in settings
     * @return true if auto-connect is enabled, false otherwise
     */
    private fun isAutoConnectEnabled(): Boolean {
        // This would be implemented to check auto-connect setting
        return false
    }
    
    /**
     * Connect to the best server
     * @param serverId Server ID to connect to
     */
    private suspend fun connectToBestServer(serverId: Long) = withContext(Dispatchers.IO) {
        // This would be implemented to connect to the server
        Log.i(TAG, "Connecting to best server with ID $serverId")
    }
}