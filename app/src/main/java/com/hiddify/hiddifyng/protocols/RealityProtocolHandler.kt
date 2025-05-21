package com.hiddify.hiddifyng.protocols

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * Enhanced handler for REALITY protocol - a cutting-edge security enhancement for VLESS
 * Features advanced anti-detection mechanisms, multiple server destinations, and advanced Spider verification
 */
class RealityProtocolHandler : ProtocolHandler {
    private val TAG = "RealityProtocol"
    
    companion object {
        // Default values for REALITY protocol
        private const val DEFAULT_PORT = 443
        private const val DEFAULT_FLOW = "xtls-rprx-vision"
        private const val DEFAULT_ENCRYPTION = "none"
        private const val DEFAULT_NETWORK = "tcp"
        private const val DEFAULT_SECURITY = "reality"
        private const val DEFAULT_PUBLIC_KEY_LENGTH = 43 // Base64 length of typical Ed25519 key
        private const val DEFAULT_FINGERPRINT = "chrome"
        
        // Well-known values
        private const val SHORTID_LENGTH = 8
        
        // Known fingerprints
        private val VALID_FINGERPRINTS = listOf(
            "chrome", "firefox", "safari", "ios", "android", "edge", "360", "qq",
            "random", "randomized", "chrome_105", "chrome_106", "chrome_107", "chrome_108", 
            "chrome_109", "chrome_110", "chrome_111", "chrome_112", "chrome_113", "chrome_114",
            "firefox_102", "firefox_103", "firefox_104", "firefox_105", "firefox_106",
            "edge_106", "edge_107", "edge_108", "edge_109", "safari_15_6_1", "safari_16_0"
        )
        
        // Known network types
        private val SUPPORTED_NETWORKS = listOf(
            "tcp", "kcp", "ws", "http", "quic", "grpc", "httpupgrade", "tuic", "h2"
        )
    }
    
    override fun getProtocolName(): String = "reality"
    
    /**
     * Generate Xray-compatible configuration for REALITY protocol with extended features
     * @param server Server configuration
     * @return JSON configuration string optimized for Xray core
     */
    override fun generateConfig(server: Server): String {
        try {
            // Create root configuration object
            val rootConfig = JSONObject()
            
            // Create outbounds array
            val outbounds = JSONArray()
            
            // Main outbound (VLESS + REALITY)
            val mainOutbound = JSONObject().apply {
                put("tag", "proxy")
                put("protocol", "vless")
                
                // VLESS settings
                val settings = JSONObject().apply {
                    // VLESS requires servers array
                    val serverObj = JSONObject().apply {
                        put("address", server.address)
                        put("port", server.port)
                        put("users", JSONArray().apply {
                            put(JSONObject().apply {
                                // UUID is required for VLESS
                                put("id", server.uuid)
                                // Flow control for XTLS Vision
                                put("flow", server.flow ?: DEFAULT_FLOW)
                                // VLESS encryption must be "none"
                                put("encryption", server.encryption ?: DEFAULT_ENCRYPTION)
                            })
                        })
                    }
                    put("vnext", JSONArray().put(serverObj))
                }
                put("settings", settings)
                
                // Stream settings with REALITY
                val streamSettings = JSONObject().apply {
                    // Network protocol (usually tcp for REALITY)
                    put("network", server.network ?: DEFAULT_NETWORK)
                    
                    // Security type must be "reality"
                    put("security", DEFAULT_SECURITY)
                    
                    // REALITY settings
                    val realitySettings = JSONObject().apply {
                        // Required: server public key for encryption
                        put("publicKey", server.publicKey)
                        
                        // Enhanced: Support for multiple server destinations
                        if (!server.serverNames.isNullOrEmpty()) {
                            // Split comma-separated server names into array
                            val serverNameArray = JSONArray()
                            server.serverNames?.split(",")?.map { it.trim() }?.forEach { 
                                if (it.isNotEmpty()) serverNameArray.put(it)
                            }
                            
                            // Add default serverName if not in list
                            val sniHostname = server.sni ?: server.address
                            var containsDefault = false
                            
                            for (i in 0 until serverNameArray.length()) {
                                if (serverNameArray.getString(i) == sniHostname) {
                                    containsDefault = true
                                    break
                                }
                            }
                            
                            if (!containsDefault && serverNameArray.length() > 0) {
                                serverNameArray.put(sniHostname)
                            }
                            
                            // Add server names array if not empty
                            if (serverNameArray.length() > 0) {
                                put("serverNames", serverNameArray)
                            }
                        }
                        
                        // Required: Primary Server Name Indication for TLS
                        put("serverName", server.sni ?: server.address)
                        
                        // Optional: REALITY short ID (server-side ID verification)
                        server.shortId?.let { put("shortId", it) }
                        
                        // Optional: Fingerprint to mimic (e.g., "chrome", "firefox", etc)
                        server.fingerprint?.takeIf { it.isNotEmpty() }?.let {
                            put("fingerprint", it)
                        } ?: put("fingerprint", DEFAULT_FINGERPRINT)
                        
                        // Enhanced: Support for multiple fingerprints
                        if (!server.utlsFingerprints.isNullOrEmpty()) {
                            val fingerprintArray = JSONArray()
                            server.utlsFingerprints?.split(",")?.map { it.trim() }?.forEach {
                                if (it.isNotEmpty() && VALID_FINGERPRINTS.contains(it.lowercase())) {
                                    fingerprintArray.put(it)
                                }
                            }
                            
                            if (fingerprintArray.length() > 0) {
                                put("fingerprints", fingerprintArray)
                            }
                        }
                        
                        // Advanced: Spider verification (X and Y parameters)
                        server.spiderX?.let { put("spiderX", it) }
                        server.spiderY?.let { put("spiderY", it) }
                        
                        // Optional: User-provided private key
                        server.privateKey?.let { put("privateKey", it) }
                        
                        // Show network for uTLS handshake
                        server.showNetwork?.let { put("showNetwork", it) }
                        
                        // uTLS Version
                        server.utlsVersion?.let { put("utlsVersion", it) }
                    }
                    put("realitySettings", realitySettings)
                    
                    // Network-specific settings based on the network type
                    when (server.network?.lowercase(Locale.getDefault())) {
                        null, "tcp" -> {
                            val tcpSettings = JSONObject()
                            server.headerType?.let {
                                val header = JSONObject().apply {
                                    put("type", it)
                                    if (it == "http") {
                                        // HTTP header configuration for obfuscation
                                        val request = JSONObject()
                                        val headers = JSONObject()
                                        
                                        // Add Host header if specified
                                        server.host?.let { host ->
                                            headers.put("Host", JSONArray().put(host))
                                        }
                                        
                                        // Parse additional headers
                                        server.headers?.split(";")?.forEach { header ->
                                            val parts = header.split(":", limit = 2)
                                            if (parts.size == 2) {
                                                val key = parts[0].trim()
                                                val value = parts[1].trim()
                                                if (key.isNotEmpty() && value.isNotEmpty()) {
                                                    headers.put(key, JSONArray().put(value))
                                                }
                                            }
                                        }
                                        
                                        request.put("headers", headers)
                                        put("request", request)
                                    }
                                }
                                tcpSettings.put("header", header)
                            }
                            put("tcpSettings", tcpSettings)
                        }
                        
                        "ws" -> {
                            val wsSettings = JSONObject().apply {
                                server.path?.let { put("path", it) }
                                server.host?.let { 
                                    put("headers", JSONObject().apply {
                                        put("Host", it)
                                    }) 
                                }
                                
                                // Parse additional headers
                                val headers = JSONObject()
                                server.host?.let { headers.put("Host", it) }
                                
                                server.headers?.split(";")?.forEach { header ->
                                    val parts = header.split(":", limit = 2)
                                    if (parts.size == 2) {
                                        headers.put(parts[0].trim(), parts[1].trim())
                                    }
                                }
                                
                                if (headers.length() > 0) {
                                    put("headers", headers)
                                }
                            }
                            put("wsSettings", wsSettings)
                        }
                        
                        "grpc" -> {
                            val grpcSettings = JSONObject().apply {
                                server.path?.let { put("serviceName", it) }
                                server.grpcMultiMode?.let { put("multiMode", it) }
                            }
                            put("grpcSettings", grpcSettings)
                        }
                        
                        "h2", "http2" -> {
                            val httpSettings = JSONObject().apply {
                                // Multiple hosts for HTTP/2
                                val hosts = JSONArray()
                                server.host?.let { hosts.put(it) }
                                server.serverNames?.split(",")?.forEach { hosts.put(it.trim()) }
                                
                                if (hosts.length() > 0) {
                                    put("host", hosts)
                                }
                                
                                server.path?.let { put("path", it) }
                                
                                // HTTP/2 specific settings
                                server.allowHttp2?.let { put("read_idle_timeout", 60) }
                            }
                            put("httpSettings", httpSettings)
                        }
                        
                        "httpupgrade" -> {
                            val httpupgradeSettings = JSONObject().apply {
                                server.path?.let { put("path", it) }
                                server.host?.let { 
                                    put("headers", JSONObject().apply {
                                        put("Host", it)
                                    }) 
                                }
                            }
                            put("httpupgradeSettings", httpupgradeSettings)
                        }
                        
                        "quic" -> {
                            val quicSettings = JSONObject().apply {
                                server.quicSecurity?.let { put("security", it) }
                                server.quicKey?.let { put("key", it) }
                                server.headerType?.let { put("header", JSONObject().put("type", it)) }
                            }
                            put("quicSettings", quicSettings)
                        }
                    }
                }
                put("streamSettings", streamSettings)
                
                // Add mux settings if enabled
                if (server.enableMux == true) {
                    val mux = JSONObject().apply {
                        put("enabled", true)
                        put("concurrency", server.muxConcurrency ?: 8)
                    }
                    put("mux", mux)
                }
            }
            
            // Add main outbound to array
            outbounds.put(mainOutbound)
            
            // Add direct outbound for bypass rules
            val directOutbound = JSONObject().apply {
                put("tag", "direct")
                put("protocol", "freedom")
                put("settings", JSONObject())
            }
            outbounds.put(directOutbound)
            
            // Add block outbound for blocking rules
            val blockOutbound = JSONObject().apply {
                put("tag", "block")
                put("protocol", "blackhole")
                put("settings", JSONObject())
            }
            outbounds.put(blockOutbound)
            
            // Add outbounds to root config
            rootConfig.put("outbounds", outbounds)
            
            return rootConfig.toString(2)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating REALITY config", e)
            // Return minimal valid configuration in case of error
            return """{"outbounds":[{"protocol":"vless","settings":{},"tag":"proxy"}]}"""
        }
    }
    
    /**
     * Validate server configuration for REALITY protocol with enhanced validation
     * @param server Server configuration to validate
     * @return true if valid, false otherwise with error logging
     */
    override fun validateServer(server: Server): Boolean {
        // Check server address
        if (server.address.isNullOrEmpty()) {
            Log.w(TAG, "Invalid REALITY server: missing address")
            return false
        }
        
        // Validate port
        if (server.port <= 0 || server.port > 65535) {
            Log.w(TAG, "Invalid REALITY server: port out of range (${server.port})")
            return false
        }
        
        // Validate UUID (essential for VLESS)
        if (server.uuid.isNullOrEmpty()) {
            Log.w(TAG, "Invalid REALITY server: missing UUID")
            return false
        }
        
        try {
            // Verify UUID format
            UUID.fromString(server.uuid)
        } catch (e: Exception) {
            Log.w(TAG, "Invalid REALITY server: malformed UUID")
            return false
        }
        
        // Validate public key (essential for REALITY)
        if (server.publicKey.isNullOrEmpty()) {
            Log.w(TAG, "Invalid REALITY server: missing public key")
            return false
        }
        
        // Check if public key has reasonable length
        if (server.publicKey?.length ?: 0 < DEFAULT_PUBLIC_KEY_LENGTH) {
            Log.w(TAG, "Invalid REALITY server: public key seems too short")
            return false
        }
        
        // Check SNI presence
        if (server.sni.isNullOrEmpty()) {
            Log.w(TAG, "REALITY server warning: missing SNI (using address as fallback)")
            // Not a fatal error, we'll use address as fallback
        }
        
        // Validate network type if specified
        if (!server.network.isNullOrEmpty() && !SUPPORTED_NETWORKS.contains(server.network?.lowercase(Locale.getDefault()))) {
            Log.w(TAG, "REALITY server warning: unsupported network type '${server.network}' (using TCP)")
            // Not fatal, we'll use TCP as fallback
        }
        
        // Validate multiple server names if provided
        if (!server.serverNames.isNullOrEmpty()) {
            // Check that at least one server name is valid
            val validNames = server.serverNames?.split(",")?.filter { it.trim().isNotEmpty() }
            if (validNames.isNullOrEmpty()) {
                Log.w(TAG, "REALITY server warning: no valid server names in serverNames list")
                // Not fatal
            }
        }
        
        // Validate fingerprint if specified
        if (!server.fingerprint.isNullOrEmpty() && 
            !VALID_FINGERPRINTS.contains(server.fingerprint?.lowercase(Locale.getDefault()))) {
            Log.w(TAG, "REALITY server warning: fingerprint '${server.fingerprint}' not recognized (using chrome)")
            // Not fatal, we'll use chrome as fallback
        }
        
        return true
    }
    
    /**
     * Parse REALITY URL into Server object with enhanced parameter support
     * @param url REALITY URL string (reality:// or vless+reality://)
     * @return Server object or null if parsing failed
     */
    override fun parseUrl(url: String): Server? {
        try {
            // Handle various possible URL formats for REALITY
            val processedUrl = when {
                url.startsWith("reality://") -> url
                url.startsWith("vless+reality://") -> url.replace("vless+reality://", "reality://")
                url.startsWith("vless://") && url.contains("security=reality") -> 
                    url.replace("vless://", "reality://")
                else -> return null
            }
            
            // Parse URI
            val uri = Uri.parse(processedUrl)
            val userInfo = uri.userInfo
            val host = uri.host ?: return null
            val port = uri.port.takeIf { it > 0 } ?: DEFAULT_PORT
            
            // UUID is required in userInfo
            if (userInfo.isNullOrEmpty()) {
                Log.w(TAG, "Invalid REALITY URL: missing UUID in userInfo")
                return null
            }
            
            // Create server with required fields
            val server = Server(
                name = "REALITY $host:$port",
                protocol = "reality", // We'll handle this in Xray as VLESS+REALITY
                address = host,
                port = port,
                uuid = userInfo.trim(),  // userInfo contains the UUID
                security = DEFAULT_SECURITY,
                network = DEFAULT_NETWORK
            )
            
            // Process all query parameters with enhanced parameter support
            uri.queryParameterNames.forEach { param ->
                try {
                    when (param.lowercase(Locale.getDefault())) {
                        // Network type parameters
                        "type", "network" -> uri.getQueryParameter(param)?.let { server.network = it }
                        "path" -> uri.getQueryParameter(param)?.let { server.path = it }
                        "host" -> uri.getQueryParameter(param)?.let { server.host = it }
                        
                        // Core REALITY parameters
                        "sni" -> uri.getQueryParameter(param)?.let { server.sni = it }
                        "pbk", "publickey" -> uri.getQueryParameter(param)?.let { server.publicKey = it }
                        "fp", "fingerprint" -> uri.getQueryParameter(param)?.let { server.fingerprint = it }
                        "sid", "shortid" -> uri.getQueryParameter(param)?.let { server.shortId = it }
                        
                        // Advanced REALITY parameters
                        "spx", "spiderx" -> uri.getQueryParameter(param)?.let { server.spiderX = it }
                        "spy", "spidery" -> uri.getQueryParameter(param)?.let { server.spiderY = it }
                        "sni-list", "servernames", "serverNames" -> uri.getQueryParameter(param)?.let { 
                            server.serverNames = it 
                        }
                        "pbk-list", "pbks", "publickeys" -> uri.getQueryParameter(param)?.let {
                            // Multiple public keys not supported yet, use the first one
                            val keys = it.split(",")
                            if (keys.isNotEmpty() && server.publicKey.isNullOrEmpty()) {
                                server.publicKey = keys[0].trim()
                            }
                        }
                        "utls-version", "utlsVersion" -> uri.getQueryParameter(param)?.let { 
                            server.utlsVersion = it 
                        }
                        "show-network", "showNetwork" -> uri.getQueryParameter(param)?.let {
                            server.showNetwork = it == "1" || it.equals("true", ignoreCase = true)
                        }
                        
                        // Multiple fingerprints
                        "fp-list", "fingerprints", "utlsFingerprints" -> uri.getQueryParameter(param)?.let {
                            server.utlsFingerprints = it
                        }
                        
                        // VLESS parameters
                        "flow" -> uri.getQueryParameter(param)?.let { server.flow = it }
                        "encryption" -> uri.getQueryParameter(param)?.let { server.encryption = it }
                        
                        // Transport parameters
                        "headerType" -> uri.getQueryParameter(param)?.let { server.headerType = it }
                        "serviceName" -> uri.getQueryParameter(param)?.let { server.path = it } // gRPC service name
                        "quicsecurity" -> uri.getQueryParameter(param)?.let { server.quicSecurity = it }
                        "quickey" -> uri.getQueryParameter(param)?.let { server.quicKey = it }
                        "allowinsecure", "allowInsecure" -> uri.getQueryParameter(param)?.let {
                            server.allowInsecure = it == "1" || it.equals("true", ignoreCase = true)
                        }
                        "allowhttp2", "allowHttp2" -> uri.getQueryParameter(param)?.let {
                            server.allowHttp2 = it == "1" || it.equals("true", ignoreCase = true)
                        }
                        
                        // Headers parameter (Base64 encoded semicolon-separated key:value)
                        "headers" -> uri.getQueryParameter(param)?.let {
                            try {
                                val decodedHeaders = decodeBase64IfNeeded(it)
                                server.headers = decodedHeaders
                            } catch (e: Exception) {
                                // If decoding fails, use as-is
                                server.headers = it
                            }
                        }
                        
                        // Connection parameters
                        "mux" -> uri.getQueryParameter(param)?.let {
                            server.enableMux = it == "1" || it.equals("true", ignoreCase = true)
                        }
                        "muxConcurrency" -> uri.getQueryParameter(param)?.toIntOrNull()?.let {
                            if (it > 0) server.muxConcurrency = it
                        }
                        
                        // gRPC mode
                        "mode" -> uri.getQueryParameter(param)?.let {
                            if (it == "multi" || it == "gun") server.grpcMultiMode = true
                        }
                        
                        // Private key (rarely used)
                        "privkey", "privatekey" -> uri.getQueryParameter(param)?.let {
                            server.privateKey = it
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing parameter '$param': ${e.message}")
                }
            }
            
            // Set defaults if not specified
            if (server.fingerprint.isNullOrEmpty()) {
                server.fingerprint = DEFAULT_FINGERPRINT
            }
            
            if (server.flow.isNullOrEmpty()) {
                server.flow = DEFAULT_FLOW
            }
            
            if (server.encryption.isNullOrEmpty()) {
                server.encryption = DEFAULT_ENCRYPTION
            }
            
            // Validate the parsed server
            return if (validateServer(server)) server else null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing REALITY URL", e)
            return null
        }
    }
    
    /**
     * Generate shareable URL for this REALITY server with enhanced parameters
     * @param server Server configuration
     * @return URL string for sharing
     */
    override fun generateUrl(server: Server): String {
        try {
            // Validate required fields
            if (server.address.isNullOrEmpty() || 
                server.uuid.isNullOrEmpty() || 
                server.publicKey.isNullOrEmpty()) {
                Log.e(TAG, "Cannot generate URL: missing required fields")
                return ""
            }
            
            // Basic URL structure: reality://uuid@host:port?params
            val uriBuilder = Uri.Builder()
                .scheme("reality")
                .encodedAuthority("${server.uuid}@${server.address}:${server.port}")
            
            // Add all the required and optional parameters
            
            // Public key (required for REALITY)
            server.publicKey?.let {
                uriBuilder.appendQueryParameter("pbk", it)
            }
            
            // SNI (required for REALITY)
            server.sni?.let {
                uriBuilder.appendQueryParameter("sni", it)
            }
            
            // Short ID
            server.shortId?.let {
                uriBuilder.appendQueryParameter("sid", it)
            }
            
            // Spider X
            server.spiderX?.let {
                uriBuilder.appendQueryParameter("spx", it)
            }
            
            // Enhanced: Spider Y
            server.spiderY?.let {
                uriBuilder.appendQueryParameter("spy", it)
            }
            
            // Enhanced: Multiple server names
            server.serverNames?.let {
                uriBuilder.appendQueryParameter("serverNames", it)
            }
            
            // Fingerprint
            server.fingerprint?.let {
                if (it != DEFAULT_FINGERPRINT) {
                    uriBuilder.appendQueryParameter("fp", it)
                }
            }
            
            // Enhanced: Multiple fingerprints
            server.utlsFingerprints?.let {
                uriBuilder.appendQueryParameter("fingerprints", it)
            }
            
            // uTLS version
            server.utlsVersion?.let {
                uriBuilder.appendQueryParameter("utlsVersion", it)
            }
            
            // Show network
            if (server.showNetwork == true) {
                uriBuilder.appendQueryParameter("showNetwork", "1")
            }
            
            // Flow control
            server.flow?.let {
                if (it != DEFAULT_FLOW) {
                    uriBuilder.appendQueryParameter("flow", it)
                }
            }
            
            // Network type
            if (server.network != DEFAULT_NETWORK && !server.network.isNullOrEmpty()) {
                uriBuilder.appendQueryParameter("type", server.network)
                
                // Add network-specific parameters
                when (server.network?.lowercase(Locale.getDefault())) {
                    "ws" -> {
                        server.path?.let { uriBuilder.appendQueryParameter("path", it) }
                        server.host?.let { uriBuilder.appendQueryParameter("host", it) }
                    }
                    "grpc" -> {
                        server.path?.let { uriBuilder.appendQueryParameter("serviceName", it) }
                        if (server.grpcMultiMode == true) {
                            uriBuilder.appendQueryParameter("mode", "multi")
                        }
                    }
                    "tcp" -> {
                        server.headerType?.let { uriBuilder.appendQueryParameter("headerType", it) }
                    }
                    "h2", "http2" -> {
                        server.path?.let { uriBuilder.appendQueryParameter("path", it) }
                        server.host?.let { uriBuilder.appendQueryParameter("host", it) }
                        if (server.allowHttp2 == true) {
                            uriBuilder.appendQueryParameter("allowHttp2", "1")
                        }
                    }
                    "quic" -> {
                        server.quicSecurity?.let { uriBuilder.appendQueryParameter("quicSecurity", it) }
                        server.quicKey?.let { uriBuilder.appendQueryParameter("quicKey", it) }
                        server.headerType?.let { uriBuilder.appendQueryParameter("headerType", it) }
                    }
                    "httpupgrade" -> {
                        server.path?.let { uriBuilder.appendQueryParameter("path", it) }
                        server.host?.let { uriBuilder.appendQueryParameter("host", it) }
                    }
                }
            }
            
            // Headers (Base64 encoded)
            server.headers?.let {
                try {
                    val encodedHeaders = Base64.encodeToString(
                        it.toByteArray(), 
                        Base64.URL_SAFE or Base64.NO_PADDING
                    )
                    uriBuilder.appendQueryParameter("headers", encodedHeaders)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to encode headers, using raw", e)
                    uriBuilder.appendQueryParameter("headers", it)
                }
            }
            
            // MUX settings
            if (server.enableMux == true) {
                uriBuilder.appendQueryParameter("mux", "1")
                server.muxConcurrency?.let {
                    uriBuilder.appendQueryParameter("muxConcurrency", it.toString())
                }
            }
            
            // Private key (rarely used)
            server.privateKey?.let {
                uriBuilder.appendQueryParameter("privateKey", it)
            }
            
            return uriBuilder.build().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating REALITY URL", e)
            return ""
        }
    }
    
    /**
     * Helper function to decode Base64 if the string is encoded
     * Supports multiple Base64 encoding formats
     * 
     * @param input Potentially Base64 encoded string
     * @return Decoded string or original if not Base64
     */
    private fun decodeBase64IfNeeded(input: String): String {
        return try {
            // Try different Base64 decoding flags
            val decoded = try {
                Base64.decode(input, Base64.URL_SAFE)
            } catch (e: Exception) {
                try {
                    Base64.decode(input, Base64.DEFAULT)
                } catch (e2: Exception) {
                    try {
                        Base64.decode(input, Base64.URL_SAFE or Base64.NO_PADDING)
                    } catch (e3: Exception) {
                        // Last attempt with default and no padding
                        Base64.decode(input, Base64.DEFAULT or Base64.NO_PADDING)
                    }
                }
            }
            String(decoded)
        } catch (e: Exception) {
            // If it's not Base64, return the original string
            input
        }
    }
}