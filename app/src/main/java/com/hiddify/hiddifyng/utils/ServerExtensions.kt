package com.hiddify.hiddifyng.utils

import com.hiddify.hiddifyng.database.entity.Server
import org.json.JSONObject

/**
 * Extension functions for Server entity
 */

/**
 * Get host from server address
 */
val Server.host: String
    get() {
        return parseHost(address)
    }

/**
 * Parse host from address format (host:port or [IPv6]:port)
 */
fun parseHost(address: String): String {
    return try {
        if (address.startsWith("[")) {
            // IPv6 address
            val endBracket = address.indexOf("]")
            if (endBracket > 0) {
                address.substring(1, endBracket)
            } else {
                address
            }
        } else {
            // IPv4 or hostname
            val colon = address.lastIndexOf(":")
            if (colon > 0) {
                address.substring(0, colon)
            } else {
                address
            }
        }
    } catch (e: Exception) {
        address
    }
}

/**
 * Parse port from server address
 */
fun parsePort(address: String): Int? {
    return try {
        if (address.startsWith("[")) {
            // IPv6 address
            val endBracket = address.indexOf("]")
            if (endBracket > 0 && address.length > endBracket + 1 && address[endBracket + 1] == ':') {
                address.substring(endBracket + 2).toIntOrNull()
            } else {
                null
            }
        } else {
            // IPv4 or hostname
            val colon = address.lastIndexOf(":")
            if (colon > 0) {
                address.substring(colon + 1).toIntOrNull()
            } else {
                null
            }
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Get IP or host from server
 */
val Server.ip: String
    get() = host

/**
 * Get port from server
 */
val Server.port: Int?
    get() = parsePort(address)

/**
 * Get extra parameters as JSONObject
 */
val Server.extraParams: JSONObject
    get() {
        return try {
            JSONObject(extraParams ?: "{}")
        } catch (e: Exception) {
            JSONObject()
        }
    }

/**
 * Get server icon resource based on protocol
 */
val Server.iconResource: Int
    get() {
        return android.R.drawable.ic_menu_manage // Placeholder - replace with actual resources
    }

/**
 * Get server flow attribute from extraParams
 */
val Server.flow: String?
    get() {
        return try {
            extraParams.optString("flow", null)
        } catch (e: Exception) {
            null
        }
    }

/**
 * Get server fingerprint attribute from extraParams
 */
val Server.fingerprint: String?
    get() {
        return try {
            extraParams.optString("fingerprint", null)
        } catch (e: Exception) {
            null
        }
    }