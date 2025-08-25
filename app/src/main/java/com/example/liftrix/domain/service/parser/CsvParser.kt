package com.example.liftrix.domain.service.parser

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.portability.ParsedWorkout
import com.example.liftrix.domain.model.portability.ParsedExercise
import com.example.liftrix.domain.model.portability.ParsedSet
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

class CsvParser @Inject constructor() : WorkoutParser {
    
    override suspend fun parse(inputStream: InputStream): LiftrixResult<List<ParsedWorkout>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CSV_PARSE_FAILED",
                errorMessage = "Failed to parse CSV workout data",
                analyticsContext = mapOf("error" to throwable.message.orEmpty())
            )
        }
    ) {
        val lines = inputStream.bufferedReader().use { it.readLines() }
        if (lines.isEmpty()) {
            return@liftrixCatching emptyList()
        }
        
        val headers = parseCsvLine(lines.first()).map { it.lowercase().trim() }
        val dataLines = lines.drop(1)
        
        // Group data by workout (using title + start_time as unique identifier)
        val workoutGroups = mutableMapOf<String, MutableList<Map<String, String>>>()
        
        for (line in dataLines) {
            if (line.isBlank()) continue
            
            val values = parseCsvLine(line)
            if (values.size != headers.size) continue
            
            val rowData = headers.zip(values).toMap()
            
            // Use workout title + start time as grouping key to handle multiple workouts with same name
            val groupKey = when {
                rowData.containsKey("workout_id") -> rowData["workout_id"]!!
                rowData.containsKey("title") && rowData.containsKey("start_time") -> 
                    "${rowData["title"]}_${rowData["start_time"]}"
                rowData.containsKey("title") -> rowData["title"]!!
                rowData.containsKey("date") && rowData.containsKey("workout_name") -> 
                    "${rowData["date"]}_${rowData["workout_name"]}"
                rowData.containsKey("date") -> rowData["date"]!!
                else -> "unknown_${workoutGroups.size}"
            }
            
            workoutGroups.getOrPut(groupKey) { mutableListOf() }.add(rowData)
        }
        
        // Convert groups to workouts
        workoutGroups.values.map { rows ->
            parseWorkoutFromCsvRows(rows, headers)
        }
    }
    
    private fun parseWorkoutFromCsvRows(rows: List<Map<String, String>>, headers: List<String>): ParsedWorkout {
        val firstRow = rows.first()
        
        // Group exercises by name - support multiple column name variations
        val exerciseGroups = rows.groupBy { 
            it["exercise_title"] ?: it["exercise"] ?: it["exercise_name"] ?: 
            it["movement"] ?: "Unknown Exercise"
        }
        
        val exercises = exerciseGroups.map { (exerciseName, exerciseRows) ->
            parseExerciseFromCsvRows(exerciseName, exerciseRows)
        }
        
        // Parse date/time from various possible formats
        val dateTime = when {
            firstRow.containsKey("start_time") -> parseDateTime(firstRow["start_time"] ?: "")
            firstRow.containsKey("date") -> parseDateTime(firstRow["date"] ?: "")
            firstRow.containsKey("timestamp") -> parseDateTime(firstRow["timestamp"] ?: "")
            else -> LocalDateTime.now()
        }
        
        // Calculate duration if we have start and end times
        val duration = if (firstRow.containsKey("start_time") && firstRow.containsKey("end_time")) {
            try {
                val startTime = parseDateTime(firstRow["start_time"] ?: "")
                val endTime = parseDateTime(firstRow["end_time"] ?: "")
                java.time.Duration.between(startTime, endTime).seconds
            } catch (e: Exception) {
                firstRow["duration"]?.toLongOrNull()
            }
        } else {
            firstRow["duration"]?.toLongOrNull()
        }
        
        return ParsedWorkout(
            id = firstRow["workout_id"],
            name = firstRow["title"] ?: firstRow["workout_name"] ?: 
                   firstRow["session_name"] ?: "Imported Workout",
            date = dateTime,
            duration = duration,
            exercises = exercises,
            notes = firstRow["description"] ?: firstRow["notes"] ?: firstRow["comments"],
            sourceApp = firstRow["source"] ?: firstRow["app"],
            originalFormat = "CSV"
        )
    }
    
    private fun parseExerciseFromCsvRows(exerciseName: String, rows: List<Map<String, String>>): ParsedExercise {
        // Sort by set_index if available to maintain proper ordering
        val sortedRows = if (rows.first().containsKey("set_index")) {
            rows.sortedBy { it["set_index"]?.toIntOrNull() ?: 0 }
        } else {
            rows
        }
        
        val sets = sortedRows.mapIndexed { index, row ->
            // Handle weight in kg or generic weight field
            val weight = row["weight_kg"]?.toDoubleOrNull() 
                ?: row["weight"]?.toDoubleOrNull() 
                ?: row["load"]?.toDoubleOrNull()
            
            // Handle distance in km or generic distance
            val distance = row["distance_km"]?.toDoubleOrNull()?.times(1000) // Convert km to meters
                ?: row["distance"]?.toDoubleOrNull()
            
            ParsedSet(
                reps = row["reps"]?.toIntOrNull() ?: row["repetitions"]?.toIntOrNull(),
                weight = weight,
                distance = distance,
                duration = row["duration_seconds"]?.toLongOrNull() 
                    ?: row["set_duration"]?.toLongOrNull() 
                    ?: row["time"]?.toLongOrNull(),
                completed = row["completed"]?.toBoolean() ?: true,
                notes = row["set_notes"] ?: row["set_comment"],
                restAfter = row["rest"]?.toLongOrNull() ?: row["rest_time"]?.toLongOrNull()
            )
        }
        
        val firstRow = rows.first()
        return ParsedExercise(
            name = exerciseName,
            category = firstRow["category"] ?: firstRow["muscle_group"],
            sets = sets,
            notes = firstRow["exercise_notes"],
            restTime = firstRow["default_rest"]?.toLongOrNull(),
            equipment = firstRow["equipment"]
        )
    }
    
    private fun parseCsvLine(line: String): List<String> {
        // First check if it's tab-separated
        val tabCount = line.count { it == '\t' }
        val commaCount = line.count { it == ',' }
        
        // Use tab as separator if there are more tabs than commas
        val separator = if (tabCount > commaCount) '\t' else ','
        
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        
        while (i < line.length) {
            val char = line[i]
            when {
                char == '"' && !inQuotes -> inQuotes = true
                char == '"' && inQuotes -> {
                    // Check for escaped quote
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++ // Skip next quote
                    } else {
                        inQuotes = false
                    }
                }
                char == separator && !inQuotes -> {
                    result.add(current.toString().trim())
                    current = StringBuilder()
                }
                else -> current.append(char)
            }
            i++
        }
        
        result.add(current.toString().trim())
        return result
    }
    
    private fun parseDateTime(dateString: String): LocalDateTime {
        if (dateString.isBlank()) {
            return LocalDateTime.now()
        }
        
        val formatters = listOf(
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"), // "23 Aug 2025, 19:47"
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"), // "23 Aug 2025, 19:47"
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE
        )
        
        for (formatter in formatters) {
            try {
                // Try parsing as LocalDateTime first
                return LocalDateTime.parse(dateString, formatter)
            } catch (e: DateTimeParseException) {
                try {
                    // If that fails, try parsing as LocalDate and convert
                    val date = java.time.LocalDate.parse(dateString, formatter)
                    return date.atStartOfDay()
                } catch (e2: DateTimeParseException) {
                    // Continue to next formatter
                }
            }
        }
        
        return LocalDateTime.now()
    }
    
    override fun getSupportedFileExtensions(): List<String> = listOf("csv")
    
    override fun getFormatName(): String = "CSV"
}