package com.hiddify.hiddifyng.utils

import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Utility for ping testing servers
 * Supports TCP, ICMP, and HTTP ping methods for comprehensive latency measurement
 */
object PingUtils {
    private const val TAG = "PingUtils"
    private const val TIMEOUT_MS = 5000
    private const val NUM_SAMPLES = 3 // Number of ping samples to average
    private const val FAILED_PING = -1
    
    /**
     * Ping a server using the most appropriate method based on server type
     * Returns the ping time in milliseconds, or FAILED_PING if failed
     */
    suspend fun pingServer(server: Server): Int {
        val host = parseHost(server.address)
        val port = parsePort(server.address)
        
        if (host.isEmpty() || port <= 0) {
            Log.e(TAG, "Invalid server address: ${server.address}")
            return FAILED_PING
        }
        
        // Try multiple ping methods and take the best result
        val results = mutableListOf<Int>()
        
        // Try TCP ping (most reliable for VPN servers)
        val tcpPing = pingTcp(host, port)
        if (tcpPing > 0) results.add(tcpPing)
        
        // Try ICMP ping if TCP failed
        if (results.isEmpty()) {
            val icmpPing = pingIcmp(host)
            if (icmpPing > 0) results.add(icmpPing)
        }
        
        // Try HTTP ping as a fallback
        if (results.isEmpty() && isCommonHttpPort(port)) {
            val httpPing = pingHttp(host, port)
            if (httpPing > 0) results.add(httpPing)
        }
        
        // Return the minimum successful ping value or FAILED_PING
        return if (results.isNotEmpty()) results.minOrNull() ?: FAILED_PING else FAILED_PING
    }
    
    /**
     * Parse host from server address
     */
    private fun parseHost(address: String): String {
        try {
            // Extract host from address formats:
            // host:port, host, [2001:db8::1]:port, etc.
            val hostPart = if (address.startsWith("[")) {
                // IPv6 address
                val closeBracketIndex = address.indexOf("]")
                if (closeBracketIndex > 0) {
                    address.substring(1, closeBracketIndex)
                } else {
                    ""
                }
            } else {
                // IPv4 or hostname
                val colonIndex = address.lastIndexOf(":")
                if (colonIndex > 0) {
                    address.substring(0, colonIndex)
                } else {
                    address
                }
            }
            
            return hostPart.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing host from address: $address", e)
            return ""
        }
    }
    
    /**
     * Parse port from server address
     */
    private fun parsePort(address: String): Int {
        try {
            // Extract port from address formats:
            // host:port, [2001:db8::1]:port
            val portStr = when {
                address.contains("]:") -> {
                    // IPv6 with port
                    address.substringAfterLast("]:")
                }
                address.contains(":") && !address.startsWith("[") -> {
                    // IPv4 with port or hostname:port
                    address.substringAfterLast(":")
                }
                else -> {
                    // Default to common ports
                    return 443 // Default to HTTPS port
                }
            }
            
            return portStr.trim().toIntOrNull() ?: 443
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing port from address: $address", e)
            return 443 // Default to HTTPS port
        }
    }
    
    /**
     * Perform a TCP ping
     */
    private fun pingTcp(host: String, port: Int): Int {
        var bestTime = FAILED_PING
        var socket: Socket? = null
        
        for (i in 0 until NUM_SAMPLES) {
            try {
                val startTime = System.nanoTime()
                socket = Socket()
                socket.connect(InetSocketAddress(host, port), TIMEOUT_MS)
                val endTime = System.nanoTime()
                
                val timeMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime)
                
                // Update best time
                if (bestTime == FAILED_PING || timeMs < bestTime) {
                    bestTime = timeMs.toInt()
                }
                
                Log.d(TAG, "TCP ping to $host:$port - $timeMs ms")
            } catch (e: Exception) {
                Log.d(TAG, "TCP ping to $host:$port failed", e)
            } finally {
                try {
                    socket?.close()
                } catch (e: IOException) {
                    // Ignore
                }
            }
            
            // Small delay between samples
            Thread.sleep(100)
        }
        
        return bestTime
    }
    
    /**
     * Perform an ICMP ping (requires root)
     */
    private fun pingIcmp(host: String): Int {
        try {
            val runtime = Runtime.getRuntime()
            var pingTimeout = min(TIMEOUT_MS, 5000).toString()
            
            // Determine OS and adjust command
            val pingCommand = if (System.getProperty("os.name").lowercase().contains("windows")) {
                // Windows ping
                "ping -n 1 -w $pingTimeout $host"
            } else {
                // Linux/Android ping
                "ping -c 1 -W $pingTimeout $host"
            }
            
            val startTime = System.nanoTime()
            val process = runtime.exec(pingCommand)
            val exitValue = process.waitFor()
            val endTime = System.nanoTime()
            
            return if (exitValue == 0) {
                // Ping succeeded
                val timeMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime)
                Log.d(TAG, "ICMP ping to $host - $timeMs ms")
                timeMs.toInt()
            } else {
                Log.d(TAG, "ICMP ping to $host failed with exit code $exitValue")
                FAILED_PING
            }
        } catch (e: Exception) {
            Log.d(TAG, "ICMP ping to $host failed", e)
            return FAILED_PING
        }
    }
    
    /**
     * Perform an HTTP ping
     */
    private fun pingHttp(host: String, port: Int): Int {
        var bestTime = FAILED_PING
        
        for (i in 0 until NUM_SAMPLES) {
            try {
                val protocol = if (port == 443) "https" else "http"
                val url = java.net.URL("$protocol://$host:$port")
                
                val startTime = System.nanoTime()
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.requestMethod = "HEAD" // Just get headers, not body
                
                val responseCode = connection.responseCode
                val endTime = System.nanoTime()
                
                if (responseCode >= 200 && responseCode < 400) {
                    val timeMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime)
                    
                    // Update best time
                    if (bestTime == FAILED_PING || timeMs < bestTime) {
                        bestTime = timeMs.toInt()
                    }
                    
                    Log.d(TAG, "HTTP ping to $host:$port - $timeMs ms")
                } else {
                    Log.d(TAG, "HTTP ping to $host:$port failed with response code $responseCode")
                }
                
                connection.disconnect()
            } catch (e: Exception) {
                Log.d(TAG, "HTTP ping to $host:$port failed", e)
            }
            
            // Small delay between samples
            Thread.sleep(100)
        }
        
        return bestTime
    }
    
    /**
     * Check if port is a common HTTP/HTTPS port
     */
    private fun isCommonHttpPort(port: Int): Boolean {
        return port == 80 || port == 443 || port == 8080 || port == 8443
    }
}