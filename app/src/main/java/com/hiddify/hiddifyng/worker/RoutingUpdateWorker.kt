package com.hiddify.hiddifyng.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.utils.RoutingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker for updating routing rules
 * Uses Chocolate4U/Iran-v2ray-rules repository for up-to-date routing data
 */
class RoutingUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    // Store params for later use
    private val workerParams = params
    private val TAG = "RoutingUpdateWorker"
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting routing rules update")
                
                // Get app settings
                val database = AppDatabase.getInstance(context)
                val settings = database.appSettingsDao().getSettings()
                
                if (settings == null) {
                    Log.e(TAG, "App settings not found")
                    return@withContext Result.failure()
                }
                
                // Check if auto-update routing is enabled
                if (!settings.autoUpdateRouting) {
                    Log.i(TAG, "Auto-update routing is disabled, skipping")
                    return@withContext Result.success()
                }
                
                // Initialize routing manager
                val routingManager = RoutingManager(context)
                
                // Check if routing files need updates
                val hasUpdates = routingManager.checkForRoutingUpdates()
                if (!hasUpdates) {
                    Log.i(TAG, "Routing files are already up to date")
                    return@withContext Result.success()
                }
                
                // Update routing files from repository
                Log.i(TAG, "Updating routing files from Chocolate4U/Iran-v2ray-rules")
                val success = routingManager.updateRoutingFiles()
                
                if (success) {
                    Log.i(TAG, "Routing files updated successfully")
                    
                    // Apply new routing rules if a connection is active
                    val xrayManager = XrayManager.getInstance(context)
                    if (xrayManager.isServiceRunning()) {
                        Log.i(TAG, "Applying new routing rules to active connection")
                        xrayManager.updateRoutingRules()
                    }
                    
                    return@withContext Result.success()
                } else {
                    Log.e(TAG, "Failed to update routing files")
                    return@withContext Result.retry()
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during routing update worker execution", e)
                return@withContext Result.failure()
            }
        }
    }
}