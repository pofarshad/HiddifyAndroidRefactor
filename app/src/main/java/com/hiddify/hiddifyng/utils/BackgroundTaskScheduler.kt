package com.hiddify.hiddifyng.utils

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hiddify.hiddifyng.workers.PingWorker
import com.hiddify.hiddifyng.workers.RoutingUpdateWorker
import com.hiddify.hiddifyng.workers.SubscriptionWorker
import java.util.concurrent.TimeUnit

/**
 * Smart background task scheduler for optimizing battery usage
 * Intelligently manages WorkManager tasks based on device state and priority
 */
class BackgroundTaskScheduler(private val context: Context) {
    companion object {
        private const val TAG = "BackgroundTaskScheduler"
        
        // Task names for unique identification
        const val PING_TASK_NAME = "ping_servers_task"
        const val SUBSCRIPTION_UPDATE_TASK_NAME = "subscription_update_task"
        const val ROUTING_UPDATE_TASK_NAME = "routing_update_task"
        
        // Optimal intervals (in minutes) - configurable
        const val PING_INTERVAL_MINUTES = 10
        const val SUBSCRIPTION_UPDATE_INTERVAL_MINUTES = 30
        const val ROUTING_UPDATE_INTERVAL_HOURS = 24
    }
    
    private val workManager = WorkManager.getInstance(context)
    
    /**
     * Schedule all required background tasks with optimal settings
     */
    fun scheduleAllTasks(
        pingEnabled: Boolean = true,
        subscriptionUpdateEnabled: Boolean = true,
        routingUpdateEnabled: Boolean = true
    ) {
        Log.d(TAG, "Scheduling background tasks")
        
        if (pingEnabled) {
            schedulePingTask()
        }
        
        if (subscriptionUpdateEnabled) {
            scheduleSubscriptionUpdateTask()
        }
        
        if (routingUpdateEnabled) {
            scheduleRoutingUpdateTask()
        }
    }
    
    /**
     * Schedule server ping task - runs every 10 minutes
     * Helps identify the fastest server for auto-switching
     */
    fun schedulePingTask() {
        Log.d(TAG, "Scheduling ping task to run every $PING_INTERVAL_MINUTES minutes")
        
        // Basic constraints - network connection required
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Create the periodic work request
        val pingRequest = PeriodicWorkRequestBuilder<PingWorker>(
            PING_INTERVAL_MINUTES.toLong(), TimeUnit.MINUTES,
            (PING_INTERVAL_MINUTES / 2).toLong(), TimeUnit.MINUTES // Flex period
        )
            .setConstraints(constraints)
            .build()
        
        // Enqueue the work request, replacing any existing one
        workManager.enqueueUniquePeriodicWork(
            PING_TASK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            pingRequest
        )
    }
    
    /**
     * Schedule subscription update task - runs every 30 minutes
     * Updates server lists from subscription URLs
     */
    fun scheduleSubscriptionUpdateTask() {
        Log.d(TAG, "Scheduling subscription update task to run every $SUBSCRIPTION_UPDATE_INTERVAL_MINUTES minutes")
        
        // Network constraints with timeout and retry policy
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Create the periodic work request
        val subscriptionRequest = PeriodicWorkRequestBuilder<SubscriptionWorker>(
            SUBSCRIPTION_UPDATE_INTERVAL_MINUTES.toLong(), TimeUnit.MINUTES,
            (SUBSCRIPTION_UPDATE_INTERVAL_MINUTES / 5).toLong(), TimeUnit.MINUTES // Flex period
        )
            .setConstraints(constraints)
            .build()
        
        // Enqueue the work request
        workManager.enqueueUniquePeriodicWork(
            SUBSCRIPTION_UPDATE_TASK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            subscriptionRequest
        )
    }
    
    /**
     * Schedule routing rules update task - runs once per day
     * Updates the routing rules from Chocolate4U/Iran-v2ray-rules repository
     */
    fun scheduleRoutingUpdateTask() {
        Log.d(TAG, "Scheduling routing update task to run every $ROUTING_UPDATE_INTERVAL_HOURS hours")
        
        // Require unmetered network (Wi-Fi) to save user's data
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        
        // Create the periodic work request
        val routingRequest = PeriodicWorkRequestBuilder<RoutingUpdateWorker>(
            ROUTING_UPDATE_INTERVAL_HOURS.toLong(), TimeUnit.HOURS,
            (ROUTING_UPDATE_INTERVAL_HOURS / 4).toLong(), TimeUnit.HOURS // Flex period
        )
            .setConstraints(constraints)
            .build()
        
        // Enqueue the work request
        workManager.enqueueUniquePeriodicWork(
            ROUTING_UPDATE_TASK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            routingRequest
        )
    }
    
    /**
     * Cancel all scheduled tasks
     */
    fun cancelAllTasks() {
        Log.d(TAG, "Cancelling all scheduled background tasks")
        workManager.cancelAllWork()
    }
    
    /**
     * Cancel a specific task by name
     */
    fun cancelTask(taskName: String) {
        Log.d(TAG, "Cancelling task: $taskName")
        workManager.cancelUniqueWork(taskName)
    }
}