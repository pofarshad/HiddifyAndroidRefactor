package com.hiddify.hiddifyng.core.protocols

import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONObject

/**
 * Base class for all protocol handlers
 */
abstract class ProtocolHandler {
    
    /**
     * Get protocol name
     * @return Protocol name (e.g., "vmess", "vless", "trojan", etc.)
     */
    abstract fun getProtocolName(): String
    
    /**
     * Create outbound configuration for Xray
     * @return JSON configuration for the protocol
     */
    abstract fun createOutboundConfig(): JSONObject
    
    /**
     * Parse server URI to extract protocol-specific parameters
     * @param uri Server URI (e.g., "vless://...")
     * @return Server object with extracted parameters, or null if parsing failed
     */
    open fun parseUri(uri: String): Server? {
        // Base implementation, to be overridden by subclasses
        return null
    }
    
    /**
     * Generate URI for the server
     * @param server Server object
     * @return URI string, or null if generation failed
     */
    open fun generateUri(server: Server): String? {
        // Base implementation, to be overridden by subclasses
        return null
    }
    
    /**
     * Create default settings for the protocol
     * @return JSON configuration with default settings
     */
    protected fun createDefaultSettings(): JSONObject {
        return JSONObject()
    }
    
    /**
     * Check if the URI is valid for this protocol
     * @param uri Server URI
     * @return true if the URI is valid for this protocol, false otherwise
     */
    open fun isValidUri(uri: String): Boolean {
        // Base implementation, to be overridden by subclasses
        return false
    }
    
    /**
     * Get protocol icon resource
     * @return Resource ID for the protocol icon
     */
    open fun getProtocolIcon(): Int {
        // Base implementation, to be overridden by subclasses
        return android.R.drawable.ic_menu_manage
    }
}