package com.hiddify.hiddifyng.protocols

import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONArray
import org.json.JSONObject

/**
 * Base class for handling various protocols supported by Xray
 */
open class ProtocolHandler {
    private val TAG = "ProtocolHandler"
    
    /**
     * Creates outbound configuration for the given server
     */
    open fun createOutboundConfig(server: Server): JSONObject {
        val protocol = server.protocol.toLowerCase()
        
        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", protocol)
            
            // Protocol specific settings
            put("settings", createProtocolSettings(server))
            
            // Stream settings
            put("streamSettings", createStreamSettings(server))
            
            // Mux settings
            put("mux", JSONObject().apply {
                put("enabled", server.enableMux ?: false)
                put("concurrency", server.muxConcurrency ?: 8)
            })
        }
    }
    
    /**
     * Creates protocol-specific settings part of the outbound config
     */
    protected open fun createProtocolSettings(server: Server): JSONObject {
        val settings = JSONObject()
        val protocol = server.protocol.toLowerCase()
        
        when (protocol) {
            "vmess" -> {
                val vmessSettings = JSONObject()
                val vnext = JSONArray()
                
                val serverObj = JSONObject().apply {
                    put("address", server.address)
                    put("port", server.port)
                    
                    val users = JSONArray()
                    val user = JSONObject().apply {
                        put("id", server.uuid)
                        put("alterId", server.alterId ?: 0)
                        put("security", server.security ?: "auto")
                        server.encryption?.let { put("encryption", it) }
                    }
                    users.put(user)
                    
                    put("users", users)
                }
                
                vnext.put(serverObj)
                vmessSettings.put("vnext", vnext)
                return vmessSettings
            }
            
            "vless" -> {
                val vlessSettings = JSONObject()
                val vnext = JSONArray()
                
                val serverObj = JSONObject().apply {
                    put("address", server.address)
                    put("port", server.port)
                    
                    val users = JSONArray()
                    val user = JSONObject().apply {
                        put("id", server.uuid)
                        put("encryption", server.encryption ?: "none")
                        server.flow?.let { put("flow", it) }
                    }
                    users.put(user)
                    
                    put("users", users)
                }
                
                vnext.put(serverObj)
                vlessSettings.put("vnext", vnext)
                return vlessSettings
            }
            
            "trojan" -> {
                val trojanSettings = JSONObject()
                val servers = JSONArray()
                
                val serverObj = JSONObject().apply {
                    put("address", server.address)
                    put("port", server.port)
                    put("password", server.password)
                    server.flow?.let { put("flow", it) }
                }
                
                servers.put(serverObj)
                trojanSettings.put("servers", servers)
                return trojanSettings
            }
            
            "shadowsocks" -> {
                val ssSettings = JSONObject()
                val servers = JSONArray()
                
                val serverObj = JSONObject().apply {
                    put("address", server.address)
                    put("port", server.port)
                    put("method", server.method ?: "chacha20-poly1305")
                    put("password", server.password)
                    put("ivCheck", true)
                }
                
                servers.put(serverObj)
                ssSettings.put("servers", servers)
                return ssSettings
            }
            
            else -> {
                Log.w(TAG, "Unknown protocol: $protocol, using generic settings")
                return JSONObject()
            }
        }
    }
    
    /**
     * Creates stream settings part of the outbound config
     */
    protected open fun createStreamSettings(server: Server): JSONObject {
        val streamSettings = JSONObject()
        
        // Network type
        streamSettings.put("network", server.network ?: "tcp")
        
        // Security type (tls, etc)
        server.tls?.let {
            streamSettings.put("security", it)
            
            // TLS settings
            if (it == "tls" || it == "xtls") {
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
                        alpn.split(",").forEach {
                            if (it.trim().isNotEmpty()) {
                                alpnArray.put(it.trim())
                            }
                        }
                        if (alpnArray.length() > 0) {
                            put("alpn", alpnArray)
                        }
                    }
                }
                
                val settingKey = if (it == "tls") "tlsSettings" else "xtlsSettings"
                streamSettings.put(settingKey, tlsSettings)
            }
        }
        
        // Network specific settings
        when (server.network?.toLowerCase()) {
            "tcp" -> {
                val tcpSettings = JSONObject()
                server.headerType?.let { headerType ->
                    val header = JSONObject().apply {
                        put("type", headerType)
                        
                        // HTTP headers if type is http
                        if (headerType == "http") {
                            val request = JSONObject()
                            val headers = JSONObject()
                            
                            // Add host header
                            server.host?.let { host ->
                                val hostArray = JSONArray()
                                host.split(",").forEach {
                                    if (it.trim().isNotEmpty()) {
                                        hostArray.put(it.trim())
                                    }
                                }
                                if (hostArray.length() > 0) {
                                    headers.put("Host", hostArray)
                                }
                            }
                            
                            request.put("headers", headers)
                            put("request", request)
                        }
                    }
                    tcpSettings.put("header", header)
                }
                streamSettings.put("tcpSettings", tcpSettings)
            }
            
            "ws" -> {
                val wsSettings = JSONObject().apply {
                    server.path?.let { put("path", it) }
                    
                    // Headers
                    val headers = JSONObject()
                    server.host?.let { 
                        headers.put("Host", it.split(",")[0].trim())
                    }
                    if (headers.length() > 0) {
                        put("headers", headers)
                    }
                }
                streamSettings.put("wsSettings", wsSettings)
            }
            
            "grpc" -> {
                val grpcSettings = JSONObject().apply {
                    server.path?.let { put("serviceName", it) }
                    put("multiMode", server.grpcMultiMode ?: false)
                }
                streamSettings.put("grpcSettings", grpcSettings)
            }
            
            "quic" -> {
                val quicSettings = JSONObject().apply {
                    server.headerType?.let { put("header", JSONObject().put("type", it)) }
                    server.quicSecurity?.let { security ->
                        put("security", security)
                        server.quicKey?.let { key ->
                            put("key", key)
                        }
                    }
                }
                streamSettings.put("quicSettings", quicSettings)
            }
            
            "http" -> {
                val httpSettings = JSONObject().apply {
                    // Hosts
                    server.host?.let { host ->
                        val hostArray = JSONArray()
                        host.split(",").forEach {
                            if (it.trim().isNotEmpty()) {
                                hostArray.put(it.trim())
                            }
                        }
                        if (hostArray.length() > 0) {
                            put("host", hostArray)
                        }
                    }
                    
                    // Path
                    server.path?.let { put("path", it) }
                }
                streamSettings.put("httpSettings", httpSettings)
            }
        }
        
        return streamSettings
    }
}
