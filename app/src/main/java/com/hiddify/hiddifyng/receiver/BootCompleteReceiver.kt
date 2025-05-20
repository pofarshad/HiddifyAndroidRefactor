package com.hiddify.hiddifyng.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.service.V2RayServiceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Broadcast receiver that handles device boot completion events
 * Used to auto-start the VPN service based on user settings
 */
class BootCompleteReceiver : BroadcastReceiver() {
    private val TAG = "BootCompleteReceiver"
    private val scope = CoroutineScope(Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed, checking auto-start setting")
            
            scope.launch {
                try {
                    // Get app settings from database
                    val database = AppDatabase.getInstance(context)
                    val settings = database.appSettingsDao().getSettings()
                    
                    // Check if auto-start is enabled
                    if (settings?.autoStart == true) {
                        Log.i(TAG, "Auto-start enabled, starting VPN service")
                        
                        // Determine server to use
                        val serverId = if (settings.autoConnect) {
                            // Use preferred server if set, otherwise best ping server
                            settings.preferredServerId ?: findBestServerId(database)
                        } else {
                            settings.preferredServerId
                        }
                        
                        if (serverId != null) {
                            // Start VPN service
                            Log.i(TAG, "Starting VPN service with server ID: $serverId")
                            V2RayServiceManager.startService(context, serverId)
                        } else {
                            Log.w(TAG, "No server available for auto-start")
                        }
                    } else {
                        Log.i(TAG, "Auto-start disabled, not starting VPN service")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in boot receiver", e)
                }
            }
        }
    }
    
    /**
     * Find the best server by ping time
     */
    private suspend fun findBestServerId(database: AppDatabase): Long? {
        return try {
            val bestServer = database.serverDao().getBestServerByPing()
            bestServer?.id
        } catch (e: Exception) {
            Log.e(TAG, "Error finding best server", e)
            null
        }
    }
}
