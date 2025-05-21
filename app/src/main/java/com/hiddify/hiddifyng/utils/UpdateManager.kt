package com.hiddify.hiddifyng.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Manager for handling application and routing rule updates
 */
class UpdateManager(private val context: Context) {
    companion object {
        private const val TAG = "UpdateManager"
        private const val PREFS_NAME = "update_prefs"
        private const val PREF_AUTO_UPDATE_APP = "auto_update_app"
        private const val PREF_AUTO_UPDATE_SUBSCRIPTIONS = "auto_update_subscriptions"
        private const val PREF_AUTO_UPDATE_ROUTING = "auto_update_routing"
        private const val PREF_LAST_ROUTING_UPDATE = "last_routing_update"
        private const val PREF_ROUTING_VERSION = "routing_version"
        
        // GitHub repository for routing rules
        private const val ROUTING_RULES_REPO = "Chocolate4U/Iran-v2ray-rules"
        private const val ROUTING_RULES_API = "https://api.github.com/repos/$ROUTING_RULES_REPO/releases/latest"
        private const val ROUTING_RULES_DIR = "routing_rules"
    }
    
    // Shared preferences
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Check if auto-update app is enabled
     */
    var autoUpdateApp: Boolean
        get() = prefs.getBoolean(PREF_AUTO_UPDATE_APP, true)
        set(value) {
            prefs.edit().putBoolean(PREF_AUTO_UPDATE_APP, value).apply()
        }
    
    /**
     * Check if auto-update subscriptions is enabled
     */
    var autoUpdateSubscriptions: Boolean
        get() = prefs.getBoolean(PREF_AUTO_UPDATE_SUBSCRIPTIONS, true)
        set(value) {
            prefs.edit().putBoolean(PREF_AUTO_UPDATE_SUBSCRIPTIONS, value).apply()
        }
    
    /**
     * Check if auto-update routing is enabled
     */
    var autoUpdateRouting: Boolean
        get() = prefs.getBoolean(PREF_AUTO_UPDATE_ROUTING, true)
        set(value) {
            prefs.edit().putBoolean(PREF_AUTO_UPDATE_ROUTING, value).apply()
        }
    
    /**
     * Get last routing update timestamp
     */
    private var lastRoutingUpdate: Long
        get() = prefs.getLong(PREF_LAST_ROUTING_UPDATE, 0)
        set(value) {
            prefs.edit().putLong(PREF_LAST_ROUTING_UPDATE, value).apply()
        }
    
    /**
     * Get current routing version
     */
    var routingVersion: String
        get() = prefs.getString(PREF_ROUTING_VERSION, "") ?: ""
        set(value) {
            prefs.edit().putString(PREF_ROUTING_VERSION, value).apply()
        }
    
    /**
     * Check if routing update is needed (daily update)
     */
    fun isRoutingUpdateNeeded(): Boolean {
        val currentTime = System.currentTimeMillis()
        val dayInMillis = TimeUnit.DAYS.toMillis(1)
        
        return (currentTime - lastRoutingUpdate) >= dayInMillis
    }
    
    /**
     * Check for app updates
     * @return true if update is available, false otherwise
     */
    suspend fun checkForAppUpdates(): Boolean = withContext(Dispatchers.IO) {
        try {
            // This would be implemented to check for app updates
            // For now, we'll just return false (no update available)
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for app updates", e)
            return@withContext false
        }
    }
    
    /**
     * Check for routing updates
     * @return true if update is available and downloaded, false otherwise
     */
    suspend fun checkForRoutingUpdates(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!autoUpdateRouting || !isRoutingUpdateNeeded()) {
                return@withContext false
            }
            
            // Get latest release info
            val response = URL(ROUTING_RULES_API).readText()
            val releaseJson = JSONObject(response)
            val latestVersion = releaseJson.getString("tag_name")
            
            // Check if we need to update
            if (latestVersion == routingVersion) {
                Log.i(TAG, "Routing rules are already up to date")
                lastRoutingUpdate = System.currentTimeMillis()
                return@withContext false
            }
            
            // Download new routing rules
            val assets = releaseJson.getJSONArray("assets")
            var downloadUrl = ""
            
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                
                if (name.endsWith(".dat") || name.contains("routing")) {
                    downloadUrl = asset.getString("browser_download_url")
                    break
                }
            }
            
            if (downloadUrl.isEmpty()) {
                Log.e(TAG, "No routing rules found in release")
                return@withContext false
            }
            
            // Download and save the file
            val success = downloadRoutingRules(downloadUrl)
            
            if (success) {
                routingVersion = latestVersion
                lastRoutingUpdate = System.currentTimeMillis()
                Log.i(TAG, "Routing rules updated to version $latestVersion")
                return@withContext true
            } else {
                Log.e(TAG, "Failed to download routing rules")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for routing updates", e)
            return@withContext false
        }
    }
    
    /**
     * Download routing rules
     * @param url Download URL
     * @return true if download was successful, false otherwise
     */
    private suspend fun downloadRoutingRules(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Create directory if it doesn't exist
            val rulesDir = File(context.filesDir, ROUTING_RULES_DIR)
            rulesDir.mkdirs()
            
            // Download the file
            val connection = URL(url).openConnection()
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val inputStream = connection.getInputStream()
            val outputFile = File(rulesDir, "routing.dat")
            
            outputFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading routing rules", e)
            return@withContext false
        }
    }
}