package com.hiddify.hiddifyng.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hiddify.hiddifyng.database.converter.Converters
import com.hiddify.hiddifyng.database.dao.AppSettingsDao
import com.hiddify.hiddifyng.database.dao.ServerDao
import com.hiddify.hiddifyng.database.dao.ServerGroupDao
import com.hiddify.hiddifyng.database.entity.AppSettings
import com.hiddify.hiddifyng.database.entity.Server
import com.hiddify.hiddifyng.database.entity.ServerGroup

@Database(
    entities = [
        Server::class,
        ServerGroup::class,
        AppSettings::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun serverGroupDao(): ServerGroupDao
    abstract fun appSettingsDao(): AppSettingsDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hiddify_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                
                INSTANCE = instance
                
                // Initialize default settings if needed
                instance.initializeDefaults()
                
                instance
            }
        }
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to server table for new protocol support
                database.execSQL("ALTER TABLE server ADD COLUMN hysteriaProtocol TEXT")
                database.execSQL("ALTER TABLE server ADD COLUMN hysteriaUpMbps INTEGER")
                database.execSQL("ALTER TABLE server ADD COLUMN hysteriaDownMbps INTEGER")
                database.execSQL("ALTER TABLE server ADD COLUMN hysteriaObfs TEXT")
                database.execSQL("ALTER TABLE server ADD COLUMN hysteriaAuthString TEXT")
                database.execSQL("ALTER TABLE server ADD COLUMN hysteriaRecvWindowConn INTEGER")
                database.execSQL("ALTER TABLE server ADD COLUMN hysteriaRecvWindow INTEGER")
                database.execSQL("ALTER TABLE server ADD COLUMN hysteriaDisableMtuDiscovery INTEGER")
                database.execSQL("ALTER TABLE server ADD COLUMN ping INTEGER DEFAULT 0")
                database.execSQL("ALTER TABLE server ADD COLUMN customRules TEXT")
                database.execSQL("ALTER TABLE server ADD COLUMN routingMode TEXT")
                
                // Create app_settings table
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS app_settings (" +
                    "id INTEGER PRIMARY KEY NOT NULL," +
                    "theme TEXT NOT NULL DEFAULT 'system'," +
                    "language TEXT NOT NULL DEFAULT 'en'," +
                    "autoStart INTEGER NOT NULL DEFAULT 0," +
                    "autoConnect INTEGER NOT NULL DEFAULT 0," +
                    "preferredServerId INTEGER," +
                    "autoSwitchToBestServer INTEGER NOT NULL DEFAULT 0," +
                    "minPingThreshold INTEGER NOT NULL DEFAULT 300," +
                    "pingFrequencyMinutes INTEGER NOT NULL DEFAULT 30," +
                    "updateFrequencyHours INTEGER NOT NULL DEFAULT 24," +
                    "autoUpdateEnabled INTEGER NOT NULL DEFAULT 1," +
                    "autoUpdateXrayCore INTEGER NOT NULL DEFAULT 1)"
                )
                
                // Insert default settings
                database.execSQL(
                    "INSERT OR IGNORE INTO app_settings (id) VALUES (1)"
                )
            }
        }
    }
    
    private fun initializeDefaults() {
        // Run in a background thread since Room doesn't allow DB access on main thread
        Thread {
            // Ensure default settings exist
            if (appSettingsDao().getSettings() == null) {
                appSettingsDao().insert(
                    AppSettings(
                        id = 1,
                        theme = "system",
                        language = "en",
                        autoStart = false,
                        autoConnect = false,
                        preferredServerId = null,
                        autoSwitchToBestServer = false,
                        minPingThreshold = 300,
                        pingFrequencyMinutes = 30,
                        updateFrequencyHours = 24,
                        autoUpdateEnabled = true,
                        autoUpdateXrayCore = true
                    )
                )
            }
        }.start()
    }
}
