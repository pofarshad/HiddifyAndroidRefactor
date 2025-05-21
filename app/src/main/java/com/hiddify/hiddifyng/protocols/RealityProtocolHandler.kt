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
 * Handler for REALITY protocol - a cutting-edge security enhancement for VLESS
 * Focuses on server fingerprint simulation and advanced anti-detection features
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
        
        // Well-known shortid length
        private const val SHORTID_LENGTH = 8
    }
    
    override fun getProtocolName(): String = "reality"
    
    /**
     * Generate Xray-compatible configuration for REALITY protocol
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
                        
                        // Required: Server Name Indication for TLS
                        put("serverName", server.sni ?: server.address)
                        
                        // Optional: REALITY short ID (server-side ID verification)
                        server.shortId?.let { put("shortId", it) }
                        
                        // Optional: Fingerprint to mimic (e.g., "chrome", "firefox", etc)
                        server.fingerprint?.takeIf { it.isNotEmpty() }?.let {
                            put("fingerprint", it)
                        } ?: put("fingerprint", "chrome")
                        
                        // Optional: spider x for server-side validation
                        server.spiderX?.let { put("spiderX", it) }
                    }
                    put("realitySettings", realitySettings)
                    
                    // TCP settings for most REALITY configurations
                    if (server.network == "tcp" || server.network == null) {
                        val tcpSettings = JSONObject()
                        // Add any TCP-specific settings if needed
                        put("tcpSettings", tcpSettings)
                    }
                    
                    // Handle other network types if specified
                    when (server.network) {
                        "ws" -> {
                            val wsSettings = JSONObject().apply {
                                server.path?.let { put("path", it) }
                                server.host?.let { 
                                    put("headers", JSONObject().apply {
                                        put("Host", it)
                                    }) 
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
     * Validate server configuration for REALITY protocol
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
        
        return true
    }
    
    /**
     * Parse REALITY URL into Server object
     * @param url REALITY URL string (reality:// or vless+reality://)
     * @return Server object or null if parsing failed
     */
    override fun parseUrl(url: String): Server? {
        try {
            // Handle two possible URL formats
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
            
            // Process all query parameters
            uri.queryParameterNames.forEach { param ->
                try {
                    when (param.lowercase(Locale.getDefault())) {
                        "type", "network" -> uri.getQueryParameter(param)?.let { server.network = it }
                        "path" -> uri.getQueryParameter(param)?.let { server.path = it }
                        "host" -> uri.getQueryParameter(param)?.let { server.host = it }
                        "sni" -> uri.getQueryParameter(param)?.let { server.sni = it }
                        "pbk", "publickey" -> uri.getQueryParameter(param)?.let { server.publicKey = it }
                        "fp", "fingerprint" -> uri.getQueryParameter(param)?.let { server.fingerprint = it }
                        "sid", "shortid" -> uri.getQueryParameter(param)?.let { server.shortId = it }
                        "spx", "spiderx" -> uri.getQueryParameter(param)?.let { server.spiderX = it }
                        "flow" -> uri.getQueryParameter(param)?.let { server.flow = it }
                        "encryption" -> uri.getQueryParameter(param)?.let { server.encryption = it }
                        "headerType" -> uri.getQueryParameter(param)?.let { server.headerType = it }
                        "serviceName" -> uri.getQueryParameter(param)?.let { server.path = it } // gRPC service name
                        "mux" -> uri.getQueryParameter(param)?.let {
                            server.enableMux = it == "1" || it.equals("true", ignoreCase = true)
                        }
                        "muxConcurrency" -> uri.getQueryParameter(param)?.toIntOrNull()?.let {
                            if (it > 0) server.muxConcurrency = it
                        }
                        // Handle gRPC mode
                        "mode" -> uri.getQueryParameter(param)?.let {
                            if (it == "multi" || it == "gun") server.grpcMultiMode = true
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error parsing parameter '$param': ${e.message}")
                }
            }
            
            // Set defaults if not specified
            if (server.fingerprint.isNullOrEmpty()) {
                server.fingerprint = "chrome"
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
     * Generate shareable URL for this REALITY server
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
            
            // Fingerprint
            server.fingerprint?.let {
                uriBuilder.appendQueryParameter("fp", it)
            }
            
            // Flow control
            server.flow?.let {
                uriBuilder.appendQueryParameter("flow", it)
            }
            
            // Network type if not default
            if (server.network != DEFAULT_NETWORK && !server.network.isNullOrEmpty()) {
                uriBuilder.appendQueryParameter("type", server.network)
                
                // Add network-specific parameters
                when (server.network) {
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
                }
            }
            
            // MUX settings
            if (server.enableMux == true) {
                uriBuilder.appendQueryParameter("mux", "1")
                server.muxConcurrency?.let {
                    uriBuilder.appendQueryParameter("muxConcurrency", it.toString())
                }
            }
            
            return uriBuilder.build().toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating REALITY URL", e)
            return ""
        }
    }
}