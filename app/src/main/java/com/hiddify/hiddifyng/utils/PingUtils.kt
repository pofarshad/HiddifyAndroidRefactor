package com.hiddify.hiddifyng.utils

import android.util.Log
import com.hiddify.hiddifyng.database.entity.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Utility class for testing server ping and connectivity
 */
class PingUtils {
    private val TAG = "PingUtils"
    
    companion object {
        private const val PING_COUNT = 3  // Number of ping attempts
        private const val PING_TIMEOUT = 2000  // Timeout in milliseconds
        private const val TCP_CONNECT_TIMEOUT = 3000  // Timeout for TCP connection test
        
        /**
         * Test ping to a server
         * @param server Server to ping
         * @return Ping time in milliseconds, -1 if failed
         */
        suspend fun pingServer(server: Server): Int {
            return withContext(Dispatchers.IO) {
                try {
                    Log.d("PingUtils", "Pinging server ${server.name} at ${server.address}:${server.port}")
                    
                    // Try both ICMP ping and TCP connection test
                    val icmpPing = pingICMP(server.address)
                    val tcpPing = pingTCP(server.address, server.port)
                    
                    // Use the better method that succeeded
                    val pingTime = when {
                        icmpPing > 0 && tcpPing > 0 -> min(icmpPing, tcpPing)
                        icmpPing > 0 -> icmpPing
                        tcpPing > 0 -> tcpPing
                        else -> -1
                    }
                    
                    Log.d("PingUtils", "Ping result for ${server.name}: $pingTime ms")
                    return@withContext pingTime
                } catch (e: Exception) {
                    Log.e("PingUtils", "Error pinging server ${server.name}", e)
                    return@withContext -1
                }
            }
        }
        
        /**
         * Ping a server using ICMP protocol
         * @param address Server address
         * @return Ping time in milliseconds, -1 if failed
         */
        private suspend fun pingICMP(address: String): Int {
            return withContext(Dispatchers.IO) {
                try {
                    val pingCommand = "ping -c $PING_COUNT -W ${PING_TIMEOUT / 1000} $address"
                    val process = Runtime.getRuntime().exec(pingCommand)
                    
                    // Wait for ping to complete with timeout
                    val completed = process.waitFor(PING_TIMEOUT.toLong() * PING_COUNT, TimeUnit.MILLISECONDS)
                    
                    if (!completed) {
                        process.destroy()
                        return@withContext -1
                    }
                    
                    // Parse ping output
                    val output = process.inputStream.bufferedReader().use { it.readText() }
                    
                    // Extract average ping time
                    val timeRegex = """time=(\d+(\.\d+)?) ms""".toRegex()
                    val matches = timeRegex.findAll(output)
                    val pingTimes = matches.map { it.groupValues[1].toFloatOrNull() ?: 0f }.toList()
                    
                    if (pingTimes.isEmpty()) {
                        return@withContext -1
                    }
                    
                    // Calculate average ping time
                    val averagePing = pingTimes.sum() / pingTimes.size
                    return@withContext averagePing.toInt()
                } catch (e: Exception) {
                    Log.e("PingUtils", "Error during ICMP ping to $address", e)
                    return@withContext -1
                }
            }
        }
        
        /**
         * Test TCP connection to a server
         * @param address Server address
         * @param port Server port
         * @return Connection time in milliseconds, -1 if failed
         */
        private suspend fun pingTCP(address: String, port: Int): Int {
            return withContext(Dispatchers.IO) {
                var socket: Socket? = null
                
                try {
                    val startTime = System.currentTimeMillis()
                    
                    socket = Socket()
                    socket.connect(InetSocketAddress(address, port), TCP_CONNECT_TIMEOUT)
                    
                    val endTime = System.currentTimeMillis()
                    val connectionTime = (endTime - startTime).toInt()
                    
                    // Validate the connection time (avoid unrealistic values)
                    val validatedTime = max(1, min(connectionTime, TCP_CONNECT_TIMEOUT))
                    
                    return@withContext validatedTime
                } catch (e: IOException) {
                    Log.e("PingUtils", "TCP connection failed to $address:$port", e)
                    return@withContext -1
                } finally {
                    try {
                        socket?.close()
                    } catch (e: IOException) {
                        Log.e("PingUtils", "Error closing socket", e)
                    }
                }
            }
        }
        
        /**
         * Check if a host is reachable
         * @param host Host address
         * @return true if reachable, false otherwise
         */
        suspend fun isHostReachable(host: String): Boolean {
            return withContext(Dispatchers.IO) {
                try {
                    val address = InetAddress.getByName(host)
                    return@withContext address.isReachable(PING_TIMEOUT)
                } catch (e: Exception) {
                    Log.e("PingUtils", "Error checking if host is reachable: $host", e)
                    return@withContext false
                }
            }
        }
    }
}