package com.hiddify.hiddifyng.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity class for application settings
 */
@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey
    var id: Int = 1,  // Changed from val to var to fix Room compatibility
    
    // Theme and appearance
    var theme: String = "system",  // "system", "light", "dark"
    var language: String = "en",
    
    // Auto-start settings
    var autoStart: Boolean = false,  // Start on boot
    var autoConnect: Boolean = false,  // Auto-connect to preferred server
    
    // Preferred server
    var preferredServerId: Long? = null,
    
    // Auto-switch to best server
    var autoSwitchToBestServer: Boolean = false,
    var minPingThreshold: Int = 300,  // Min ping difference to trigger switch
    
    // Ping and update frequencies
    var pingFrequencyMinutes: Int = 30,
    var updateFrequencyHours: Int = 24,
    
    // Auto-update settings
    var autoUpdateEnabled: Boolean = true,
    var autoUpdateXrayCore: Boolean = true
)