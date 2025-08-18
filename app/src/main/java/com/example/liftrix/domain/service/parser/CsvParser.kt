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
        
        // Group data by workout (assuming date or workout_id grouping)
        val workoutGroups = mutableMapOf<String, MutableList<Map<String, String>>>()
        
        for (line in dataLines) {
            if (line.isBlank()) continue
            
            val values = parseCsvLine(line)
            if (values.size != headers.size) continue
            
            val rowData = headers.zip(values).toMap()
            
            // Use date + workout_name as grouping key, fallback to date
            val groupKey = when {
                rowData.containsKey("workout_id") -> rowData["workout_id"]!!
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
        
        // Group exercises by name
        val exerciseGroups = rows.groupBy { 
            it["exercise"] ?: it["exercise_name"] ?: it["movement"] ?: "Unknown Exercise"
        }
        
        val exercises = exerciseGroups.map { (exerciseName, exerciseRows) ->
            parseExerciseFromCsvRows(exerciseName, exerciseRows)
        }
        
        return ParsedWorkout(
            id = firstRow["workout_id"],
            name = firstRow["workout_name"] ?: firstRow["session_name"] ?: "Imported Workout",
            date = parseDateTime(firstRow["date"] ?: firstRow["timestamp"] ?: ""),
            duration = firstRow["duration"]?.toLongOrNull(),
            exercises = exercises,
            notes = firstRow["notes"] ?: firstRow["comments"],
            sourceApp = firstRow["source"] ?: firstRow["app"],
            originalFormat = "CSV"
        )
    }
    
    private fun parseExerciseFromCsvRows(exerciseName: String, rows: List<Map<String, String>>): ParsedExercise {
        val sets = rows.mapIndexed { index, row ->
            ParsedSet(
                reps = row["reps"]?.toIntOrNull() ?: row["repetitions"]?.toIntOrNull(),
                weight = row["weight"]?.toDoubleOrNull() ?: row["load"]?.toDoubleOrNull(),
                distance = row["distance"]?.toDoubleOrNull(),
                duration = row["set_duration"]?.toLongOrNull() ?: row["time"]?.toLongOrNull(),
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
                char == ',' && !inQuotes -> {
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
                return LocalDateTime.parse(dateString, formatter)
            } catch (e: DateTimeParseException) {
                // Try next formatter
            }
        }
        
        return LocalDateTime.now()
    }
    
    override fun getSupportedFileExtensions(): List<String> = listOf("csv")
    
    override fun getFormatName(): String = "CSV"
}