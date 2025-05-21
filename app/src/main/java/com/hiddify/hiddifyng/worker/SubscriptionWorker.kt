package com.hiddify.hiddifyng.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.Base64

/**
 * Worker for updating server subscriptions
 * Runs every 30 minutes to keep server configurations up-to-date
 */
class SubscriptionWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "SubscriptionWorker"
    }
    
    // Store parameters for child worker creation
    private val workerParams = params
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting subscription update operation")
            
            // Get all subscriptions from database
            val subscriptions = getAllSubscriptions()
            if (subscriptions.isEmpty()) {
                Log.i(TAG, "No subscriptions to update")
                return@withContext Result.success()
            }
            
            // Update each subscription
            var totalServersAdded = 0
            var totalServersUpdated = 0
            var totalServersRemoved = 0
            
            for (subscription in subscriptions) {
                Log.d(TAG, "Updating subscription: ${subscription.name}")
                
                // Download subscription content
                val content = downloadSubscriptionContent(subscription.url)
                if (content.isNullOrEmpty()) {
                    Log.e(TAG, "Failed to download subscription: ${subscription.name}")
                    continue
                }
                
                // Parse subscription content
                val updatedServers = parseSubscriptionContent(content)
                if (updatedServers.isEmpty()) {
                    Log.e(TAG, "No servers found in subscription: ${subscription.name}")
                    continue
                }
                
                // Get existing servers for this subscription
                val existingServers = getServersBySubscriptionId(subscription.id)
                
                // Process server changes
                val (added, updated, removed) = processServerChanges(subscription.id, existingServers, updatedServers)
                
                totalServersAdded += added
                totalServersUpdated += updated
                totalServersRemoved += removed
                
                Log.i(TAG, "Subscription ${subscription.name} updated: $added added, $updated updated, $removed removed")
                
                // Update subscription last update time
                updateSubscriptionLastUpdate(subscription.id)
            }
            
            Log.i(TAG, "All subscriptions updated: $totalServersAdded added, $totalServersUpdated updated, $totalServersRemoved removed")
            
            // If we added or updated servers, schedule a ping worker to update server statistics
            if (totalServersAdded > 0 || totalServersUpdated > 0) {
                schedulePingWorker()
            }
            
            return@withContext Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in subscription worker", e)
            return@withContext Result.failure()
        }
    }
    
    /**
     * Get all subscriptions from database
     */
    private suspend fun getAllSubscriptions(): List<Subscription> = withContext(Dispatchers.IO) {
        // This would be implemented to get subscriptions from database
        // For now, we'll just return an empty list
        return@withContext emptyList<Subscription>()
    }
    
    /**
     * Download subscription content from URL
     * @param url Subscription URL
     * @return Subscription content, or null if failed
     */
    private suspend fun downloadSubscriptionContent(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val inputStream = connection.getInputStream()
            val content = inputStream.bufferedReader().use { it.readText() }
            
            return@withContext content
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading subscription content", e)
            return@withContext null
        }
    }
    
    /**
     * Parse subscription content
     * @param content Subscription content
     * @return List of servers
     */
    private suspend fun parseSubscriptionContent(content: String): List<Server> = withContext(Dispatchers.IO) {
        try {
            val servers = mutableListOf<Server>()
            
            // Check if content is base64 encoded
            val decodedContent = if (isBase64Encoded(content)) {
                try {
                    String(Base64.getDecoder().decode(content))
                } catch (e: Exception) {
                    content
                }
            } else {
                content
            }
            
            // Split content into lines
            val lines = decodedContent.split("\n")
            
            for (line in lines) {
                val trimmedLine = line.trim()
                if (trimmedLine.isEmpty()) continue
                
                // Parse server URI
                val server = parseServerUri(trimmedLine)
                if (server != null) {
                    servers.add(server)
                }
            }
            
            return@withContext servers
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing subscription content", e)
            return@withContext emptyList()
        }
    }
    
    /**
     * Check if content is base64 encoded
     * @param content Content to check
     * @return true if base64 encoded, false otherwise
     */
    private fun isBase64Encoded(content: String): Boolean {
        val base64Pattern = "^[A-Za-z0-9+/]*={0,2}$"
        return content.matches(base64Pattern.toRegex())
    }
    
    /**
     * Parse server URI
     * @param uri Server URI
     * @return Server object, or null if parsing failed
     */
    private fun parseServerUri(uri: String): Server? {
        try {
            // Get protocol from URI
            val protocolEnd = uri.indexOf("://")
            if (protocolEnd <= 0) return null
            
            val protocol = uri.substring(0, protocolEnd).toLowerCase()
            
            // Create appropriate protocol handler
            val protocolHandler = when (protocol) {
                "vmess" -> VmessProtocolHandler()
                "vless" -> VlessProtocolHandler()
                "trojan" -> TrojanProtocolHandler()
                "ss", "shadowsocks" -> ShadowsocksProtocolHandler()
                "hysteria" -> HysteriaProtocolHandler()
                "reality" -> RealityProtocolHandler()
                "xhttp" -> XhttpProtocolHandler()
                else -> null
            }
            
            // Parse server URI using protocol handler
            return protocolHandler?.parseUri(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server URI", e)
            return null
        }
    }
    
    /**
     * Get servers by subscription ID
     * @param subscriptionId Subscription ID
     * @return List of servers
     */
    private suspend fun getServersBySubscriptionId(subscriptionId: Long): List<Server> = withContext(Dispatchers.IO) {
        // This would be implemented to get servers from database
        // For now, we'll just return an empty list
        return@withContext emptyList<Server>()
    }
    
    /**
     * Process server changes
     * @param subscriptionId Subscription ID
     * @param existingServers Existing servers
     * @param updatedServers Updated servers
     * @return Triple of (added, updated, removed) server counts
     */
    private suspend fun processServerChanges(
        subscriptionId: Long, 
        existingServers: List<Server>, 
        updatedServers: List<Server>
    ): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        var added = 0
        var updated = 0
        var removed = 0
        
        // Add new servers and update existing ones
        for (updatedServer in updatedServers) {
            // Set subscription ID
            updatedServer.serverSubscriptionId = subscriptionId
            
            // Check if server already exists
            val existingServer = existingServers.find { it.address == updatedServer.address && it.port == updatedServer.port }
            
            if (existingServer != null) {
                // Update existing server
                updatedServer.id = existingServer.id
                updatedServer.lastPing = existingServer.lastPing
                updatedServer.avgPing = existingServer.avgPing
                updatedServer.favorite = existingServer.favorite
                updatedServer.isSelected = existingServer.isSelected
                updatedServer.order = existingServer.order
                
                updateServer(updatedServer)
                updated++
            } else {
                // Add new server
                addServer(updatedServer)
                added++
            }
        }
        
        // Remove servers that are no longer in the subscription
        val updatedAddresses = updatedServers.map { "${it.address}:${it.port}" }.toSet()
        val serversToRemove = existingServers.filter { 
            "${it.address}:${it.port}" !in updatedAddresses 
        }
        
        for (server in serversToRemove) {
            removeServer(server.id)
            removed++
        }
        
        return@withContext Triple(added, updated, removed)
    }
    
    /**
     * Add server to database
     * @param server Server to add
     */
    private suspend fun addServer(server: Server) = withContext(Dispatchers.IO) {
        // This would be implemented to add server to database
        Log.d(TAG, "Added server: ${server.name}")
    }
    
    /**
     * Update server in database
     * @param server Server to update
     */
    private suspend fun updateServer(server: Server) = withContext(Dispatchers.IO) {
        // This would be implemented to update server in database
        Log.d(TAG, "Updated server: ${server.name}")
    }
    
    /**
     * Remove server from database
     * @param serverId Server ID to remove
     */
    private suspend fun removeServer(serverId: Long) = withContext(Dispatchers.IO) {
        // This would be implemented to remove server from database
        Log.d(TAG, "Removed server with ID: $serverId")
    }
    
    /**
     * Update subscription last update time
     * @param subscriptionId Subscription ID
     */
    private suspend fun updateSubscriptionLastUpdate(subscriptionId: Long) = withContext(Dispatchers.IO) {
        // This would be implemented to update subscription last update time
        Log.d(TAG, "Updated subscription last update time for ID: $subscriptionId")
    }
    
    /**
     * Schedule ping worker
     */
    private fun schedulePingWorker() {
        // This would be implemented to schedule ping worker
        Log.d(TAG, "Scheduled ping worker to update server statistics")
    }
    
    /**
     * Data class for subscription
     */
    data class Subscription(
        val id: Long,
        val name: String,
        val url: String,
        val lastUpdate: Long
    )
    
    /**
     * Data class for server
     */
    data class Server(
        var id: Long = 0,
        var name: String = "",
        var protocol: String = "",
        var address: String = "",
        var port: Int = 0,
        var serverSubscriptionId: Long? = null,
        var lastPing: Long? = null,
        var avgPing: Int? = null,
        var favorite: Boolean = false,
        var isSelected: Boolean = false,
        var order: Int = 0
    )
    
    /**
     * Interface for protocol handlers
     */
    interface ProtocolHandler {
        fun parseUri(uri: String): Server?
    }
    
    /**
     * VMess protocol handler
     */
    class VmessProtocolHandler : ProtocolHandler {
        override fun parseUri(uri: String): Server? {
            // This would be implemented to parse VMess URI
            return null
        }
    }
    
    /**
     * VLESS protocol handler
     */
    class VlessProtocolHandler : ProtocolHandler {
        override fun parseUri(uri: String): Server? {
            // This would be implemented to parse VLESS URI
            return null
        }
    }
    
    /**
     * Trojan protocol handler
     */
    class TrojanProtocolHandler : ProtocolHandler {
        override fun parseUri(uri: String): Server? {
            // This would be implemented to parse Trojan URI
            return null
        }
    }
    
    /**
     * Shadowsocks protocol handler
     */
    class ShadowsocksProtocolHandler : ProtocolHandler {
        override fun parseUri(uri: String): Server? {
            // This would be implemented to parse Shadowsocks URI
            return null
        }
    }
    
    /**
     * Hysteria protocol handler
     */
    class HysteriaProtocolHandler : ProtocolHandler {
        override fun parseUri(uri: String): Server? {
            // This would be implemented to parse Hysteria URI
            return null
        }
    }
    
    /**
     * REALITY protocol handler
     */
    class RealityProtocolHandler : ProtocolHandler {
        override fun parseUri(uri: String): Server? {
            // This would be implemented to parse REALITY URI
            return null
        }
    }
    
    /**
     * XHTTP protocol handler
     */
    class XhttpProtocolHandler : ProtocolHandler {
        override fun parseUri(uri: String): Server? {
            // This would be implemented to parse XHTTP URI
            return null
        }
    }
}