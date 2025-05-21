package com.hiddify.hiddifyng.protocols

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale

/**
 * Handler for Hysteria protocol (a fast and reliable UDP protocol)
 * Optimized with full Xray compatibility and improved error handling
 */
class HysteriaProtocolHandler : ProtocolHandler {
    private val TAG = "HysteriaProtocol"
    
    companion object {
        // Default values for Hysteria
        private const val DEFAULT_PROTOCOL = "udp"
        private const val DEFAULT_UP_MBPS = 10
        private const val DEFAULT_DOWN_MBPS = 50
        private const val DEFAULT_PORT = 443
    }
    
    override fun getProtocolName(): String = "hysteria"
    
    /**
     * Generate Xray-compatible configuration for Hysteria protocol
     * @param server Server configuration
     * @return JSON configuration string optimized for Xray core
     */
    override fun generateConfig(server: Server): String {
        try {
            // Create the root configuration object
            val rootConfig = JSONObject()
            
            // Create outbounds array
            val outbounds = JSONArray()
            
            // Main outbound (hysteria)
            val mainOutbound = JSONObject()
            mainOutbound.put("tag", "proxy")
            mainOutbound.put("protocol", "hysteria")
            
            // Hysteria settings
            val hysteriaSettings = JSONObject()
            
            // Server address and port
            val serverObj = JSONObject()
            serverObj.put("address", server.address)
            serverObj.put("port", server.port)
            hysteriaSettings.put("server", serverObj)
            
            // Protocol (UDP, wechat-video, faketcp)
            val protocol = server.hysteriaProtocol ?: DEFAULT_PROTOCOL
            hysteriaSettings.put("protocol", protocol)
            
            // Authentication using password or auth string
            val auth = server.hysteriaAuthString ?: server.password
            if (!auth.isNullOrEmpty()) {
                hysteriaSettings.put("auth_str", auth)
            }
            
            // Bandwidth settings with defaults for better performance
            val up = server.hysteriaUpMbps ?: DEFAULT_UP_MBPS
            val down = server.hysteriaDownMbps ?: DEFAULT_DOWN_MBPS
            hysteriaSettings.put("up_mbps", up)
            hysteriaSettings.put("down_mbps", down)
            
            // Obfuscation for traffic hiding
            server.hysteriaObfs?.takeIf { it.isNotEmpty() }?.let {
                hysteriaSettings.put("obfs", it)
            }
            
            // TLS settings for security
            val tlsConfig = JSONObject()
            tlsConfig.put("enabled", true)
            tlsConfig.put("server_name", server.sni ?: server.address)
            
            // Only add insecure option if true (more secure by default)
            if (server.allowInsecure == true) {
                tlsConfig.put("insecure", true)
            }
            
            // Add ALPN protocols if specified
            server.alpn?.takeIf { it.isNotEmpty() }?.let {
                val alpnArray = JSONArray()
                it.split(",").forEach { protocol ->
                    alpnArray.put(protocol.trim())
                }
                tlsConfig.put("alpn", alpnArray)
            }
            
            hysteriaSettings.put("tls", tlsConfig)
            
            // Advanced performance settings when specified
            server.hysteriaRecvWindowConn?.let {
                hysteriaSettings.put("recv_window_conn", it)
            }
            
            server.hysteriaRecvWindow?.let {
                hysteriaSettings.put("recv_window", it)
            }
            
            if (server.hysteriaDisableMtuDiscovery == true) {
                hysteriaSettings.put("disable_mtu_discovery", true)
            }
            
            // Add hysteria settings to outbound
            mainOutbound.put("settings", hysteriaSettings)
            
            // Add main outbound to outbounds array
            outbounds.put(mainOutbound)
            
            // Direct outbound for bypassed traffic
            val directOutbound = JSONObject()
            directOutbound.put("tag", "direct")
            directOutbound.put("protocol", "freedom")
            outbounds.put(directOutbound)
            
            // Block outbound for blocked domains
            val blockOutbound = JSONObject()
            blockOutbound.put("tag", "block")
            blockOutbound.put("protocol", "blackhole")
            outbounds.put(blockOutbound)
            
            // Add outbounds to root config
            rootConfig.put("outbounds", outbounds)
            
            return rootConfig.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating Hysteria config", e)
            // Return a minimal valid configuration in case of error
            return """{"outbounds":[{"protocol":"hysteria","settings":{}}]}"""
        }
    }
    
    /**
     * Validate server configuration for Hysteria protocol
     * @param server Server configuration to validate
     * @return true if valid, false otherwise
     */
    override fun validateServer(server: Server): Boolean {
        // Check required fields
        if (server.address.isNullOrEmpty()) {
            Log.w(TAG, "Invalid server: missing address")
            return false
        }
        
        // Port validation with default fallback
        if (server.port <= 0 || server.port > 65535) {
            Log.w(TAG, "Invalid server: port out of range (${server.port})")
            return false
        }
        
        // Either password or auth_str must be present
        if (server.password.isNullOrEmpty() && server.hysteriaAuthString.isNullOrEmpty()) {
            Log.w(TAG, "Invalid server: missing authentication")
            return false
        }
        
        // Check if protocol is valid (if specified)
        server.hysteriaProtocol?.let {
            if (it !in listOf("udp", "wechat-video", "faketcp")) {
                Log.w(TAG, "Invalid server: unsupported protocol ($it)")
                return false
            }
        }
        
        return true
    }
    
    /**
     * Parse Hysteria URL into Server object
     * @param url Hysteria URL string
     * @return Server object or null if parsing failed
     */
    override fun parseUrl(url: String): Server? {
        try {
            if (!url.startsWith("hysteria://")) {
                return null
            }
            
            // Example URL format: 
            // hysteria://host:port?protocol=udp&auth=base64(pass)&peer=sni&insecure=1&upmbps=20&downmbps=100&obfs=xplus
            
            val uri = Uri.parse(url)
            val host = uri.host ?: return null
            val port = uri.port.takeIf { it > 0 } ?: DEFAULT_PORT
            
            // Create server with required fields
            val server = Server(
                name = "Hysteria $host:$port",
                protocol = getProtocolName(),
                address = host,
                port = port
            )
            
            // Parse all query parameters
            uri.queryParameterNames.forEach { param ->
                when (param.lowercase(Locale.getDefault())) {
                    "auth" -> uri.getQueryParameter(param)?.let {
                        server.hysteriaAuthString = decodeBase64IfNeeded(it)
                    }
                    "peer", "sni" -> uri.getQueryParameter(param)?.let {
                        server.sni = it
                    }
                    "insecure" -> uri.getQueryParameter(param)?.let {
                        server.allowInsecure = it == "1" || it.equals("true", ignoreCase = true)
                    }
                    "upmbps" -> uri.getQueryParameter(param)?.toIntOrNull()?.let {
                        server.hysteriaUpMbps = it.coerceAtLeast(1) // Ensure minimum 1 Mbps
                    }
                    "downmbps" -> uri.getQueryParameter(param)?.toIntOrNull()?.let {
                        server.hysteriaDownMbps = it.coerceAtLeast(1) // Ensure minimum 1 Mbps
                    }
                    "obfs" -> uri.getQueryParameter(param)?.let {
                        server.hysteriaObfs = it
                    }
                    "protocol" -> uri.getQueryParameter(param)?.let {
                        // Validate protocol
                        if (it in listOf("udp", "wechat-video", "faketcp")) {
                            server.hysteriaProtocol = it
                        } else {
                            Log.w(TAG, "Unsupported protocol in URL: $it, using default")
                            server.hysteriaProtocol = DEFAULT_PROTOCOL
                        }
                    }
                    "alpn" -> uri.getQueryParameter(param)?.let {
                        server.alpn = it
                    }
                    "recv_window_conn" -> uri.getQueryParameter(param)?.toIntOrNull()?.let {
                        server.hysteriaRecvWindowConn = it
                    }
                    "recv_window" -> uri.getQueryParameter(param)?.toIntOrNull()?.let {
                        server.hysteriaRecvWindow = it
                    }
                    "disable_mtu_discovery" -> uri.getQueryParameter(param)?.let {
                        server.hysteriaDisableMtuDiscovery = it == "1" || it.equals("true", ignoreCase = true)
                    }
                }
            }
            
            // Set defaults if not specified
            if (server.hysteriaProtocol == null) {
                server.hysteriaProtocol = DEFAULT_PROTOCOL
            }
            
            // Validate the parsed server
            return if (validateServer(server)) server else null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Hysteria URL", e)
            return null
        }
    }
    
    /**
     * Generate shareable URL for this server
     * @param server Server configuration
     * @return Hysteria URL for sharing
     */
    override fun generateUrl(server: Server): String {
        try {
            val uriBuilder = Uri.Builder()
                .scheme("hysteria")
                .encodedAuthority("${server.address}:${server.port}")
            
            // Add parameters only if they have values to keep URL clean
            
            // Protocol (only if not default)
            if (server.hysteriaProtocol != null && server.hysteriaProtocol != DEFAULT_PROTOCOL) {
                uriBuilder.appendQueryParameter("protocol", server.hysteriaProtocol)
            }
            
            // Add authentication
            val auth = server.hysteriaAuthString ?: server.password
            if (!auth.isNullOrEmpty()) {
                uriBuilder.appendQueryParameter("auth", auth)
            }
            
            // Add SNI if different from address
            if (!server.sni.isNullOrEmpty() && server.sni != server.address) {
                uriBuilder.appendQueryParameter("peer", server.sni)
            }
            
            // Add insecure flag only if true
            if (server.allowInsecure == true) {
                uriBuilder.appendQueryParameter("insecure", "1")
            }
            
            // Add bandwidth settings if not default
            if (server.hysteriaUpMbps != null && server.hysteriaUpMbps != DEFAULT_UP_MBPS) {
                uriBuilder.appendQueryParameter("upmbps", server.hysteriaUpMbps.toString())
            }
            
            if (server.hysteriaDownMbps != null && server.hysteriaDownMbps != DEFAULT_DOWN_MBPS) {
                uriBuilder.appendQueryParameter("downmbps", server.hysteriaDownMbps.toString())
            }
            
            // Add obfuscation if present
            if (!server.hysteriaObfs.isNullOrEmpty()) {
                uriBuilder.appendQueryParameter("obfs", server.hysteriaObfs)
            }
            
            // Add ALPN if present
            if (!server.alpn.isNullOrEmpty()) {
                uriBuilder.appendQueryParameter("alpn", server.alpn)
            }
            
            // Add advanced parameters only if set
            server.hysteriaRecvWindowConn?.let {
                uriBuilder.appendQueryParameter("recv_window_conn", it.toString())
            }
            
            server.hysteriaRecvWindow?.let {
                uriBuilder.appendQueryParameter("recv_window", it.toString())
            }
            
            if (server.hysteriaDisableMtuDiscovery == true) {
                uriBuilder.appendQueryParameter("disable_mtu_discovery", "1")
            }
            
            return uriBuilder.build().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating Hysteria URL", e)
            // Return basic URL in case of error
            return "hysteria://${server.address}:${server.port}"
        }
    }
    
    /**
     * Helper function to decode Base64 if the string is encoded
     * @param input Potentially Base64 encoded string
     * @return Decoded string or original if not Base64
     */
    private fun decodeBase64IfNeeded(input: String): String {
        return try {
            val decoded = Base64.decode(input, Base64.DEFAULT)
            String(decoded)
        } catch (e: Exception) {
            // If it's not Base64, return the original string
            input
        }
    }
}