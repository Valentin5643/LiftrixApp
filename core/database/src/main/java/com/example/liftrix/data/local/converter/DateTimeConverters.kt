package com.example.liftrix.data.local.converter

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

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
                Instant.parse(timeString)
            } catch (e: Exception) {
                try {
                    val epochMillis = timeString.toLong()
                    Instant.ofEpochMilli(epochMillis)
                } catch (e2: Exception) {
                    try {
                        // SQLite datetime('now') stores UTC as "yyyy-MM-dd HH:mm:ss".
                        LocalDateTime.parse(timeString, SQLITE_DATE_TIME_FORMATTER).toInstant(ZoneOffset.UTC)
                    } catch (e3: Exception) {
                        timber.log.Timber.e("DATETIME-CONVERTER: Failed to parse timestamp '$timeString': ${e.message}, epoch fallback failed: ${e2.message}, SQLite fallback failed: ${e3.message}")
                        null
                    }
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
        return dateTimeString?.let { value ->
            try {
                LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } catch (e: Exception) {
                LocalDateTime.parse(value, SQLITE_DATE_TIME_FORMATTER)
            }
        }
    }

    private companion object {
        val SQLITE_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
