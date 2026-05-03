package com.example.liftrix.data.local.converter

import androidx.room.TypeConverter
import javax.inject.Inject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Room type converters for date and time types
 */
class DateTimeConverters @Inject constructor() {
    
    @TypeConverter
    fun fromInstant(instant: Instant?): String? {
        return instant?.toString()
    }
    
    @TypeConverter
    fun toInstant(instantString: String?): Instant? {
        return instantString?.let { timeString ->
            try {
                // Try parsing as ISO format first
                Instant.parse(timeString)
            } catch (e: Exception) {
                try {
                    // Fallback: Try parsing as epoch milliseconds
                    val epochMillis = timeString.toLong()
                    Instant.ofEpochMilli(epochMillis)
                } catch (e2: Exception) {
                    // If both fail, log error and return null
                    timber.log.Timber.e("🔥 DATETIME-CONVERTER: Failed to parse timestamp '$timeString': ${e.message}, fallback failed: ${e2.message}")
                    null
                }
            }
        }
    }
    
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? {
        return date?.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }
    
    @TypeConverter
    fun toLocalDate(dateString: String?): LocalDate? {
        return dateString?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }
    }

    @TypeConverter
    fun fromLocalDateTime(dateTime: LocalDateTime?): String? {
        return dateTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    @TypeConverter
    fun toLocalDateTime(dateTimeString: String?): LocalDateTime? {
        return dateTimeString?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
    }
} 