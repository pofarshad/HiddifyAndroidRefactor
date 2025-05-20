package com.hiddify.hiddifyng.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.database.entity.ServerGroup
import com.hiddify.hiddifyng.protocols.ProtocolHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * Manager for handling subscription links and their updates
 */
class SubscriptionManager(private val context: Context) {
    private val TAG = "SubscriptionManager"
    
    /**
     * Update all subscription groups
     * @return Map of group ID to update result (true if successful, false if failed)
     */
    suspend fun updateAllSubscriptions(): Map<Long, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<Long, Boolean>()
        val database = AppDatabase.getInstance(context)
        val serverGroupDao = database.serverGroupDao()
        
        // Get all subscription groups
        val groups = serverGroupDao.getAllGroupsWithSubscriptionUrl()
        
        Log.i(TAG, "Updating ${groups.size} subscription groups")
        
        for (group in groups) {
            if (group.subscriptionUrl.isNullOrEmpty()) {
                continue
            }
            
            Log.d(TAG, "Updating subscription for group: ${group.name}")
            val success = updateSubscription(group)
            results[group.id] = success
        }
        
        Log.i(TAG, "Subscription update completed")
        return@withContext results
    }
    
    /**
     * Update a single subscription group
     * @param group ServerGroup to update
     * @return true if successful, false if failed
     */
    suspend fun updateSubscription(group: ServerGroup): Boolean = withContext(Dispatchers.IO) {
        try {
            if (group.subscriptionUrl.isNullOrEmpty()) {
                Log.e(TAG, "Subscription URL is empty for group: ${group.name}")
                return@withContext false
            }
            
            // Fetch subscription content
            val content = fetchSubscriptionContent(group.subscriptionUrl)
            
            if (content.isNullOrEmpty()) {
                Log.e(TAG, "Failed to fetch subscription content for group: ${group.name}")
                return@withContext false
            }
            
            // Parse servers from content
            val servers = parseSubscriptionContent(content, group.id)
            
            if (servers.isEmpty()) {
                Log.e(TAG, "No valid servers found in subscription for group: ${group.name}")
                return@withContext false
            }
            
            Log.i(TAG, "Found ${servers.size} servers in subscription for group: ${group.name}")
            
            // Update database
            val database = AppDatabase.getInstance(context)
            val serverDao = database.serverDao()
            
            // Begin transaction
            database.runInTransaction {
                // Delete old servers from this subscription
                serverDao.deleteServersFromSubscription(group.id)
                
                // Insert new servers
                for (server in servers) {
                    serverDao.insert(server)
                }
                
                // Update group last updated time
                val serverGroupDao = database.serverGroupDao()
                serverGroupDao.updateLastUpdated(group.id, System.currentTimeMillis())
            }
            
            Log.i(TAG, "Subscription updated successfully for group: ${group.name}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating subscription for group: ${group.name}", e)
            return@withContext false
        }
    }
    
    /**
     * Fetch subscription content from URL
     * @param url Subscription URL
     * @return Subscription content or null if failed
     */
    private suspend fun fetchSubscriptionContent(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.readTimeout = 30000
            connection.connectTimeout = 30000
            connection.setRequestProperty("User-Agent", "MarFaNet-Co-Client")
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()
                return@withContext content
            } else {
                Log.e(TAG, "HTTP error when fetching subscription: $responseCode")
                connection.disconnect()
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching subscription", e)
            return@withContext null
        }
    }
    
    /**
     * Parse subscription content into server list
     * @param content Subscription content
     * @param groupId ID of the server group
     * @return List of servers
     */
    private fun parseSubscriptionContent(content: String, groupId: Long): List<Server> {
        val servers = mutableListOf<Server>()
        
        try {
            // Check if base64 encoded
            val decodedContent = try {
                String(Base64.decode(content, Base64.DEFAULT))
            } catch (e: IllegalArgumentException) {
                // Not base64 encoded
                content
            }
            
            // Split by lines
            val lines = decodedContent.trim().split("\\s+".toRegex())
            
            // Try to parse each line as a server URL
            for (line in lines) {
                var serverUrl = line.trim()
                if (serverUrl.isEmpty()) continue
                
                // Try to parse server URL with available protocol handlers
                ProtocolHandler.getAllProtocolHandlers().forEach { handler ->
                    try {
                        val server = handler.parseUrl(serverUrl)
                        if (server != null) {
                            server.groupId = groupId
                            server.fromSubscription = true
                            servers.add(server)
                            return@forEach
                        }
                    } catch (e: Exception) {
                        // Ignore parsing errors for this protocol
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing subscription content", e)
        }
        
        return servers
    }
    
    /**
     * Schedule background updates for subscriptions
     * This should be called during app startup
     */
    fun scheduleSubscriptionUpdates() {
        // This is implemented in the main app class using WorkManager
        // See HiddifyNGApplication.kt for implementation
    }
}