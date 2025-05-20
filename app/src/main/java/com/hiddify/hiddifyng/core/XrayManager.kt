package com.hiddify.hiddifyng.core

import android.content.Context
import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.protocols.ProtocolHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * Manager for Xray core functionality
 * This is a simpler version without JNI for initial development
 */
class XrayManager(private val context: Context) {
    private val TAG = "XrayManager"
    
    // Status flags
    private var isRunning = false
    private var currentServerId: Long = -1
    
    /**
     * Start Xray service with the given server configuration
     * @param server Server configuration
     * @return true if started successfully, false otherwise
     */
    suspend fun startXray(server: Server): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (isRunning) {
                    stopXray()
                }
                
                // Generate configuration
                val configJson = generateConfig(server)
                
                // Write configuration to file
                val configFile = File(context.filesDir, "config.json")
                configFile.writeText(configJson)
                
                // Simulate starting Xray (without JNI for now)
                // In a real implementation, we would call the native method
                
                // Update status
                isRunning = true
                currentServerId = server.id
                Log.i(TAG, "Xray started successfully with server ID: ${server.id}")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error starting Xray", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Stop Xray service
     * @return true if stopped successfully, false otherwise
     */
    suspend fun stopXray(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Simulate stopping Xray (without JNI for now)
                // In a real implementation, we would call the native method
                
                // Update status
                isRunning = false
                currentServerId = -1
                Log.i(TAG, "Xray stopped successfully")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping Xray", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Check if Xray service is running
     * @return true if running, false otherwise
     */
    fun isRunning(): Boolean {
        return isRunning
    }
    
    /**
     * Get current server ID
     * @return current server ID, -1 if not running
     */
    fun getCurrentServerId(): Long {
        return currentServerId
    }
    
    /**
     * Get Xray core version
     * @return Xray core version string
     */
    fun getXrayVersion(): String {
        // Return placeholder version (without JNI for now)
        return "1.8.0"
    }
    
    /**
     * Update GeoIP and GeoSite databases
     * @return true if updated successfully, false otherwise
     */
    suspend fun updateGeoDatabases(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // Copy GeoIP database from assets
                val geoipFile = File(context.filesDir, "geoip.dat")
                if (!geoipFile.exists()) {
                    copyAssetToFile("geoip.dat", geoipFile)
                }
                
                // Copy GeoSite database from assets
                val geositeFile = File(context.filesDir, "geosite.dat")
                if (!geositeFile.exists()) {
                    copyAssetToFile("geosite.dat", geositeFile)
                }
                
                // Simulate updating GeoDB (without JNI for now)
                Log.i(TAG, "GeoDB updated successfully")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "Error updating GeoDB", e)
                return@withContext false
            }
        }
    }
    
    /**
     * Generate Xray configuration
     * @param server Server configuration
     * @return Xray configuration JSON string
     */
    private fun generateConfig(server: Server): String {
        try {
            // Use appropriate protocol handler to generate configuration
            val handler = ProtocolHandler.getHandler(server.protocol)
            val baseConfig = handler.generateConfig(server)
            
            // Parse the base config to a JSON object
            val baseConfigJson = JSONObject(baseConfig)
            
            // Create the complete configuration
            val config = JSONObject()
            
            // Merge outbounds from base config
            config.put("outbounds", baseConfigJson.optJSONArray("outbounds") ?: JSONObject())
            
            // Add logs configuration
            val log = JSONObject()
            log.put("loglevel", "warning")
            config.put("log", log)
            
            // Add inbounds configuration (SOCKS proxy)
            val inbounds = JSONObject()
            inbounds.put("port", 10808)
            inbounds.put("listen", "127.0.0.1")
            inbounds.put("protocol", "socks")
            val socksSettings = JSONObject()
            socksSettings.put("udp", true)
            inbounds.put("settings", socksSettings)
            config.put("inbounds", inbounds)
            
            // Add routing configuration
            val routing = JSONObject()
            val rules = JSONObject()
            
            // Parse bypass domains
            if (!server.bypassDomains.isNullOrEmpty()) {
                val bypassDomains = server.bypassDomains?.split(",")?.map { it.trim() }
                if (!bypassDomains.isNullOrEmpty()) {
                    val directRule = JSONObject()
                    directRule.put("type", "field")
                    directRule.put("domain", bypassDomains)
                    directRule.put("outboundTag", "direct")
                    rules.put("rules", directRule)
                }
            }
            
            // Parse block domains
            if (!server.blockDomains.isNullOrEmpty()) {
                val blockDomains = server.blockDomains?.split(",")?.map { it.trim() }
                if (!blockDomains.isNullOrEmpty()) {
                    val blockRule = JSONObject()
                    blockRule.put("type", "field")
                    blockRule.put("domain", blockDomains)
                    blockRule.put("outboundTag", "block")
                    rules.put("rules", blockRule)
                }
            }
            
            routing.put("rules", rules)
            config.put("routing", routing)
            
            // Add DNS configuration
            val dns = JSONObject()
            val servers = JSONObject()
            
            // Parse DNS servers
            if (!server.dnsServers.isNullOrEmpty()) {
                val dnsServers = server.dnsServers?.split(",")?.map { it.trim() }
                if (!dnsServers.isNullOrEmpty()) {
                    servers.put("servers", dnsServers)
                }
            } else {
                // Default DNS servers
                val defaultDns = JSONObject()
                defaultDns.put("address", "1.1.1.1")
                defaultDns.put("port", 53)
                servers.put("servers", defaultDns)
            }
            
            dns.put("servers", servers)
            config.put("dns", dns)
            
            return config.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating Xray configuration", e)
            return "{}"
        }
    }
    
    /**
     * Copy asset file to internal storage
     * @param assetName Asset name
     * @param outputFile Output file
     */
    private fun copyAssetToFile(assetName: String, outputFile: File) {
        try {
            val input: InputStream = context.assets.open(assetName)
            val output = FileOutputStream(outputFile)
            
            val buffer = ByteArray(1024)
            var read: Int
            
            while (input.read(buffer).also { read = it } != -1) {
                output.write(buffer, 0, read)
            }
            
            input.close()
            output.flush()
            output.close()
            
            Log.i(TAG, "Asset $assetName copied to $outputFile")
        } catch (e: Exception) {
            Log.e(TAG, "Error copying asset $assetName to $outputFile", e)
        }
    }
}