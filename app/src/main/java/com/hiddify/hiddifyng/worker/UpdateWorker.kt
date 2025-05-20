package com.hiddify.hiddifyng.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiddify.hiddifyng.core.XrayManager
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.utils.AutoUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker that handles periodic app and core updates
 */
class UpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    private val TAG = "UpdateWorker"
    
    private val autoUpdateManager = AutoUpdateManager(context)
    private val xrayManager = XrayManager(context)
    private val database = AppDatabase.getInstance(context)
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting update check")
        
        try {
            // Get app settings
            val settings = database.appSettingsDao().getSettings()
            
            // Check if auto-update is enabled
            if (settings?.autoUpdateEnabled != true) {
                Log.i(TAG, "Auto-update is disabled, skipping")
                return@withContext Result.success()
            }
            
            // Check for app updates
            var hasUpdates = false
            var updateInfo: AutoUpdateManager.UpdateInfo? = null
            
            try {
                updateInfo = autoUpdateManager.checkForAppUpdates()
                hasUpdates = updateInfo.hasUpdate
                
                if (hasUpdates) {
                    Log.i(TAG, "App update available: ${updateInfo.currentVersion} -> ${updateInfo.latestVersion}")
                    // We don't automatically install updates, just notify the user
                    // The update notification will be shown based on the updateAvailable LiveData in MainViewModel
                } else {
                    Log.i(TAG, "No app update available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking for app updates", e)
            }
            
            // Check if Xray core auto-update is enabled
            if (settings.autoUpdateXrayCore == true) {
                try {
                    val updated = autoUpdateManager.checkForXrayCoreUpdates(xrayManager)
                    hasUpdates = hasUpdates || updated
                    
                    if (updated) {
                        Log.i(TAG, "Xray core updated successfully")
                    } else {
                        Log.i(TAG, "No Xray core update available")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating Xray core", e)
                }
            }
            
            // Update routing files if needed
            try {
                val routingManager = com.hiddify.hiddifyng.utils.RoutingManager(applicationContext)
                val routingUpdated = routingManager.updateRoutingFiles(false)
                
                if (routingUpdated) {
                    Log.i(TAG, "Routing files updated successfully")
                    hasUpdates = true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating routing files", e)
            }
            
            if (hasUpdates) {
                Log.i(TAG, "Updates completed successfully")
            } else {
                Log.i(TAG, "No updates available")
            }
            
            return@withContext Result.success()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error during update check", e)
            return@withContext Result.retry()
        }
    }
}
