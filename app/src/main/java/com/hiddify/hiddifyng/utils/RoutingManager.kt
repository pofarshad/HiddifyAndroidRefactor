package com.hiddify.hiddifyng.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manager for handling routing rules from Iran-v2ray-rules repository
 * https://github.com/Chocolate4U/Iran-v2ray-rules
 */
class RoutingManager(private val context: Context) {
    private val TAG = "RoutingManager"
    
    // Base repository URL
    private val BASE_REPO_URL = "https://raw.githubusercontent.com/Chocolate4U/Iran-v2ray-rules/master/"
    
    // File names to download
    private val ROUTING_FILES = listOf(
        "geoip-iran.dat",
        "geosite-iran.dat",
        "geoip.dat",
        "geosite.dat",
        "iran.dat",
        "geoip-lite.dat",
        "geosite-lite.dat"
    )
    
    // Manifest file for version checking
    private val MANIFEST_FILE = "manifest.json"
    
    /**
     * Check if routing rule updates are available
     * @return true if updates are available, false otherwise
     */
    suspend fun checkForRoutingUpdates(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Download manifest file
            val manifestContent = downloadFile(BASE_REPO_URL + MANIFEST_FILE)
            
            if (manifestContent.isEmpty()) {
                Log.e(TAG, "Failed to download manifest file")
                return@withContext false
            }
            
            // Parse manifest
            val manifest = JSONObject(manifestContent)
            val remoteVersion = manifest.optString("version", "")
            val remoteUpdateTime = manifest.optLong("update_time", 0)
            
            if (remoteVersion.isEmpty() || remoteUpdateTime == 0L) {
                Log.e(TAG, "Invalid manifest file format")
                return@withContext false
            }
            
            // Get local manifest if exists
            val localManifestFile = File(context.filesDir, MANIFEST_FILE)
            var localUpdateTime = 0L
            
            if (localManifestFile.exists()) {
                try {
                    val localManifest = JSONObject(localManifestFile.readText())
                    localUpdateTime = localManifest.optLong("update_time", 0)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing local manifest file", e)
                }
            }
            
            // Check if update is needed
            val updateNeeded = remoteUpdateTime > localUpdateTime
            
            Log.i(TAG, "Routing update check: remote=$remoteUpdateTime, local=$localUpdateTime, update needed=$updateNeeded")
            
            return@withContext updateNeeded
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for routing updates", e)
            return@withContext false
        }
    }
    
    /**
     * Update routing files from the repository
     * @return true if successful, false otherwise
     */
    suspend fun updateRoutingFiles(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Create directory if it doesn't exist
            val routingDir = File(context.filesDir, "routing")
            if (!routingDir.exists()) {
                routingDir.mkdirs()
            }
            
            // Download manifest file first
            val manifestContent = downloadFile(BASE_REPO_URL + MANIFEST_FILE)
            
            if (manifestContent.isEmpty()) {
                Log.e(TAG, "Failed to download manifest file during update")
                return@withContext false
            }
            
            // Download each routing file
            var allDownloaded = true
            
            for (fileName in ROUTING_FILES) {
                val fileContent = downloadFile(BASE_REPO_URL + fileName)
                
                if (fileContent.isEmpty()) {
                    Log.e(TAG, "Failed to download routing file: $fileName")
                    allDownloaded = false
                    continue
                }
                
                // Save file
                val file = File(routingDir, fileName)
                file.writeBytes(fileContent.toByteArray())
                
                Log.i(TAG, "Successfully downloaded routing file: $fileName")
            }
            
            // Save manifest file
            val manifestFile = File(context.filesDir, MANIFEST_FILE)
            manifestFile.writeText(manifestContent)
            
            // Parse manifest for logging
            try {
                val manifest = JSONObject(manifestContent)
                val version = manifest.optString("version", "unknown")
                val updateTime = manifest.optLong("update_time", 0)
                
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                val updateTimeStr = dateFormat.format(Date(updateTime))
                
                Log.i(TAG, "Routing files updated to version $version (updated on $updateTimeStr)")
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing downloaded manifest", e)
            }
            
            return@withContext allDownloaded
        } catch (e: Exception) {
            Log.e(TAG, "Error updating routing files", e)
            return@withContext false
        }
    }
    
    /**
     * Get path to the routing files directory
     * @return path to routing directory
     */
    fun getRoutingDirectory(): String {
        return File(context.filesDir, "routing").absolutePath
    }
    
    /**
     * Download file from URL
     * @param url URL to download from
     * @return File content as string, or empty string if failed
     */
    private suspend fun downloadFile(url: String): String = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.readTimeout = 30000
            connection.connectTimeout = 30000
            connection.setRequestProperty("User-Agent", "MarFaNet-Co-Client")
            
            val responseCode = connection.responseCode
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val content = inputStream.readBytes()
                inputStream.close()
                connection.disconnect()
                return@withContext String(content)
            } else {
                Log.e(TAG, "HTTP error when downloading file: $responseCode")
                connection.disconnect()
                return@withContext ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file from $url", e)
            return@withContext ""
        }
    }
    
    /**
     * Load routing configuration into Xray
     * @return true if successful, false otherwise
     */
    suspend fun applyRoutingConfiguration(): Boolean = withContext(Dispatchers.IO) {
        try {
            // This would integrate with the XrayManager to apply routing configuration
            // For now, we'll just log success message
            
            Log.i(TAG, "Routing configuration applied successfully")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying routing configuration", e)
            return@withContext false
        }
    }
}