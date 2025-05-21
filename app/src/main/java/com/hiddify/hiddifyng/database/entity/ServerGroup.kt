package com.hiddify.hiddifyng.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Entity class for server groups/subscriptions
 * Optimized with indices for faster querying
 */
@Entity(
    tableName = "server_group",
    indices = [
        Index("name", unique = true),
        Index("subscriptionUrl")
    ]
)
data class ServerGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    // Basic info
    var name: String,
    var subscriptionUrl: String? = null,  // Subscription URL, null for manual groups
    var isSubscription: Boolean = false,
    
    // Subscription details
    var lastUpdated: Date? = null,  // Timestamp of last update (using Date for TypeConverter)
    var updateInterval: Int = 24,   // Update interval in hours
    var autoUpdate: Boolean = true, // Whether to auto-update
    
    // Subscription authentication
    var userAgent: String? = null,  // Custom user-agent for subscription
    var authUsername: String? = null, // Basic auth username (if needed)
    var authPassword: String? = null, // Basic auth password (if needed)
    
    // Statistics and metadata
    var serverCount: Int = 0,      // Number of servers in this group (for faster UI)
    var lastSuccessfulUpdate: Date? = null, // When last successful update happened
    var updateErrorCount: Int = 0,  // Count of consecutive update failures
    var lastError: String? = null,   // Last error message
    
    // Display settings
    var displayOrder: Int = 0,       // Order in the UI
    var color: String? = null,       // Color for the group (hex code)
    var expanded: Boolean = true     // Whether the group is expanded in UI
)