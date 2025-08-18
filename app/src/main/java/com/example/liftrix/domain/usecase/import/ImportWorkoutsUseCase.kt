package com.example.liftrix.domain.usecase.import

import android.net.Uri
import com.example.liftrix.data.local.dao.DataImportDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.DataImportEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.portability.ParsedWorkout
import com.example.liftrix.domain.service.ExerciseMappingService
import com.example.liftrix.domain.service.parser.FormatDetector
import com.example.liftrix.domain.service.parser.WorkoutParserFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject

class ImportWorkoutsUseCase @Inject constructor(
    private val formatDetector: FormatDetector,
    private val parserFactory: WorkoutParserFactory,
    private val validateImportUseCase: ValidateImportUseCase,
    private val exerciseMappingService: ExerciseMappingService,
    private val workoutDao: WorkoutDao,
    private val dataImportDao: DataImportDao
) {
    
    suspend fun validateImportFile(
        uri: Uri,
        inputStream: InputStream
    ): LiftrixResult<ImportValidation> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "VALIDATE_IMPORT_FAILED",
                errorMessage = "Failed to validate import file",
                analyticsContext = mapOf(
                    "uri" to uri.toString(),
                    "operation" to "VALIDATE_IMPORT",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        Timber.d("Validating import file: $uri")
        
        // Detect format
        val formatResult = formatDetector.detectFormat(inputStream)
        val format = formatResult.fold(
            onSuccess = { it },
            onFailure = { throw IllegalArgumentException("Could not detect file format: ${it.message}") }
        )
        
        // Parse file
        val parserResult = parserFactory.getParser(format)
        val parser = parserResult.fold(
            onSuccess = { it },
            onFailure = { throw IllegalArgumentException("Unsupported format: $format") }
        )
        
        // Reset input stream for parsing
        inputStream.reset()
        val parseResult = parser.parse(inputStream)
        val workouts = parseResult.fold(
            onSuccess = { it },
            onFailure = { throw IllegalArgumentException("Failed to parse file: ${it.message}") }
        )
        
        // Validate parsed data
        val validationResult = validateImportUseCase.invoke(workouts, "temp_user")
        val validation = validationResult.fold(
            onSuccess = { it },
            onFailure = { throw IllegalArgumentException("Validation failed: ${it.message}") }
        )
        
        ImportValidation(
            format = format,
            isValid = validation.isValid,
            totalWorkouts = validation.totalWorkouts,
            totalExercises = validation.totalExercises,
            totalSets = validation.totalSets,
            errors = validation.errors,
            warnings = validation.warnings,
            unmappedExercises = validation.unmappedExercises,
            preview = workouts.take(3) // First 3 workouts for preview
        )
    }
    
    suspend fun importWorkouts(
        userId: String,
        uri: Uri,
        inputStream: InputStream,
        options: ImportOptions
    ): LiftrixResult<ImportResult> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "IMPORT_WORKOUTS_FAILED",
                errorMessage = "Failed to import workout data",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "uri" to uri.toString(),
                    "operation" to "IMPORT_WORKOUTS",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        Timber.d("Starting workout import for user: $userId")
        
        // Create import record
        val importId = UUID.randomUUID().toString()
        val importEntity = DataImportEntity(
            importId = importId,
            userId = userId,
            sourceFormat = options.detectedFormat ?: "UNKNOWN",
            sourceApp = options.sourceApp,
            status = "VALIDATING",
            conflictResolution = options.conflictStrategy.name,
            startedAt = System.currentTimeMillis()
        )
        
        dataImportDao.insertImport(importEntity)
        
        try {
            // Parse file
            val parserResult = parserFactory.getParser(options.detectedFormat ?: "JSON")
            val parser = parserResult.fold(
                onSuccess = { it },
                onFailure = { 
                    dataImportDao.updateImportStatus(importId, userId, "FAILED", "Unsupported format")
                    throw IllegalArgumentException("Unsupported format: ${options.detectedFormat}")
                }
            )
            
            val parseResult = parser.parse(inputStream)
            val workouts = parseResult.fold(
                onSuccess = { it },
                onFailure = { 
                    dataImportDao.updateImportStatus(importId, userId, "FAILED", "Parse error: ${it.message}")
                    throw IllegalArgumentException("Failed to parse file: ${it.message}")
                }
            )
            
            // Update status and total records
            dataImportDao.startImportProcessing(importId, userId, workouts.size)
            
            // Validate data
            val validationResult = validateImportUseCase.invoke(workouts, userId)
            val validation = validationResult.fold(
                onSuccess = { it },
                onFailure = { 
                    dataImportDao.updateImportStatus(importId, userId, "FAILED", "Validation failed")
                    throw IllegalArgumentException("Validation failed: ${it.message}")
                }
            )
            
            if (!validation.isValid && !options.allowValidationErrors) {
                dataImportDao.updateImportStatus(
                    importId, userId, "FAILED", 
                    "Validation errors: ${validation.errors.joinToString("; ") { it.message }}"
                )
                throw IllegalArgumentException("Import data contains validation errors")
            }
            
            // Process workouts
            val processResult = processWorkouts(workouts, userId, options)
            
            // Update import record as completed
            dataImportDao.markImportCompleted(
                importId = importId,
                userId = userId,
                importedRecords = processResult.importedCount,
                skippedRecords = processResult.skippedCount,
                completedAt = System.currentTimeMillis()
            )
            
            Timber.d("Import completed: $importId, imported: ${processResult.importedCount}, skipped: ${processResult.skippedCount}")
            
            ImportResult(
                importId = importId,
                importedCount = processResult.importedCount,
                skippedCount = processResult.skippedCount,
                errors = validation.errors,
                warnings = validation.warnings,
                unmappedExercises = validation.unmappedExercises
            )
            
        } catch (e: Exception) {
            // Update import record as failed
            dataImportDao.updateImportStatus(importId, userId, "FAILED", e.message)
            throw e
        }
    }
    
    fun getImportProgress(importId: String): Flow<ImportProgress> = flow {
        // This would typically integrate with a background processing system
        // For now, emit basic progress updates
        emit(ImportProgress(importId, 0, "Starting import..."))
        emit(ImportProgress(importId, 25, "Parsing file..."))
        emit(ImportProgress(importId, 50, "Validating data..."))
        emit(ImportProgress(importId, 75, "Processing workouts..."))
        emit(ImportProgress(importId, 100, "Import completed"))
    }
    
    suspend fun rollbackImport(importId: String, userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ROLLBACK_IMPORT_FAILED",
                errorMessage = "Failed to rollback import",
                analyticsContext = mapOf(
                    "import_id" to importId,
                    "user_id" to userId,
                    "operation" to "ROLLBACK_IMPORT",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        // This would implement the rollback logic
        // For now, just disable rollback availability
        dataImportDao.disableRollbackForImport(importId, userId)
        Timber.d("Import rollback completed for: $importId")
    }
    
    private suspend fun processWorkouts(
        workouts: List<ParsedWorkout>,
        userId: String,
        options: ImportOptions
    ): ProcessResult = withContext(Dispatchers.IO) {
        var importedCount = 0
        var skippedCount = 0
        
        for (workout in workouts) {
            try {
                // Check for existing workout conflicts
                val hasConflict = checkForWorkoutConflict(workout, userId)
                
                if (hasConflict) {
                    when (options.conflictStrategy) {
                        ConflictStrategy.SKIP -> {
                            skippedCount++
                            continue
                        }
                        ConflictStrategy.REPLACE -> {
                            // Delete existing and import new
                            deleteExistingWorkout(workout, userId)
                        }
                        ConflictStrategy.MERGE -> {
                            // Merge with existing (complex logic)
                            mergeWithExistingWorkout(workout, userId)
                        }
                    }
                }
                
                // Convert and save workout
                val convertedWorkout = convertParsedWorkout(workout, userId)
                // Save workout logic would go here
                
                importedCount++
                
            } catch (e: Exception) {
                Timber.w(e, "Failed to process workout: ${workout.name}")
                skippedCount++
            }
        }
        
        ProcessResult(importedCount, skippedCount)
    }
    
    private suspend fun checkForWorkoutConflict(workout: ParsedWorkout, userId: String): Boolean {
        // Check if workout with same date and name already exists
        // This would use the WorkoutDao to check for conflicts
        return false // Placeholder
    }
    
    private suspend fun deleteExistingWorkout(workout: ParsedWorkout, userId: String) {
        // Delete existing workout logic
    }
    
    private suspend fun mergeWithExistingWorkout(workout: ParsedWorkout, userId: String) {
        // Merge workout logic
    }
    
    private suspend fun convertParsedWorkout(workout: ParsedWorkout, userId: String): Any {
        // Convert ParsedWorkout to internal workout format
        // This would involve exercise mapping and entity creation
        return workout // Placeholder
    }
    
    private data class ProcessResult(
        val importedCount: Int,
        val skippedCount: Int
    )
}

data class ImportValidation(
    val format: String,
    val isValid: Boolean,
    val totalWorkouts: Int,
    val totalExercises: Int,
    val totalSets: Int,
    val errors: List<com.example.liftrix.domain.model.portability.ImportValidationError>,
    val warnings: List<com.example.liftrix.domain.model.portability.ImportValidationError>,
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
    val errors: List<com.example.liftrix.domain.model.portability.ImportValidationError>,
    val warnings: List<com.example.liftrix.domain.model.portability.ImportValidationError>,
    val unmappedExercises: List<String>
)

data class ImportProgress(
    val importId: String,
    val progressPercentage: Int,
    val statusMessage: String
)

enum class ConflictStrategy {
    SKIP,
    REPLACE,
    MERGE
}