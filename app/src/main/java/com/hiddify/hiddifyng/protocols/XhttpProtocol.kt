package com.hiddify.hiddifyng.protocols

import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handler for XHTTP protocol (experimental)
 */
class XhttpProtocol : ProtocolHandler() {
    
    override fun createOutboundConfig(server: Server): JSONObject {
        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "xhttp")
            
            // Protocol specific settings
            put("settings", createProtocolSettings(server))
            
            // Stream settings
            put("streamSettings", createStreamSettings(server))
        }
    }
    
    override fun createProtocolSettings(server: Server): JSONObject {
        return JSONObject().apply {
            val servers = JSONArray()
            
            val serverObj = JSONObject().apply {
                put("address", server.address)
                put("port", server.port)
                
                // Authentication
                server.uuid?.let { put("uuid", it) }
                server.password?.let { put("password", it) }
                
                // Path prefix for HTTP request
                server.path?.let { put("path", it) }
                
                // Custom headers
                val headers = JSONObject()
                server.headers?.split(";")?.forEach { header ->
                    val parts = header.split(":")
                    if (parts.size == 2) {
                        headers.put(parts[0].trim(), parts[1].trim())
                    }
                }
                if (headers.length() > 0) {
                    put("headers", headers)
                }
                
                // Host header
                server.host?.let { 
                    put("host", it.split(",")[0].trim())
                }
            }
            
            servers.put(serverObj)
            put("servers", servers)
            
            // Request timeout
            server.requestTimeout?.let { put("timeout", it) }
            
            // Allow HTTP/2
            server.allowHttp2?.let { put("allowHTTP2", it) }
        }
    }
    
    override fun createStreamSettings(server: Server): JSONObject {
        val streamSettings = JSONObject()
        
        // Set network to TCP for XHTTP
        streamSettings.put("network", "tcp")
        
        // TLS settings
        server.tls?.let {
            if (it == "tls") {
                streamSettings.put("security", "tls")
                
                val tlsSettings = JSONObject().apply {
                    server.sni?.let { sni -> put("serverName", sni) }
                    server.allowInsecure?.let { allowInsecure -> put("allowInsecure", allowInsecure) }
                    
                    // Fingerprint
                    server.fingerprint?.let { fingerprint -> 
                        put("fingerprint", fingerprint) 
                    }
                    
                    // ALPN
                    server.alpn?.let { alpn ->
                        val alpnArray = JSONArray()
                        alpn.split(",").forEach { alpnItem ->
                            if (alpnItem.trim().isNotEmpty()) {
                                alpnArray.put(alpnItem.trim())
                            }
                        }
                        if (alpnArray.length() > 0) {
                            put("alpn", alpnArray)
                        }
                    }
                }
                
                streamSettings.put("tlsSettings", tlsSettings)
            }
        }
        
        return streamSettings
    }
}
