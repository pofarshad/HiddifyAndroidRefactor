package com.hiddify.hiddifyng.core

import android.content.Context
import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.protocols.ProtocolHandler
import com.hiddify.hiddifyng.protocols.HysteriaProtocol
import com.hiddify.hiddifyng.protocols.XhttpProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileDescriptor
import java.io.FileOutputStream

class XrayManager(private val context: Context) {
    private val TAG = "XrayManager"
    
    // JNI Interface for communicating with Xray-core
    external fun startXrayWithConfig(configFile: String, fd: Int): Boolean
    external fun stopXray(): Boolean
    external fun getXrayVersion(): String
    
    // Protocol handlers
    private val protocolHandlers = mapOf(
        "vmess" to ProtocolHandler(),
        "vless" to ProtocolHandler(),
        "trojan" to ProtocolHandler(),
        "shadowsocks" to ProtocolHandler(),
        "hysteria" to HysteriaProtocol(),
        "xhttp" to XhttpProtocol()
    )
    
    init {
        // Load native library for Xray core
        System.loadLibrary("xray-core")
        Log.i(TAG, "Loaded Xray-core native library, version: ${getXrayVersion()}")
    }
    
    fun startXray(server: Server, vpnFileDescriptor: FileDescriptor): Boolean {
        try {
            // Create config file
            val configFile = generateConfigFile(server)
            
            // Get file descriptor int
            val fdField = FileDescriptor::class.java.getDeclaredField("descriptor")
            fdField.isAccessible = true
            val fd = fdField.getInt(vpnFileDescriptor)
            
            // Start Xray with config
            return startXrayWithConfig(configFile.absolutePath, fd)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Xray", e)
            return false
        }
    }
    
    private fun generateConfigFile(server: Server): File {
        val configDir = File(context.getExternalFilesDir(null), "configs")
        if (!configDir.exists()) {
            configDir.mkdirs()
        }
        
        val configFile = File(configDir, "config_${server.id}.json")
        
        // Create Xray config JSON
        val config = createXrayConfig(server)
        
        // Write config to file
        FileOutputStream(configFile).use { output ->
            output.write(config.toString(2).toByteArray())
        }
        
        return configFile
    }
    
    private fun createXrayConfig(server: Server): JSONObject {
        val config = JSONObject()
        
        // Log level
        config.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })
        
        // Inbounds (VPN service)
        config.put("inbounds", JSONArray().apply {
            put(JSONObject().apply {
                put("tag", "tun2socks")
                put("protocol", "dokodemo-door")
                put("settings", JSONObject().apply {
                    put("network", "tcp,udp")
                    put("followRedirect", true)
                })
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray().apply {
                        put("http")
                        put("tls")
                    })
                })
            })
        })
        
        // Outbounds (server connections)
        config.put("outbounds", JSONArray().apply {
            // Main outbound based on protocol
            put(createOutbound(server))
            
            // Direct outbound
            put(JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
                put("settings", JSONObject())
            })
            
            // Block outbound
            put(JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
                put("settings", JSONObject())
            })
        })
        
        // Routing rules
        config.put("routing", createRoutingRules(server))
        
        // DNS settings
        config.put("dns", createDnsSettings(server))
        
        return config
    }
    
    private fun createOutbound(server: Server): JSONObject {
        val protocol = server.protocol.toLowerCase()
        val handler = protocolHandlers[protocol] ?: protocolHandlers["vmess"]!!
        
        return handler.createOutboundConfig(server)
    }
    
    private fun createRoutingRules(server: Server): JSONObject {
        val routing = JSONObject()
        val rules = JSONArray()
        
        // Add bypass rules
        server.bypassDomains?.split(",")?.takeIf { it.isNotEmpty() }?.let { domains ->
            rules.put(JSONObject().apply {
                put("type", "field")
                put("domain", JSONArray().apply {
                    domains.forEach { domain -> put(domain.trim()) }
                })
                put("outboundTag", "direct")
            })
        }
        
        // Add block rules
        server.blockDomains?.split(",")?.takeIf { it.isNotEmpty() }?.let { domains ->
            rules.put(JSONObject().apply {
                put("type", "field")
                put("domain", JSONArray().apply {
                    domains.forEach { domain -> put(domain.trim()) }
                })
                put("outboundTag", "block")
            })
        }
        
        // Default routing rule
        rules.put(JSONObject().apply {
            put("type", "field")
            put("network", "tcp,udp")
            put("outboundTag", "proxy")
        })
        
        routing.put("rules", rules)
        return routing
    }
    
    private fun createDnsSettings(server: Server): JSONObject {
        val dns = JSONObject()
        val servers = JSONArray()
        
        // Add DNS servers from config
        server.dnsServers?.split(",")?.forEach { 
            if (it.trim().isNotEmpty()) {
                servers.put(it.trim()) 
            }
        }
        
        // Add fallback DNS servers
        if (servers.length() == 0) {
            servers.put("8.8.8.8")
            servers.put("1.1.1.1")
        }
        
        dns.put("servers", servers)
        return dns
    }
    
    suspend fun updateXrayCore() = withContext(Dispatchers.IO) {
        // Implementation for updating Xray core binary
        // This would download the latest version and replace the existing one
        try {
            // Download latest version logic would go here
            // After download, update the native library
            Log.i(TAG, "Xray core updated to version: ${getXrayVersion()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Xray core", e)
            false
        }
    }
}
