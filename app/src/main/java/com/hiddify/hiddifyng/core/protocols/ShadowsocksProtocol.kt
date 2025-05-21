package com.hiddify.hiddifyng.core.protocols

import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONArray
import org.json.JSONObject

/**
 * Protocol handler for Shadowsocks servers
 */
class ShadowsocksProtocol(private val server: Server) : ProtocolHandler() {
    
    override fun getProtocolName(): String = "shadowsocks"
    
    override fun createOutboundConfig(): JSONObject {
        return createBaseOutbound()
    }
    
    override fun createProtocolSettings(): JSONObject {
        val settings = JSONObject()
        val servers = JSONArray()
        
        val serverObj = JSONObject()
        serverObj.put("address", server.host)
        serverObj.put("port", server.port ?: getDefaultPort())
        
        // Shadowsocks requires password and encryption method
        if (!server.password.isNullOrEmpty()) {
            serverObj.put("password", server.password)
        }
        
        // Set encryption method or default to a secure one
        serverObj.put("method", server.method ?: "chacha20-poly1305")
        
        servers.put(serverObj)
        settings.put("servers", servers)
        
        return settings
    }
    
    override fun createStreamSettings(): JSONObject {
        val streamSettings = JSONObject()
        
        // Shadowsocks typically uses TCP
        streamSettings.put("network", "tcp")
        
        // Shadowsocks usually doesn't use TLS at the protocol level
        streamSettings.put("security", "none")
        
        return streamSettings
    }
    
    // Shadowsocks has its own encryption, so it doesn't require TLS
    override fun requiresTls(): Boolean = false
    
    override fun getDefaultPort(): Int = 8388
}