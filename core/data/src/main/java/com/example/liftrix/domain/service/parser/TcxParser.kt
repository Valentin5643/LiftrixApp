package com.example.liftrix.domain.service.parser

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.portability.ParsedWorkout
import com.example.liftrix.domain.model.portability.ParsedExercise
import com.example.liftrix.domain.model.portability.ParsedSet
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.inject.Inject

class TcxParser @Inject constructor() : WorkoutParser {
    
    override suspend fun parse(inputStream: InputStream): LiftrixResult<List<ParsedWorkout>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "TCX_PARSE_FAILED",
                errorMessage = "Failed to parse TCX workout data",
                analyticsContext = mapOf("error" to throwable.message.orEmpty())
            )
        }
    ) {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(inputStream, null)
        
        val workouts = mutableListOf<ParsedWorkout>()
        var eventType = parser.eventType
        
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "Activity") {
                        workouts.add(parseActivity(parser))
                    }
                }
            }
            eventType = parser.next()
        }
        
        workouts
    }
    
    private fun parseActivity(parser: XmlPullParser): ParsedWorkout {
        var workoutName = "TCX Workout"
        var sport = ""
        var id = ""
        var startTime: LocalDateTime? = null
        var totalDistance = 0.0
        var totalDuration = 0L
        var maxHeartRate = 0
        var avgHeartRate = 0
        var calories = 0
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when {
                eventType == XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "Activity" -> {
                            sport = parser.getAttributeValue(null, "Sport") ?: ""
                        }
                        "Id" -> {
                            id = readText(parser)
                            startTime = parseDateTime(id)
                        }
                        "TotalTimeSeconds" -> {
                            totalDuration = readText(parser).toDoubleOrNull()?.toLong() ?: 0L
                        }
                        "DistanceMeters" -> {
                            totalDistance = readText(parser).toDoubleOrNull() ?: 0.0
                        }
                        "MaximumHeartRateBpm" -> {
                            // Skip to Value tag
                            while (parser.next() != XmlPullParser.END_DOCUMENT && parser.name != "Value") {
                                // Find Value tag
                            }
                            maxHeartRate = readText(parser).toIntOrNull() ?: 0
                        }
                        "AverageHeartRateBpm" -> {
                            while (parser.next() != XmlPullParser.END_DOCUMENT && parser.name != "Value") {
                                // Find Value tag
                            }
                            avgHeartRate = readText(parser).toIntOrNull() ?: 0
                        }
                        "Calories" -> {
                            calories = readText(parser).toIntOrNull() ?: 0
                        }
                    }
                }
                eventType == XmlPullParser.END_TAG && parser.name == "Activity" -> {
                    break
                }
            }
            eventType = parser.next()
        }
        
        workoutName = when (sport.lowercase()) {
            "running" -> "Running Workout"
            "biking", "cycling" -> "Cycling Workout"
            "swimming" -> "Swimming Workout"
            "walking" -> "Walking Workout"
            else -> "$sport Workout"
        }
        
        // Create a single cardio exercise from the activity data
        val exercise = ParsedExercise(
            name = sport.takeIf { it.isNotBlank() } ?: "Cardio",
            category = "Cardio",
            sets = listOf(
                ParsedSet(
                    distance = if (totalDistance > 0) totalDistance else null,
                    duration = if (totalDuration > 0) totalDuration else null,
                    completed = true,
                    notes = buildString {
                        if (calories > 0) append("Calories: $calories ")
                        if (avgHeartRate > 0) append("Avg HR: $avgHeartRate ")
                        if (maxHeartRate > 0) append("Max HR: $maxHeartRate")
                    }.takeIf { it.isNotBlank() }
                )
            )
        )
        
        return ParsedWorkout(
            id = id.takeIf { it.isNotBlank() },
            name = workoutName,
            date = startTime ?: LocalDateTime.now(),
            duration = totalDuration.takeIf { it > 0 },
            exercises = listOf(exercise),
            sourceApp = "Garmin/TCX",
            originalFormat = "TCX",
            metadata = mapOf(
                "sport" to sport,
                "total_distance" to totalDistance.toString(),
                "calories" to calories.toString(),
                "avg_heart_rate" to avgHeartRate.toString(),
                "max_heart_rate" to maxHeartRate.toString()
            )
        )
    }
    
    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text
            parser.nextTag()
        }
        return result
    }
    
    private fun parseDateTime(dateString: String): LocalDateTime? {
        if (dateString.isBlank()) return null
        
        val formatters = listOf(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        )
        
        for (formatter in formatters) {
            try {
                return LocalDateTime.parse(dateString, formatter)
            } catch (e: DateTimeParseException) {
                // Try next formatter
            }
        }
        
        return null
    }
    
    override fun getSupportedFileExtensions(): List<String> = listOf("tcx")
    
    override fun getFormatName(): String = "TCX"
}