package com.hiddify.hiddifyng.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hiddify.hiddifyng.database.entity.Server

@Dao
interface ServerDao {
    @Query("SELECT * FROM server ORDER BY name ASC")
    fun getAllServersLive(): LiveData<List<Server>>
    
    @Query("SELECT * FROM server ORDER BY name ASC")
    suspend fun getAllServers(): List<Server>
    
    @Query("SELECT * FROM server WHERE groupId = :groupId ORDER BY name ASC")
    fun getServersByGroupLive(groupId: Long): LiveData<List<Server>>
    
    @Query("SELECT * FROM server WHERE groupId = :groupId ORDER BY name ASC")
    suspend fun getServersByGroup(groupId: Long): List<Server>
    
    @Query("SELECT * FROM server WHERE id = :id")
    suspend fun getServerById(id: Long): Server?
    
    @Query("SELECT * FROM server ORDER BY ping ASC, name ASC")
    suspend fun getServersOrderedByPing(): List<Server>
    
    @Query("SELECT * FROM server WHERE ping > 0 AND ping < :maxPing ORDER BY ping ASC LIMIT 1")
    suspend fun getBestServerByPing(maxPing: Int = Integer.MAX_VALUE): Server?
    
    @Insert
    suspend fun insert(server: Server): Long
    
    @Update
    suspend fun updateServer(server: Server)
    
    @Delete
    suspend fun deleteServer(server: Server)
    
    @Query("DELETE FROM server WHERE id = :id")
    suspend fun deleteServerById(id: Long)
    
    /**
     * Import servers from URL
     * @param url URL containing subscription data
     * @param groupId Group ID to assign to imported servers
     * @return Number of servers successfully imported
     */
    @Transaction
    suspend fun importFromUrl(url: String, groupId: Long): Int {
        // Implementation would be done in a repository
        return 0
    }
    
    /**
     * Parse and import server from subscription URL content
     * @param content Raw subscription content
     * @param groupId Group ID to assign to imported servers
     * @return Number of servers successfully imported
     */
    @Transaction
    suspend fun importFromContent(content: String, groupId: Long): Int {
        // Implementation would be done in a repository
        return 0
    }
    
    /**
     * Parse server from URL
     * @param url Server configuration URL
     * @return Server object if parsing successful, null otherwise
     */
    @Transaction
    suspend fun parseServerFromUrl(url: String): Server? {
        // Implementation would be done in a repository
        return null
    }
    
    /**
     * Update ping for server
     * @param id Server ID
     * @param ping Ping value in milliseconds
     */
    @Query("UPDATE server SET ping = :ping WHERE id = :id")
    suspend fun updatePing(id: Long, ping: Int)
    
    /**
     * Get server count
     * @return Number of servers in database
     */
    @Query("SELECT COUNT(*) FROM server")
    suspend fun getCount(): Int
}
