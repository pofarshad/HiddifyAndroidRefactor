package com.hiddify.hiddifyng.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

class PingUtils(private val context: Context) {
    private val TAG = "PingUtils"
    
    /**
     * Measures the ping time to a server in milliseconds
     * 
     * @param address The server address to ping
     * @param port Optional port to check (default: 80)
     * @param timeout Timeout in milliseconds
     * @return Ping time in milliseconds, or Int.MAX_VALUE if failed
     */
    suspend fun measurePing(
        address: String,
        port: Int = 80,
        timeout: Int = 5000
    ): Int = withContext(Dispatchers.IO) {
        try {
            // First try ICMP ping if available (requires root)
            val icmpPing = tryIcmpPing(address)
            if (icmpPing > 0 && icmpPing < Int.MAX_VALUE) {
                return@withContext icmpPing
            }
            
            // Fall back to TCP ping if ICMP failed
            return@withContext tryTcpPing(address, port, timeout)
        } catch (e: Exception) {
            Log.e(TAG, "Error measuring ping to $address", e)
            return@withContext Int.MAX_VALUE
        }
    }
    
    /**
     * Tries to perform an ICMP ping using Runtime.exec
     * Note: This might require root access on some devices
     */
    private fun tryIcmpPing(address: String): Int {
        try {
            val runtime = Runtime.getRuntime()
            val pingCommand = "/system/bin/ping -c 1 -W 3 $address"
            val process = runtime.exec(pingCommand)
            
            val exitValue = process.waitFor()
            if (exitValue != 0) {
                return Int.MAX_VALUE
            }
            
            // Parse the ping time from output
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val timeRegex = Regex("time=(\\d+(\\.\\d+)?) ms")
            val match = timeRegex.find(output)
            
            return match?.groupValues?.get(1)?.toFloatOrNull()?.toInt() ?: Int.MAX_VALUE
        } catch (e: Exception) {
            Log.d(TAG, "ICMP ping failed: ${e.message}")
            return Int.MAX_VALUE
        }
    }
    
    /**
     * Tries to perform a TCP ping by measuring socket connection time
     */
    private fun tryTcpPing(address: String, port: Int, timeout: Int): Int {
        val socket = Socket()
        val start = System.nanoTime()
        
        try {
            socket.connect(InetSocketAddress(address, port), timeout)
            val pingTime = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start).toInt()
            return if (pingTime == 0) 1 else pingTime // Minimum 1ms to avoid confusion with failed pings
        } catch (e: IOException) {
            Log.d(TAG, "TCP ping failed: ${e.message}")
            return Int.MAX_VALUE
        } finally {
            try {
                socket.close()
            } catch (e: IOException) {
                // Ignore
            }
        }
    }
    
    /**
     * Pings multiple servers and returns them sorted by ping time
     */
    suspend fun pingAllAndSortBySpeed(servers: List<String>): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, Int>>()
        
        for (server in servers) {
            val ping = measurePing(server)
            results.add(Pair(server, ping))
        }
        
        // Sort by ping time (lowest first)
        return@withContext results.sortedBy { it.second }
    }
    
    /**
     * Determines if a host is reachable
     */
    suspend fun isHostReachable(host: String, timeout: Int = 5000): Boolean = withContext(Dispatchers.IO) {
        try {
            return@withContext InetAddress.getByName(host).isReachable(timeout)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if host is reachable: $host", e)
            return@withContext false
        }
    }
}
