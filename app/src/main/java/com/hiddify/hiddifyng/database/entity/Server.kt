package com.hiddify.hiddifyng.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity for server configuration
 */
@Entity(tableName = "servers")
data class Server(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,
    
    // Basic information
    var name: String = "",
    var protocol: String = "",
    var address: String = "",
    var port: Int = 0,
    
    // Authentication
    var userId: String? = null,
    var password: String? = null,
    var securityType: String? = null,
    
    // TLS settings
    var tls: Boolean = false,
    var tlsServerName: String? = null,
    var tlsFingerprint: String? = null,
    
    // Proxy settings
    var network: String? = null,
    var wsPath: String? = null,
    var header: String? = null,
    
    // REALITY settings
    var realityPublicKey: String? = null,
    var realityShortId: String? = null,
    var realitySpiderX: String? = null,
    
    // Hysteria settings
    var hysteriaProtocol: String? = null,
    var hysteriaObfs: String? = null,
    var hysteriaUpMbps: Int? = null,
    var hysteriaDownMbps: Int? = null,
    
    // XHTTP settings
    var xhttpHost: String? = null,
    var xhttpPath: String? = null,
    
    // Additional settings
    var serverSubscriptionId: Long? = null,
    var lastPing: Long? = null,
    var avgPing: Int? = null,
    var extraParams: String? = null,
    
    // Status flags
    var favorite: Boolean = false,
    var isSelected: Boolean = false,
    var order: Int = 0
) {
    /**
     * Get a display name for the server
     * Uses name if available, otherwise address
     */
    val displayName: String
        get() = if (name.isNotEmpty()) name else address
}