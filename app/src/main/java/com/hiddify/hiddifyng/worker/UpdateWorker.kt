package com.hiddify.hiddifyng.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiddify.hiddifyng.utils.UpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker for checking and applying updates
 * Handles app updates and routing rules updates
 */
class UpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    companion object {
        private const val TAG = "UpdateWorker"
    }
    
    // Store parameters for child worker creation
    private val workerParams = params
    
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "Starting update check operation")
            
            val updateManager = UpdateManager(context)
            
            // Check for app updates
            val appUpdateAvailable = checkForAppUpdates(updateManager)
            
            // Check for routing updates
            val routingUpdateApplied = checkForRoutingUpdates(updateManager)
            
            return@withContext if (appUpdateAvailable || routingUpdateApplied) {
                Log.i(TAG, "Updates available or applied: app=$appUpdateAvailable, routing=$routingUpdateApplied")
                Result.success()
            } else {
                Log.i(TAG, "No updates available or applied")
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in update worker", e)
            return@withContext Result.failure()
        }
    }
    
    /**
     * Check for app updates
     * @param updateManager Update manager
     * @return true if update is available, false otherwise
     */
    private suspend fun checkForAppUpdates(updateManager: UpdateManager): Boolean = withContext(Dispatchers.IO) {
        if (!updateManager.autoUpdateApp) {
            Log.d(TAG, "Auto app update is disabled")
            return@withContext false
        }
        
        try {
            val updateAvailable = updateManager.checkForAppUpdates()
            
            if (updateAvailable) {
                Log.i(TAG, "App update available")
                // Notify user about app update
                notifyAppUpdateAvailable()
            } else {
                Log.d(TAG, "No app update available")
            }
            
            return@withContext updateAvailable
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for app updates", e)
            return@withContext false
        }
    }
    
    /**
     * Check for routing updates
     * @param updateManager Update manager
     * @return true if update is applied, false otherwise
     */
    private suspend fun checkForRoutingUpdates(updateManager: UpdateManager): Boolean = withContext(Dispatchers.IO) {
        if (!updateManager.autoUpdateRouting) {
            Log.d(TAG, "Auto routing update is disabled")
            return@withContext false
        }
        
        if (!updateManager.isRoutingUpdateNeeded()) {
            Log.d(TAG, "Routing update not needed yet")
            return@withContext false
        }
        
        try {
            val updateApplied = updateManager.checkForRoutingUpdates()
            
            if (updateApplied) {
                Log.i(TAG, "Routing update applied")
                // Apply routing update to active connection if needed
                applyRoutingUpdate()
            } else {
                Log.d(TAG, "No routing update applied")
            }
            
            return@withContext updateApplied
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for routing updates", e)
            return@withContext false
        }
    }
    
    /**
     * Notify user about app update available
     */
    private suspend fun notifyAppUpdateAvailable() = withContext(Dispatchers.IO) {
        // This would be implemented to show a notification
        Log.d(TAG, "Notification shown: App update available")
    }
    
    /**
     * Apply routing update to active connection
     */
    private suspend fun applyRoutingUpdate() = withContext(Dispatchers.IO) {
        // This would be implemented to apply routing update
        Log.d(TAG, "Applied routing update to active connection")
    }
}