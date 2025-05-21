package com.hiddify.hiddifyng.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hiddify.hiddifyng.protocols.ProtocolHandler
import java.util.Date
import java.util.Locale

/**
 * Entity class for VPN servers with optimized structure
 * Enhanced with protocol-specific validation and generation
 */
@Entity(
    tableName = "server",
    foreignKeys = [
        ForeignKey(
            entity = ServerGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE  // Delete servers when group is deleted
        )
    ],
    indices = [
        Index("groupId"),  // For faster queries based on group
        Index("protocol"), // For faster protocol filtering
        Index("ping")      // For best server queries
    ]
)
data class Server(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Basic info - required fields
    var name: String,
    var protocol: String,
    var address: String,
    var port: Int,
    
    // Group association
    var groupId: Long? = null,
    
    // Authentication details
    var uuid: String? = null,
    var password: String? = null,
    var alterId: Int? = null,
    var security: String? = null,
    var encryption: String? = null,
    var flow: String? = null,
    var method: String? = null,
    
    // Stream settings
    var network: String? = null,  // tcp, kcp, ws, http, quic, grpc
    var headerType: String? = null,
    var host: String? = null,
    var path: String? = null,
    var tls: String? = null,  // "", tls, xtls
    var sni: String? = null,
    var alpn: String? = null,
    var fingerprint: String? = null,
    var quicSecurity: String? = null,
    var quicKey: String? = null,
    var grpcMultiMode: Boolean? = null,
    var allowInsecure: Boolean? = null,
    var requestTimeout: Int? = null,
    var allowHttp2: Boolean? = null,
    
    // Mux settings
    var enableMux: Boolean? = null,
    var muxConcurrency: Int? = null,
    
    // Routing settings
    var bypassDomains: String? = null,  // Comma-separated list of domains to bypass
    var blockDomains: String? = null,  // Comma-separated list of domains to block
    var bypassIps: String? = null,  // Comma-separated list of IPs to bypass
    var dnsServers: String? = null,  // Comma-separated list of DNS servers
    var routingMode: String? = null,  // global, bypass_cn, custom
    var customRules: String? = null,  // JSON string of custom routing rules
    
    // App settings for this server
    var bypassPackages: String? = null,  // Comma-separated list of package names to bypass
    
    // Additional headers
    var headers: String? = null,  // Semicolon-separated key:value pairs
    
    // Performance and stats
    var ping: Int = 0,  // Ping time in milliseconds, 0 means not tested
    var lastPingTime: Date? = null, // When this server was last pinged
    var lastSuccessfulConnection: Date? = null, // Last time we connected successfully
    var pingCount: Int = 0, // Number of times pinged (for averaging)
    var totalUpload: Long = 0, // Total bytes uploaded through this server
    var totalDownload: Long = 0, // Total bytes downloaded through this server
    var connectionCount: Int = 0, // Number of times connected to this server
    var isFavorite: Boolean = false, // User favorite status
    var userNote: String? = null, // User notes for this server
    
    // Hysteria protocol settings
    var hysteriaProtocol: String? = null,  // udp, wechat-video, faketcp
    var hysteriaUpMbps: Int? = null,
    var hysteriaDownMbps: Int? = null,
    var hysteriaObfs: String? = null,
    var hysteriaAuthString: String? = null,
    var hysteriaRecvWindowConn: Int? = null,
    var hysteriaRecvWindow: Int? = null,
    var hysteriaDisableMtuDiscovery: Boolean? = null,
    
    // REALITY protocol settings
    var publicKey: String? = null,    // Server's public key for encryption
    var shortId: String? = null,      // REALITY short ID
    var spiderX: String? = null,      // Spider X for additional verification
    var privateKey: String? = null    // Client's private key (rarely used)
) {
    /**
     * Generate a human-readable summary of the server with connection info
     * @return Formatted string with protocol, address and port
     */
    fun getSummary(): String {
        val pingStatus = if (ping > 0) " ($ping ms)" else ""
        return "$protocol | $address:$port$pingStatus"
    }
    
    /**
     * Get display name with fallback to address if name is empty
     * @return Name to display in UI
     */
    fun getDisplayName(): String {
        return if (name.isNotEmpty()) name else "$address:$port"
    }
    
    /**
     * Get the appropriate protocol handler for this server
     * @return ProtocolHandler implementation for this server's protocol
     */
    fun getProtocolHandler(): ProtocolHandler {
        return ProtocolHandler.getHandler(protocol)
    }
    
    /**
     * Generate a sharing link for this server using the appropriate protocol handler
     * @return URL string for sharing this server configuration
     */
    fun toShareLink(): String {
        return try {
            // Use protocol handler instead of internal implementation
            val handler = getProtocolHandler()
            handler.generateUrl(this)
        } catch (e: Exception) {
            // Fallback to empty string if handler fails
            ""
        }
    }
    
    /**
     * Check if this server has valid configuration for its protocol
     * @return true if valid, false otherwise
     */
    fun isValid(): Boolean {
        return try {
            if (address.isBlank() || port <= 0 || port > 65535) {
                return false
            }
            
            // Use protocol handler to validate
            val handler = getProtocolHandler()
            handler.validateServer(this)
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Mark this server as favorite with timestamp
     */
    fun markAsFavorite() {
        isFavorite = true
    }
    
    /**
     * Register a successful connection for statistics
     */
    fun registerConnection() {
        connectionCount++
        lastSuccessfulConnection = Date()
    }
    
    /**
     * Register traffic for statistics
     * @param uploadBytes Bytes uploaded
     * @param downloadBytes Bytes downloaded
     */
    fun registerTraffic(uploadBytes: Long, downloadBytes: Long) {
        totalUpload += uploadBytes
        totalDownload += downloadBytes
    }
    
    /**
     * Update ping for this server, keeping history for consistency
     * @param newPing New ping value in milliseconds
     */
    fun updatePing(newPing: Int) {
        // Update ping with a weighted average to avoid single outliers
        if (pingCount == 0) {
            ping = newPing
        } else {
            // Weight recent pings more heavily (70% new, 30% old)
            ping = ((newPing * 0.7) + (ping * 0.3)).toInt()
        }
        pingCount++
        lastPingTime = Date()
    }
    
    companion object {
        /**
         * Parse server from URL string using appropriate protocol handler
         * @param url URL string in protocol format
         * @return Server object if parsing successful, null otherwise
         */
        fun fromUrl(url: String): Server? {
            return when {
                url.startsWith("vmess://") -> ProtocolHandler.getHandler("vmess").parseUrl(url)
                url.startsWith("vless://") -> ProtocolHandler.getHandler("vless").parseUrl(url)
                url.startsWith("trojan://") -> ProtocolHandler.getHandler("trojan").parseUrl(url)
                url.startsWith("ss://") -> ProtocolHandler.getHandler("shadowsocks").parseUrl(url)
                url.startsWith("hysteria://") -> ProtocolHandler.getHandler("hysteria").parseUrl(url)
                url.startsWith("xhttp://") -> ProtocolHandler.getHandler("xhttp").parseUrl(url)
                else -> null
            }
        }
        
        /**
         * Create a skeleton server for a specific protocol
         * @param protocol Protocol identifier
         * @return Basic server with default settings for the protocol
         */
        fun createForProtocol(protocol: String): Server {
            val protocolLower = protocol.lowercase(Locale.getDefault())
            
            return when (protocolLower) {
                "vmess" -> Server(
                    name = "New VMess Server",
                    protocol = "vmess",
                    address = "",
                    port = 443,
                    network = "tcp",
                    security = "auto"
                )
                "vless" -> Server(
                    name = "New VLESS Server",
                    protocol = "vless",
                    address = "",
                    port = 443,
                    network = "tcp",
                    security = "tls"
                )
                "trojan" -> Server(
                    name = "New Trojan Server",
                    protocol = "trojan",
                    address = "",
                    port = 443,
                    security = "tls"
                )
                "shadowsocks" -> Server(
                    name = "New Shadowsocks Server",
                    protocol = "shadowsocks",
                    address = "",
                    port = 8388,
                    method = "chacha20-ietf-poly1305"
                )
                "hysteria" -> Server(
                    name = "New Hysteria Server",
                    protocol = "hysteria",
                    address = "",
                    port = 443,
                    hysteriaProtocol = "udp",
                    hysteriaUpMbps = 10,
                    hysteriaDownMbps = 50
                )
                "xhttp" -> Server(
                    name = "New XHTTP Server",
                    protocol = "xhttp",
                    address = "",
                    port = 443,
                    security = "tls",
                    path = "/"
                )
                "reality" -> Server(
                    name = "New REALITY Server",
                    protocol = "reality",
                    address = "",
                    port = 443,
                    network = "tcp",
                    security = "reality",
                    encryption = "none",
                    flow = "xtls-rprx-vision",
                    fingerprint = "chrome",
                    sni = "",  // Needs to be filled by user
                    publicKey = ""  // Needs to be filled by user
                )
                else -> Server(
                    name = "New Server",
                    protocol = "vmess", // Default to VMess
                    address = "",
                    port = 443
                )
            }
        }
    }
}