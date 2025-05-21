package com.hiddify.hiddifyng

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.utils.AutoUpdateManager
import com.hiddify.hiddifyng.utils.RoutingManager
import com.hiddify.hiddifyng.worker.PingWorker
import com.hiddify.hiddifyng.worker.SubscriptionWorker
import com.hiddify.hiddifyng.worker.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Application class for MarFaNet Co.
 * Handles initialization of components and background workers
 */
class HiddifyNGApplication : Application() {
    private val TAG = "MarFaNetApplication"
    private val appScope = CoroutineScope(Dispatchers.Default)
    
    companion object {
        // Constants for worker intervals
        private const val SUBSCRIPTION_UPDATE_INTERVAL_MINUTES = 30L
        private const val DEFAULT_PING_INTERVAL_MINUTES = 10L
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing MarFaNet Co. application")
        
        // Initialize database
        AppDatabase.getInstance(this)
        
        // Initialize update manager
        initializeAutoUpdateManager()
        
        // Initialize routing files
        initializeRoutingFiles()
        
        // Schedule background workers
        scheduleBackgroundWorkers()
    }
    
    /**
     * Initialize auto-update manager and check current settings
     */
    private fun initializeAutoUpdateManager() {
        appScope.launch {
            try {
                // Get app settings from database
                val database = AppDatabase.getInstance(this@HiddifyNGApplication)
                val settings = database.appSettingsDao().getSettings()
                
                // Configure auto-update manager
                val updateManager = AutoUpdateManager(this@HiddifyNGApplication)
                
                if (settings?.autoUpdateEnabled == true) {
                    val frequency = settings.updateFrequencyHours
                    Log.i(TAG, "Scheduling auto-updates every $frequency hours")
                    updateManager.setAutoUpdateEnabled(true)
                    updateManager.scheduleUpdateChecks(frequency)
                } else {
                    Log.i(TAG, "Auto-updates disabled")
                    updateManager.setAutoUpdateEnabled(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing auto-update manager", e)
            }
        }
    }
    
    /**
     * Initialize routing files
     */
    private fun initializeRoutingFiles() {
        appScope.launch {
            try {
                val routingManager = RoutingManager(this@HiddifyNGApplication)
                // Check if routing files need to be updated first time
                val hasUpdates = routingManager.checkForRoutingUpdates()
                
                // Update routing files if needed or initialize them
                if (hasUpdates) {
                    val success = routingManager.updateRoutingFiles()
                    Log.i(TAG, "Initial routing files update result: $success")
                } else {
                    Log.i(TAG, "Routing files are up to date")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing routing files", e)
            }
        }
    }
    
    /**
     * Schedule background workers for ping testing, subscription updates, and app updates
     */
    private fun scheduleBackgroundWorkers() {
        appScope.launch {
            try {
                // Get app settings
                val database = AppDatabase.getInstance(this@HiddifyNGApplication)
                val settings = database.appSettingsDao().getSettings() ?: return@launch
                
                // Configure WorkManager constraints
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
                
                // Schedule ping worker (every 10 minutes as requested)
                val pingFrequency = settings.pingFrequencyMinutes.takeIf { 
                    it > 0 
                } ?: DEFAULT_PING_INTERVAL_MINUTES
                
                Log.i(TAG, "Scheduling ping worker every $pingFrequency minutes")
                
                val pingRequest = PeriodicWorkRequestBuilder<PingWorker>(
                    pingFrequency.toLong(), TimeUnit.MINUTES,
                    (pingFrequency / 2).toLong(), TimeUnit.MINUTES // Flex period
                )
                    .setConstraints(constraints)
                    .build()
                
                WorkManager.getInstance(this@HiddifyNGApplication)
                    .enqueueUniquePeriodicWork(
                        "marfanet_ping_worker",
                        ExistingPeriodicWorkPolicy.REPLACE,
                        pingRequest
                    )
                
                // Schedule subscription update worker (every 30 minutes as requested)
                if (settings.autoUpdateSubscriptions) {
                    Log.i(TAG, "Scheduling subscription update worker every $SUBSCRIPTION_UPDATE_INTERVAL_MINUTES minutes")
                    
                    val subscriptionRequest = PeriodicWorkRequestBuilder<SubscriptionWorker>(
                        SUBSCRIPTION_UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES,
                        SUBSCRIPTION_UPDATE_INTERVAL_MINUTES / 5, TimeUnit.MINUTES // Flex period
                    )
                        .setConstraints(constraints)
                        .build()
                    
                    WorkManager.getInstance(this@HiddifyNGApplication)
                        .enqueueUniquePeriodicWork(
                            "marfanet_subscription_worker",
                            ExistingPeriodicWorkPolicy.REPLACE,
                            subscriptionRequest
                        )
                }
                
                // Schedule app update worker if auto-update is enabled
                if (settings.autoUpdateEnabled) {
                    val updateFrequency = settings.updateFrequencyHours.toLong()
                    Log.i(TAG, "Scheduling app update worker every $updateFrequency hours")
                    
                    val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
                        updateFrequency, TimeUnit.HOURS,
                        updateFrequency / 2, TimeUnit.HOURS // Flex period
                    )
                        .setConstraints(constraints)
                        .build()
                    
                    WorkManager.getInstance(this@HiddifyNGApplication)
                        .enqueueUniquePeriodicWork(
                            "marfanet_update_worker",
                            ExistingPeriodicWorkPolicy.REPLACE,
                            updateRequest
                        )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error scheduling background workers", e)
            }
        }
    }
}