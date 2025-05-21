package com.hiddify.hiddifyng.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.utils.SubscriptionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker for updating subscription links
 */
class SubscriptionWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    // Store params for later use
    private val workerParams = params
    private val TAG = "SubscriptionWorker"
    
    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Starting subscription update")
                
                // Get app settings
                val database = AppDatabase.getInstance(context)
                val settings = database.appSettingsDao().getSettings()
                
                if (settings == null) {
                    Log.e(TAG, "App settings not found")
                    return@withContext Result.failure()
                }
                
                // Check if auto-update subscriptions is enabled
                if (!settings.autoUpdateSubscriptions) {
                    Log.i(TAG, "Auto-update subscriptions is disabled, skipping")
                    return@withContext Result.success()
                }
                
                // Update all subscriptions
                val subscriptionManager = SubscriptionManager(context)
                val results = subscriptionManager.updateAllSubscriptions()
                
                // Log results
                val successCount = results.values.count { it }
                val failureCount = results.values.count { !it }
                
                Log.i(TAG, "Subscription update completed: $successCount succeeded, $failureCount failed")
                
                // Run ping test after subscription update if auto-switch is enabled
                if (settings.autoSwitchToBestServer && successCount > 0) {
                    Log.i(TAG, "Starting ping test after subscription update")
                    
                    try {
                        val pingWorker = PingWorker(context, workerParams)
                        pingWorker.doWork()
                        Log.i(TAG, "Ping test completed after subscription update")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error running ping test after subscription update", e)
                    }
                }
                
                return@withContext Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Error during subscription worker execution", e)
                return@withContext Result.failure()
            }
        }
    }
}