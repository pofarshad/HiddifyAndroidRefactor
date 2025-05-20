package com.hiddify.hiddifyng

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.work.*
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.utils.AutoUpdateManager
import com.hiddify.hiddifyng.utils.RoutingManager
import com.hiddify.hiddifyng.worker.PingWorker
import com.hiddify.hiddifyng.worker.UpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Application class for HiddifyNG
 * Handles initialization of components and background workers
 */
class HiddifyNGApplication : Application() {
    private val TAG = "HiddifyNGApplication"
    private val appScope = CoroutineScope(Dispatchers.Default)
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing HiddifyNG application")
        
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
                val initialized = routingManager.initializeRoutingFiles()
                
                if (initialized) {
                    Log.i(TAG, "Routing files initialized successfully")
                } else {
                    Log.e(TAG, "Failed to initialize routing files")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing routing files", e)
            }
        }
    }
    
    /**
     * Schedule background workers for ping testing and updates
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
                
                // Schedule ping worker if auto-switch is enabled
                if (settings.autoSwitchToBestServer) {
                    val pingFrequency = settings.pingFrequencyMinutes.toLong()
                    Log.i(TAG, "Scheduling ping worker every $pingFrequency minutes")
                    
                    val pingRequest = PeriodicWorkRequestBuilder<PingWorker>(
                        pingFrequency, TimeUnit.MINUTES,
                        pingFrequency / 2, TimeUnit.MINUTES // Flex period
                    )
                        .setConstraints(constraints)
                        .build()
                    
                    WorkManager.getInstance(this@HiddifyNGApplication)
                        .enqueueUniquePeriodicWork(
                            "hiddify_ping_worker",
                            ExistingPeriodicWorkPolicy.REPLACE,
                            pingRequest
                        )
                }
                
                // Schedule update worker if auto-update is enabled
                if (settings.autoUpdateEnabled) {
                    val updateFrequency = settings.updateFrequencyHours.toLong()
                    Log.i(TAG, "Scheduling update worker every $updateFrequency hours")
                    
                    val updateRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
                        updateFrequency, TimeUnit.HOURS,
                        updateFrequency / 2, TimeUnit.HOURS // Flex period
                    )
                        .setConstraints(constraints)
                        .build()
                    
                    WorkManager.getInstance(this@HiddifyNGApplication)
                        .enqueueUniquePeriodicWork(
                            "hiddify_update_worker",
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