package com.hiddify.hiddifyng.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Utility class for tracking network traffic statistics
 */
class TrafficStats(private val context: Context) {
    companion object {
        private const val TAG = "TrafficStats"
        private const val STATS_PATH = "traffic_stats"
    }
    
    // Current session statistics
    private val uploadBytes = AtomicLong(0)
    private val downloadBytes = AtomicLong(0)
    
    // Total statistics (persisted)
    private var totalUploadBytes = AtomicLong(0)
    private var totalDownloadBytes = AtomicLong(0)
    
    // Last update timestamp
    private var lastUpdated = System.currentTimeMillis()
    
    // Upload/download speeds in bytes per second
    private var uploadSpeed = 0L
    private var downloadSpeed = 0L
    
    init {
        // Load persisted stats
        loadStats()
    }
    
    /**
     * Update traffic statistics with new values
     */
    fun updateStats(newUploadBytes: Long, newDownloadBytes: Long) {
        val currentTime = System.currentTimeMillis()
        val timeDiff = (currentTime - lastUpdated) / 1000.0 // time diff in seconds
        
        if (timeDiff > 0) {
            // Calculate speed
            val upDiff = newUploadBytes - uploadBytes.get()
            val downDiff = newDownloadBytes - downloadBytes.get()
            
            uploadSpeed = (upDiff / timeDiff).toLong()
            downloadSpeed = (downDiff / timeDiff).toLong()
            
            // Update total bytes
            totalUploadBytes.addAndGet(upDiff)
            totalDownloadBytes.addAndGet(downDiff)
            
            // Update current session bytes
            uploadBytes.set(newUploadBytes)
            downloadBytes.set(newDownloadBytes)
            
            // Update timestamp
            lastUpdated = currentTime
            
            // Save updated stats
            saveStats()
        }
    }
    
    /**
     * Reset session statistics
     */
    fun resetSessionStats() {
        uploadBytes.set(0)
        downloadBytes.set(0)
        uploadSpeed = 0
        downloadSpeed = 0
        lastUpdated = System.currentTimeMillis()
    }
    
    /**
     * Get current upload speed in bytes per second
     */
    fun getUploadSpeed(): Long = uploadSpeed
    
    /**
     * Get current download speed in bytes per second
     */
    fun getDownloadSpeed(): Long = downloadSpeed
    
    /**
     * Get session upload bytes
     */
    fun getSessionUploadBytes(): Long = uploadBytes.get()
    
    /**
     * Get session download bytes
     */
    fun getSessionDownloadBytes(): Long = downloadBytes.get()
    
    /**
     * Get total upload bytes
     */
    fun getTotalUploadBytes(): Long = totalUploadBytes.get()
    
    /**
     * Get total download bytes
     */
    fun getTotalDownloadBytes(): Long = totalDownloadBytes.get()
    
    /**
     * Format bytes to human-readable string
     */
    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
    
    /**
     * Format speed to human-readable string
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        return "${formatBytes(bytesPerSecond)}/s"
    }
    
    /**
     * Save stats to persistence
     */
    private fun saveStats() {
        try {
            val statsFile = File(context.filesDir, STATS_PATH)
            statsFile.writeText("${totalUploadBytes.get()},${totalDownloadBytes.get()}")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving traffic stats", e)
        }
    }
    
    /**
     * Load stats from persistence
     */
    private fun loadStats() {
        try {
            val statsFile = File(context.filesDir, STATS_PATH)
            if (statsFile.exists()) {
                val stats = statsFile.readText().split(",")
                if (stats.size == 2) {
                    totalUploadBytes.set(stats[0].toLongOrNull() ?: 0)
                    totalDownloadBytes.set(stats[1].toLongOrNull() ?: 0)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading traffic stats", e)
        }
    }
}