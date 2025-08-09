package com.example.liftrix.core.extensions

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Date

/**
 * Extension functions for Date conversion and manipulation
 */

/**
 * Converts java.util.Date to kotlinx.datetime.LocalDate using system timezone
 */
fun Date.toLocalDate(): LocalDate {
    return Instant.fromEpochMilliseconds(this.time)
        .toLocalDateTime(TimeZone.currentSystemDefault())
        .date
}

/**
 * Converts java.util.Date to kotlinx.datetime.LocalDate using UTC timezone
 */
fun Date.toLocalDateUTC(): LocalDate {
    return Instant.fromEpochMilliseconds(this.time)
        .toLocalDateTime(TimeZone.UTC)
        .date
}