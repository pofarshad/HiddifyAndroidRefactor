package com.hiddify.hiddifyng.utils

import android.content.Context
import android.util.Log
import com.hiddify.hiddifyng.database.AppDatabase
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.database.entity.Subscription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Manages subscription links and server updates
 * Handles automatic updates every 30 minutes
 */
class SubscriptionManager(private val context: Context) {
    companion object {
        private const val TAG = "SubscriptionManager"
        private const val CONNECT_TIMEOUT = 30L // seconds
        private const val READ_TIMEOUT = 30L // seconds
    }
    
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
        .build()
    
    // Database access
    private val database = AppDatabase.getInstance(context)
    private val subscriptionDao = database.subscriptionDao()
    private val serverDao = database.serverDao()
    
    /**
     * Update all subscriptions and return results map (subscription URL to success/failure)
     */
    suspend fun updateAllSubscriptions(): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()
        
        try {
            // Get all subscriptions
            val subscriptions = subscriptionDao.getAllSubscriptions()
            Log.i(TAG, "Updating ${subscriptions.size} subscriptions")
            
            // Update each subscription
            for (subscription in subscriptions) {
                try {
                    val success = updateSubscription(subscription)
                    results[subscription.url] = success
                    
                    if (success) {
                        Log.i(TAG, "Successfully updated subscription: ${subscription.name}")
                    } else {
                        Log.e(TAG, "Failed to update subscription: ${subscription.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error updating subscription: ${subscription.name}", e)
                    results[subscription.url] = false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating subscriptions", e)
        }
        
        return@withContext results
    }
    
    /**
     * Update a single subscription
     */
    private suspend fun updateSubscription(subscription: Subscription): Boolean = withContext(Dispatchers.IO) {
        try {
            // Build request
            val request = Request.Builder()
                .url(subscription.url)
                .build()
            
            // Execute request
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Error fetching subscription: ${response.code}")
                    return@withContext false
                }
                
                val responseBody = response.body?.string()
                if (responseBody.isNullOrEmpty()) {
                    Log.e(TAG, "Empty response from subscription URL")
                    return@withContext false
                }
                
                // Parse servers from response
                val servers = parseSubscriptionContent(responseBody, subscription.id)
                
                if (servers.isEmpty()) {
                    Log.e(TAG, "No valid servers found in subscription response")
                    return@withContext false
                }
                
                Log.i(TAG, "Parsed ${servers.size} servers from subscription")
                
                // Update database
                updateServers(subscription.id, servers)
                
                // Update last updated time
                subscriptionDao.updateLastUpdated(subscription.id, System.currentTimeMillis())
                
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating subscription", e)
            return@withContext false
        }
    }
    
    /**
     * Parse subscription content to extract servers
     * Supports multiple subscription formats (JSON, Base64, V2Ray/Xray standard)
     */
    private fun parseSubscriptionContent(content: String, subscriptionId: Long): List<Server> {
        val servers = mutableListOf<Server>()
        
        try {
            // Try parsing as JSON
            if (content.trim().startsWith("{") || content.trim().startsWith("[")) {
                servers.addAll(parseJsonSubscription(content, subscriptionId))
            } else {
                // Try parsing as Base64
                val decodedContent = decodeBase64(content.trim())
                if (decodedContent.isNotEmpty()) {
                    servers.addAll(parseBase64Subscription(decodedContent, subscriptionId))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing subscription content", e)
        }
        
        return servers
    }
    
    /**
     * Parse JSON format subscription
     */
    private fun parseJsonSubscription(content: String, subscriptionId: Long): List<Server> {
        val servers = mutableListOf<Server>()
        
        try {
            if (content.trim().startsWith("[")) {
                // JSON array of servers
                val jsonArray = JSONArray(content)
                for (i in 0 until jsonArray.length()) {
                    val serverObj = jsonArray.getJSONObject(i)
                    val server = parseJsonServer(serverObj, subscriptionId)
                    if (server != null) {
                        servers.add(server)
                    }
                }
            } else if (content.trim().startsWith("{")) {
                // JSON object, try different known formats
                val jsonObject = JSONObject(content)
                
                // Check if it's a standard MarFaNet/HiddifyNG format
                if (jsonObject.has("servers") && jsonObject.get("servers") is JSONArray) {
                    val serversArray = jsonObject.getJSONArray("servers")
                    for (i in 0 until serversArray.length()) {
                        val serverObj = serversArray.getJSONObject(i)
                        val server = parseJsonServer(serverObj, subscriptionId)
                        if (server != null) {
                            servers.add(server)
                        }
                    }
                } else {
                    // Try parsing as a single server
                    val server = parseJsonServer(jsonObject, subscriptionId)
                    if (server != null) {
                        servers.add(server)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON subscription", e)
        }
        
        return servers
    }
    
    /**
     * Parse Base64 encoded subscription
     */
    private fun parseBase64Subscription(content: String, subscriptionId: Long): List<Server> {
        val servers = mutableListOf<Server>()
        
        try {
            // Split by lines and parse each line
            content.lines().forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isNotEmpty()) {
                    // Try to parse the line as a server config
                    parseServerFromUrl(trimmedLine, subscriptionId)?.let { server ->
                        servers.add(server)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Base64 subscription", e)
        }
        
        return servers
    }
    
    /**
     * Parse server from JSON object
     */
    private fun parseJsonServer(serverObj: JSONObject, subscriptionId: Long): Server? {
        try {
            // Required fields
            val name = serverObj.optString("name", "")
            val address = serverObj.optString("address", "")
            val type = serverObj.optString("type", "")
            
            if (name.isEmpty() || address.isEmpty() || type.isEmpty()) {
                return null
            }
            
            // Server settings
            val port = serverObj.optInt("port", 443)
            val method = serverObj.optString("method", "")
            val password = serverObj.optString("password", "")
            val network = serverObj.optString("network", "tcp")
            val security = serverObj.optString("security", "tls")
            
            // Optional fields
            val uuid = serverObj.optString("uuid", "")
            val alterId = serverObj.optInt("alterId", 0)
            val sni = serverObj.optString("sni", "")
            val alpn = serverObj.optString("alpn", "")
            
            // Extra parameters (stored as JSON string)
            val extraParams = JSONObject()
            val configKeys = listOf("path", "host", "quicSecurity", "key", "fingerprint", "flow")
            
            for (key in configKeys) {
                if (serverObj.has(key)) {
                    extraParams.put(key, serverObj.getString(key))
                }
            }
            
            // Create server object
            return Server(
                id = 0, // Will be auto-assigned by Room
                name = name,
                address = address,
                port = port,
                protocol = type,
                method = method,
                password = password,
                subscriptionId = subscriptionId,
                network = network,
                security = security,
                sni = sni,
                alpn = alpn,
                uuid = uuid,
                alterId = alterId,
                extraParams = extraParams.toString(),
                ping = 0, // No ping yet
                lastPingTime = 0L,
                isActive = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON server", e)
            return null
        }
    }
    
    /**
     * Parse server from URL (e.g. vmess://, vless://, trojan://, etc.)
     */
    private fun parseServerFromUrl(url: String, subscriptionId: Long): Server? {
        try {
            // Identify protocol
            val protocol = when {
                url.startsWith("vmess://") -> "vmess"
                url.startsWith("vless://") -> "vless"
                url.startsWith("trojan://") -> "trojan"
                url.startsWith("ss://") -> "shadowsocks"
                url.startsWith("ssr://") -> "shadowsocksr"
                url.startsWith("hysteria://") -> "hysteria"
                url.startsWith("xhttp://") -> "xhttp"
                url.startsWith("reality://") -> "reality"
                else -> return null
            }
            
            // Parse based on protocol
            return when (protocol) {
                "vmess" -> parseVmessUrl(url, subscriptionId)
                "vless" -> parseVlessUrl(url, subscriptionId)
                "trojan" -> parseTrojanUrl(url, subscriptionId)
                "shadowsocks" -> parseShadowsocksUrl(url, subscriptionId)
                "hysteria" -> parseHysteriaUrl(url, subscriptionId)
                "xhttp" -> parseXHttpUrl(url, subscriptionId)
                "reality" -> parseRealityUrl(url, subscriptionId)
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server URL: $url", e)
            return null
        }
    }
    
    /**
     * Parse VMess URL format
     */
    private fun parseVmessUrl(url: String, subscriptionId: Long): Server? {
        try {
            // VMess URLs are base64 encoded JSON
            val base64Content = url.substring("vmess://".length)
            val jsonContent = decodeBase64(base64Content)
            
            if (jsonContent.isEmpty()) {
                return null
            }
            
            val vmessConfig = JSONObject(jsonContent)
            
            val name = vmessConfig.optString("ps", "VMess Server")
            val address = vmessConfig.optString("add", "")
            val port = vmessConfig.optInt("port", 443)
            val uuid = vmessConfig.optString("id", "")
            val alterId = vmessConfig.optInt("aid", 0)
            val network = vmessConfig.optString("net", "tcp")
            val type = vmessConfig.optString("type", "none")
            val security = vmessConfig.optString("tls", "none")
            val sni = vmessConfig.optString("sni", "")
            
            // Extra parameters
            val extraParams = JSONObject()
            extraParams.put("path", vmessConfig.optString("path", ""))
            extraParams.put("host", vmessConfig.optString("host", ""))
            extraParams.put("fingerprint", vmessConfig.optString("fp", ""))
            
            return Server(
                id = 0,
                name = name,
                address = address,
                port = port,
                protocol = "vmess",
                method = "auto",
                password = "",
                subscriptionId = subscriptionId,
                network = network,
                security = if (security == "none") "" else security,
                sni = sni,
                alpn = "",
                uuid = uuid,
                alterId = alterId,
                extraParams = extraParams.toString(),
                ping = 0,
                lastPingTime = 0L,
                isActive = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VMess URL", e)
            return null
        }
    }
    
    /**
     * Parse VLESS URL format
     */
    private fun parseVlessUrl(url: String, subscriptionId: Long): Server? {
        try {
            // Format: vless://uuid@address:port?params#name
            val remaining = url.substring("vless://".length)
            
            // Extract name from fragment
            val nameIndex = remaining.lastIndexOf("#")
            val name = if (nameIndex > 0) {
                remaining.substring(nameIndex + 1)
            } else {
                "VLESS Server"
            }
            
            val configPart = if (nameIndex > 0) {
                remaining.substring(0, nameIndex)
            } else {
                remaining
            }
            
            // Extract UUID and address:port
            val atIndex = configPart.indexOf("@")
            if (atIndex <= 0) {
                return null
            }
            
            val uuid = configPart.substring(0, atIndex)
            
            // Parse address:port and params
            val paramsIndex = configPart.indexOf("?", atIndex)
            val serverPart = if (paramsIndex > 0) {
                configPart.substring(atIndex + 1, paramsIndex)
            } else {
                configPart.substring(atIndex + 1)
            }
            
            // Extract address and port
            val lastColonIndex = serverPart.lastIndexOf(":")
            if (lastColonIndex <= 0) {
                return null
            }
            
            val address = serverPart.substring(0, lastColonIndex)
            val port = serverPart.substring(lastColonIndex + 1).toIntOrNull() ?: 443
            
            // Parse parameters
            val params = if (paramsIndex > 0) {
                parseUrlParams(configPart.substring(paramsIndex + 1))
            } else {
                mapOf()
            }
            
            // Extract common parameters
            val type = params["type"] ?: "tcp"
            val security = params["security"] ?: ""
            val sni = params["sni"] ?: ""
            val alpn = params["alpn"] ?: ""
            val flow = params["flow"] ?: ""
            
            // Extra parameters
            val extraParams = JSONObject()
            extraParams.put("path", params["path"] ?: "")
            extraParams.put("host", params["host"] ?: "")
            extraParams.put("fingerprint", params["fp"] ?: "")
            extraParams.put("flow", flow)
            
            return Server(
                id = 0,
                name = name,
                address = address,
                port = port,
                protocol = "vless",
                method = "",
                password = "",
                subscriptionId = subscriptionId,
                network = type,
                security = security,
                sni = sni,
                alpn = alpn,
                uuid = uuid,
                alterId = 0,
                extraParams = extraParams.toString(),
                ping = 0,
                lastPingTime = 0L,
                isActive = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing VLESS URL", e)
            return null
        }
    }
    
    /**
     * Parse Trojan URL format
     */
    private fun parseTrojanUrl(url: String, subscriptionId: Long): Server? {
        try {
            // Format: trojan://password@address:port?params#name
            val remaining = url.substring("trojan://".length)
            
            // Extract name from fragment
            val nameIndex = remaining.lastIndexOf("#")
            val name = if (nameIndex > 0) {
                remaining.substring(nameIndex + 1)
            } else {
                "Trojan Server"
            }
            
            val configPart = if (nameIndex > 0) {
                remaining.substring(0, nameIndex)
            } else {
                remaining
            }
            
            // Extract password and address:port
            val atIndex = configPart.indexOf("@")
            if (atIndex <= 0) {
                return null
            }
            
            val password = configPart.substring(0, atIndex)
            
            // Parse address:port and params
            val paramsIndex = configPart.indexOf("?", atIndex)
            val serverPart = if (paramsIndex > 0) {
                configPart.substring(atIndex + 1, paramsIndex)
            } else {
                configPart.substring(atIndex + 1)
            }
            
            // Extract address and port
            val lastColonIndex = serverPart.lastIndexOf(":")
            if (lastColonIndex <= 0) {
                return null
            }
            
            val address = serverPart.substring(0, lastColonIndex)
            val port = serverPart.substring(lastColonIndex + 1).toIntOrNull() ?: 443
            
            // Parse parameters
            val params = if (paramsIndex > 0) {
                parseUrlParams(configPart.substring(paramsIndex + 1))
            } else {
                mapOf()
            }
            
            // Extract common parameters
            val sni = params["sni"] ?: ""
            val alpn = params["alpn"] ?: ""
            
            // Extra parameters
            val extraParams = JSONObject()
            extraParams.put("fingerprint", params["fp"] ?: "")
            
            return Server(
                id = 0,
                name = name,
                address = address,
                port = port,
                protocol = "trojan",
                method = "",
                password = password,
                subscriptionId = subscriptionId,
                network = "tcp",
                security = "tls",
                sni = sni,
                alpn = alpn,
                uuid = "",
                alterId = 0,
                extraParams = extraParams.toString(),
                ping = 0,
                lastPingTime = 0L,
                isActive = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Trojan URL", e)
            return null
        }
    }
    
    /**
     * Parse Shadowsocks URL format
     */
    private fun parseShadowsocksUrl(url: String, subscriptionId: Long): Server? {
        try {
            // Format: ss://base64(method:password)@address:port#name
            // Or: ss://base64(method:password@address:port)#name
            val remaining = url.substring("ss://".length)
            
            // Extract name from fragment
            val nameIndex = remaining.lastIndexOf("#")
            val name = if (nameIndex > 0) {
                remaining.substring(nameIndex + 1)
            } else {
                "Shadowsocks Server"
            }
            
            val configPart = if (nameIndex > 0) {
                remaining.substring(0, nameIndex)
            } else {
                remaining
            }
            
            // Check if we have the legacy format (everything base64 encoded)
            if (!configPart.contains("@")) {
                // Legacy format
                val decoded = decodeBase64(configPart)
                if (decoded.isEmpty()) {
                    return null
                }
                
                // Format should be method:password@address:port
                val parts = decoded.split("@")
                if (parts.size != 2) {
                    return null
                }
                
                val methodPass = parts[0].split(":")
                if (methodPass.size != 2) {
                    return null
                }
                
                val method = methodPass[0]
                val password = methodPass[1]
                
                val addrPort = parts[1].split(":")
                if (addrPort.size != 2) {
                    return null
                }
                
                val address = addrPort[0]
                val port = addrPort[1].toIntOrNull() ?: 443
                
                return Server(
                    id = 0,
                    name = name,
                    address = address,
                    port = port,
                    protocol = "shadowsocks",
                    method = method,
                    password = password,
                    subscriptionId = subscriptionId,
                    network = "tcp",
                    security = "",
                    sni = "",
                    alpn = "",
                    uuid = "",
                    alterId = 0,
                    extraParams = "{}",
                    ping = 0,
                    lastPingTime = 0L,
                    isActive = false
                )
            } else {
                // Modern format
                val atIndex = configPart.indexOf("@")
                if (atIndex <= 0) {
                    return null
                }
                
                // Decode method:password part
                val encoded = configPart.substring(0, atIndex)
                val decoded = decodeBase64(encoded)
                if (decoded.isEmpty()) {
                    return null
                }
                
                val methodPass = decoded.split(":")
                if (methodPass.size != 2) {
                    return null
                }
                
                val method = methodPass[0]
                val password = methodPass[1]
                
                // Parse address and port
                val serverPart = configPart.substring(atIndex + 1)
                val lastColonIndex = serverPart.lastIndexOf(":")
                if (lastColonIndex <= 0) {
                    return null
                }
                
                val address = serverPart.substring(0, lastColonIndex)
                val port = serverPart.substring(lastColonIndex + 1).toIntOrNull() ?: 443
                
                return Server(
                    id = 0,
                    name = name,
                    address = address,
                    port = port,
                    protocol = "shadowsocks",
                    method = method,
                    password = password,
                    subscriptionId = subscriptionId,
                    network = "tcp",
                    security = "",
                    sni = "",
                    alpn = "",
                    uuid = "",
                    alterId = 0,
                    extraParams = "{}",
                    ping = 0,
                    lastPingTime = 0L,
                    isActive = false
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Shadowsocks URL", e)
            return null
        }
    }
    
    /**
     * Parse Hysteria URL format (modern protocol)
     */
    private fun parseHysteriaUrl(url: String, subscriptionId: Long): Server? {
        try {
            // Format: hysteria://address:port?protocol=udp&auth=x&peer=sni&insecure=1&upmbps=100&downmbps=100#name
            val remaining = url.substring("hysteria://".length)
            
            // Extract name from fragment
            val nameIndex = remaining.lastIndexOf("#")
            val name = if (nameIndex > 0) {
                remaining.substring(nameIndex + 1)
            } else {
                "Hysteria Server"
            }
            
            val configPart = if (nameIndex > 0) {
                remaining.substring(0, nameIndex)
            } else {
                remaining
            }
            
            // Parse address:port and params
            val paramsIndex = configPart.indexOf("?")
            val serverPart = if (paramsIndex > 0) {
                configPart.substring(0, paramsIndex)
            } else {
                configPart
            }
            
            // Extract address and port
            val lastColonIndex = serverPart.lastIndexOf(":")
            if (lastColonIndex <= 0) {
                return null
            }
            
            val address = serverPart.substring(0, lastColonIndex)
            val port = serverPart.substring(lastColonIndex + 1).toIntOrNull() ?: 443
            
            // Parse parameters
            val params = if (paramsIndex > 0) {
                parseUrlParams(configPart.substring(paramsIndex + 1))
            } else {
                mapOf()
            }
            
            // Extract common parameters
            val protocol = params["protocol"] ?: "udp"
            val auth = params["auth"] ?: ""
            val sni = params["peer"] ?: ""
            val insecure = params["insecure"] == "1"
            val upMbps = params["upmbps"] ?: "100"
            val downMbps = params["downmbps"] ?: "100"
            
            // Extra parameters
            val extraParams = JSONObject()
            extraParams.put("protocol", protocol)
            extraParams.put("insecure", insecure)
            extraParams.put("upmbps", upMbps)
            extraParams.put("downmbps", downMbps)
            
            return Server(
                id = 0,
                name = name,
                address = address,
                port = port,
                protocol = "hysteria",
                method = "",
                password = auth, // Auth doubles as password
                subscriptionId = subscriptionId,
                network = "udp",
                security = "tls",
                sni = sni,
                alpn = params["alpn"] ?: "",
                uuid = "",
                alterId = 0,
                extraParams = extraParams.toString(),
                ping = 0,
                lastPingTime = 0L,
                isActive = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Hysteria URL", e)
            return null
        }
    }
    
    /**
     * Parse XHTTP URL format (new HTTP-based protocol)
     */
    private fun parseXHttpUrl(url: String, subscriptionId: Long): Server? {
        try {
            // Format: xhttp://auth@address:port?path=/app&security=tls&sni=server#name
            val remaining = url.substring("xhttp://".length)
            
            // Extract name from fragment
            val nameIndex = remaining.lastIndexOf("#")
            val name = if (nameIndex > 0) {
                remaining.substring(nameIndex + 1)
            } else {
                "XHTTP Server"
            }
            
            val configPart = if (nameIndex > 0) {
                remaining.substring(0, nameIndex)
            } else {
                remaining
            }
            
            // Extract auth and address:port
            val atIndex = configPart.indexOf("@")
            val auth = if (atIndex > 0) {
                configPart.substring(0, atIndex)
            } else {
                ""
            }
            
            // Parse address:port and params
            val paramsIndex = configPart.indexOf("?", atIndex)
            val serverPart = if (paramsIndex > 0) {
                configPart.substring(atIndex + 1, paramsIndex)
            } else {
                configPart.substring(atIndex + 1)
            }
            
            // Extract address and port
            val lastColonIndex = serverPart.lastIndexOf(":")
            if (lastColonIndex <= 0) {
                return null
            }
            
            val address = serverPart.substring(0, lastColonIndex)
            val port = serverPart.substring(lastColonIndex + 1).toIntOrNull() ?: 443
            
            // Parse parameters
            val params = if (paramsIndex > 0) {
                parseUrlParams(configPart.substring(paramsIndex + 1))
            } else {
                mapOf()
            }
            
            // Extract common parameters
            val path = params["path"] ?: "/"
            val security = params["security"] ?: "tls"
            val sni = params["sni"] ?: ""
            
            // Extra parameters
            val extraParams = JSONObject()
            extraParams.put("path", path)
            extraParams.put("host", params["host"] ?: "")
            
            return Server(
                id = 0,
                name = name,
                address = address,
                port = port,
                protocol = "xhttp",
                method = "",
                password = auth,
                subscriptionId = subscriptionId,
                network = "http",
                security = security,
                sni = sni,
                alpn = params["alpn"] ?: "",
                uuid = "",
                alterId = 0,
                extraParams = extraParams.toString(),
                ping = 0,
                lastPingTime = 0L,
                isActive = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing XHTTP URL", e)
            return null
        }
    }
    
    /**
     * Parse REALITY URL format (enhanced TLS obfuscation)
     */
    private fun parseRealityUrl(url: String, subscriptionId: Long): Server? {
        try {
            // Format: reality://uuid@address:port?flow=xtls-rprx-vision&security=reality&pbk=x&fp=chrome&sni=domain#name
            val remaining = url.substring("reality://".length)
            
            // Extract name from fragment
            val nameIndex = remaining.lastIndexOf("#")
            val name = if (nameIndex > 0) {
                remaining.substring(nameIndex + 1)
            } else {
                "REALITY Server"
            }
            
            val configPart = if (nameIndex > 0) {
                remaining.substring(0, nameIndex)
            } else {
                remaining
            }
            
            // Extract UUID and address:port
            val atIndex = configPart.indexOf("@")
            if (atIndex <= 0) {
                return null
            }
            
            val uuid = configPart.substring(0, atIndex)
            
            // Parse address:port and params
            val paramsIndex = configPart.indexOf("?", atIndex)
            val serverPart = if (paramsIndex > 0) {
                configPart.substring(atIndex + 1, paramsIndex)
            } else {
                configPart.substring(atIndex + 1)
            }
            
            // Extract address and port
            val lastColonIndex = serverPart.lastIndexOf(":")
            if (lastColonIndex <= 0) {
                return null
            }
            
            val address = serverPart.substring(0, lastColonIndex)
            val port = serverPart.substring(lastColonIndex + 1).toIntOrNull() ?: 443
            
            // Parse parameters
            val params = if (paramsIndex > 0) {
                parseUrlParams(configPart.substring(paramsIndex + 1))
            } else {
                mapOf()
            }
            
            // Extract required REALITY parameters
            val publicKey = params["pbk"] ?: ""
            val fingerprint = params["fp"] ?: "chrome"
            val sni = params["sni"] ?: ""
            val flow = params["flow"] ?: "xtls-rprx-vision"
            
            // Extra parameters
            val extraParams = JSONObject()
            extraParams.put("publicKey", publicKey)
            extraParams.put("fingerprint", fingerprint)
            extraParams.put("flow", flow)
            extraParams.put("shortId", params["sid"] ?: "")
            extraParams.put("spiderX", params["spx"] ?: "")
            
            return Server(
                id = 0,
                name = name,
                address = address,
                port = port,
                protocol = "vless", // REALITY uses VLESS protocol
                method = "",
                password = "",
                subscriptionId = subscriptionId,
                network = params["type"] ?: "tcp",
                security = "reality", // Special security type
                sni = sni,
                alpn = "",
                uuid = uuid,
                alterId = 0,
                extraParams = extraParams.toString(),
                ping = 0,
                lastPingTime = 0L,
                isActive = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing REALITY URL", e)
            return null
        }
    }
    
    /**
     * Parse URL parameters to a map
     */
    private fun parseUrlParams(paramString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        
        try {
            // Split by & and parse each param
            paramString.split("&").forEach { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0]
                    val value = parts[1]
                    result[key] = value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing URL parameters", e)
        }
        
        return result
    }
    
    /**
     * Decode Base64 string (with padding fix)
     */
    private fun decodeBase64(base64: String): String {
        try {
            // Fix padding if needed
            var fixedBase64 = base64
            while (fixedBase64.length % 4 != 0) {
                fixedBase64 += "="
            }
            
            val decodedBytes = android.util.Base64.decode(
                fixedBase64,
                android.util.Base64.DEFAULT
            )
            
            return String(decodedBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding Base64", e)
            return ""
        }
    }
    
    /**
     * Update servers in the database
     */
    private suspend fun updateServers(subscriptionId: Long, servers: List<Server>) = withContext(Dispatchers.IO) {
        try {
            // Get existing servers for this subscription
            val existingServers = serverDao.getServersBySubscriptionId(subscriptionId)
            
            // Create a map of name to existing server
            val existingServerMap = existingServers.associateBy { "${it.protocol}-${it.address}-${it.port}" }
            
            // Track which servers to add, update, or remove
            val serversToAdd = mutableListOf<Server>()
            val serversToUpdate = mutableListOf<Server>()
            
            // Create a set to track which existing servers are still in the subscription
            val updatedServerKeys = mutableSetOf<String>()
            
            // Process all servers in the subscription
            for (server in servers) {
                val key = "${server.protocol}-${server.address}-${server.port}"
                updatedServerKeys.add(key)
                
                val existingServer = existingServerMap[key]
                if (existingServer != null) {
                    // Update existing server, preserving id and ping
                    val updatedServer = server.copy(
                        id = existingServer.id,
                        ping = existingServer.ping,
                        lastPingTime = existingServer.lastPingTime,
                        isActive = existingServer.isActive
                    )
                    serversToUpdate.add(updatedServer)
                } else {
                    // Add new server
                    serversToAdd.add(server)
                }
            }
            
            // Determine which servers to remove
            val serversToRemove = existingServers.filter { 
                val key = "${it.protocol}-${it.address}-${it.port}"
                !updatedServerKeys.contains(key)
            }
            
            // Update database
            if (serversToRemove.isNotEmpty()) {
                Log.i(TAG, "Removing ${serversToRemove.size} servers from subscription $subscriptionId")
                serverDao.deleteServers(serversToRemove)
            }
            
            if (serversToUpdate.isNotEmpty()) {
                Log.i(TAG, "Updating ${serversToUpdate.size} servers in subscription $subscriptionId")
                serverDao.updateServers(serversToUpdate)
            }
            
            if (serversToAdd.isNotEmpty()) {
                Log.i(TAG, "Adding ${serversToAdd.size} new servers to subscription $subscriptionId")
                serverDao.insertServers(serversToAdd)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating servers", e)
        }
    }
}