package com.hiddify.hiddifyng.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hiddify.hiddifyng.activity.MainActivity
import com.hiddify.hiddifyng.core.XrayManager
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.database.entity.Server
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.NetworkInterface
import java.util.*

/**
 * Service for managing V2Ray/Xray VPN connection
 */
class V2RayServiceManager : Service() {
    private val TAG = "V2RayServiceManager"
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    
    // Xray manager instance
    private lateinit var xrayManager: XrayManager
    
    // Service status
    private var isRunning = false
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "hiddify_vpn_service"
        
        // Intent actions
        const val ACTION_START = "com.hiddify.hiddifyng.service.START"
        const val ACTION_STOP = "com.hiddify.hiddifyng.service.STOP"
        const val ACTION_RESTART = "com.hiddify.hiddifyng.service.RESTART"
        
        // Intent extras
        const val EXTRA_SERVER_ID = "server_id"
        
        /**
         * Start the service
         * @param context Application context
         * @param serverId Server ID to connect
         */
        fun startService(context: Context, serverId: Long) {
            val intent = Intent(context, V2RayServiceManager::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SERVER_ID, serverId)
            }
            
            // For Android 8+ we need to start as foreground service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            
            Log.i("V2RayServiceManager", "Starting service for server ID: $serverId")
        }
        
        /**
         * Stop the service
         * @param context Application context
         */
        fun stopService(context: Context) {
            val intent = Intent(context, V2RayServiceManager::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
            
            Log.i("V2RayServiceManager", "Stopping service")
        }
        
        /**
         * Restart the service with a new server
         * @param context Application context
         * @param serverId Server ID to connect
         */
        fun restartService(context: Context, serverId: Long) {
            val intent = Intent(context, V2RayServiceManager::class.java).apply {
                action = ACTION_RESTART
                putExtra(EXTRA_SERVER_ID, serverId)
            }
            context.startService(intent)
            
            Log.i("V2RayServiceManager", "Restarting service for server ID: $serverId")
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Xray manager
        xrayManager = XrayManager(this)
        
        // Create notification channel for Android 8+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Hiddify VPN Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the VPN service running"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // Start as foreground service with notification
        startForeground(NOTIFICATION_ID, createNotification("Initializing..."))
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val serverId = intent.getLongExtra(EXTRA_SERVER_ID, -1)
                if (serverId != -1L) {
                    startV2Ray(serverId)
                } else {
                    Log.e(TAG, "Invalid server ID")
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopV2Ray()
                stopSelf()
            }
            ACTION_RESTART -> {
                val serverId = intent.getLongExtra(EXTRA_SERVER_ID, -1)
                if (serverId != -1L) {
                    restartV2Ray(serverId)
                } else {
                    Log.e(TAG, "Invalid server ID for restart")
                    stopSelf()
                }
            }
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Make sure to stop Xray when service is destroyed
        if (isRunning) {
            serviceScope.launch {
                xrayManager.stopXray()
            }
        }
        
        isRunning = false
        Log.i(TAG, "Service destroyed")
    }
    
    /**
     * Start V2Ray with the given server ID
     * @param serverId Server ID to connect
     */
    private fun startV2Ray(serverId: Long) {
        serviceScope.launch {
            try {
                // Get server from database
                val database = AppDatabase.getInstance(this@V2RayServiceManager)
                val server = database.serverDao().getServerByIdSync(serverId)
                
                if (server == null) {
                    Log.e(TAG, "Server not found: $serverId")
                    stopSelf()
                    return@launch
                }
                
                // Update notification with server info
                updateNotification("Connecting to ${server.name}...")
                
                // Start Xray
                val success = xrayManager.startXray(server)
                
                if (success) {
                    isRunning = true
                    
                    // Update server as last connected in settings
                    database.appSettingsDao().setPreferredServerId(serverId)
                    
                    // Update notification with success status
                    updateNotification("Connected to ${server.name}")
                    
                    // Log connection details
                    logConnectionDetails(server)
                } else {
                    Log.e(TAG, "Failed to start Xray for server: ${server.name}")
                    updateNotification("Failed to connect")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting V2Ray", e)
                updateNotification("Error connecting")
                stopSelf()
            }
        }
    }
    
    /**
     * Stop V2Ray connection
     */
    private fun stopV2Ray() {
        serviceScope.launch {
            try {
                updateNotification("Disconnecting...")
                
                // Stop Xray
                val success = xrayManager.stopXray()
                
                if (success) {
                    isRunning = false
                    updateNotification("Disconnected")
                    Log.i(TAG, "Xray stopped successfully")
                } else {
                    Log.e(TAG, "Failed to stop Xray")
                    updateNotification("Failed to disconnect")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping V2Ray", e)
                updateNotification("Error disconnecting")
            }
        }
    }
    
    /**
     * Restart V2Ray with a new server
     * @param serverId Server ID to connect
     */
    private fun restartV2Ray(serverId: Long) {
        serviceScope.launch {
            try {
                // Stop current connection
                if (isRunning) {
                    updateNotification("Reconnecting...")
                    xrayManager.stopXray()
                }
                
                // Start with new server
                startV2Ray(serverId)
            } catch (e: Exception) {
                Log.e(TAG, "Error restarting V2Ray", e)
                updateNotification("Error reconnecting")
                stopSelf()
            }
        }
    }
    
    /**
     * Create notification for the foreground service
     * @param status Current status text
     * @return Notification
     */
    private fun createNotification(status: String): android.app.Notification {
        // Create intent for tapping the notification
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )
        
        // Build notification
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Hiddify VPN")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * Update notification with new status
     * @param status New status text
     */
    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Log connection details for debugging
     * @param server Connected server
     */
    private fun logConnectionDetails(server: Server) {
        try {
            Log.i(TAG, "Connected to server: ${server.name}")
            Log.i(TAG, "Protocol: ${server.protocol}")
            Log.i(TAG, "Address: ${server.address}:${server.port}")
            
            // Log local network interfaces
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            while (networkInterfaces.hasMoreElements()) {
                val networkInterface = networkInterfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress) {
                        Log.i(TAG, "Network interface: ${networkInterface.name}, Address: ${address.hostAddress}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error logging connection details", e)
        }
    }
}