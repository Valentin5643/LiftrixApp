package com.example.liftrix.domain.usecase.data_import

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.portability.ImportValidationError
import com.example.liftrix.domain.model.portability.ParsedWorkout
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

interface DataImportUseCase {
    suspend fun validateFile(uri: Any, inputStream: InputStream): LiftrixResult<ImportValidation>
    suspend fun import(
        userId: String,
        uri: Any,
        inputStream: InputStream,
        options: ImportOptions
    ): LiftrixResult<ImportResult>
    fun getImportProgress(importId: String): Flow<ImportProgress>
}

data class ImportValidation(
    val format: String,
    val isValid: Boolean,
    val totalWorkouts: Int,
    val totalExercises: Int,
    val totalSets: Int,
    val errors: List<ImportValidationError>,
    val warnings: List<ImportValidationError>,
    val unmappedExercises: List<String>,
    val preview: List<ParsedWorkout>
)

data class ImportOptions(
    val detectedFormat: String?,
    val sourceApp: String?,
    val conflictStrategy: ConflictStrategy,
    val allowValidationErrors: Boolean = false
)

data class ImportResult(
    val importId: String,
    val importedCount: Int,
    val skippedCount: Int,
    val errors: List<ImportValidationError>,
    val warnings: List<ImportValidationError>,
    val unmappedExercises: List<String>
)

data class ImportProgress(
    val importId: String,
    val progressPercentage: Int,
    val statusMessage: String
)

data class ImportValidationResult(
    val isValid: Boolean,
    val errors: List<ImportValidationError>,
    val warnings: List<ImportValidationError>,
    val totalWorkouts: Int,
    val validWorkouts: Int,
    val totalExercises: Int,
    val validExercises: Int,
    val totalSets: Int,
    val validSets: Int,
    val unmappedExercises: List<String>
)

enum class ConflictStrategy {
    SKIP,
    REPLACE,
    MERGE
}
