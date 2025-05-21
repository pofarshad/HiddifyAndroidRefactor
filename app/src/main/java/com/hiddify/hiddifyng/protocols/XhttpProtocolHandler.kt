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
    
    companion object {
        // Default values for XHTTP protocol
        private const val DEFAULT_SECURITY = "tls"
        private const val DEFAULT_PATH = "/"
        private const val DEFAULT_MUX_CONCURRENCY = 8
        private const val DEFAULT_PORT = 443
    }
    
    override fun getProtocolName(): String = "xhttp"
    
    /**
     * Generate Xray-compatible configuration for XHTTP protocol
     * @param server Server configuration
     * @return JSON configuration string optimized for Xray core
     */
    override fun generateConfig(server: Server): String {
        try {
            // Create root configuration object
            val rootConfig = JSONObject()
            
            // Create outbounds array with main outbound and fallbacks
            val outbounds = JSONArray()
            
            // Main outbound for XHTTP
            val mainOutbound = JSONObject().apply {
                put("protocol", "xhttp")
                put("tag", "proxy")
                
                // Create settings object
                val settings = JSONObject().apply {
                    // Add server configuration
                    val serverConfig = JSONObject().apply {
                        put("address", server.address)
                        put("port", server.port)
                    }
                    put("servers", JSONArray().put(serverConfig))
                    
                    // Security/Encryption with default fallback
                    put("security", server.security ?: DEFAULT_SECURITY)
                    
                    // TLS settings when security is set to TLS
                    if (getString("security") == "tls") {
                        val tlsSettings = JSONObject().apply {
                            // Server name (SNI) with fallback to address
                            put("serverName", server.sni ?: server.address)
                            
                            // Allow insecure connections only when explicitly set
                            if (server.allowInsecure == true) {
                                put("allowInsecure", true)
                            }
                            
                            // ALPN protocols with defaults
                            val alpnArray = JSONArray()
                            if (!server.alpn.isNullOrEmpty()) {
                                server.alpn?.split(",")?.forEach { alpnArray.put(it.trim()) }
                            } else {
                                // Default ALPN settings for optimal compatibility
                                alpnArray.put("h2")
                                alpnArray.put("http/1.1")
                            }
                            put("alpn", alpnArray)
                            
                            // Add fingerprint for TLS if specified
                            server.fingerprint?.takeIf { it.isNotEmpty() }?.let {
                                put("fingerprint", it)
                            }
                        }
                        put("tlsSettings", tlsSettings)
                    }
                    
                    // Path with default
                    put("path", server.path?.takeIf { it.isNotEmpty() } ?: DEFAULT_PATH)
                    
                    // UUID for authentication (required)
                    server.uuid?.let { put("uuid", it) }
                    
                    // HTTP headers for stealth
                    if (!server.host.isNullOrEmpty() || !server.headers.isNullOrEmpty()) {
                        val headers = JSONObject()
                        
                        // Add Host header if specified
                        server.host?.takeIf { it.isNotEmpty() }?.let {
                            headers.put("Host", it)
                        }
                        
                        // Parse and add custom headers
                        server.headers?.takeIf { it.isNotEmpty() }?.let { headerStr ->
                            headerStr.split(";").forEach { header ->
                                val parts = header.split(":", limit = 2)
                                if (parts.size == 2) {
                                    headers.put(parts[0].trim(), parts[1].trim())
                                }
                            }
                        }
                        
                        if (headers.length() > 0) {
                            put("headers", headers)
                        }
                    }
                }
                put("settings", settings)
                
                // Add multiplexing settings if enabled
                if (server.enableMux == true) {
                    val mux = JSONObject().apply {
                        put("enabled", true)
                        put("concurrency", server.muxConcurrency ?: DEFAULT_MUX_CONCURRENCY)
                    }
                    put("mux", mux)
                }
            }
            
            // Add main outbound to array
            outbounds.put(mainOutbound)
            
            // Add direct outbound for bypass rules
            val directOutbound = JSONObject().apply {
                put("protocol", "freedom")
                put("tag", "direct")
                put("settings", JSONObject())
            }
            outbounds.put(directOutbound)
            
            // Add blackhole outbound for blocking
            val blockOutbound = JSONObject().apply {
                put("protocol", "blackhole")
                put("tag", "block")
                put("settings", JSONObject())
            }
            outbounds.put(blockOutbound)
            
            // Add all outbounds to root config
            rootConfig.put("outbounds", outbounds)
            
            return rootConfig.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating XHTTP config", e)
            return """{"outbounds":[{"protocol":"xhttp","tag":"proxy","settings":{}}]}"""
        }
    }
    
    /**
     * Validate server configuration for XHTTP protocol
     * @param server Server configuration to validate
     * @return true if valid, false otherwise with error logging
     */
    override fun validateServer(server: Server): Boolean {
        // Check server address
        if (server.address.isNullOrEmpty()) {
            Log.w(TAG, "Invalid XHTTP server: missing address")
            return false
        }
        
        // Validate port
        if (server.port <= 0 || server.port > 65535) {
            Log.w(TAG, "Invalid XHTTP server: port out of range (${server.port})")
            return false
        }
        
        // Check UUID (required for authentication)
        if (server.uuid.isNullOrEmpty()) {
            Log.w(TAG, "Invalid XHTTP server: missing UUID")
            return false
        }
        
        // Validate security setting if present
        server.security?.let {
            if (it !in listOf("tls", "none")) {
                Log.w(TAG, "Invalid XHTTP server: unsupported security mode ($it)")
                return false
            }
        }
        
        return true
    }
    
    /**
     * Parse XHTTP URL into Server object with comprehensive parameter handling
     * @param url XHTTP URL string
     * @return Server object or null if parsing failed
     */
    override fun parseUrl(url: String): Server? {
        try {
            // Validate URL scheme
            if (!url.startsWith("xhttp://")) {
                return null
            }
            
            // Example URL format:
            // xhttp://uuid@host:port?path=/&security=tls&sni=example.com&fp=chrome&type=http
            
            val uri = Uri.parse(url)
            val userInfo = uri.userInfo
            val host = uri.host ?: return null
            val port = uri.port.takeIf { it > 0 } ?: DEFAULT_PORT
            
            // UUID is required in userInfo
            if (userInfo.isNullOrEmpty()) {
                Log.w(TAG, "Invalid XHTTP URL: missing UUID in userInfo")
                return null
            }
            
            // Create server with required fields
            val server = Server(
                name = "XHTTP $host:$port",
                protocol = getProtocolName(),
                address = host,
                port = port,
                uuid = userInfo.trim()  // userInfo contains the UUID
            )
            
            // Process all query parameters with proper error handling
            uri.queryParameterNames.forEach { param ->
                try {
                    when (param.lowercase(Locale.getDefault())) {
                        "path" -> uri.getQueryParameter(param)?.let { server.path = it }
                        "security" -> uri.getQueryParameter(param)?.let { 
                            if (it in listOf("tls", "none")) {
                                server.security = it 
                            } else {
                                Log.w(TAG, "Ignoring invalid security value: $it")
                                server.security = DEFAULT_SECURITY
                            }
                        }
                        "sni" -> uri.getQueryParameter(param)?.let { server.sni = it }
                        "allowinsecure" -> uri.getQueryParameter(param)?.let {
                            server.allowInsecure = it == "1" || it.equals("true", ignoreCase = true)
                        }
                        "alpn" -> uri.getQueryParameter(param)?.let { server.alpn = it }
                        "fp" -> uri.getQueryParameter(param)?.let { server.fingerprint = it }
                        "host" -> uri.getQueryParameter(param)?.let { server.host = it }
                        "headers" -> uri.getQueryParameter(param)?.let {
                            server.headers = decodeBase64IfNeeded(it)
                        }
                        "mux" -> uri.getQueryParameter(param)?.let {
                            server.enableMux = it == "1" || it.equals("true", ignoreCase = true)
                        }
                        "muxconcurrency" -> uri.getQueryParameter(param)?.toIntOrNull()?.let {
                            if (it > 0) {
                                server.muxConcurrency = it
                            } else {
                                Log.w(TAG, "Ignoring invalid muxConcurrency: $it")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing parameter '$param': ${e.message}")
                }
            }
            
            // Set default security if not specified
            if (server.security == null) {
                server.security = DEFAULT_SECURITY
            }
            
            // Validate the parsed server
            return if (validateServer(server)) server else null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XHTTP URL", e)
            return null
        }
    }
    
    /**
     * Generate shareable URL for this server
     * @param server Server configuration
     * @return XHTTP URL for sharing, with optimal parameters
     */
    override fun generateUrl(server: Server): String {
        try {
            // Validate required fields
            if (server.address.isNullOrEmpty() || server.uuid.isNullOrEmpty()) {
                Log.e(TAG, "Cannot generate URL: missing required fields")
                return ""
            }
            
            // Basic URL structure: xhttp://uuid@host:port?parameters
            val uriBuilder = Uri.Builder()
                .scheme("xhttp")
                .encodedAuthority("${server.uuid}@${server.address}:${server.port}")
            
            // Add only non-default parameters for a cleaner URL
            
            // Add path if not default
            if (!server.path.isNullOrEmpty() && server.path != DEFAULT_PATH) {
                uriBuilder.appendQueryParameter("path", server.path)
            }
            
            // Add security type if not default
            if (!server.security.isNullOrEmpty() && server.security != DEFAULT_SECURITY) {
                uriBuilder.appendQueryParameter("security", server.security)
            }
            
            // Add SNI if different from address
            if (!server.sni.isNullOrEmpty() && server.sni != server.address) {
                uriBuilder.appendQueryParameter("sni", server.sni)
            }
            
            // Add allowInsecure flag only if true
            if (server.allowInsecure == true) {
                uriBuilder.appendQueryParameter("allowInsecure", "1")
            }
            
            // Add ALPN if specified
            if (!server.alpn.isNullOrEmpty()) {
                uriBuilder.appendQueryParameter("alpn", server.alpn)
            }
            
            // Add fingerprint if specified
            if (!server.fingerprint.isNullOrEmpty()) {
                uriBuilder.appendQueryParameter("fp", server.fingerprint)
            }
            
            // Add host header if specified
            if (!server.host.isNullOrEmpty()) {
                uriBuilder.appendQueryParameter("host", server.host)
            }
            
            // Add additional headers (Base64 encoded) if specified
            if (!server.headers.isNullOrEmpty()) {
                val encodedHeaders = Base64.encodeToString(
                    server.headers!!.toByteArray(), 
                    Base64.URL_SAFE or Base64.NO_PADDING
                )
                uriBuilder.appendQueryParameter("headers", encodedHeaders)
            }
            
            // Add mux settings if enabled
            if (server.enableMux == true) {
                uriBuilder.appendQueryParameter("mux", "1")
                
                // Add muxConcurrency only if not default
                if (server.muxConcurrency != null && server.muxConcurrency != DEFAULT_MUX_CONCURRENCY) {
                    uriBuilder.appendQueryParameter("muxConcurrency", server.muxConcurrency.toString())
                }
            }
            
            return uriBuilder.build().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating XHTTP URL", e)
            // Return basic URL with required parameters
            return "xhttp://${server.uuid}@${server.address}:${server.port}"
        }
    }
    
    /**
     * Helper function to decode Base64 if the string is encoded
     * @param input Potentially Base64 encoded string
     * @return Decoded string or original if not Base64
     */
    private fun decodeBase64IfNeeded(input: String): String {
        return try {
            // Try with different flags to handle various Base64 formats
            val decoded = try {
                Base64.decode(input, Base64.URL_SAFE)
            } catch (e: Exception) {
                Base64.decode(input, Base64.DEFAULT)
            }
            String(decoded)
        } catch (e: Exception) {
            // If it's not Base64, return the original string
            input
        }
    }
}