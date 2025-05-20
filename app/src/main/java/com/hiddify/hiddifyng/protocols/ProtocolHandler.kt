package com.hiddify.hiddifyng.protocols

import com.hiddify.hiddifyng.database.entity.Server

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
        /**
         * Get appropriate protocol handler for the given protocol
         * @param protocol Protocol identifier
         * @return ProtocolHandler implementation
         */
        fun getHandler(protocol: String): ProtocolHandler {
            return when (protocol.toLowerCase()) {
                "vmess" -> VmessProtocolHandler()
                "vless" -> VlessProtocolHandler()
                "trojan" -> TrojanProtocolHandler()
                "shadowsocks" -> ShadowsocksProtocolHandler()
                "hysteria" -> HysteriaProtocolHandler()
                "xhttp" -> XhttpProtocolHandler()
                else -> throw IllegalArgumentException("Unsupported protocol: $protocol")
            }
        }
    }
}