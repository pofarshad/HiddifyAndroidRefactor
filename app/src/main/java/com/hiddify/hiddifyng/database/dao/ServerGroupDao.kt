package com.hiddify.hiddifyng.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hiddify.hiddifyng.database.entity.ServerGroup

/**
 * DAO for ServerGroup entity
 */
@Dao
interface ServerGroupDao {
    @Query("SELECT * FROM server_group ORDER BY name ASC")
    fun getAllGroups(): LiveData<List<ServerGroup>>
    
    @Query("SELECT * FROM server_group WHERE id = :groupId")
    fun getGroupById(groupId: Long): LiveData<ServerGroup?>
    
    @Query("SELECT * FROM server_group WHERE id = :groupId")
    suspend fun getGroupByIdSync(groupId: Long): ServerGroup?
    
    @Query("SELECT * FROM server_group WHERE isSubscription = 1")
    fun getAllSubscriptions(): LiveData<List<ServerGroup>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(group: ServerGroup): Long
    
    @Update
    suspend fun update(group: ServerGroup)
    
    @Delete
    suspend fun delete(group: ServerGroup)
    
    @Query("UPDATE server_group SET lastUpdated = :timestamp WHERE id = :groupId")
    suspend fun updateLastUpdatedTime(groupId: Long, timestamp: Long)
    
    @Query("SELECT * FROM server_group WHERE isSubscription = 1 AND autoUpdate = 1")
    suspend fun getAutoUpdateSubscriptions(): List<ServerGroup>
}