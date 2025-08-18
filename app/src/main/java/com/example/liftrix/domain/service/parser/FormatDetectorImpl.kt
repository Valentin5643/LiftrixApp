package com.example.liftrix.domain.service.parser

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject

class FormatDetectorImpl @Inject constructor() : FormatDetector {
    
    override suspend fun detectFormat(inputStream: InputStream): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "FORMAT_DETECTION_FAILED",
                errorMessage = "Failed to detect file format",
                analyticsContext = mapOf("error" to throwable.message.orEmpty())
            )
        }
    ) {
        val bufferedReader = BufferedReader(InputStreamReader(inputStream))
        val firstLines = mutableListOf<String>()
        
        // Read first 10 lines to analyze content
        repeat(10) {
            val line = bufferedReader.readLine() ?: return@repeat
            firstLines.add(line.trim())
        }
        
        val content = firstLines.joinToString("\n")
        
        when {
            // JSON detection
            content.trimStart().startsWith("{") || content.trimStart().startsWith("[") -> "JSON"
            
            // CSV detection - look for common patterns
            firstLines.any { it.contains(",") } && 
            (firstLines.firstOrNull()?.lowercase()?.contains("date") == true ||
             firstLines.firstOrNull()?.lowercase()?.contains("exercise") == true ||
             firstLines.firstOrNull()?.lowercase()?.contains("workout") == true) -> "CSV"
            
            // TCX detection - XML with TCX specific elements
            content.contains("<TrainingCenterDatabase") ||
            content.contains("<tcx:") ||
            content.contains("xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase") -> "TCX"
            
            // GPX detection - XML with GPX specific elements
            content.contains("<gpx") ||
            content.contains("xmlns=\"http://www.topografix.com/GPX") -> "GPX"
            
            // FIT files are binary, check for FIT header
            content.startsWith(".FIT") || firstLines.any { it.contains("FIT") && it.length < 20 } -> "FIT"
            
            else -> throw IllegalArgumentException("Unsupported file format")
        }
    }
    
    override suspend fun detectFormat(filename: String, mimeType: String?): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "FORMAT_DETECTION_FILENAME_FAILED",
                errorMessage = "Failed to detect format from filename",
                analyticsContext = mapOf(
                    "filename" to filename,
                    "mimeType" to mimeType.orEmpty(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        val extension = filename.substringAfterLast(".", "").lowercase()
        
        when (extension) {
            "json" -> "JSON"
            "csv" -> "CSV"
            "tcx" -> "TCX"
            "gpx" -> "GPX"
            "fit" -> "FIT"
            else -> {
                // Fall back to MIME type
                when (mimeType) {
                    "application/json" -> "JSON"
                    "text/csv" -> "CSV"
                    "application/xml", "text/xml" -> {
                        // Default to TCX for XML files in fitness context
                        "TCX"
                    }
                    else -> throw IllegalArgumentException("Cannot determine format from filename '$filename' and MIME type '$mimeType'")
                }
            }
        }
    }
}