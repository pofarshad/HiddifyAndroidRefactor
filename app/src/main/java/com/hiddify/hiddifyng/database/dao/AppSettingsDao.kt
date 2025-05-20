package com.hiddify.hiddifyng.database.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import com.hiddify.hiddifyng.database.entity.AppSettings

/**
 * DAO for AppSettings entity
 */
@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsLive(): LiveData<AppSettings?>
    
    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettings?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: AppSettings)
    
    @Update
    suspend fun update(settings: AppSettings)
    
    @Query("UPDATE app_settings SET autoSwitchToBestServer = :enabled WHERE id = 1")
    suspend fun setAutoSwitchToBestServer(enabled: Boolean)
    
    @Query("UPDATE app_settings SET autoUpdateEnabled = :enabled WHERE id = 1")
    suspend fun setAutoUpdateEnabled(enabled: Boolean)
    
    @Query("UPDATE app_settings SET autoStart = :enabled WHERE id = 1")
    suspend fun setAutoStart(enabled: Boolean)
    
    @Query("UPDATE app_settings SET autoConnect = :enabled WHERE id = 1")
    suspend fun setAutoConnect(enabled: Boolean)
    
    @Query("UPDATE app_settings SET preferredServerId = :serverId WHERE id = 1")
    suspend fun setPreferredServerId(serverId: Long?)
}