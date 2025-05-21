package com.hiddify.hiddifyng.utils

import android.util.Log
import com.hiddify.hiddifyng.database.dao.ServerDao
import com.hiddify.hiddifyng.database.entity.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

object PingUtils {
    private const val TAG = "PingUtils"
    private const val PING_TIMEOUT = 2000 // 2 seconds
    
    /**
     * Ping all servers in the database and update their ping values
     * Returns the server with the lowest ping value
     */
    suspend fun pingAllServersAsync(serverDao: ServerDao): Server? = coroutineScope {
        try {
            // Get all servers
            val servers = serverDao.getAllServers()
            if (servers.isEmpty()) return@coroutineScope null
            
            // Ping all servers concurrently
            val pingTasks = servers.map { server ->
                async(Dispatchers.IO) {
                    val pingResult = pingServer(server)
                    // Update server ping in database
                    serverDao.updateServerPing(server.id, pingResult)
                    Pair(server, pingResult)
                }
            }
            
            // Wait for all pings to complete
            val results = pingTasks.awaitAll()
            
            // Find server with lowest ping
            val bestServer = results
                .filter { (_, ping) -> ping > 0 } // Filter out failed pings
                .minByOrNull { (_, ping) -> ping }
                ?.first
            
            // Log results
            Log.d(TAG, "Pinged ${servers.size} servers, best ping: ${bestServer?.ping ?: "none"}")
            
            bestServer
        } catch (e: Exception) {
            Log.e(TAG, "Error during ping operation", e)
            null
        }
    }
    
    /**
     * Get the best server (lowest ping) from database
     */
    suspend fun bestServer(serverDao: ServerDao): Server? = withContext(Dispatchers.IO) {
        serverDao.getBestServer()
    }
    
    /**
     * Ping a single server and return the ping time in milliseconds
     * Returns -1 if ping fails
     */
    suspend fun pingServer(server: Server): Int = withContext(Dispatchers.IO) {
        try {
            // Extract host from server address
            val uri = URI(server.address)
            val host = uri.host ?: return@withContext -1
            
            val port = when {
                uri.port > 0 -> uri.port
                uri.scheme == "https" -> 443
                uri.scheme == "http" -> 80
                else -> 80
            }
            
            // Use socket connection to measure ping
            val startTime = System.currentTimeMillis()
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), PING_TIMEOUT)
            }
            val pingTime = (System.currentTimeMillis() - startTime).toInt()
            
            // Log successful ping
            Log.d(TAG, "Ping to ${server.name}: $pingTime ms")
            pingTime
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Could not resolve host for ${server.name}", e)
            -1
        } catch (e: IOException) {
            Log.e(TAG, "Connection error for ${server.name}", e)
            -1
        } catch (e: Exception) {
            Log.e(TAG, "Ping failed for ${server.name}", e)
            -1
        }
    }
    
    /**
     * Get server by ID from database
     */
    suspend fun getServerById(serverDao: ServerDao, serverId: Long): Server? = withContext(Dispatchers.IO) {
        serverDao.getServerById(serverId)
    }
}