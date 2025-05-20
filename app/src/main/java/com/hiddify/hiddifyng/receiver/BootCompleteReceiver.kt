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
 * Broadcast receiver for handling device boot complete event
 * Used to auto-start the VPN service on device boot
 */
class BootCompleteReceiver : BroadcastReceiver() {
    private val TAG = "BootCompleteReceiver"
    private val receiverScope = CoroutineScope(Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed, checking auto-start settings")
            
            // Check if auto-start is enabled in settings
            receiverScope.launch {
                try {
                    val database = AppDatabase.getInstance(context)
                    val settings = database.appSettingsDao().getSettings()
                    
                    if (settings?.autoStart == true) {
                        Log.i(TAG, "Auto-start is enabled")
                        
                        // Check if auto-connect is enabled
                        if (settings.autoConnect) {
                            Log.i(TAG, "Auto-connect is enabled")
                            
                            // Get preferred server ID
                            val preferredServerId = settings.preferredServerId
                            
                            if (preferredServerId != null) {
                                // Start service with preferred server
                                Log.i(TAG, "Starting VPN service with server ID: $preferredServerId")
                                V2RayServiceManager.startService(context, preferredServerId)
                            } else {
                                // No preferred server, check if auto-switch is enabled
                                if (settings.autoSwitchToBestServer) {
                                    Log.i(TAG, "No preferred server, but auto-switch is enabled")
                                    
                                    // Get server with lowest ping
                                    val bestServer = database.serverDao().getServerWithLowestPing()
                                    
                                    if (bestServer != null) {
                                        // Start service with best server
                                        Log.i(TAG, "Starting VPN service with best server: ${bestServer.name}")
                                        V2RayServiceManager.startService(context, bestServer.id)
                                    } else {
                                        Log.i(TAG, "No server with valid ping found")
                                    }
                                } else {
                                    Log.i(TAG, "No preferred server and auto-switch is disabled")
                                }
                            }
                        } else {
                            Log.i(TAG, "Auto-connect is disabled, not starting VPN service")
                        }
                    } else {
                        Log.i(TAG, "Auto-start is disabled, not starting VPN service")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling boot complete", e)
                }
            }
        }
    }
}