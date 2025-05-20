package com.hiddify.hiddifyng.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "server",
    foreignKeys = [
        ForeignKey(
            entity = ServerGroup::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("groupId")]
)
data class Server(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Basic info
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
    
    // Hysteria protocol settings
    var hysteriaProtocol: String? = null,  // udp, wechat-video, faketcp
    var hysteriaUpMbps: Int? = null,
    var hysteriaDownMbps: Int? = null,
    var hysteriaObfs: String? = null,
    var hysteriaAuthString: String? = null,
    var hysteriaRecvWindowConn: Int? = null,
    var hysteriaRecvWindow: Int? = null,
    var hysteriaDisableMtuDiscovery: Boolean? = null
) {
    /**
     * Generate a human-readable summary of the server
     */
    fun getSummary(): String {
        return "$protocol | $address:$port"
    }
    
    /**
     * Generate a sharing link for this server
     */
    fun toShareLink(): String {
        return when (protocol.toLowerCase()) {
            "vmess" -> generateVmessLink()
            "vless" -> generateVlessLink()
            "trojan" -> generateTrojanLink()
            "shadowsocks" -> generateShadowsocksLink()
            "hysteria" -> generateHysteriaLink()
            else -> ""
        }
    }
    
    /**
     * Generate vmess:// link
     */
    private fun generateVmessLink(): String {
        // This would implement VMess protocol URL generation
        // Example: vmess://base64({...})
        return ""
    }
    
    /**
     * Generate vless:// link
     */
    private fun generateVlessLink(): String {
        // This would implement VLESS protocol URL generation
        // Example: vless://uuid@host:port?params
        return ""
    }
    
    /**
     * Generate trojan:// link
     */
    private fun generateTrojanLink(): String {
        // This would implement Trojan protocol URL generation
        // Example: trojan://password@host:port?params
        return ""
    }
    
    /**
     * Generate ss:// link
     */
    private fun generateShadowsocksLink(): String {
        // This would implement Shadowsocks protocol URL generation
        // Example: ss://base64(method:password@host:port)
        return ""
    }
    
    /**
     * Generate hysteria:// link
     */
    private fun generateHysteriaLink(): String {
        // This would implement Hysteria protocol URL generation
        return ""
    }
    
    companion object {
        /**
         * Parse server from URL string
         * @param url URL string in protocol format
         * @return Server object if parsing successful, null otherwise
         */
        fun fromUrl(url: String): Server? {
            // This would implement parsing logic for different protocol URLs
            // Example: vmess://, vless://, trojan://, ss://, hysteria://
            return null
        }
    }
}
