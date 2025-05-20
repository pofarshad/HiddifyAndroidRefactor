package com.hiddify.hiddifyng.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.work.*
import com.hiddify.hiddifyng.BuildConfig
import com.hiddify.hiddifyng.core.XrayManager
import com.hiddify.hiddifyng.worker.UpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.util.concurrent.TimeUnit

class AutoUpdateManager(private val context: Context) {
    private val TAG = "AutoUpdateManager"
    
    companion object {
        const val GITHUB_API_URL = "https://api.github.com/repos/hiddify/HiddifyNG/releases/latest"
        const val UPDATE_WORK_NAME = "hiddify_update_checker"
        
        // Shared Preferences keys
        const val PREF_NAME = "hiddify_update_prefs"
        const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        const val KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled"
        const val KEY_AUTO_UPDATE_FREQUENCY = "auto_update_frequency" // in hours
    }
    
    /**
     * Schedule periodic update checks
     */
    fun scheduleUpdateChecks(frequencyHours: Int = 24) {
        // Save the update frequency
        setUpdateFrequency(frequencyHours)
        
        // Cancel any existing update work
        WorkManager.getInstance(context).cancelUniqueWork(UPDATE_WORK_NAME)
        
        // Only schedule if auto-update is enabled
        if (isAutoUpdateEnabled()) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
                frequencyHours.toLong(), TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UPDATE_WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                updateRequest
            )
            
            Log.i(TAG, "Scheduled update checks every $frequencyHours hours")
        }
    }
    
    /**
     * Check for app updates manually
     */
    suspend fun checkForAppUpdates(): UpdateInfo = withContext(Dispatchers.IO) {
        try {
            // Record last update check time
            setLastUpdateCheckTime(System.currentTimeMillis())
            
            // Fetch release info from GitHub
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val inputStream = connection.getInputStream()
            val response = inputStream.bufferedReader().use { it.readText() }
            
            val releaseJson = JSONObject(response)
            val latestVersion = releaseJson.getString("tag_name").removePrefix("v")
            val currentVersion = BuildConfig.VERSION_NAME
            
            val hasUpdate = compareVersions(latestVersion, currentVersion) > 0
            val downloadUrl = if (hasUpdate) {
                // Find the APK asset
                val assets = releaseJson.getJSONArray("assets")
                var apkUrl: String? = null
                
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }
                
                apkUrl
            } else null
            
            Log.i(TAG, "Update check: Current=$currentVersion, Latest=$latestVersion, HasUpdate=$hasUpdate")
            
            return@withContext UpdateInfo(
                currentVersion = currentVersion,
                latestVersion = latestVersion,
                hasUpdate = hasUpdate,
                downloadUrl = downloadUrl,
                releaseNotes = releaseJson.getString("body")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates", e)
            return@withContext UpdateInfo(
                currentVersion = BuildConfig.VERSION_NAME,
                latestVersion = "",
                hasUpdate = false,
                downloadUrl = null,
                releaseNotes = "",
                error = e.message
            )
        }
    }
    
    /**
     * Check for Xray core updates
     */
    suspend fun checkForXrayCoreUpdates(xrayManager: XrayManager): Boolean = withContext(Dispatchers.IO) {
        try {
            // This would implement the logic to check for Xray core updates
            // Would involve checking the current version against latest GitHub release
            // For now, we'll just call the update method directly
            return@withContext xrayManager.updateXrayCore()
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for Xray core updates", e)
            return@withContext false
        }
    }
    
    /**
     * Download and install the latest app update
     */
    suspend fun downloadAndInstallUpdate(downloadUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Create downloads directory if it doesn't exist
            val downloadsDir = File(context.getExternalFilesDir(null), "downloads")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            // Download the APK
            val apkFile = File(downloadsDir, "HiddifyNG-update.apk")
            
            URL(downloadUrl).openStream().use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Install the APK
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".provider",
                apkFile
            )
            
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            context.startActivity(installIntent)
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading/installing update", e)
            return@withContext false
        }
    }
    
    /**
     * Compare version strings (e.g. "1.2.3" > "1.2.2")
     * Returns positive if v1 > v2, negative if v1 < v2, 0 if equal
     */
    private fun compareVersions(v1: String, v2: String): Int {
        val v1Parts = v1.split(".")
        val v2Parts = v2.split(".")
        
        for (i in 0 until Math.max(v1Parts.size, v2Parts.size)) {
            val part1 = if (i < v1Parts.size) v1Parts[i].toIntOrNull() ?: 0 else 0
            val part2 = if (i < v2Parts.size) v2Parts[i].toIntOrNull() ?: 0 else 0
            
            if (part1 != part2) {
                return part1 - part2
            }
        }
        
        return 0
    }
    
    /**
     * Enable or disable automatic updates
     */
    fun setAutoUpdateEnabled(enabled: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled)
            .apply()
        
        // If enabling, schedule checks. If disabling, cancel scheduled work
        if (enabled) {
            val frequency = getUpdateFrequency()
            scheduleUpdateChecks(frequency)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(UPDATE_WORK_NAME)
        }
    }
    
    /**
     * Check if automatic updates are enabled
     */
    fun isAutoUpdateEnabled(): Boolean {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_UPDATE_ENABLED, true)
    }
    
    /**
     * Set the update frequency (in hours)
     */
    private fun setUpdateFrequency(hours: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_AUTO_UPDATE_FREQUENCY, hours)
            .apply()
    }
    
    /**
     * Get the update frequency (in hours)
     */
    fun getUpdateFrequency(): Int {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_AUTO_UPDATE_FREQUENCY, 24)
    }
    
    /**
     * Record the time of the last update check
     */
    private fun setLastUpdateCheckTime(time: Long) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_UPDATE_CHECK, time)
            .apply()
    }
    
    /**
     * Get the time of the last update check
     */
    fun getLastUpdateCheckTime(): Long {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_UPDATE_CHECK, 0)
    }
    
    /**
     * Data class to hold update information
     */
    data class UpdateInfo(
        val currentVersion: String,
        val latestVersion: String,
        val hasUpdate: Boolean,
        val downloadUrl: String?,
        val releaseNotes: String,
        val error: String? = null
    )
}
