package com.hiddify.hiddifyng.protocols

import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONArray
import org.json.JSONObject

/**
 * Handler for Hysteria protocol
 * https://github.com/apernet/hysteria
 */
class HysteriaProtocol : ProtocolHandler() {
    
    override fun createOutboundConfig(server: Server): JSONObject {
        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "hysteria")
            
            // Protocol specific settings
            put("settings", createProtocolSettings(server))
            
            // Stream settings for Hysteria
            put("streamSettings", createStreamSettings(server))
        }
    }
    
    override fun createProtocolSettings(server: Server): JSONObject {
        return JSONObject().apply {
            // Server address and port
            put("address", server.address)
            put("port", server.port)
            
            // Auth type and credentials
            if (!server.password.isNullOrEmpty()) {
                put("auth", server.password)
            } else if (!server.uuid.isNullOrEmpty()) {
                put("auth", server.uuid)
            }
            
            // Protocol configurations
            server.hysteriaProtocol?.let { put("protocol", it) }  // "udp" or "wechat-video" or "faketcp"
            
            // Up and down speeds
            server.hysteriaUpMbps?.let { put("up", it) }
            server.hysteriaDownMbps?.let { put("down", it) }
            
            // Obfuscation
            server.hysteriaObfs?.let { put("obfs", it) }
            
            // Auth string for server verification
            server.hysteriaAuthString?.let { put("auth_str", it) }
            
            // ALPN settings
            server.alpn?.let { alpn ->
                val alpnArray = JSONArray()
                alpn.split(",").forEach {
                    if (it.trim().isNotEmpty()) {
                        alpnArray.put(it.trim())
                    }
                }
                if (alpnArray.length() > 0) {
                    put("alpn", alpnArray.getString(0))
                }
            }
            
            // SNI for TLS
            server.sni?.let { put("server_name", it) }
            
            // Allow insecure connections
            server.allowInsecure?.let { put("insecure", it) }
            
            // Receive window size
            server.hysteriaRecvWindowConn?.let { put("recv_window_conn", it) }
            server.hysteriaRecvWindow?.let { put("recv_window", it) }
            
            // Disable MTU discovery
            server.hysteriaDisableMtuDiscovery?.let { put("disable_mtu_discovery", it) }
        }
    }
    
    override fun createStreamSettings(server: Server): JSONObject {
        // Hysteria protocol handles TLS internally in its settings
        // So we don't need separate stream settings
        return JSONObject()
    }
}
