package com.hiddify.hiddifyng.utils

import android.os.SystemClock
import java.io.BufferedReader
import java.io.FileReader
import java.util.concurrent.TimeUnit

/**
 * Helper class to track and calculate network traffic statistics
 */
data class TrafficStats(
    val rxBytes: Long = 0,
    val txBytes: Long = 0,
    val timestamp: Long = SystemClock.elapsedRealtime(),
    val connectTime: Long = 0
) {
    companion object {
        // Create an empty stats object
        fun empty() = TrafficStats()
        
        // Create from total bytes counters
        fun fromBytes(rx: Long, tx: Long): TrafficStats = TrafficStats(
            rxBytes = rx,
            txBytes = tx
        )
        
        // Parse Xray stats from file
        fun fromStatsFile(path: String): TrafficStats? {
            return try {
                var rx = 0L
                var tx = 0L
                
                BufferedReader(FileReader(path)).use { reader ->
                    reader.lineSequence().forEach { line ->
                        when {
                            line.startsWith("uplink:") -> {
                                tx = line.substringAfter("uplink:").trim().toLongOrNull() ?: 0L
                            }
                            line.startsWith("downlink:") -> {
                                rx = line.substringAfter("downlink:").trim().toLongOrNull() ?: 0L
                            }
                        }
                    }
                }
                
                TrafficStats(rx, tx)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // Calculate speed difference between two stats objects
    fun speedDiff(previous: TrafficStats): Pair<Long, Long> {
        val elapsedTimeMs = timestamp - previous.timestamp
        if (elapsedTimeMs <= 0) return Pair(0, 0)
        
        val elapsedTimeSec = TimeUnit.MILLISECONDS.toSeconds(elapsedTimeMs).coerceAtLeast(1)
        val rxDiff = (rxBytes - previous.rxBytes).coerceAtLeast(0)
        val txDiff = (txBytes - previous.txBytes).coerceAtLeast(0)
        
        return Pair(rxDiff / elapsedTimeSec, txDiff / elapsedTimeSec)
    }
    
    // Format bytes as human-readable string
    fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
    
    // Format total traffic
    fun formatTotal(): String = "↓ ${formatBytes(rxBytes)} ↑ ${formatBytes(txBytes)}"
    
    // Format speed
    fun formatSpeed(previous: TrafficStats): String {
        val (rxSpeed, txSpeed) = speedDiff(previous)
        return "↓ ${formatBytes(rxSpeed)}/s ↑ ${formatBytes(txSpeed)}/s"
    }
}