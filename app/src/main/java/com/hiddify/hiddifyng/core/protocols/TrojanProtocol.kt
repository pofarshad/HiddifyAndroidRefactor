package com.hiddify.hiddifyng.core.protocols

import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONArray
import org.json.JSONObject

/**
 * Protocol handler for Trojan servers
 */
class TrojanProtocol(private val server: Server) : ProtocolHandler() {
    
    override fun getProtocolName(): String = "trojan"
    
    override fun createOutboundConfig(): JSONObject {
        return createBaseOutbound()
    }
    
    override fun createProtocolSettings(): JSONObject {
        val settings = JSONObject()
        val servers = JSONArray()
        
        val serverObj = JSONObject()
        serverObj.put("address", server.host)
        serverObj.put("port", server.port ?: getDefaultPort())
        
        // For Trojan, the password is used for authentication
        // If not available, fall back to using the UUID field
        if (!server.password.isNullOrEmpty()) {
            serverObj.put("password", server.password)
        } else if (!server.uuid.isNullOrEmpty()) {
            serverObj.put("password", server.uuid)
        }
        
        servers.put(serverObj)
        settings.put("servers", servers)
        
        return settings
    }
    
    override fun createStreamSettings(): JSONObject {
        val streamSettings = JSONObject()
        
        // Network settings
        streamSettings.put("network", server.network ?: "tcp")
        
        // Security settings (Trojan typically requires TLS)
        streamSettings.put("security", "tls")
        
        // TLS settings
        val tlsSettings = JSONObject()
        tlsSettings.put("allowInsecure", server.allowInsecure ?: false)
        
        if (!server.sni.isNullOrEmpty()) {
            tlsSettings.put("serverName", server.sni)
        }
        
        if (!server.alpn.isNullOrEmpty()) {
            val alpnArray = JSONArray()
            server.alpn.split(",").forEach { alpn ->
                if (alpn.isNotEmpty()) {
                    alpnArray.put(alpn.trim())
                }
            }
            tlsSettings.put("alpn", alpnArray)
        }
        
        // Add TLS settings to streamSettings
        streamSettings.put("tlsSettings", tlsSettings)
        
        // Handle transport-specific settings
        when (server.network?.lowercase()) {
            "ws" -> {
                val wsSettings = JSONObject()
                
                if (!server.path.isNullOrEmpty()) {
                    wsSettings.put("path", server.path)
                }
                
                if (!server.host.isNullOrEmpty()) {
                    val headers = JSONObject()
                    headers.put("Host", server.host)
                    wsSettings.put("headers", headers)
                }
                
                streamSettings.put("wsSettings", wsSettings)
            }
            "grpc" -> {
                val grpcSettings = JSONObject()
                
                if (!server.serviceName.isNullOrEmpty()) {
                    grpcSettings.put("serviceName", server.serviceName)
                }
                
                grpcSettings.put("multiMode", server.multiMode ?: false)
                streamSettings.put("grpcSettings", grpcSettings)
            }
        }
        
        return streamSettings
    }
    
    // Trojan typically requires TLS for security
    override fun requiresTls(): Boolean = true
    
    override fun getDefaultPort(): Int = 443
}