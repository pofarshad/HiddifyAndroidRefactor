package com.hiddify.hiddifyng.core.protocols

import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONArray
import org.json.JSONObject

/**
 * Protocol handler for VMess servers
 */
class VmessProtocol(private val server: Server) : ProtocolHandler() {
    
    override fun getProtocolName(): String = "vmess"
    
    override fun createOutboundConfig(): JSONObject {
        return createBaseOutbound()
    }
    
    override fun createProtocolSettings(): JSONObject {
        val settings = JSONObject()
        val vnext = JSONArray()
        
        val serverObj = JSONObject()
        serverObj.put("address", server.host)
        serverObj.put("port", server.port ?: getDefaultPort())
        
        val users = JSONArray()
        val user = JSONObject()
        user.put("id", server.uuid)
        user.put("alterId", server.alterId ?: 0)
        user.put("security", server.security ?: "auto")
        users.put(user)
        
        serverObj.put("users", users)
        vnext.put(serverObj)
        
        settings.put("vnext", vnext)
        return settings
    }
    
    override fun createStreamSettings(): JSONObject {
        val streamSettings = JSONObject()
        
        // Network settings
        streamSettings.put("network", server.network ?: "tcp")
        
        // Security settings
        if (requiresTls()) {
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
        }
        
        // Transport settings based on network type
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
            "tcp" -> {
                // Add TCP-specific settings if needed
                val tcpSettings = JSONObject()
                streamSettings.put("tcpSettings", tcpSettings)
            }
        }
        
        return streamSettings
    }
}