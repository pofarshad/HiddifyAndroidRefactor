package com.hiddify.hiddifyng.utils

import java.io.Serializable

/**
 * Data class to hold app update information
 */
data class UpdateInfo(
    val version: String,
    val versionCode: Int,
    val releaseDate: String,
    val downloadUrl: String,
    val changelogUrl: String,
    val critical: Boolean = false
) : Serializable {
    // Return a simple version string like "v1.2.3"
    fun versionString(): String = "v$version"
    
    // Check if this version is newer than the current app version
    fun isNewerThan(currentVersionCode: Int): Boolean = versionCode > currentVersionCode
    
    companion object {
        // Create an update info from JSON data
        fun fromJson(json: Map<String, Any>): UpdateInfo? {
            return try {
                UpdateInfo(
                    version = json["version"] as? String ?: "",
                    versionCode = (json["versionCode"] as? Number)?.toInt() ?: 0,
                    releaseDate = json["releaseDate"] as? String ?: "",
                    downloadUrl = json["downloadUrl"] as? String ?: "",
                    changelogUrl = json["changelogUrl"] as? String ?: "",
                    critical = json["critical"] as? Boolean ?: false
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}