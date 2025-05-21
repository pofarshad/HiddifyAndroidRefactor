package com.hiddify.hiddifyng.protocols

import com.hiddify.hiddifyng.database.entity.Server
import java.util.Locale

/**
 * Interface for handling different protocols
 * This provides a standardized way to interact with different VPN protocols
 */
interface ProtocolHandler {
    /**
     * Get protocol name
     */
    fun getProtocolName(): String
    
    /**
     * Generate configuration for the protocol
     * @param server Server configuration
     * @return Configuration as a String (usually JSON)
     */
    fun generateConfig(server: Server): String
    
    /**
     * Validate server configuration for this protocol
     * @param server Server configuration
     * @return true if valid, false otherwise
     */
    fun validateServer(server: Server): Boolean
    
    /**
     * Parse server configuration from URL
     * @param url Protocol URL (e.g., vmess://, vless://, etc.)
     * @return Server object if parsing successful, null otherwise
     */
    fun parseUrl(url: String): Server?
    
    /**
     * Generate sharing URL for this server
     * @param server Server configuration
     * @return URL string for sharing
     */
    fun generateUrl(server: Server): String
    
    companion object {
        // Cache for protocol handlers to improve performance
        private val handlers = mutableMapOf<String, ProtocolHandler>()
        
        /**
         * Get appropriate protocol handler for the given protocol
         * @param protocol Protocol identifier
         * @return ProtocolHandler implementation
         * @throws IllegalArgumentException if protocol is not supported
         */
        @Throws(IllegalArgumentException::class)
        fun getHandler(protocol: String): ProtocolHandler {
            val normalizedProtocol = protocol.lowercase(Locale.getDefault())
            
            // Return cached handler if available
            handlers[normalizedProtocol]?.let { return it }
            
            // Create new handler
            val handler = when (normalizedProtocol) {
                "vmess" -> VmessProtocolHandler()
                "vless" -> VlessProtocolHandler()
                "trojan" -> TrojanProtocolHandler()
                "shadowsocks" -> ShadowsocksProtocolHandler()
                "hysteria" -> HysteriaProtocolHandler()
                "xhttp" -> XhttpProtocolHandler()
                "reality" -> RealityProtocolHandler()
                else -> throw IllegalArgumentException("Unsupported protocol: $protocol")
            }
            
            // Cache the handler
            handlers[normalizedProtocol] = handler
            return handler
        }
        
        /**
         * Check if protocol is supported
         * @param protocol Protocol identifier to check
         * @return true if supported, false otherwise
         */
        fun isProtocolSupported(protocol: String): Boolean {
            val normalizedProtocol = protocol.lowercase(Locale.getDefault())
            return normalizedProtocol in getSupportedProtocols().map { it.lowercase(Locale.getDefault()) }
        }
        
        /**
         * Get list of all supported protocols
         * @return List of supported protocol names
         */
        fun getSupportedProtocols(): List<String> {
            return listOf("vmess", "vless", "trojan", "shadowsocks", "hysteria", "xhttp", "reality")
        }
    }
}