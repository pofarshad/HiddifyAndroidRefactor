package com.hiddify.hiddifyng.core.protocols

import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONObject

/**
 * Protocol handler for Hysteria servers
 * Hysteria is a UDP-based protocol offering low-latency, high-throughput connections
 */
class HysteriaProtocol(private val server: Server) : ProtocolHandler() {
    
    override fun getProtocolName(): String = "hysteria"
    
    override fun createOutboundConfig(): JSONObject {
        return createBaseOutbound()
    }
    
    override fun createProtocolSettings(): JSONObject {
        val settings = JSONObject()
        
        // Server address and port
        settings.put("server", server.host)
        settings.put("port", server.port ?: 443)
        
        // Authentication
        if (!server.password.isNullOrEmpty()) {
            settings.put("auth_str", server.password)
        } else if (!server.uuid.isNullOrEmpty()) {
            settings.put("auth_str", server.uuid)
        }
        
        // Connection settings
        settings.put("up_mbps", server.uploadMbps ?: 100)
        settings.put("down_mbps", server.downloadMbps ?: 100)
        
        // Optional settings with sensible defaults
        settings.put("fast_open", true)
        settings.put("insecure", server.allowInsecure ?: false)
        
        // Server name for verification if provided
        if (!server.sni.isNullOrEmpty()) {
            settings.put("server_name", server.sni)
        }
        
        return settings
    }
    
    override fun createStreamSettings(): JSONObject {
        // Hysteria uses its own transport implementation, 
        // so standard stream settings are not applicable
        return JSONObject()
    }
    
    override fun getDefaultPort(): Int = 443
}