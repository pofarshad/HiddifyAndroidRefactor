package com.hiddify.hiddifyng.utils

import android.util.LruCache
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Memory cache for frequently accessed data to improve app performance
 * Uses an LRU (Least Recently Used) algorithm to efficiently manage memory
 */
class MemoryCache<K, V>(
    private val maxSize: Int,
    private val expireTimeMillis: Long = TimeUnit.MINUTES.toMillis(10)
) {
    // Thread-safe operations
    private val mutex = Mutex()
    
    // Internal data structure for cache entries with timestamps
    private data class CacheEntry<V>(val value: V, val timestamp: Long)
    
    // LruCache implementation for efficient memory usage
    private val cache = object : LruCache<K, CacheEntry<V>>(maxSize) {
        override fun sizeOf(key: K, value: CacheEntry<V>): Int {
            // Custom size calculation, override for complex objects if needed
            return 1
        }
    }
    
    /**
     * Store a value in the cache
     */
    suspend fun put(key: K, value: V) = mutex.withLock {
        cache.put(key, CacheEntry(value, System.currentTimeMillis()))
    }
    
    /**
     * Get a value from the cache, returns null if expired or not found
     */
    suspend fun get(key: K): V? = mutex.withLock {
        val entry = cache.get(key) ?: return@withLock null
        
        // Check if the entry has expired
        val now = System.currentTimeMillis()
        if (now - entry.timestamp > expireTimeMillis) {
            cache.remove(key)
            return@withLock null
        }
        
        return@withLock entry.value
    }
    
    /**
     * Remove an item from the cache
     */
    suspend fun remove(key: K) = mutex.withLock {
        cache.remove(key)
    }
    
    /**
     * Clear all entries from the cache
     */
    suspend fun clear() = mutex.withLock {
        cache.evictAll()
    }
    
    /**
     * Clean up expired entries
     */
    suspend fun cleanupExpired() = mutex.withLock {
        val now = System.currentTimeMillis()
        val keysToRemove = mutableListOf<K>()
        
        // Find all expired entries
        for (key in cache.snapshot().keys) {
            val entry = cache.get(key) ?: continue
            if (now - entry.timestamp > expireTimeMillis) {
                keysToRemove.add(key)
            }
        }
        
        // Remove expired entries
        keysToRemove.forEach { cache.remove(it) }
    }
    
    /**
     * Get the current size of the cache
     */
    fun size(): Int = cache.size()
}