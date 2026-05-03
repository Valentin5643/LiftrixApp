package com.example.liftrix.domain.service.parser

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject

/**
 * Implementation of FormatDetector service to detect the format of imported workout data files.
 */
class FormatDetectorImpl @Inject constructor() : FormatDetector {
    
    /**
     * Detects the format of the input stream content.
     * 
     * @param inputStream The input stream to analyze
     * @return The detected format (JSON, CSV, TCX, GPX, FIT) or error
     */
    override suspend fun detectFormat(inputStream: InputStream): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "FORMAT_DETECTION_FAILED",
                errorMessage = "Failed to detect file format",
                analyticsContext = mapOf(
                    "operation" to "DETECT_FORMAT",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        // Mark the stream for reset if supported
        if (inputStream.markSupported()) {
            inputStream.mark(1024 * 10) // Mark first 10KB
        }
        
        val reader = BufferedReader(InputStreamReader(inputStream))
        val firstLines = mutableListOf<String>()
        
        // Read first few lines to detect format
        repeat(10) {
            val line = reader.readLine() ?: return@repeat
            firstLines.add(line.trim())
        }
        
        // Reset stream if possible
        if (inputStream.markSupported()) {
            inputStream.reset()
        }
        
        val content = firstLines.joinToString("\n")
        
        when {
            // JSON detection
            content.startsWith("{") || content.startsWith("[") -> "JSON"
            
            // XML-based formats (TCX)
            content.contains("<?xml") && content.contains("TrainingCenterDatabase") -> "TCX"
            
            // GPX detection
            content.contains("<?xml") && content.contains("<gpx") -> "GPX"
            
            // CSV detection - look for comma or tab-separated values
            firstLines.any { line ->
                val hasCommas = line.split(",").size > 3
                val hasTabs = line.split("\t").size > 3
                (hasCommas || hasTabs) && 
                (line.lowercase().contains("workout") || line.lowercase().contains("exercise") || 
                 line.lowercase().contains("title") || line.lowercase().contains("date") || 
                 line.lowercase().contains("reps") || line.lowercase().contains("weight") ||
                 line.lowercase().contains("exercise_title") || line.lowercase().contains("start_time"))
            } -> "CSV"
            
            // FIT file detection (binary format, usually starts with specific bytes)
            firstLines.firstOrNull()?.startsWith(".FIT") == true -> "FIT"
            
            else -> throw IllegalArgumentException("Unable to detect file format. Supported formats: JSON, CSV, TCX, GPX, FIT")
        }
    }
}