package com.hiddify.hiddifyng.protocols

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Handler for XHTTP protocol
 * This protocol is designed for improved stealth and penetration through firewalls
 * by disguising traffic as normal HTTP/HTTPS traffic
 */
class XhttpProtocolHandler : ProtocolHandler {
    private val TAG = "XhttpProtocol"
    
    override fun getProtocolName(): String {
        return "xhttp"
    }
    
    override fun generateConfig(server: Server): String {
        try {
            // Create core JSON configuration object for XHTTP
            val config = JSONObject()
            val outbound = JSONObject()
            val xhttpSettings = JSONObject()
            
            // Basic server settings
            outbound.put("protocol", "xhttp")
            outbound.put("tag", "xhttp-outbound")
            
            // Server configuration
            val serverConfig = JSONObject()
            serverConfig.put("address", server.address)
            serverConfig.put("port", server.port)
            
            // XHTTP-specific settings
            xhttpSettings.put("servers", JSONArray().put(serverConfig))
            
            // Security/Encryption
            if (!server.security.isNullOrEmpty()) {
                xhttpSettings.put("security", server.security)
            } else {
                xhttpSettings.put("security", "tls") // Default to TLS
            }
            
            // TLS settings
            if (xhttpSettings.getString("security") == "tls") {
                val tlsSettings = JSONObject()
                
                // Server name (SNI)
                if (!server.sni.isNullOrEmpty()) {
                    tlsSettings.put("serverName", server.sni)
                } else {
                    tlsSettings.put("serverName", server.address)
                }
                
                // Allow insecure connections
                if (server.allowInsecure == true) {
                    tlsSettings.put("allowInsecure", true)
                }
                
                // ALPN protocols
                if (!server.alpn.isNullOrEmpty()) {
                    val alpnArray = JSONArray()
                    server.alpn?.split(",")?.forEach { 
                        alpnArray.put(it.trim()) 
                    }
                    tlsSettings.put("alpn", alpnArray)
                } else {
                    // Default ALPN settings
                    val defaultAlpn = JSONArray()
                    defaultAlpn.put("h2")
                    defaultAlpn.put("http/1.1")
                    tlsSettings.put("alpn", defaultAlpn)
                }
                
                // Fingerprint
                if (!server.fingerprint.isNullOrEmpty()) {
                    tlsSettings.put("fingerprint", server.fingerprint)
                }
                
                xhttpSettings.put("tlsSettings", tlsSettings)
            }
            
            // Path and Host headers (important for XHTTP)
            val headers = JSONObject()
            if (!server.host.isNullOrEmpty()) {
                headers.put("Host", server.host)
            }
            
            if (!server.headers.isNullOrEmpty()) {
                // Parse additional headers (semicolon-separated key:value pairs)
                server.headers?.split(";")?.forEach { header ->
                    val parts = header.split(":", limit = 2)
                    if (parts.size == 2) {
                        headers.put(parts[0].trim(), parts[1].trim())
                    }
                }
            }
            
            if (headers.length() > 0) {
                xhttpSettings.put("headers", headers)
            }
            
            // Add path if specified
            if (!server.path.isNullOrEmpty()) {
                xhttpSettings.put("path", server.path)
            } else {
                xhttpSettings.put("path", "/") // Default path
            }
            
            // UUID for authentication
            if (!server.uuid.isNullOrEmpty()) {
                xhttpSettings.put("uuid", server.uuid)
            }
            
            // Add settings to outbound
            outbound.put("settings", xhttpSettings)
            
            // Mux settings
            if (server.enableMux == true) {
                val mux = JSONObject()
                mux.put("enabled", true)
                mux.put("concurrency", server.muxConcurrency ?: 8)
                outbound.put("mux", mux)
            }
            
            // Add outbound to config
            val outbounds = JSONArray().put(outbound)
            config.put("outbounds", outbounds)
            
            return config.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating XHTTP config", e)
            return "{}"
        }
    }
    
    override fun validateServer(server: Server): Boolean {
        // Basic validation for XHTTP server configuration
        if (server.address.isNullOrEmpty() || server.port <= 0) {
            return false
        }
        
        // UUID is required for XHTTP
        if (server.uuid.isNullOrEmpty()) {
            return false
        }
        
        return true
    }
    
    override fun parseUrl(url: String): Server? {
        try {
            if (!url.startsWith("xhttp://")) {
                return null
            }
            
            // Example URL format:
            // xhttp://uuid@host:port?path=/&security=tls&sni=example.com&fp=chrome&type=http
            
            val uri = Uri.parse(url)
            val userInfo = uri.userInfo
            val host = uri.host ?: return null
            val port = uri.port
            
            if (port <= 0 || userInfo.isNullOrEmpty()) {
                return null
            }
            
            val server = Server(
                name = "XHTTP $host:$port",
                protocol = "xhttp",
                address = host,
                port = port,
                uuid = userInfo  // userInfo contains the UUID
            )
            
            // Parse query parameters
            uri.getQueryParameter("path")?.let {
                server.path = it
            }
            
            uri.getQueryParameter("security")?.let {
                server.security = it
            } ?: run {
                server.security = "tls" // Default security
            }
            
            uri.getQueryParameter("sni")?.let {
                server.sni = it
            }
            
            uri.getQueryParameter("allowInsecure")?.let {
                server.allowInsecure = it == "1" || it.equals("true", ignoreCase = true)
            }
            
            uri.getQueryParameter("alpn")?.let {
                server.alpn = it
            }
            
            uri.getQueryParameter("fp")?.let {
                server.fingerprint = it
            }
            
            uri.getQueryParameter("host")?.let {
                server.host = it
            }
            
            uri.getQueryParameter("headers")?.let {
                server.headers = decodeBase64IfNeeded(it)
            }
            
            uri.getQueryParameter("mux")?.let {
                server.enableMux = it == "1" || it.equals("true", ignoreCase = true)
            }
            
            uri.getQueryParameter("muxConcurrency")?.toIntOrNull()?.let {
                server.muxConcurrency = it
            }
            
            return server
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XHTTP URL", e)
            return null
        }
    }
    
    override fun generateUrl(server: Server): String {
        try {
            // Basic URL structure: xhttp://uuid@host:port?parameters
            val uriBuilder = Uri.Builder()
                .scheme("xhttp")
                .encodedAuthority("${server.uuid}@${server.address}:${server.port}")
            
            // Add path
            server.path?.let {
                uriBuilder.appendQueryParameter("path", it)
            }
            
            // Add security type
            server.security?.let {
                uriBuilder.appendQueryParameter("security", it)
            }
            
            // Add SNI
            server.sni?.let {
                uriBuilder.appendQueryParameter("sni", it)
            }
            
            // Add allowInsecure flag
            if (server.allowInsecure == true) {
                uriBuilder.appendQueryParameter("allowInsecure", "1")
            }
            
            // Add ALPN
            server.alpn?.let {
                uriBuilder.appendQueryParameter("alpn", it)
            }
            
            // Add fingerprint
            server.fingerprint?.let {
                uriBuilder.appendQueryParameter("fp", it)
            }
            
            // Add host header
            server.host?.let {
                uriBuilder.appendQueryParameter("host", it)
            }
            
            // Add additional headers (Base64 encoded)
            server.headers?.let {
                val encodedHeaders = Base64.encodeToString(it.toByteArray(), Base64.URL_SAFE)
                uriBuilder.appendQueryParameter("headers", encodedHeaders)
            }
            
            // Add mux settings
            if (server.enableMux == true) {
                uriBuilder.appendQueryParameter("mux", "1")
                server.muxConcurrency?.let {
                    uriBuilder.appendQueryParameter("muxConcurrency", it.toString())
                }
            }
            
            return uriBuilder.build().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating XHTTP URL", e)
            return ""
        }
    }
    
    /**
     * Helper function to decode Base64 if the string is encoded
     */
    private fun decodeBase64IfNeeded(input: String): String {
        return try {
            val decoded = Base64.decode(input, Base64.URL_SAFE)
            String(decoded)
        } catch (e: Exception) {
            // If it's not Base64, return the original string
            input
        }
    }
}