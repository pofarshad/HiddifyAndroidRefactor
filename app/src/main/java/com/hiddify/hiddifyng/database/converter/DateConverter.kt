package com.hiddify.hiddifyng.database.converter

import androidx.room.TypeConverter
import java.util.Date

/**
 * Type converter for Room database to handle Date objects
 * Automatically converts between Date objects and Long (timestamp) values
 */
class DateConverter {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }
    
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}