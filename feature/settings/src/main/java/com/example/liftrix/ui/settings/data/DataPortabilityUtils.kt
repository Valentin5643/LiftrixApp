package com.example.liftrix.ui.settings.data

/**
 * Utility functions for data portability features.
 */

/**
 * Formats file size in bytes to human-readable format.
 * 
 * @param bytes File size in bytes
 * @return Formatted string with appropriate unit (B, KB, MB, GB)
 */
fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0
    
    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }
    
    return "%.1f %s".format(size, units[unitIndex])
}

/**
 * Formats duration in milliseconds to human-readable format.
 * 
 * @param milliseconds Duration in milliseconds
 * @return Formatted duration string (e.g., "1h 30m", "45m 15s", "30s")
 */
fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m ${seconds % 60}s"
        else -> "${seconds}s"
    }
}

/**
 * Validates export request parameters.
 * 
 * @param dataTypes Selected data types
 * @param dateRange Optional date range
 * @return List of validation errors, empty if valid
 */
fun validateExportRequest(
    dataTypes: Set<com.example.liftrix.domain.usecase.export.DataType>,
    dateRange: com.example.liftrix.domain.usecase.export.DateRange?
): List<String> {
    val errors = mutableListOf<String>()
    
    if (dataTypes.isEmpty()) {
        errors.add("At least one data type must be selected")
    }
    
    dateRange?.let { range ->
        if (range.start.isAfter(range.end)) {
            errors.add("Start date must be before end date")
        }
        if (range.start.isBefore(java.time.LocalDate.now().minusYears(10))) {
            errors.add("Date range extends too far into the past")
        }
        if (range.end.isAfter(java.time.LocalDate.now())) {
            errors.add("End date cannot be in the future")
        }
    }
    
    return errors
}