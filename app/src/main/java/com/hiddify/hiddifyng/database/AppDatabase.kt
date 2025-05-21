package com.hiddify.hiddifyng.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hiddify.hiddifyng.database.dao.AppSettingsDao
import com.hiddify.hiddifyng.database.dao.ServerDao
import com.hiddify.hiddifyng.database.dao.ServerGroupDao
import com.hiddify.hiddifyng.database.entity.AppSettings
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.database.entity.ServerGroup
import com.hiddify.hiddifyng.database.converter.DateConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room database for the application
 * Optimized for performance with query execution on background thread
 */
@Database(
    entities = [Server::class, ServerGroup::class, AppSettings::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(DateConverter::class)
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
         * If it doesn't exist, create it with optimized configuration
         * @param context Application context
         * @return Singleton instance of AppDatabase
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "marfanet_database"  // Updated name to match new branding
                )
                    // Set journaling mode to WAL for better performance
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    // Enable multi-threading
                    .allowMainThreadQueries() // Only for immediate UI updates
                    // Add fallback migration in case schema changes
                    .fallbackToDestructiveMigration()
                    // Configure database creation callback
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            
                            // Initialize database with default settings in background thread
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.let { database ->
                                    // Create default settings
                                    val settingsDao = database.appSettingsDao()
                                    
                                    // Create optimized default settings
                                    val defaultSettings = AppSettings(
                                        id = 1,
                                        autoUpdateEnabled = true,
                                        updateFrequencyHours = 24,
                                        autoUpdateSubscriptions = true,
                                        autoSwitchToBestServer = true,
                                        pingFrequencyMinutes = 10,
                                        useCustomRoutingRules = true,
                                        routingMode = "smart",
                                        useDomainRules = true
                                    )
                                    settingsDao.insert(defaultSettings)
                                    
                                    // Create default server group
                                    val serverGroupDao = database.serverGroupDao()
                                    val defaultGroup = ServerGroup(
                                        id = 1,
                                        name = "Default",
                                        isSubscription = false,
                                        subscriptionUrl = null
                                    )
                                    serverGroupDao.insert(defaultGroup)
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
         * Database migrations for future updates
         * Add migrations here when database schema changes
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example migration for future use
                // database.execSQL("ALTER TABLE Server ADD COLUMN last_ping_time INTEGER")
            }
        }
        
        /**
         * Clear the database instance (for testing or user logout)
         */
        fun destroyInstance() {
            INSTANCE = null
        }
    }
}