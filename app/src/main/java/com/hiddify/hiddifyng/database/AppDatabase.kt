package com.hiddify.hiddifyng.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hiddify.hiddifyng.database.dao.AppSettingsDao
import com.hiddify.hiddifyng.database.dao.ServerDao
import com.hiddify.hiddifyng.database.dao.ServerGroupDao
import com.hiddify.hiddifyng.database.entity.AppSettings
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.database.entity.ServerGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room database for the application
 */
@Database(
    entities = [Server::class, ServerGroup::class, AppSettings::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun serverGroupDao(): ServerGroupDao
    abstract fun appSettingsDao(): AppSettingsDao
    
    companion object {
        // Singleton instance
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Get the database instance
         * If it doesn't exist, create it
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hiddify_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            
                            // Initialize database with default settings
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    // Create default settings
                                    val settingsDao = database.appSettingsDao()
                                    val defaultSettings = AppSettings()
                                    settingsDao.insert(defaultSettings)
                                }
                            }
                        }
                    })
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * Database migrations
         * Add migrations here when database schema changes
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migration code will be added when needed
            }
        }
    }
}