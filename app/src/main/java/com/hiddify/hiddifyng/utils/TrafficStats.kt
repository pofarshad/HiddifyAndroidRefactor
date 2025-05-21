package com.hiddify.hiddifyng.utils

import android.content.Context
import java.text.DecimalFormat
import java.util.concurrent.atomic.AtomicLong

/**
 * Class for tracking network traffic statistics
 */
class TrafficStats(private val context: Context) {
    
    // Traffic counters
    private val uploadBytes = AtomicLong(0)
    private val downloadBytes = AtomicLong(0)
    private val uploadSpeed = AtomicLong(0)
    private val downloadSpeed = AtomicLong(0)
    
    // Last update timestamp
    private var lastUpdateTime = System.currentTimeMillis()
    
    /**
     * Update traffic statistics
     * @param uploadDelta Upload bytes since last update
     * @param downloadDelta Download bytes since last update
     */
    fun updateStats(uploadDelta: Long, downloadDelta: Long) {
        // Update counters
        uploadBytes.addAndGet(uploadDelta)
        downloadBytes.addAndGet(downloadDelta)
        
        // Calculate speeds
        val currentTime = System.currentTimeMillis()
        val timeDelta = currentTime - lastUpdateTime
        
        if (timeDelta > 0) {
            uploadSpeed.set((uploadDelta * 1000) / timeDelta)
            downloadSpeed.set((downloadDelta * 1000) / timeDelta)
            lastUpdateTime = currentTime
        }
    }
    
    /**
     * Get upload traffic in bytes
     * @return Upload traffic in bytes
     */
    fun getUploadBytes(): Long {
        return uploadBytes.get()
    }
    
    /**
     * Get download traffic in bytes
     * @return Download traffic in bytes
     */
    fun getDownloadBytes(): Long {
        return downloadBytes.get()
    }
    
    /**
     * Get upload speed in bytes per second
     * @return Upload speed in bytes per second
     */
    fun getUploadSpeed(): Long {
        return uploadSpeed.get()
    }
    
    /**
     * Get download speed in bytes per second
     * @return Download speed in bytes per second
     */
    fun getDownloadSpeed(): Long {
        return downloadSpeed.get()
    }
    
    /**
     * Reset all counters and speeds
     */
    fun reset() {
        uploadBytes.set(0)
        downloadBytes.set(0)
        uploadSpeed.set(0)
        downloadSpeed.set(0)
        lastUpdateTime = System.currentTimeMillis()
    }
    
    /**
     * Format bytes to human-readable string
     * @param bytes Bytes to format
     * @return Formatted string (e.g., "1.2 MB")
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        
        return DecimalFormat("#,##0.##").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
    
    /**
     * Format speed to human-readable string
     * @param bytesPerSecond Speed in bytes per second
     * @return Formatted string (e.g., "1.2 MB/s")
     */
    fun formatSpeed(bytesPerSecond: Long): String {
        return formatBytes(bytesPerSecond) + "/s"
    }
}