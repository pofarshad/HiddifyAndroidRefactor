package com.hiddify.hiddifyng.utils

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

/**
 * Connection pool manager for improved network performance
 * Reuses connections to the same hosts to reduce latency
 */
object ConnectionPool {
    private const val TAG = "ConnectionPool"
    
    // OkHttp connection pool configuration
    private val okHttpPool = ConnectionPool(
        maxIdleConnections = 20,  // Maximum idle connections to keep in the pool
        keepAliveDuration = 30,   // Keep connections alive for 30 seconds
        timeUnit = TimeUnit.SECONDS
    )
    
    // Shared OkHttpClient instance with connection pooling
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(okHttpPool)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
    
    // For standard HttpURLConnection
    private val urlConnectionPool = ConcurrentHashMap<String, MutableList<HttpURLConnection>>()
    private val mutex = Mutex()
    
    /**
     * Get an HttpURLConnection from the pool or create a new one
     */
    suspend fun getConnection(urlString: String): HttpURLConnection = mutex.withLock {
        val host = URL(urlString).host
        val connections = urlConnectionPool.getOrPut(host) { mutableListOf() }
        
        // Try to reuse an existing connection
        val connection = connections.removeFirstOrNull() ?: createNewConnection(urlString)
        
        return connection
    }
    
    /**
     * Return a connection to the pool for future reuse
     */
    suspend fun releaseConnection(connection: HttpURLConnection) = mutex.withLock {
        try {
            val host = connection.url.host
            val connections = urlConnectionPool.getOrPut(host) { mutableListOf() }
            
            // Only keep a reasonable number of connections per host
            if (connections.size < 5) {
                // Reset the connection for reuse
                connection.disconnect()
                connection.connect()
                connections.add(connection)
            } else {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing connection", e)
            connection.disconnect()
        }
    }
    
    /**
     * Create a new connection with optimized settings
     */
    private fun createNewConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        
        // Configure for performance
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        connection.useCaches = true
        connection.defaultUseCaches = true
        
        return connection
    }
    
    /**
     * Clean up old connections
     */
    fun cleanup() {
        try {
            urlConnectionPool.forEach { (_, connections) ->
                connections.forEach { it.disconnect() }
                connections.clear()
            }
            urlConnectionPool.clear()
        } catch (e: Exception) {
            Log.e(TAG, "Error during connection pool cleanup", e)
        }
    }
}