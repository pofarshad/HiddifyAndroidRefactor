package com.hiddify.hiddifyng.utils

import android.content.Context
import android.util.Log
import com.hiddify.hiddifyng.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Manages routing rule updates from Chocolate4U/Iran-v2ray-rules repository
 * Handles downloading, extraction and application of the routing rules
 */
class RoutingManager(private val context: Context) {
    companion object {
        private const val TAG = "RoutingManager"
        
        // Repository information
        private const val REPO_OWNER = "Chocolate4U"
        private const val REPO_NAME = "Iran-v2ray-rules"
        private const val GITHUB_API_URL = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"
        private const val ROUTING_RULES_FILE_NAME = "rules.zip"
        
        // File paths
        private const val ROUTING_DIR = "routing"
        private const val VERSION_FILE = "version.txt"
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val routingDir by lazy {
        File(context.filesDir, ROUTING_DIR).also { it.mkdirs() }
    }
    
    private val versionFile by lazy {
        File(routingDir, VERSION_FILE)
    }
    
    /**
     * Check if routing files need to be updated
     * Compares local version with the latest GitHub release
     */
    suspend fun checkForRoutingUpdates(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Checking for routing updates")
            
            // Get current version
            val currentVersion = getCurrentVersion()
            Log.d(TAG, "Current version: $currentVersion")
            
            // Get latest version from GitHub
            val latestVersion = getLatestVersion()
            Log.d(TAG, "Latest version: $latestVersion")
            
            // Files need update if versions don't match or files are missing
            return@withContext if (latestVersion.isNullOrEmpty()) {
                Log.w(TAG, "Failed to retrieve latest version")
                false
            } else if (currentVersion != latestVersion) {
                Log.i(TAG, "New routing version available: $latestVersion")
                true
            } else {
                // Check if essential files exist
                val filesExist = checkEssentialFiles()
                if (!filesExist) {
                    Log.i(TAG, "Essential routing files missing, need update")
                }
                !filesExist
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            false
        }
    }
    
    /**
     * Update routing files from GitHub repository
     */
    suspend fun updateRoutingFiles(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Updating routing files")
            
            // Get latest release information
            val releaseInfo = getLatestReleaseInfo() ?: return@withContext false
            
            // Find the routing rules asset URL
            val downloadUrl = releaseInfo.assets
                .find { it.name == ROUTING_RULES_FILE_NAME }
                ?.downloadUrl
            
            if (downloadUrl.isNullOrEmpty()) {
                Log.e(TAG, "Download URL not found in release info")
                return@withContext false
            }
            
            // Download the ZIP file
            val zipFile = File(context.cacheDir, ROUTING_RULES_FILE_NAME)
            if (!downloadFile(downloadUrl, zipFile)) {
                Log.e(TAG, "Failed to download routing rules file")
                return@withContext false
            }
            
            // Extract files to routing directory
            val extractSuccess = extractZipFile(zipFile, routingDir)
            if (!extractSuccess) {
                Log.e(TAG, "Failed to extract routing files")
                return@withContext false
            }
            
            // Save the new version
            saveCurrentVersion(releaseInfo.tag)
            Log.i(TAG, "Updated routing files to version ${releaseInfo.tag}")
            
            // Clean up
            zipFile.delete()
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error updating routing files", e)
            return@withContext false
        }
    }
    
    /**
     * Get current version from local storage
     */
    private fun getCurrentVersion(): String? {
        if (!versionFile.exists()) {
            return null
        }
        
        return try {
            versionFile.readText().trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading version file", e)
            null
        }
    }
    
    /**
     * Save current version to local storage
     */
    private fun saveCurrentVersion(version: String) {
        try {
            versionFile.writeText(version)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing version file", e)
        }
    }
    
    /**
     * Check if essential routing files exist
     */
    private fun checkEssentialFiles(): Boolean {
        val requiredFiles = listOf(
            "geoip.dat",
            "geosite.dat",
            "direct.txt",
            "proxy.txt",
            "block.txt"
        )
        
        for (file in requiredFiles) {
            if (!File(routingDir, file).exists()) {
                Log.d(TAG, "Missing essential file: $file")
                return false
            }
        }
        
        return true
    }
    
    /**
     * Get latest version from GitHub API
     */
    private suspend fun getLatestVersion(): String? = withContext(Dispatchers.IO) {
        val releaseInfo = getLatestReleaseInfo() ?: return@withContext null
        return@withContext releaseInfo.tag
    }
    
    /**
     * Get latest release information from GitHub API
     */
    private suspend fun getLatestReleaseInfo(): ReleaseInfo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_API_URL)
                .header("Accept", "application/vnd.github.v3+json")
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Error getting latest release: ${response.code}")
                    return@withContext null
                }
                
                val body = response.body?.string() ?: return@withContext null
                
                // Parse release information
                val tag = extractTagName(body)
                val assets = extractAssets(body)
                
                return@withContext ReleaseInfo(tag, assets)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting latest release info", e)
            return@withContext null
        }
    }
    
    /**
     * Extract tag name from GitHub API response
     */
    private fun extractTagName(body: String): String {
        val tagRegex = "\"tag_name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val match = tagRegex.find(body)
        return match?.groupValues?.get(1) ?: ""
    }
    
    /**
     * Extract assets information from GitHub API response
     */
    private fun extractAssets(body: String): List<AssetInfo> {
        val result = mutableListOf<AssetInfo>()
        
        val assetsRegex = "\"assets\"\\s*:\\s*\\[(.*?)\\]".toRegex(RegexOption.DOT_MATCHES_ALL)
        val assetsMatch = assetsRegex.find(body)
        val assetsJson = assetsMatch?.groupValues?.get(1) ?: return result
        
        val assetRegex = "\\{(.*?)\\}".toRegex(RegexOption.DOT_MATCHES_ALL)
        val nameRegex = "\"name\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        val urlRegex = "\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"".toRegex()
        
        assetRegex.findAll(assetsJson).forEach { assetMatch ->
            val assetJson = assetMatch.groupValues[1]
            val name = nameRegex.find(assetJson)?.groupValues?.get(1) ?: ""
            val url = urlRegex.find(assetJson)?.groupValues?.get(1) ?: ""
            
            if (name.isNotEmpty() && url.isNotEmpty()) {
                result.add(AssetInfo(name, url))
            }
        }
        
        return result
    }
    
    /**
     * Download a file from a URL
     */
    private suspend fun downloadFile(url: String, destination: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .build()
            
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Error downloading file: ${response.code}")
                    return@withContext false
                }
                
                val body = response.body ?: return@withContext false
                
                FileOutputStream(destination).use { output ->
                    body.byteStream().use { input ->
                        input.copyTo(output)
                    }
                }
                
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading file", e)
            return@withContext false
        }
    }
    
    /**
     * Extract a ZIP file to a destination directory
     */
    private suspend fun extractZipFile(zipFile: File, destDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            ZipInputStream(zipFile.inputStream()).use { zipInputStream ->
                var entry = zipInputStream.nextEntry
                
                while (entry != null) {
                    val outputFile = File(destDir, entry.name)
                    
                    // Create directories if needed
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        // Create parent directories if needed
                        outputFile.parentFile?.mkdirs()
                        
                        // Extract file
                        FileOutputStream(outputFile).use { output ->
                            zipInputStream.copyTo(output)
                        }
                    }
                    
                    zipInputStream.closeEntry()
                    entry = zipInputStream.nextEntry
                }
            }
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting ZIP file", e)
            return@withContext false
        }
    }
    
    /**
     * Data class for release information
     */
    data class ReleaseInfo(
        val tag: String,
        val assets: List<AssetInfo>
    )
    
    /**
     * Data class for asset information
     */
    data class AssetInfo(
        val name: String,
        val downloadUrl: String
    )
}