package com.hiddify.hiddifyng.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Manager for handling routing files and configurations
 * Provides functionality to download, update, and manage routing rules
 */
class RoutingManager(private val context: Context) {
    private val TAG = "RoutingManager"
    
    // Remote repository info for routing files
    private val routingRepoOwner = "hiddify"
    private val routingRepoName = "routing-configs"
    private val apiUrl = "https://api.github.com/repos/$routingRepoOwner/$routingRepoName/releases/latest"
    
    // Local routing files directory
    private val routingDir by lazy { File(context.filesDir, "routing") }
    
    /**
     * Initialize routing files
     * Downloads them if they don't exist
     * @return true if successful, false otherwise
     */
    suspend fun initializeRoutingFiles(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Create routing directory if it doesn't exist
                if (!routingDir.exists()) {
                    routingDir.mkdirs()
                }
                
                // Check if routing files already exist
                val siteConfigFile = File(routingDir, "site.dat")
                val ipConfigFile = File(routingDir, "ip.dat")
                
                // If files don't exist, download them
                if (!siteConfigFile.exists() || !ipConfigFile.exists()) {
                    Log.i(TAG, "Routing files not found, downloading...")
                    return@withContext downloadRoutingFiles()
                }
                
                Log.i(TAG, "Routing files already exist")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing routing files", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Check for routing file updates
     * @return true if updates are available, false otherwise
     */
    suspend fun checkForRoutingUpdates(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Checking for routing updates...")
                
                // Get local version
                val versionFile = File(routingDir, "version.txt")
                val localVersion = if (versionFile.exists()) {
                    versionFile.readText().trim()
                } else {
                    "0.0.0"
                }
                
                // Get remote version
                val latestVersion = getLatestRoutingVersion()
                
                if (latestVersion == null) {
                    Log.e(TAG, "Failed to get latest routing version")
                    return@withContext false
                }
                
                // Compare versions
                val hasUpdate = isNewerVersion(localVersion, latestVersion)
                
                Log.i(TAG, "Routing update check: local=$localVersion, remote=$latestVersion, hasUpdate=$hasUpdate")
                
                return@withContext hasUpdate
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for routing updates", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Update routing files
     * @return true if successful, false otherwise
     */
    suspend fun updateRoutingFiles(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Updating routing files...")
                
                // Download routing files
                val success = downloadRoutingFiles()
                
                if (success) {
                    Log.i(TAG, "Routing files updated successfully")
                } else {
                    Log.e(TAG, "Failed to update routing files")
                }
                
                return@withContext success
            } catch (e: Exception) {
                Log.e(TAG, "Error updating routing files", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Get default routing config based on mode
     * @param mode Routing mode (global, bypass_cn, ...)
     * @return Routing configuration as JSON string
     */
    fun getDefaultRoutingConfig(mode: String): String {
        return try {
            val routingConfig = when (mode.toLowerCase()) {
                "global" -> {
                    JSONObject().apply {
                        put("domainStrategy", "AsIs")
                        put("rules", JSONObject().apply {
                            put("type", "field")
                            put("outboundTag", "proxy")
                            put("port", "0-65535")
                        })
                    }
                }
                "bypass_cn" -> {
                    JSONObject().apply {
                        put("domainStrategy", "IPIfNonMatch")
                        put("rules", arrayOf(
                            JSONObject().apply {
                                put("type", "field")
                                put("outboundTag", "direct")
                                put("domain", arrayOf("geosite:cn"))
                            },
                            JSONObject().apply {
                                put("type", "field")
                                put("outboundTag", "direct")
                                put("ip", arrayOf("geoip:cn"))
                            },
                            JSONObject().apply {
                                put("type", "field")
                                put("outboundTag", "proxy")
                                put("port", "0-65535")
                            }
                        ))
                    }
                }
                "custom" -> {
                    // Empty shell for custom routing
                    JSONObject().apply {
                        put("domainStrategy", "AsIs")
                        put("rules", JSONObject().apply {
                            put("type", "field")
                            put("outboundTag", "proxy")
                            put("port", "0-65535")
                        })
                    }
                }
                else -> {
                    // Default to global routing
                    JSONObject().apply {
                        put("domainStrategy", "AsIs")
                        put("rules", JSONObject().apply {
                            put("type", "field")
                            put("outboundTag", "proxy")
                            put("port", "0-65535")
                        })
                    }
                }
            }
            
            return routingConfig.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating routing config for mode: $mode", e)
            return "{}"
        }
    }
    
    /**
     * Download routing files from repository
     * @return true if successful, false otherwise
     */
    private suspend fun downloadRoutingFiles(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Get latest version and download URL
                val (latestVersion, downloadUrl) = getLatestRoutingVersionAndUrl() ?: return@withContext false
                
                Log.i(TAG, "Downloading routing files from: $downloadUrl")
                
                // Create connection
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                // Check response
                val responseCode = connection.responseCode
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Failed to download routing files. Response code: $responseCode")
                    return@withContext false
                }
                
                // Download and extract zip file
                val zipInputStream = ZipInputStream(connection.inputStream)
                var zipEntry = zipInputStream.nextEntry
                
                while (zipEntry != null) {
                    val entryName = zipEntry.name
                    val outputFile = File(routingDir, entryName)
                    
                    // Create directories if needed
                    if (zipEntry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        // Create parent directories if needed
                        outputFile.parentFile?.mkdirs()
                        
                        // Write file
                        val outputStream = FileOutputStream(outputFile)
                        val buffer = ByteArray(4096)
                        var bytesRead: Int
                        
                        while (zipInputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                        }
                        
                        outputStream.close()
                        Log.d(TAG, "Extracted file: $entryName")
                    }
                    
                    zipInputStream.closeEntry()
                    zipEntry = zipInputStream.nextEntry
                }
                
                zipInputStream.close()
                
                // Save version info
                val versionFile = File(routingDir, "version.txt")
                versionFile.writeText(latestVersion)
                
                Log.i(TAG, "Routing files download completed, version: $latestVersion")
                
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading routing files", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Get latest routing version from the repository
     * @return Latest version string, null if failed
     */
    private suspend fun getLatestRoutingVersion(): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Create connection to GitHub API
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                // Read response
                val responseCode = connection.responseCode
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Failed to get latest routing version. Response code: $responseCode")
                    return@withContext null
                }
                
                // Read and parse response
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                
                reader.close()
                
                // Parse JSON response
                val jsonResponse = JSONObject(response.toString())
                val latestVersion = jsonResponse.getString("tag_name")
                
                return@withContext latestVersion
            } catch (e: Exception) {
                Log.e(TAG, "Error getting latest routing version", e)
                return@withContext null
            }
        }
    }
    
    /**
     * Get latest routing version and download URL
     * @return Pair of latest version and download URL, null if failed
     */
    private suspend fun getLatestRoutingVersionAndUrl(): Pair<String, String>? {
        return withContext(Dispatchers.IO) {
            try {
                // Create connection to GitHub API
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                // Read response
                val responseCode = connection.responseCode
                
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "Failed to get latest routing version and URL. Response code: $responseCode")
                    return@withContext null
                }
                
                // Read and parse response
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                
                reader.close()
                
                // Parse JSON response
                val jsonResponse = JSONObject(response.toString())
                val latestVersion = jsonResponse.getString("tag_name")
                
                // Find zip asset
                val assets = jsonResponse.getJSONArray("assets")
                var downloadUrl: String? = null
                
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.getString("name")
                    
                    if (assetName.endsWith(".zip")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                
                if (downloadUrl == null) {
                    Log.e(TAG, "No zip asset found in release")
                    return@withContext null
                }
                
                return@withContext Pair(latestVersion, downloadUrl)
            } catch (e: Exception) {
                Log.e(TAG, "Error getting latest routing version and URL", e)
                return@withContext null
            }
        }
    }
    
    /**
     * Compare version strings to determine if new version is newer
     * @param currentVersion Current version string
     * @param newVersion New version string
     * @return true if new version is newer, false otherwise
     */
    private fun isNewerVersion(currentVersion: String, newVersion: String): Boolean {
        try {
            // Strip 'v' prefix if present
            val current = currentVersion.removePrefix("v")
            val new = newVersion.removePrefix("v")
            
            // Split version strings by dots
            val currentParts = current.split('.')
            val newParts = new.split('.')
            
            // Compare version parts
            val minLength = minOf(currentParts.size, newParts.size)
            
            for (i in 0 until minLength) {
                val currentPart = currentParts[i].toIntOrNull() ?: 0
                val newPart = newParts[i].toIntOrNull() ?: 0
                
                if (newPart > currentPart) {
                    return true
                } else if (newPart < currentPart) {
                    return false
                }
                // If equal, continue to next part
            }
            
            // If we get here and new version has more parts, it's newer
            return newParts.size > currentParts.size
        } catch (e: Exception) {
            Log.e(TAG, "Error comparing versions: $currentVersion and $newVersion", e)
            return false
        }
    }
}