package com.hiddify.hiddifyng

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hiddify.hiddifyng.utils.UpdateManager
import com.hiddify.hiddifyng.worker.PingWorker
import com.hiddify.hiddifyng.worker.SubscriptionWorker
import com.hiddify.hiddifyng.worker.UpdateWorker
import java.util.concurrent.TimeUnit

/**
 * Application class for MarFaNet
 * Handles initialization of background workers and app-wide services
 */
class HiddifyNGApplication : Application() {
    companion object {
        private const val TAG = "HiddifyNGApplication"
        
        // Worker intervals
        const val PING_INTERVAL_MINUTES = 10L
        const val SUBSCRIPTION_UPDATE_INTERVAL_MINUTES = 30L
        const val UPDATE_CHECK_INTERVAL_HOURS = 12L
        
        // Singleton instance
        private var instance: HiddifyNGApplication? = null
        
        /**
         * Get application instance
         * @return Application instance
         */
        fun getInstance(): HiddifyNGApplication = instance!!
        
        /**
         * Get application context
         * @return Application context
         */
        fun getAppContext(): Context = instance!!.applicationContext
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize background workers
        initializeWorkers()
        
        Log.i(TAG, "MarFaNet application initialized")
    }
    
    /**
     * Initialize background workers
     */
    private fun initializeWorkers() {
        Log.i(TAG, "Initializing background workers")
        
        // Work constraints (require network connection)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        
        // Initialize WorkManager
        val workManager = WorkManager.getInstance(this)
        
        // Schedule ping worker (every 10 minutes as requested)
        val pingRequest = PeriodicWorkRequestBuilder<PingWorker>(
            PING_INTERVAL_MINUTES, TimeUnit.MINUTES,
            (PING_INTERVAL_MINUTES / 5), TimeUnit.MINUTES // Flex period
        )
            .setConstraints(constraints)
            .addTag("ping_worker")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "ping_worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            pingRequest
        )
        
        // Schedule subscription update worker (every 30 minutes as requested)
        val updateManager = UpdateManager(this)
        if (updateManager.autoUpdateSubscriptions) {
            Log.i(TAG, "Scheduling subscription update worker every $SUBSCRIPTION_UPDATE_INTERVAL_MINUTES minutes")
            
            val subscriptionRequest = PeriodicWorkRequestBuilder<SubscriptionWorker>(
                SUBSCRIPTION_UPDATE_INTERVAL_MINUTES, TimeUnit.MINUTES,
                (SUBSCRIPTION_UPDATE_INTERVAL_MINUTES / 5), TimeUnit.MINUTES // Flex period
            )
                .setConstraints(constraints)
                .addTag("subscription_worker")
                .build()
            
            workManager.enqueueUniquePeriodicWork(
                "subscription_worker",
                ExistingPeriodicWorkPolicy.UPDATE,
                subscriptionRequest
            )
        }
        
        // Schedule update check worker (every 12 hours)
        val updateCheckRequest = PeriodicWorkRequestBuilder<UpdateWorker>(
            UPDATE_CHECK_INTERVAL_HOURS, TimeUnit.HOURS,
            (UPDATE_CHECK_INTERVAL_HOURS / 4), TimeUnit.HOURS // Flex period
        )
            .setConstraints(constraints)
            .addTag("update_worker")
            .build()
        
        workManager.enqueueUniquePeriodicWork(
            "update_worker",
            ExistingPeriodicWorkPolicy.UPDATE,
            updateCheckRequest
        )
    }
}