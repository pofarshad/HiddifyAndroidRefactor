package com.hiddify.hiddifyng.protocols

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Handler for Hysteria protocol (a fast and reliable UDP protocol)
 */
class HysteriaProtocolHandler : ProtocolHandler {
    private val TAG = "HysteriaProtocol"
    
    override fun getProtocolName(): String {
        return "hysteria"
    }
    
    override fun generateConfig(server: Server): String {
        try {
            // Create core JSON configuration object for Hysteria
            val config = JSONObject()
            val outbounds = JSONObject()
            val hysteriaSettings = JSONObject()
            
            // Server address and port
            config.put("server", server.address)
            config.put("port", server.port)
            
            // Protocol (UDP, wechat-video, faketcp)
            val protocol = server.hysteriaProtocol ?: "udp"
            config.put("protocol", protocol)
            
            // Authentication using password or auth string
            if (!server.password.isNullOrEmpty()) {
                config.put("auth_str", server.password)
            } else if (!server.hysteriaAuthString.isNullOrEmpty()) {
                config.put("auth_str", server.hysteriaAuthString)
            }
            
            // Bandwidth settings
            val up = server.hysteriaUpMbps ?: 10
            val down = server.hysteriaDownMbps ?: 50
            config.put("up_mbps", up)
            config.put("down_mbps", down)
            
            // Obfuscation
            if (!server.hysteriaObfs.isNullOrEmpty()) {
                config.put("obfs", server.hysteriaObfs)
            }
            
            // TLS settings
            val tlsConfig = JSONObject()
            tlsConfig.put("sni", server.sni ?: server.address)
            
            if (server.allowInsecure == true) {
                tlsConfig.put("insecure", true)
            }
            
            if (!server.alpn.isNullOrEmpty()) {
                tlsConfig.put("alpn", server.alpn)
            }
            
            config.put("tls", tlsConfig)
            
            // Advanced settings
            if (server.hysteriaRecvWindowConn != null) {
                config.put("recv_window_conn", server.hysteriaRecvWindowConn)
            }
            
            if (server.hysteriaRecvWindow != null) {
                config.put("recv_window", server.hysteriaRecvWindow)
            }
            
            if (server.hysteriaDisableMtuDiscovery == true) {
                config.put("disable_mtu_discovery", true)
            }
            
            return config.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating Hysteria config", e)
            return "{}"
        }
    }
    
    override fun validateServer(server: Server): Boolean {
        // Basic validation for Hysteria server configuration
        if (server.address.isNullOrEmpty() || server.port <= 0) {
            return false
        }
        
        // Either password or auth_str must be present
        if (server.password.isNullOrEmpty() && server.hysteriaAuthString.isNullOrEmpty()) {
            return false
        }
        
        return true
    }
    
    override fun parseUrl(url: String): Server? {
        try {
            if (!url.startsWith("hysteria://")) {
                return null
            }
            
            // Example URL format: 
            // hysteria://host:port?protocol=udp&auth=base64(pass)&peer=sni&insecure=1&upmbps=20&downmbps=100&obfs=xplus
            
            val uri = Uri.parse(url)
            val host = uri.host ?: return null
            val port = uri.port
            
            if (port <= 0) {
                return null
            }
            
            val server = Server(
                name = "Hysteria $host:$port",
                protocol = "hysteria",
                address = host,
                port = port
            )
            
            // Get query parameters
            uri.getQueryParameter("auth")?.let {
                server.hysteriaAuthString = decodeBase64IfNeeded(it)
            }
            
            uri.getQueryParameter("peer")?.let {
                server.sni = it
            }
            
            uri.getQueryParameter("insecure")?.let {
                server.allowInsecure = it == "1" || it.equals("true", ignoreCase = true)
            }
            
            uri.getQueryParameter("upmbps")?.toIntOrNull()?.let {
                server.hysteriaUpMbps = it
            }
            
            uri.getQueryParameter("downmbps")?.toIntOrNull()?.let {
                server.hysteriaDownMbps = it
            }
            
            uri.getQueryParameter("obfs")?.let {
                server.hysteriaObfs = it
            }
            
            uri.getQueryParameter("protocol")?.let {
                server.hysteriaProtocol = it
            } ?: run {
                server.hysteriaProtocol = "udp" // Default protocol
            }
            
            uri.getQueryParameter("alpn")?.let {
                server.alpn = it
            }
            
            return server
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Hysteria URL", e)
            return null
        }
    }
    
    override fun generateUrl(server: Server): String {
        try {
            val uriBuilder = Uri.Builder()
                .scheme("hysteria")
                .encodedAuthority("${server.address}:${server.port}")
            
            // Add protocol
            server.hysteriaProtocol?.let {
                uriBuilder.appendQueryParameter("protocol", it)
            }
            
            // Add authentication
            server.hysteriaAuthString?.let {
                uriBuilder.appendQueryParameter("auth", it)
            } ?: server.password?.let {
                uriBuilder.appendQueryParameter("auth", it)
            }
            
            // Add SNI
            server.sni?.let {
                uriBuilder.appendQueryParameter("peer", it)
            }
            
            // Add insecure flag
            if (server.allowInsecure == true) {
                uriBuilder.appendQueryParameter("insecure", "1")
            }
            
            // Add bandwidth settings
            server.hysteriaUpMbps?.let {
                uriBuilder.appendQueryParameter("upmbps", it.toString())
            }
            
            server.hysteriaDownMbps?.let {
                uriBuilder.appendQueryParameter("downmbps", it.toString())
            }
            
            // Add obfuscation
            server.hysteriaObfs?.let {
                uriBuilder.appendQueryParameter("obfs", it)
            }
            
            // Add ALPN
            server.alpn?.let {
                uriBuilder.appendQueryParameter("alpn", it)
            }
            
            return uriBuilder.build().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating Hysteria URL", e)
            return ""
        }
    }
    
    /**
     * Helper function to decode Base64 if the string is encoded
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