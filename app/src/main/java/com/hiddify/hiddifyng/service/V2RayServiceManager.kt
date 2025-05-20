package com.hiddify.hiddifyng.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.*
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.hiddify.hiddifyng.R
import com.hiddify.hiddifyng.activity.MainActivity
import com.hiddify.hiddifyng.core.XrayManager
import com.hiddify.hiddifyng.utils.PingUtils
import com.hiddify.hiddifyng.utils.RoutingManager
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import android.util.Log
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.database.entity.Server

class V2RayServiceManager : VpnService() {
    private val TAG = "V2RayServiceManager"
    private val NOTIFICATION_ID = 1
    private val NOTIFICATION_CHANNEL_ID = "hiddify_channel"
    
    private lateinit var xrayManager: XrayManager
    private lateinit var routingManager: RoutingManager
    private lateinit var pingUtils: PingUtils
    private lateinit var database: AppDatabase
    
    private var serviceScope = CoroutineScope(Dispatchers.IO)
    private var isRunning = false
    private var currentServerId: Long = -1
    
    companion object {
        private const val VPN_MTU = 1500
        private const val PRIVATE_VLAN4_CLIENT = "172.19.0.1"
        private const val PRIVATE_VLAN4_ROUTER = "172.19.0.2"
        private const val PRIVATE_VLAN6_CLIENT = "fdfe:dcba:9876::1"
        private const val PRIVATE_VLAN6_ROUTER = "fdfe:dcba:9876::2"
        
        fun startService(context: Context, serverId: Long) {
            val intent = Intent(context, V2RayServiceManager::class.java)
            intent.putExtra("serverId", serverId)
            context.startService(intent)
        }
        
        fun stopService(context: Context) {
            context.stopService(Intent(context, V2RayServiceManager::class.java))
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service onCreate")
        
        xrayManager = XrayManager(applicationContext)
        routingManager = RoutingManager(applicationContext)
        pingUtils = PingUtils(applicationContext)
        database = AppDatabase.getInstance(applicationContext)
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Service onStartCommand")
        
        if (intent == null) {
            return START_NOT_STICKY
        }
        
        // Get server ID from intent
        val serverId = intent.getLongExtra("serverId", -1)
        if (serverId == -1L) {
            // If no server ID is provided, try to find the one with lowest ping
            serviceScope.launch {
                val bestServer = findBestServer()
                if (bestServer != null) {
                    startVpnService(bestServer.id)
                } else {
                    Log.e(TAG, "No server available to connect")
                    stopSelf()
                }
            }
        } else {
            startVpnService(serverId)
        }
        
        return START_STICKY
    }
    
    private fun startVpnService(serverId: Long) {
        if (isRunning && currentServerId == serverId) {
            Log.i(TAG, "Already running with the same server")
            return
        }
        
        serviceScope.launch {
            try {
                // Get server configuration
                val server = database.serverDao().getServerById(serverId)
                if (server == null) {
                    Log.e(TAG, "Server not found: $serverId")
                    stopSelf()
                    return@launch
                }
                
                currentServerId = serverId
                
                // Prepare VPN interface
                val builder = prepare(this@V2RayServiceManager)
                if (builder == null) {
                    // Configure VPN interface
                    val vpnInterface = configureVpnInterface(server)
                    
                    // Start Xray core with the server configuration
                    val success = xrayManager.startXray(server, vpnInterface.fd)
                    
                    if (success) {
                        isRunning = true
                        startForeground(NOTIFICATION_ID, createNotification(server.name))
                        
                        // Start ping monitoring in the background
                        startPingMonitoring()
                    } else {
                        Log.e(TAG, "Failed to start Xray core")
                        vpnInterface.close()
                        stopSelf()
                    }
                } else {
                    Log.e(TAG, "VPN permission not granted")
                    stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting VPN service", e)
                stopSelf()
            }
        }
    }
    
    private fun configureVpnInterface(server: Server): ParcelFileDescriptor {
        val builder = Builder()
            .setMtu(VPN_MTU)
            .addAddress(PRIVATE_VLAN4_CLIENT, 24)
            .addAddress(PRIVATE_VLAN6_CLIENT, 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            .setSession("HiddifyNG")
            
        // Apply routing rules from RoutingManager
        routingManager.applyRoutingRules(builder, server)
        
        // Set DNS servers based on server configuration
        server.dnsServers?.split(",")?.forEach {
            if (it.trim().isNotEmpty()) {
                builder.addDnsServer(it.trim())
            }
        }
        
        // Allow VPN to bypass selected apps if configured
        server.bypassPackages?.split(",")?.forEach {
            if (it.trim().isNotEmpty()) {
                builder.addDisallowedApplication(it.trim())
            }
        }
        
        return builder.establish()!!
    }
    
    private fun startPingMonitoring() {
        serviceScope.launch {
            while (isRunning) {
                try {
                    // Ping current and other servers
                    pingAllServers()
                    
                    // If auto-connection to best server is enabled, switch if needed
                    val appSettings = database.appSettingsDao().getSettings()
                    if (appSettings.autoSwitchToBestServer) {
                        val bestServer = findBestServer()
                        if (bestServer != null && bestServer.id != currentServerId && 
                            bestServer.ping < appSettings.minPingThreshold) {
                            Log.i(TAG, "Switching to better server: ${bestServer.name} with ping ${bestServer.ping}")
                            switchServer(bestServer.id)
                        }
                    }
                    
                    delay(TimeUnit.MINUTES.toMillis(5))  // Check every 5 minutes
                } catch (e: Exception) {
                    Log.e(TAG, "Error in ping monitoring", e)
                    delay(TimeUnit.MINUTES.toMillis(5))
                }
            }
        }
    }
    
    private suspend fun pingAllServers() {
        val servers = database.serverDao().getAllServers()
        servers.forEach { server ->
            val ping = pingUtils.measurePing(server.address)
            server.ping = ping
            database.serverDao().updateServer(server)
        }
    }
    
    private suspend fun findBestServer(): Server? {
        return database.serverDao().getServersOrderedByPing().firstOrNull()
    }
    
    private fun switchServer(serverId: Long) {
        // Save current state if needed
        xrayManager.stopXray()
        isRunning = false
        
        // Start with new server
        startVpnService(serverId)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Service onDestroy")
        
        isRunning = false
        serviceScope.cancel()
        xrayManager.stopXray()
        stopForeground(true)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "HiddifyNG VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Shows HiddifyNG VPN status"
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(serverName: String): Notification {
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
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("HiddifyNG VPN Running")
            .setContentText("Connected to $serverName")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }
}
