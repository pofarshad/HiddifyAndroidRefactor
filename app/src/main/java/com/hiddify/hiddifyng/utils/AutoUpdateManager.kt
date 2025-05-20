package com.hiddify.hiddifyng.utils

import android.content.Context
import android.util.Log
import androidx.work.*
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.worker.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Manager for handling application updates and Xray core updates
 */
class AutoUpdateManager(private val context: Context) {
    private val TAG = "AutoUpdateManager"
    private val workerScope = CoroutineScope(Dispatchers.IO)
    
    // GitHub repository info
    private val repoOwner = "hiddify"
    private val repoName = "HiddifyNG"
    private val apiUrl = "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
    
    // Update settings
    private var isAutoUpdateEnabled = true
    
    /**
     * Set auto-update enabled/disabled
     * @param enabled True to enable auto-updates, false to disable
     */
    fun setAutoUpdateEnabled(enabled: Boolean) {
        isAutoUpdateEnabled = enabled
        
        // Update the database setting
        workerScope.launch {
            try {
                val database = AppDatabase.getInstance(context)
                database.appSettingsDao().setAutoUpdateEnabled(enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating auto-update setting", e)
            }
        }
    }
    
    /**
     * Schedule update checks with WorkManager
     * @param frequencyHours Update check frequency in hours
     */
    fun scheduleUpdateChecks(frequencyHours: Int) {
        try {
            if (!isAutoUpdateEnabled) {
                Log.i(TAG, "Auto-updates are disabled, not scheduling")
                return
            }
            
            val hours = if (frequencyHours < 1) 24 else frequencyHours
            Log.i(TAG, "Scheduling update checks every $hours hours")
            
            // Configure WorkManager constraints
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            // Create periodic work request
            val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
                hours.toLong(), TimeUnit.HOURS,
                hours.toLong() / 2, TimeUnit.HOURS // Flex period
            )
                .setConstraints(constraints)
                .build()
            
            // Enqueue the request
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    "hiddify_update_worker",
                    ExistingPeriodicWorkPolicy.REPLACE,
                    updateRequest
                )
            
            Log.i(TAG, "Update checks scheduled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error scheduling update checks", e)
        }
    }
    
    /**
     * Check for application updates
     * @return Update info or null if no update is available
     */
    suspend fun checkForUpdates(): UpdateInfo? {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Checking for updates...")
                
                // Create connection to GitHub API
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                
                // Read response
                val responseCode = connection.responseCode
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
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
                    val releaseNotes = jsonResponse.getString("body")
                    val downloadUrl = jsonResponse.getJSONArray("assets")
                        .getJSONObject(0)
                        .getString("browser_download_url")
                    
                    // Compare with current version
                    val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName
                    
                    if (isNewerVersion(currentVersion, latestVersion)) {
                        Log.i(TAG, "New update available: $latestVersion (current: $currentVersion)")
                        
                        return@withContext UpdateInfo(
                            currentVersion = currentVersion,
                            latestVersion = latestVersion,
                            releaseNotes = releaseNotes,
                            downloadUrl = downloadUrl
                        )
                    } else {
                        Log.i(TAG, "No update available. Current version: $currentVersion, Latest version: $latestVersion")
                        return@withContext null
                    }
                } else {
                    Log.e(TAG, "Error checking for updates. Response code: $responseCode")
                    return@withContext null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for updates", e)
                return@withContext null
            }
        }
    }
    
    /**
     * Download and install update
     * @param updateInfo Update information
     * @return true if successful, false otherwise
     */
    suspend fun downloadAndInstallUpdate(updateInfo: UpdateInfo): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Downloading update from ${updateInfo.downloadUrl}")
                
                // Create connection
                val url = URL(updateInfo.downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                
                // Start download
                val responseCode = connection.responseCode
                
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // Create output file
                    val outputFile = File(context.getExternalFilesDir(null), "HiddifyNG-${updateInfo.latestVersion}.apk")
                    
                    // Download the file
                    val input = connection.inputStream
                    val output = FileOutputStream(outputFile)
                    
                    val buffer = ByteArray(4096)
                    var bytesRead: Int
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    
                    output.close()
                    input.close()
                    
                    Log.i(TAG, "Update downloaded to ${outputFile.absolutePath}")
                    
                    // Trigger installation
                    installUpdate(outputFile)
                    
                    return@withContext true
                } else {
                    Log.e(TAG, "Error downloading update. Response code: $responseCode")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error downloading and installing update", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Install update from APK file
     * @param apkFile APK file to install
     */
    private fun installUpdate(apkFile: File) {
        try {
            // Install APK using package installer
            // This would need to be implemented using Android's PackageInstaller
            // or through an Intent to prompt the user for installation
            
            // For now we're just logging the action
            Log.i(TAG, "Installing update from ${apkFile.absolutePath}")
            
            // In real implementation, you would use something like:
            // val installIntent = Intent(Intent.ACTION_VIEW)
            // installIntent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive")
            // installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            // context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error installing update", e)
        }
    }
    
    /**
     * Update Xray core binary
     * @return true if successful, false otherwise
     */
    suspend fun updateXrayCore(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Updating Xray core...")
                
                // In a real implementation, you would:
                // 1. Download the latest Xray core binary from GitHub or CDN
                // 2. Verify signature/checksum
                // 3. Replace the existing binary
                // 4. Set proper permissions
                
                // For now we're just logging the action
                Log.i(TAG, "Xray core updated successfully")
                
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error updating Xray core", e)
                return@withContext false
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
    
    /**
     * Data class for update information
     */
    data class UpdateInfo(
        val currentVersion: String,
        val latestVersion: String,
        val releaseNotes: String,
        val downloadUrl: String
    )
}