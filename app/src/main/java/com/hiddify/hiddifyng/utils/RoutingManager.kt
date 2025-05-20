package com.hiddify.hiddifyng.utils

import android.content.Context
import android.net.VpnService
import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit

class RoutingManager(private val context: Context) {
    private val TAG = "RoutingManager"
    
    companion object {
        // Default routing rules sources
        private const val GEOIP_URL = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"
        private const val GEOSITE_URL = "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
        
        // Local file names
        private const val GEOIP_FILE = "geoip.dat"
        private const val GEOSITE_FILE = "geosite.dat"
        private const val ROUTING_CONFIG_FILE = "routing.json"
        
        // Shared Preferences
        private const val PREF_NAME = "hiddify_routing_prefs"
        private const val KEY_LAST_ROUTING_UPDATE = "last_routing_update"
    }
    
    /**
     * Initialize routing files if needed
     */
    suspend fun initializeRoutingFiles(): Boolean = withContext(Dispatchers.IO) {
        val dataDir = File(context.getExternalFilesDir(null), "xray")
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        
        val geoipFile = File(dataDir, GEOIP_FILE)
        val geositeFile = File(dataDir, GEOSITE_FILE)
        
        // Check if files exist and download if needed
        var success = true
        
        if (!geoipFile.exists()) {
            success = success && downloadFile(GEOIP_URL, geoipFile)
        }
        
        if (!geositeFile.exists()) {
            success = success && downloadFile(GEOSITE_URL, geositeFile)
        }
        
        // Create default routing config if it doesn't exist
        val routingConfigFile = File(dataDir, ROUTING_CONFIG_FILE)
        if (!routingConfigFile.exists()) {
            success = success && createDefaultRoutingConfig(routingConfigFile)
        }
        
        return@withContext success
    }
    
    /**
     * Update routing files if they're old
     * @param forceUpdate Force update even if recently updated
     */
    suspend fun updateRoutingFiles(forceUpdate: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        // Check if we've updated recently (within 7 days)
        val lastUpdate = getLastRoutingUpdateTime()
        val sevenDaysInMillis = TimeUnit.DAYS.toMillis(7)
        
        if (!forceUpdate && System.currentTimeMillis() - lastUpdate < sevenDaysInMillis) {
            Log.i(TAG, "Routing files were updated recently, skipping update")
            return@withContext true
        }
        
        val dataDir = File(context.getExternalFilesDir(null), "xray")
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
        
        val geoipFile = File(dataDir, GEOIP_FILE)
        val geositeFile = File(dataDir, GEOSITE_FILE)
        
        // Attempt to download and update files
        var success = true
        
        success = success && downloadFile(GEOIP_URL, geoipFile)
        success = success && downloadFile(GEOSITE_URL, geositeFile)
        
        if (success) {
            setLastRoutingUpdateTime(System.currentTimeMillis())
        }
        
        return@withContext success
    }
    
    /**
     * Create default routing config file
     */
    private fun createDefaultRoutingConfig(configFile: File): Boolean {
        return try {
            val config = JSONObject().apply {
                put("domainStrategy", "IPIfNonMatch")
                
                // Rules array
                val rules = JSONArray().apply {
                    // Route all traffic through proxy by default
                    put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "proxy")
                        put("port", "0-65535")
                    })
                }
                
                put("rules", rules)
            }
            
            FileOutputStream(configFile).use { output ->
                output.write(config.toString(2).toByteArray())
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating default routing config", e)
            false
        }
    }
    
    /**
     * Download a file from URL
     */
    private suspend fun downloadFile(url: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            // Create a temporary file to download to
            val tempFile = File(destination.absolutePath + ".tmp")
            
            URL(url).openStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // If download successful, replace the old file
            if (destination.exists()) {
                destination.delete()
            }
            tempFile.renameTo(destination)
            
            Log.i(TAG, "Successfully downloaded $url to ${destination.name}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading $url", e)
            return@withContext false
        }
    }
    
    /**
     * Apply routing rules to VPN interface builder
     */
    fun applyRoutingRules(builder: VpnService.Builder, server: Server) {
        // Add bypass IPs based on server configuration or defaults
        addBypassIps(builder, server)
        
        // Apply any custom routing logic from configuration
        applyCustomRouting(builder, server)
    }
    
    /**
     * Add bypass IPs to the VPN interface
     */
    private fun addBypassIps(builder: VpnService.Builder, server: Server) {
        // Always bypass the server IP to avoid connection loops
        server.address?.let {
            try {
                builder.addRoute(it, 32)
            } catch (e: Exception) {
                Log.e(TAG, "Error adding server IP to bypass", e)
            }
        }
        
        // Add other bypass IPs from server config
        server.bypassIps?.split(",")?.forEach { ip ->
            try {
                val trimmedIp = ip.trim()
                if (trimmedIp.isNotEmpty()) {
                    val parts = trimmedIp.split("/")
                    val prefix = if (parts.size > 1) parts[1].toInt() else 32
                    builder.addRoute(parts[0], prefix)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding bypass IP: $ip", e)
            }
        }
    }
    
    /**
     * Apply custom routing logic based on server configuration
     */
    private fun applyCustomRouting(builder: VpnService.Builder, server: Server) {
        // This would implement custom routing logic based on the server's configuration
        // For example, using different routing rules for different server types
        
        when (server.routingMode?.toLowerCase()) {
            "global" -> {
                // All traffic through VPN, no additional routes needed
            }
            "bypass_cn" -> {
                // Bypass China IP ranges
                // In a real implementation, these IP ranges would be loaded from a file
                arrayOf(
                    "223.0.0.0/8", 
                    "222.0.0.0/8",
                    "221.0.0.0/8", 
                    "220.0.0.0/8", 
                    "219.0.0.0/8", 
                    "218.0.0.0/8",
                    "120.0.0.0/8", 
                    "117.0.0.0/8"
                ).forEach { range ->
                    try {
                        val parts = range.split("/")
                        val prefix = if (parts.size > 1) parts[1].toInt() else 32
                        builder.addRoute(parts[0], prefix)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error adding bypass range: $range", e)
                    }
                }
            }
            "custom" -> {
                // Custom routing rules would be applied here
                // For now we'll use the default behavior
            }
            else -> {
                // Default behavior, no special routes
            }
        }
    }
    
    /**
     * Generate updated routing rules based on templates and custom configuration
     */
    suspend fun generateRoutingRules(server: Server): JSONObject = withContext(Dispatchers.IO) {
        val dataDir = File(context.getExternalFilesDir(null), "xray")
        val routingConfigFile = File(dataDir, ROUTING_CONFIG_FILE)
        
        try {
            // Read base routing config
            var routingJson = if (routingConfigFile.exists()) {
                val content = routingConfigFile.readText()
                JSONObject(content)
            } else {
                JSONObject().apply {
                    put("domainStrategy", "IPIfNonMatch")
                    put("rules", JSONArray())
                }
            }
            
            // Clear existing rules
            routingJson.put("rules", JSONArray())
            
            // Add rules based on server routing mode
            val rules = routingJson.getJSONArray("rules")
            
            when (server.routingMode?.toLowerCase()) {
                "global" -> {
                    // Simple rule to route everything through proxy
                    rules.put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "proxy")
                    })
                }
                "bypass_cn" -> {
                    // Bypass China sites & IPs
                    rules.put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "direct")
                        put("domain", JSONArray().apply {
                            put("geosite:cn")
                        })
                    })
                    
                    rules.put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "direct")
                        put("ip", JSONArray().apply {
                            put("geoip:cn")
                        })
                    })
                    
                    // Default to proxy for everything else
                    rules.put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "proxy")
                        put("port", "0-65535")
                    })
                }
                "custom" -> {
                    // Load custom rules from server configuration
                    server.customRules?.let { customRulesStr ->
                        try {
                            val customRules = JSONArray(customRulesStr)
                            for (i in 0 until customRules.length()) {
                                rules.put(customRules.getJSONObject(i))
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing custom rules", e)
                        }
                    }
                    
                    // Default to proxy for everything else
                    rules.put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "proxy")
                        put("port", "0-65535")
                    })
                }
                else -> {
                    // Default to proxy for everything
                    rules.put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "proxy")
                        put("port", "0-65535")
                    })
                }
            }
            
            // Add custom bypass domains if configured
            server.bypassDomains?.let { domains ->
                val bypassDomainsArray = JSONArray()
                domains.split(",").forEach { domain ->
                    if (domain.trim().isNotEmpty()) {
                        bypassDomainsArray.put(domain.trim())
                    }
                }
                
                if (bypassDomainsArray.length() > 0) {
                    rules.put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "direct")
                        put("domain", bypassDomainsArray)
                    })
                }
            }
            
            // Add custom block domains if configured
            server.blockDomains?.let { domains ->
                val blockDomainsArray = JSONArray()
                domains.split(",").forEach { domain ->
                    if (domain.trim().isNotEmpty()) {
                        blockDomainsArray.put(domain.trim())
                    }
                }
                
                if (blockDomainsArray.length() > 0) {
                    rules.put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "block")
                        put("domain", blockDomainsArray)
                    })
                }
            }
            
            return@withContext routingJson
        } catch (e: Exception) {
            Log.e(TAG, "Error generating routing rules", e)
            return@withContext JSONObject().apply {
                put("domainStrategy", "IPIfNonMatch")
                put("rules", JSONArray().apply {
                    put(JSONObject().apply {
                        put("type", "field")
                        put("outboundTag", "proxy")
                        put("port", "0-65535")
                    })
                })
            }
        }
    }
    
    /**
     * Save the last routing update time
     */
    private fun setLastRoutingUpdateTime(time: Long) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_ROUTING_UPDATE, time)
            .apply()
    }
    
    /**
     * Get the last routing update time
     */
    private fun getLastRoutingUpdateTime(): Long {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_ROUTING_UPDATE, 0)
    }
}
