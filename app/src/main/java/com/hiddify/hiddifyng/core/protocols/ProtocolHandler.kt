package com.hiddify.hiddifyng.core.protocols

import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONObject

/**
 * Abstract base class for all protocol handlers
 * Each supported protocol must extend this class and implement its methods
 */
abstract class ProtocolHandler {
    
    companion object {
        private const val TAG = "ProtocolHandler"
        
        /**
         * Factory method to create the appropriate protocol handler based on server type
         */
        fun createHandler(server: Server): ProtocolHandler {
            return when (server.protocol.lowercase()) {
                "vmess" -> VmessProtocol(server)
                "vless" -> VlessProtocol(server)
                "trojan" -> TrojanProtocol(server)
                "shadowsocks" -> ShadowsocksProtocol(server)
                "hysteria" -> HysteriaProtocol(server)
                "reality" -> RealityProtocol(server)
                "xhttp" -> XHttpProtocol(server)
                else -> {
                    Log.e(TAG, "Unsupported protocol: ${server.protocol}")
                    // Default to VLESS as fallback
                    VlessProtocol(server)
                }
            }
        }
    }
    
    /**
     * Get the protocol name (lowercase)
     */
    abstract fun getProtocolName(): String
    
    /**
     * Create the outbound configuration for this protocol
     */
    abstract fun createOutboundConfig(): JSONObject
    
    /**
     * Create the protocol-specific settings
     */
    abstract fun createProtocolSettings(): JSONObject
    
    /**
     * Create the stream settings (transport, security, etc.)
     */
    abstract fun createStreamSettings(): JSONObject
    
    /**
     * Check if this protocol requires TLS
     */
    open fun requiresTls(): Boolean = true
    
    /**
     * Get the default port for this protocol
     */
    open fun getDefaultPort(): Int = 443
    
    /**
     * Create a basic outbound template
     */
    protected fun createBaseOutbound(): JSONObject {
        val outbound = JSONObject()
        outbound.put("tag", "proxy")
        outbound.put("protocol", getProtocolName())
        outbound.put("settings", createProtocolSettings())
        outbound.put("streamSettings", createStreamSettings())
        outbound.put("mux", JSONObject().put("enabled", false))
        return outbound
    }
}