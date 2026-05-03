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
import kotlin.math.*

class GpxParser @Inject constructor() : WorkoutParser {
    
    override suspend fun parse(inputStream: InputStream): LiftrixResult<List<ParsedWorkout>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "GPX_PARSE_FAILED",
                errorMessage = "Failed to parse GPX workout data",
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
                    if (parser.name == "trk") {
                        workouts.add(parseTrack(parser))
                    }
                }
            }
            eventType = parser.next()
        }
        
        // If no tracks found, try to parse as route
        if (workouts.isEmpty()) {
            parser.setInput(inputStream, null)
            eventType = parser.eventType
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "rte") {
                            workouts.add(parseRoute(parser))
                        }
                    }
                }
                eventType = parser.next()
            }
        }
        
        workouts
    }
    
    private fun parseTrack(parser: XmlPullParser): ParsedWorkout {
        var trackName = "GPX Track"
        var startTime: LocalDateTime? = null
        var endTime: LocalDateTime? = null
        val trackPoints = mutableListOf<TrackPoint>()
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when {
                eventType == XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "name" -> {
                            trackName = readText(parser)
                        }
                        "trkpt" -> {
                            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            val point = parseTrackPoint(parser, lat, lon)
                            trackPoints.add(point)
                            
                            if (startTime == null) startTime = point.time
                            point.time?.let { endTime = it }
                        }
                    }
                }
                eventType == XmlPullParser.END_TAG && parser.name == "trk" -> {
                    break
                }
            }
            eventType = parser.next()
        }
        
        val totalDistance = calculateTotalDistance(trackPoints)
        val duration = if (startTime != null && endTime != null) {
            java.time.Duration.between(startTime, endTime).seconds
        } else null
        
        val exercise = ParsedExercise(
            name = "GPS Activity",
            category = "Cardio",
            sets = listOf(
                ParsedSet(
                    distance = totalDistance.takeIf { it > 0 },
                    duration = duration,
                    completed = true,
                    notes = "Total points: ${trackPoints.size}"
                )
            )
        )
        
        return ParsedWorkout(
            name = trackName,
            date = startTime ?: LocalDateTime.now(),
            duration = duration,
            exercises = listOf(exercise),
            sourceApp = "GPS/GPX",
            originalFormat = "GPX",
            metadata = mapOf(
                "total_points" to trackPoints.size.toString(),
                "total_distance" to totalDistance.toString()
            )
        )
    }
    
    private fun parseRoute(parser: XmlPullParser): ParsedWorkout {
        var routeName = "GPX Route"
        val routePoints = mutableListOf<TrackPoint>()
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when {
                eventType == XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "name" -> {
                            routeName = readText(parser)
                        }
                        "rtept" -> {
                            val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            routePoints.add(TrackPoint(lat, lon, null, null))
                        }
                    }
                }
                eventType == XmlPullParser.END_TAG && parser.name == "rte" -> {
                    break
                }
            }
            eventType = parser.next()
        }
        
        val totalDistance = calculateTotalDistance(routePoints)
        
        val exercise = ParsedExercise(
            name = "Planned Route",
            category = "Cardio",
            sets = listOf(
                ParsedSet(
                    distance = totalDistance.takeIf { it > 0 },
                    completed = false, // Routes are planned, not completed
                    notes = "Planned route with ${routePoints.size} waypoints"
                )
            )
        )
        
        return ParsedWorkout(
            name = routeName,
            date = LocalDateTime.now(),
            exercises = listOf(exercise),
            sourceApp = "GPS/GPX",
            originalFormat = "GPX",
            metadata = mapOf(
                "route_points" to routePoints.size.toString(),
                "planned_distance" to totalDistance.toString()
            )
        )
    }
    
    private fun parseTrackPoint(parser: XmlPullParser, lat: Double, lon: Double): TrackPoint {
        var elevation: Double? = null
        var time: LocalDateTime? = null
        
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when {
                eventType == XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "ele" -> {
                            elevation = readText(parser).toDoubleOrNull()
                        }
                        "time" -> {
                            time = parseDateTime(readText(parser))
                        }
                    }
                }
                eventType == XmlPullParser.END_TAG && parser.name == "trkpt" -> {
                    break
                }
            }
            eventType = parser.next()
        }
        
        return TrackPoint(lat, lon, elevation, time)
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
    
    private fun calculateTotalDistance(points: List<TrackPoint>): Double {
        if (points.size < 2) return 0.0
        
        var totalDistance = 0.0
        for (i in 1 until points.size) {
            totalDistance += calculateDistance(points[i-1], points[i])
        }
        
        return totalDistance
    }
    
    private fun calculateDistance(point1: TrackPoint, point2: TrackPoint): Double {
        val earthRadius = 6371000.0 // Earth radius in meters
        
        val lat1Rad = Math.toRadians(point1.latitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val deltaLatRad = Math.toRadians(point2.latitude - point1.latitude)
        val deltaLonRad = Math.toRadians(point2.longitude - point1.longitude)
        
        val a = sin(deltaLatRad / 2).pow(2) + 
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLonRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return earthRadius * c
    }
    
    override fun getSupportedFileExtensions(): List<String> = listOf("gpx")
    
    override fun getFormatName(): String = "GPX"
    
    private data class TrackPoint(
        val latitude: Double,
        val longitude: Double,
        val elevation: Double?,
        val time: LocalDateTime?
    )
}