package com.example.liftrix.domain.service.parser

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.portability.ParsedWorkout
import com.example.liftrix.domain.model.portability.ParsedExercise
import com.example.liftrix.domain.model.portability.ParsedSet
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

class JsonParser @Inject constructor() : WorkoutParser {
    
    override suspend fun parse(inputStream: InputStream): LiftrixResult<List<ParsedWorkout>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "JSON_PARSE_FAILED",
                errorMessage = "Failed to parse JSON workout data",
                analyticsContext = mapOf("error" to throwable.message.orEmpty())
            )
        }
    ) {
        val jsonText = inputStream.bufferedReader().use { it.readText() }
        val workouts = mutableListOf<ParsedWorkout>()
        
        // Handle both single workout object and array of workouts
        if (jsonText.trimStart().startsWith("[")) {
            val jsonArray = JSONArray(jsonText)
            for (i in 0 until jsonArray.length()) {
                val workoutJson = jsonArray.getJSONObject(i)
                workouts.add(parseWorkoutFromJson(workoutJson))
            }
        } else {
            val jsonObject = JSONObject(jsonText)
            // Check if it's a workout or contains workouts array
            when {
                jsonObject.has("workouts") -> {
                    val workoutsArray = jsonObject.getJSONArray("workouts")
                    for (i in 0 until workoutsArray.length()) {
                        val workoutJson = workoutsArray.getJSONObject(i)
                        workouts.add(parseWorkoutFromJson(workoutJson))
                    }
                }
                jsonObject.has("date") || jsonObject.has("exercises") -> {
                    // Single workout object
                    workouts.add(parseWorkoutFromJson(jsonObject))
                }
                else -> {
                    throw IllegalArgumentException("JSON does not contain recognizable workout data")
                }
            }
        }
        
        workouts
    }
    
    private fun parseWorkoutFromJson(json: JSONObject): ParsedWorkout {
        val exercises = mutableListOf<ParsedExercise>()
        
        if (json.has("exercises")) {
            val exercisesArray = json.getJSONArray("exercises")
            for (i in 0 until exercisesArray.length()) {
                val exerciseJson = exercisesArray.getJSONObject(i)
                exercises.add(parseExerciseFromJson(exerciseJson))
            }
        }
        
        return ParsedWorkout(
            id = json.optString("id").takeIf { it.isNotBlank() },
            name = json.optString("name", "Imported Workout"),
            date = parseDateTime(json.optString("date", json.optString("timestamp", ""))),
            duration = json.optLong("duration").takeIf { it > 0 },
            exercises = exercises,
            notes = json.optString("notes").takeIf { it.isNotBlank() },
            sourceApp = json.optString("source_app", json.optString("app")),
            originalFormat = "JSON",
            metadata = parseMetadata(json)
        )
    }
    
    private fun parseExerciseFromJson(json: JSONObject): ParsedExercise {
        val sets = mutableListOf<ParsedSet>()
        
        if (json.has("sets")) {
            val setsArray = json.getJSONArray("sets")
            for (i in 0 until setsArray.length()) {
                val setJson = setsArray.getJSONObject(i)
                sets.add(parseSetFromJson(setJson))
            }
        }
        
        return ParsedExercise(
            name = json.getString("name"),
            category = json.optString("category").takeIf { it.isNotBlank() },
            sets = sets,
            notes = json.optString("notes").takeIf { it.isNotBlank() },
            restTime = json.optLong("rest_time").takeIf { it > 0 },
            equipment = json.optString("equipment").takeIf { it.isNotBlank() }
        )
    }
    
    private fun parseSetFromJson(json: JSONObject): ParsedSet {
        return ParsedSet(
            reps = json.optInt("reps").takeIf { it > 0 },
            weight = json.optDouble("weight").takeIf { !it.isNaN() && it > 0 },
            distance = json.optDouble("distance").takeIf { !it.isNaN() && it > 0 },
            duration = json.optLong("duration").takeIf { it > 0 },
            completed = json.optBoolean("completed", true),
            notes = json.optString("notes").takeIf { it.isNotBlank() },
            restAfter = json.optLong("rest_after").takeIf { it > 0 }
        )
    }
    
    private fun parseDateTime(dateString: String): LocalDateTime {
        if (dateString.isBlank()) {
            return LocalDateTime.now()
        }
        
        val formatters = listOf(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy")
        )
        
        for (formatter in formatters) {
            try {
                return LocalDateTime.parse(dateString, formatter)
            } catch (e: DateTimeParseException) {
                // Try next formatter
            }
        }
        
        // If all parsing fails, return current time
        return LocalDateTime.now()
    }
    
    private fun parseMetadata(json: JSONObject): Map<String, String> {
        val metadata = mutableMapOf<String, String>()
        
        // Extract common metadata fields
        listOf("version", "export_timestamp", "total_workouts", "user_id", "platform").forEach { field ->
            json.optString(field).takeIf { it.isNotBlank() }?.let { 
                metadata[field] = it 
            }
        }
        
        return metadata
    }
    
    override fun getSupportedFileExtensions(): List<String> = listOf("json")
    
    override fun getFormatName(): String = "JSON"
}