package com.hiddify.hiddifyng.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.utils.AutoUpdateManager
import com.hiddify.hiddifyng.utils.RoutingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker for checking and installing updates
 * Handles both app updates and routing rules updates
 */
class UpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    // Store params for later use
    private val workerParams = params
    private val TAG = "UpdateWorker"
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting update check")
                
                // Get app settings
                val database = AppDatabase.getInstance(context)
                val settings = database.appSettingsDao().getSettings()
                
                if (settings == null) {
                    Log.e(TAG, "App settings not found")
                    return@withContext Result.failure()
                }
                
                // Check for routing updates
                checkRoutingUpdates()
                
                // Check if auto-update is enabled
                if (!settings.autoUpdateEnabled) {
                    Log.i(TAG, "Auto-update is disabled, skipping app update check")
                    return@withContext Result.success()
                }
                
                // Check for app updates
                val updateManager = AutoUpdateManager(context)
                val updateInfo = updateManager.checkForUpdates()
                
                if (updateInfo != null) {
                    Log.i(TAG, "New app update available: ${updateInfo.latestVersion}")
                    
                    // Download and install update
                    val success = updateManager.downloadAndInstallUpdate(updateInfo)
                    
                    if (success) {
                        Log.i(TAG, "App update downloaded and installation initiated")
                    } else {
                        Log.e(TAG, "Failed to download and install app update")
                    }
                } else {
                    Log.i(TAG, "No app updates available")
                }
                
                // Check for Xray core updates if enabled
                if (settings.autoUpdateXrayCore) {
                    Log.i(TAG, "Checking for Xray core updates")
                    
                    val xrayCoreUpdated = updateManager.updateXrayCore()
                    
                    if (xrayCoreUpdated) {
                        Log.i(TAG, "Xray core updated successfully")
                    } else {
                        Log.i(TAG, "No Xray core updates available or update failed")
                    }
                } else {
                    Log.i(TAG, "Xray core auto-update is disabled")
                }
                
                return@withContext Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Error during update worker execution", e)
                return@withContext Result.failure()
            }
        }
    }
    
    /**
     * Check for routing rule updates
     */
    private suspend fun checkRoutingUpdates() {
        try {
            Log.i(TAG, "Checking for routing updates")
            
            val routingManager = RoutingManager(context)
            val hasUpdates = routingManager.checkForRoutingUpdates()
            
            if (hasUpdates) {
                Log.i(TAG, "Routing updates available, downloading...")
                
                val success = routingManager.updateRoutingFiles()
                
                if (success) {
                    Log.i(TAG, "Routing files updated successfully")
                } else {
                    Log.e(TAG, "Failed to update routing files")
                }
            } else {
                Log.i(TAG, "No routing updates available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for routing updates", e)
        }
    }
}