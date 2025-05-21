package com.hiddify.hiddifyng.core.protocols

import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONArray
import org.json.JSONObject

/**
 * Protocol handler for REALITY servers (enhanced VLESS with XTLS)
 */
class RealityProtocol(private val server: Server) : ProtocolHandler() {
    
    override fun getProtocolName(): String = "vless"
    
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
        user.put("encryption", "none")
        user.put("flow", server.flow ?: "xtls-rprx-vision")
        users.put(user)
        
        serverObj.put("users", users)
        vnext.put(serverObj)
        
        settings.put("vnext", vnext)
        return settings
    }
    
    override fun createStreamSettings(): JSONObject {
        val streamSettings = JSONObject()
        
        // Network settings (typically TCP for REALITY)
        streamSettings.put("network", server.network ?: "tcp")
        
        // REALITY settings
        streamSettings.put("security", "reality")
        
        val realitySettings = JSONObject()
        
        // Required fields
        realitySettings.put("show", false)
        realitySettings.put("fingerprint", server.fingerprint ?: "chrome")
        
        // Optional fields with fallbacks
        if (!server.publicKey.isNullOrEmpty()) {
            realitySettings.put("publicKey", server.publicKey)
        }
        
        if (!server.shortId.isNullOrEmpty()) {
            realitySettings.put("shortId", server.shortId)
        }
        
        if (!server.spiderX.isNullOrEmpty()) {
            realitySettings.put("spiderX", server.spiderX)
        }
        
        // Server name indication
        if (!server.sni.isNullOrEmpty()) {
            realitySettings.put("serverName", server.sni)
        }
        
        // Add REALITY settings to streamSettings
        streamSettings.put("realitySettings", realitySettings)
        
        return streamSettings
    }
    
    // REALITY doesn't use traditional TLS
    override fun requiresTls(): Boolean = false
}