package com.hiddify.hiddifyng.core.protocols

import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONObject

/**
 * Protocol handler for XHttp protocol
 * XHttp is a camouflaged protocol that looks like standard HTTP traffic
 */
class XHttpProtocol(private val server: Server) : ProtocolHandler() {
    
    override fun getProtocolName(): String = "xhttp"
    
    override fun createOutboundConfig(): JSONObject {
        return createBaseOutbound()
    }
    
    override fun createProtocolSettings(): JSONObject {
        val settings = JSONObject()
        
        // Server configuration
        settings.put("server", server.host)
        settings.put("port", server.port ?: 80)
        
        // Authentication
        if (!server.uuid.isNullOrEmpty()) {
            settings.put("id", server.uuid)
        }
        
        // Path settings
        if (!server.path.isNullOrEmpty()) {
            settings.put("path", server.path)
        } else {
            settings.put("path", "/")
        }
        
        // Optional HTTP headers if specified
        if (!server.host.isNullOrEmpty()) {
            val headers = JSONObject()
            headers.put("Host", server.host)
            settings.put("headers", headers)
        }
        
        return settings
    }
    
    override fun createStreamSettings(): JSONObject {
        val streamSettings = JSONObject()
        
        // XHttp typically uses TCP
        streamSettings.put("network", "tcp")
        
        // Security settings (optional TLS)
        if (requiresTls()) {
            streamSettings.put("security", "tls")
            
            val tlsSettings = JSONObject()
            tlsSettings.put("allowInsecure", server.allowInsecure ?: false)
            
            if (!server.sni.isNullOrEmpty()) {
                tlsSettings.put("serverName", server.sni)
            }
            
            streamSettings.put("tlsSettings", tlsSettings)
        } else {
            streamSettings.put("security", "none")
        }
        
        return streamSettings
    }
    
    // XHttp can work with or without TLS
    override fun requiresTls(): Boolean {
        // Check if the server is configured to use TLS
        return server.useTls ?: (server.port == 443)
    }
    
    override fun getDefaultPort(): Int = 80
}