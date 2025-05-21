package com.hiddify.hiddifyng.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Manages routing rules with efficient caching and daily updates from Chocolate4U/Iran-v2ray-rules
 */
class RoutingManager(private val context: Context) {
    companion object {
        private const val TAG = "RoutingManager"
        private const val RULES_URL = "https://github.com/Chocolate4U/Iran-v2ray-rules/releases/latest/download/iran.dat"
        private const val FALLBACK_RULES_URL = "https://cdn.jsdelivr.net/gh/Chocolate4U/Iran-v2ray-rules@latest/iran.dat"
        private const val RULES_FILE_NAME = "iran.dat"
        private const val RULES_JSON_FILE_NAME = "rules.json"
        private const val PREFS_NAME = "routing_manager_prefs"
        private const val KEY_LAST_UPDATE = "last_update_timestamp"
        private const val UPDATE_INTERVAL_HOURS = 24
    }
    
    private val dataDir = File(context.filesDir, "routing")
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    init {
        // Ensure routing directory exists
        if (!dataDir.exists()) {
            dataDir.mkdirs()
        }
    }
    
    /**
     * Check if routing rules need to be updated
     */
    fun isUpdateNeeded(): Boolean {
        val rulesFile = File(dataDir, RULES_FILE_NAME)
        
        // If rules file doesn't exist, update is needed
        if (!rulesFile.exists()) {
            return true
        }
        
        // Check if update interval has passed
        val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0)
        val now = System.currentTimeMillis()
        val updateIntervalMillis = TimeUnit.HOURS.toMillis(UPDATE_INTERVAL_HOURS.toLong())
        
        return now - lastUpdate > updateIntervalMillis
    }
    
    /**
     * Check for routing rule updates from repository
     * @return true if updates are available, false otherwise
     */
    suspend fun checkForRoutingUpdates(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for routing rule updates")
            
            // If we don't have rules yet, definitely need update
            if (isUpdateNeeded()) {
                return@withContext true
            }
            
            // Try to fetch metadata and compare versions
            // For now, just return true if the update interval has passed
            val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0)
            val now = System.currentTimeMillis()
            val updateIntervalMillis = TimeUnit.HOURS.toMillis(UPDATE_INTERVAL_HOURS.toLong())
            
            return@withContext now - lastUpdate > updateIntervalMillis
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for routing updates", e)
            false
        }
    }
    
    /**
     * Update routing files from repository
     * @return true if successful, false otherwise
     */
    suspend fun updateRoutingFiles(): Boolean = updateRoutingRules()
    
    /**
     * Download and update routing rules from Chocolate4U/Iran-v2ray-rules repository
     * @return true if update was successful, false otherwise
     */
    suspend fun updateRoutingRules(): Boolean = withContext(Dispatchers.IO) {
        Log.d(TAG, "Updating routing rules")
        
        try {
            // Try primary URL first
            var success = downloadRules(RULES_URL)
            
            // Fall back to alternative source if primary fails
            if (!success) {
                Log.d(TAG, "Primary URL failed, trying fallback URL")
                success = downloadRules(FALLBACK_RULES_URL)
            }
            
            if (success) {
                // Mark update as complete
                prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
                Log.d(TAG, "Routing rules updated successfully")
            }
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "Error updating routing rules", e)
            false
        }
    }
    
    /**
     * Download rules from specified URL
     */
    private suspend fun downloadRules(urlString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(urlString)
            val connection = url.openConnection().apply {
                connectTimeout = 10000 // 10s
                readTimeout = 30000 // 30s
            }
            
            // Download to temporary file first
            val tempFile = File(dataDir, "${RULES_FILE_NAME}.tmp")
            connection.getInputStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            // Replace old file with new one
            val destFile = File(dataDir, RULES_FILE_NAME)
            if (tempFile.exists() && tempFile.length() > 0) {
                tempFile.renameTo(destFile)
                return@withContext true
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading routing rules from $urlString", e)
            false
        }
    }
    
    /**
     * Get path to the routing rules file
     */
    fun getRoutingRulesPath(): String {
        return File(dataDir, RULES_FILE_NAME).absolutePath
    }
    
    /**
     * Create a Xray routing configuration object that uses the local rules
     */
    fun createRoutingConfig(): JSONObject {
        val routing = JSONObject()
        
        // Domain strategy
        routing.put("domainStrategy", "IPIfNonMatch")
        
        // Rules array
        val rules = JSONArray()
        
        // Direct rule for private IPs
        val directRule = JSONObject()
        directRule.put("type", "field")
        directRule.put("outboundTag", "direct")
        
        // IP ranges for direct connection
        val directIPs = JSONArray()
        directIPs.put("geoip:private")
        directIPs.put("geoip:cn")
        directRule.put("ip", directIPs)
        
        // Domains for direct connection
        val directDomains = JSONArray()
        directDomains.put("geosite:cn")
        directRule.put("domain", directDomains)
        
        // Proxy rule using iran.dat custom routing file
        val proxyRule = JSONObject()
        proxyRule.put("type", "field")
        proxyRule.put("outboundTag", "proxy")
        
        // Custom iran.dat file reference
        val proxyDomains = JSONArray()
        proxyDomains.put("ext:${getRoutingRulesPath()}:ir")
        proxyRule.put("domain", proxyDomains)
        
        // IP ranges for proxy
        val proxyIPs = JSONArray()
        proxyIPs.put("geoip:ir")
        proxyRule.put("ip", proxyIPs)
        
        // Add rules to array
        rules.put(directRule)
        rules.put(proxyRule)
        
        // Default rule (anything not matching above rules)
        val defaultRule = JSONObject()
        defaultRule.put("type", "field")
        defaultRule.put("outboundTag", "proxy")
        rules.put(defaultRule)
        
        routing.put("rules", rules)
        
        return routing
    }
    
    /**
     * Get the timestamp of the last update
     */
    fun getLastUpdateTime(): Long {
        return prefs.getLong(KEY_LAST_UPDATE, 0)
    }
}