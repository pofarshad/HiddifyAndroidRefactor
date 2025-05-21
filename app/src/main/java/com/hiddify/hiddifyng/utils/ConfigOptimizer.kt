package com.hiddify.hiddifyng.utils

import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONObject

/**
 * Optimizes Xray configurations for better performance based on protocol and network conditions
 * This helps improve connection speed and reduce battery usage
 */
class ConfigOptimizer {
    companion object {
        private const val TAG = "ConfigOptimizer"
        
        // Default mux configuration based on protocol type
        private val DEFAULT_MUX_SETTINGS = mapOf(
            "vmess" to 8,
            "vless" to 8,
            "trojan" to 4,
            "shadowsocks" to 4,
            "reality" to 2,
            "hysteria" to 0, // Hysteria has its own multiplexing
            "xhttp" to 4
        )
    }
    
    /**
     * Apply performance optimizations to an outbound configuration
     * @param config The original outbound configuration
     * @param server The server details
     * @param connectionQuality The current connection quality (0-3)
     * @return Optimized configuration
     */
    fun optimizeOutboundConfig(config: JSONObject, server: Server, connectionQuality: Int): JSONObject {
        try {
            // Get protocol type
            val protocol = server.protocol.lowercase()
            
            // Optimize multiplexing
            optimizeMux(config, protocol, connectionQuality)
            
            // Optimize buffer sizes based on connection quality
            optimizeBuffers(config, connectionQuality)
            
            // Protocol-specific optimizations
            when (protocol) {
                "vmess", "vless" -> optimizeV2rayProtocols(config, connectionQuality)
                "hysteria" -> optimizeHysteria(config, connectionQuality)
                "reality" -> optimizeReality(config)
                "trojan" -> optimizeTrojan(config, connectionQuality)
                "shadowsocks" -> optimizeShadowsocks(config)
                "xhttp" -> optimizeXHttp(config, connectionQuality)
            }
            
            // Add global performance optimizations
            addGlobalOptimizations(config, connectionQuality)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing config", e)
        }
        
        return config
    }
    
    /**
     * Optimize multiplexing settings based on protocol and connection quality
     */
    private fun optimizeMux(config: JSONObject, protocol: String, connectionQuality: Int): JSONObject {
        try {
            if (!config.has("mux")) {
                config.put("mux", JSONObject())
            }
            
            val mux = config.getJSONObject("mux")
            
            // Get default concurrent connections for this protocol
            val defaultConcurrent = DEFAULT_MUX_SETTINGS[protocol] ?: 4
            
            // Adjust based on connection quality
            val concurrentValue = when (connectionQuality) {
                3 -> defaultConcurrent + 4 // Excellent - increase
                2 -> defaultConcurrent     // Good - use default
                1 -> defaultConcurrent - 2 // Moderate - slightly reduce
                else -> 1                  // Poor - minimal multiplexing
            }.coerceAtLeast(1) // Ensure at least 1
            
            // Don't enable mux for protocols that don't support it well
            val enabled = protocol != "hysteria" && concurrentValue > 0
            
            mux.put("enabled", enabled)
            if (enabled) {
                mux.put("concurrency", concurrentValue)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing mux settings", e)
        }
        
        return config
    }
    
    /**
     * Optimize buffer sizes based on connection quality
     */
    private fun optimizeBuffers(config: JSONObject, connectionQuality: Int): JSONObject {
        try {
            if (!config.has("streamSettings")) {
                return config
            }
            
            val streamSettings = config.getJSONObject("streamSettings")
            if (!streamSettings.has("sockopt")) {
                streamSettings.put("sockopt", JSONObject())
            }
            
            val sockopt = streamSettings.getJSONObject("sockopt")
            
            // TCP keep-alive increases reliability
            sockopt.put("tcpKeepAliveInterval", 30)
            
            // Optimize buffer sizes based on connection quality
            val bufferSize = when (connectionQuality) {
                3 -> 4 * 1024 * 1024  // 4MB for excellent connections
                2 -> 2 * 1024 * 1024  // 2MB for good connections
                1 -> 1 * 1024 * 1024  // 1MB for moderate connections
                else -> 512 * 1024    // 512KB for poor connections
            }
            
            sockopt.put("tcpSendBufferSize", bufferSize)
            sockopt.put("tcpReceiveBufferSize", bufferSize)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing buffer settings", e)
        }
        
        return config
    }
    
    /**
     * V2Ray protocols (VMess/VLESS) specific optimizations
     */
    private fun optimizeV2rayProtocols(config: JSONObject, connectionQuality: Int): JSONObject {
        try {
            // These protocols benefit from optimizing WebSocket or TCP settings
            val streamSettings = config.optJSONObject("streamSettings") ?: return config
            
            // Add WebSocket optimizations if using WS transport
            if (streamSettings.optString("network") == "ws" && 
                !streamSettings.has("wsSettings")) {
                
                val wsSettings = JSONObject()
                wsSettings.put("maxEarlyData", 2048)
                wsSettings.put("earlyDataHeaderName", "Sec-WebSocket-Protocol")
                
                // Add compression for slower connections
                if (connectionQuality < 2) {
                    wsSettings.put("enableCompression", true)
                }
                
                streamSettings.put("wsSettings", wsSettings)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing V2Ray protocols", e)
        }
        
        return config
    }
    
    /**
     * REALITY protocol specific optimizations
     */
    private fun optimizeReality(config: JSONObject): JSONObject {
        try {
            val streamSettings = config.optJSONObject("streamSettings") ?: return config
            
            // REALITY works best with TCP
            if (!streamSettings.has("realitySettings")) {
                return config
            }
            
            val realitySettings = streamSettings.getJSONObject("realitySettings")
            
            // Set optimized fingerprint
            if (!realitySettings.has("fingerprint")) {
                realitySettings.put("fingerprint", "chrome")
            }
            
            // Add public key validation
            realitySettings.put("publicKeyInsecure", false)
            
            // Disable mux for REALITY as it often performs better without
            if (config.has("mux")) {
                val mux = config.getJSONObject("mux")
                mux.put("enabled", false)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing REALITY protocol", e)
        }
        
        return config
    }
    
    /**
     * Hysteria protocol specific optimizations
     */
    private fun optimizeHysteria(config: JSONObject, connectionQuality: Int): JSONObject {
        try {
            val settings = config.optJSONObject("settings") ?: return config
            
            // Set bandwidth based on connection quality
            // These values are in Mbps
            val uploadMbps = when (connectionQuality) {
                3 -> 200  // Excellent
                2 -> 100  // Good
                1 -> 50   // Moderate
                else -> 10 // Poor
            }
            
            val downloadMbps = when (connectionQuality) {
                3 -> 250  // Excellent
                2 -> 150  // Good
                1 -> 80   // Moderate
                else -> 20 // Poor
            }
            
            // Update settings if they're not already set
            if (!settings.has("up_mbps") || settings.getInt("up_mbps") == 0) {
                settings.put("up_mbps", uploadMbps)
            }
            
            if (!settings.has("down_mbps") || settings.getInt("down_mbps") == 0) {
                settings.put("down_mbps", downloadMbps)
            }
            
            // Enable optimizations
            settings.put("fast_open", true)
            settings.put("hopInterval", 30)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing Hysteria protocol", e)
        }
        
        return config
    }
    
    /**
     * Trojan protocol specific optimizations
     */
    private fun optimizeTrojan(config: JSONObject, connectionQuality: Int): JSONObject {
        try {
            // Trojan works well with TLS optimizations
            val streamSettings = config.optJSONObject("streamSettings") ?: return config
            
            if (streamSettings.optString("security") == "tls" && 
                !streamSettings.has("tlsSettings")) {
                
                val tlsSettings = JSONObject()
                
                // Add ALPN for better negotiation
                val alpn = JSONObject()
                alpn.put("alpn", "h2,http/1.1")
                
                // Session tickets improve handshake speed
                tlsSettings.put("sessionTicket", true)
                
                // Optimize TLS settings
                tlsSettings.put("minVersion", "1.2")
                tlsSettings.put("cipherSuites", "TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256:TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256:TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256:TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256")
                
                streamSettings.put("tlsSettings", tlsSettings)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing Trojan protocol", e)
        }
        
        return config
    }
    
    /**
     * Shadowsocks protocol specific optimizations
     */
    private fun optimizeShadowsocks(config: JSONObject): JSONObject {
        try {
            val settings = config.optJSONObject("settings") ?: return config
            
            // Make sure we're using AEAD ciphers for better performance
            if (settings.has("servers")) {
                val servers = settings.getJSONArray("servers")
                for (i in 0 until servers.length()) {
                    val server = servers.getJSONObject(i)
                    if (server.has("method")) {
                        val method = server.getString("method")
                        if (!method.contains("chacha20") && !method.contains("aes-")) {
                            // Suggest a better cipher
                            server.put("method", "chacha20-ietf-poly1305")
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing Shadowsocks protocol", e)
        }
        
        return config
    }
    
    /**
     * XHttp protocol specific optimizations
     */
    private fun optimizeXHttp(config: JSONObject, connectionQuality: Int): JSONObject {
        try {
            // XHttp benefits from HTTP/2 when available
            val streamSettings = config.optJSONObject("streamSettings") ?: return config
            
            if (streamSettings.optString("network") == "tcp") {
                val tcpSettings = streamSettings.optJSONObject("tcpSettings") ?: JSONObject()
                
                // HTTP headers for camouflage
                val header = tcpSettings.optJSONObject("header") ?: JSONObject()
                header.put("type", "http")
                
                // Optimize request headers
                val request = header.optJSONObject("request") ?: JSONObject()
                request.put("version", "1.1")
                request.put("method", "GET")
                
                // Set user agent based on connection quality (newer UAs for better connections)
                val headers = request.optJSONObject("headers") ?: JSONObject()
                val userAgent = when (connectionQuality) {
                    3, 2 -> "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36"
                    else -> "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.130 Safari/537.36"
                }
                
                headers.put("User-Agent", userAgent)
                headers.put("Accept-Encoding", "gzip, deflate")
                headers.put("Connection", "keep-alive")
                headers.put("Pragma", "no-cache")
                
                request.put("headers", headers)
                header.put("request", request)
                tcpSettings.put("header", header)
                streamSettings.put("tcpSettings", tcpSettings)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error optimizing XHttp protocol", e)
        }
        
        return config
    }
    
    /**
     * Add global optimizations for all protocols
     */
    private fun addGlobalOptimizations(config: JSONObject, connectionQuality: Int): JSONObject {
        try {
            // Add stats for traffic monitoring if not present
            if (!config.has("stats")) {
                config.put("stats", JSONObject())
            }
            
            // Add DNS optimization
            if (!config.has("dns")) {
                val dns = JSONObject()
                dns.put("hosts", JSONObject().put("domain:googleapis.cn", "googleapis.com"))
                
                // Set optimal DNS servers
                val servers = JSONObject()
                servers.put("address", "1.1.1.1")
                servers.put("port", 53)
                dns.put("servers", servers)
                
                config.put("dns", dns)
            }
            
            // Log level based on connection quality (less logging on poor connections to save resources)
            val logLevel = when (connectionQuality) {
                0 -> "error"  // Poor connection - minimal logging
                1 -> "warning" // Moderate connection
                else -> "info" // Good/excellent connection - normal logging
            }
            
            if (!config.has("log")) {
                val log = JSONObject()
                log.put("loglevel", logLevel)
                config.put("log", log)
            } else {
                val log = config.getJSONObject("log")
                log.put("loglevel", logLevel)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error adding global optimizations", e)
        }
        
        return config
    }
}