package com.hiddify.hiddifyng.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hiddify.hiddifyng.database.entity.Server

/**
 * DAO for Server entity
 */
@Dao
interface ServerDao {
    @Query("SELECT * FROM server ORDER BY name ASC")
    fun getAllServers(): LiveData<List<Server>>
    
    @Query("SELECT * FROM server WHERE id = :serverId")
    fun getServerById(serverId: Long): LiveData<Server?>
    
    @Query("SELECT * FROM server WHERE id = :serverId")
    suspend fun getServerByIdSync(serverId: Long): Server?
    
    @Query("SELECT * FROM server WHERE groupId = :groupId ORDER BY name ASC")
    fun getServersByGroup(groupId: Long): LiveData<List<Server>>
    
    @Query("SELECT * FROM server ORDER BY ping ASC LIMIT 1")
    suspend fun getServerWithLowestPing(): Server?
    
    @Query("SELECT COUNT(*) FROM server")
    suspend fun getServerCount(): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(server: Server): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(servers: List<Server>): List<Long>
    
    @Update
    suspend fun update(server: Server)
    
    @Delete
    suspend fun delete(server: Server)
    
    @Query("DELETE FROM server WHERE groupId = :groupId")
    suspend fun deleteByGroup(groupId: Long)
    
    @Query("UPDATE server SET ping = :ping WHERE id = :serverId")
    suspend fun updatePing(serverId: Long, ping: Int)
}