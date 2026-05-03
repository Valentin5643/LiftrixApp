package com.example.liftrix.domain.service.parser

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.common.liftrixSuccess
import com.example.liftrix.domain.model.error.LiftrixError
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutParserFactory @Inject constructor(
    private val jsonParser: JsonParser,
    private val csvParser: CsvParser,
    private val tcxParser: TcxParser,
    private val gpxParser: GpxParser,
    private val fitParser: FitParser
) {
    
    private val parsers = mapOf(
        "JSON" to jsonParser,
        "CSV" to csvParser,
        "TCX" to tcxParser,
        "GPX" to gpxParser,
        "FIT" to fitParser
    )
    
    fun getParser(format: String): LiftrixResult<WorkoutParser> {
        val parser = parsers[format.uppercase()]
        return if (parser != null) {
            liftrixSuccess(parser)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "format",
                    violations = listOf("Unsupported format: $format"),
                    analyticsContext = mapOf(
                        "requested_format" to format,
                        "supported_formats" to getSupportedFormats().joinToString(",")
                    )
                )
            )
        }
    }
    
    fun getSupportedFormats(): List<String> = parsers.keys.toList()
    
    fun getSupportedExtensions(): Map<String, String> {
        return parsers.flatMap { (format, parser) ->
            parser.getSupportedFileExtensions().map { extension ->
                extension to format
            }
        }.toMap()
    }
    
    fun getFormatFromExtension(extension: String): String? {
        return getSupportedExtensions()[extension.lowercase()]
    }
}