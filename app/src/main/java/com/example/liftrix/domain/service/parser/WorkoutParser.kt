package com.example.liftrix.domain.service.parser

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.portability.ParsedWorkout
import java.io.InputStream

interface WorkoutParser {
    suspend fun parse(inputStream: InputStream): LiftrixResult<List<ParsedWorkout>>
    fun getSupportedFileExtensions(): List<String>
    fun getFormatName(): String
}

interface FormatDetector {
    suspend fun detectFormat(inputStream: InputStream): LiftrixResult<String>
    suspend fun detectFormat(filename: String, mimeType: String?): LiftrixResult<String>
}