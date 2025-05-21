package com.hiddify.hiddifyng.utils

import com.hiddify.hiddifyng.database.entity.Server

/**
 * Comparator for sorting servers by ping time and favorites
 */
class ServerComparator : Comparator<Server> {
    override fun compare(a: Server?, b: Server?): Int {
        // Handle null values (should not happen in practice)
        if (a == null && b == null) return 0
        if (a == null) return 1
        if (b == null) return -1
        
        // Favorite servers come first
        if (a.favorite && !b.favorite) return -1
        if (!a.favorite && b.favorite) return 1
        
        // Then sort by ping (null ping values go to the end)
        val aPing = a.avgPing ?: Int.MAX_VALUE
        val bPing = b.avgPing ?: Int.MAX_VALUE
        
        return aPing.compareTo(bPing)
    }
}