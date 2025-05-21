package com.hiddify.hiddifyng.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity class for server groups/subscriptions
 */
@Entity(tableName = "server_group")
data class ServerGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Basic info
    var name: String,
    var url: String? = null,  // Subscription URL, null for manual groups
    var isSubscription: Boolean = false,
    
    // Subscription details
    var lastUpdated: Long? = null,  // Timestamp of last update
    var updateInterval: Int = 24,  // Update interval in hours
    var autoUpdate: Boolean = true,  // Whether to auto-update
    
    // Custom user-agent for subscription
    var userAgent: String? = null
)