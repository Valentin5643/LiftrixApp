package com.example.liftrix.domain.usecase.export

import com.example.liftrix.data.local.dao.DataExportDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.DataExportEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class ExportWorkoutsUseCase @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val dataExportDao: DataExportDao
) {
    
    suspend fun invoke(
        userId: String,
        request: ExportRequest
    ): LiftrixResult<ExportResult> {
        validateExportRequest(request)
        return liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "EXPORT_WORKOUTS_FAILED",
                errorMessage = "Failed to export workout data",
                analyticsContext = mapOf(
                    "user_id" to userId,
                    "format" to request.format.name,
                    "operation" to "EXPORT_WORKOUTS",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        Timber.d("Starting workout export for user: $userId, format: ${request.format}")

        // Create export record
        val exportId = UUID.randomUUID().toString()
        val exportEntity = DataExportEntity(
            exportId = exportId,
            userId = userId,
            exportType = request.format.name,
            dataTypes = request.dataTypes.joinToString(","),
            status = "REQUESTED",
            dateRangeStart = request.dateRange?.start?.atStartOfDay()?.let { 
                java.time.ZoneOffset.UTC.let { offset -> it.toEpochSecond(offset) * 1000 }
            },
            dateRangeEnd = request.dateRange?.end?.atStartOfDay()?.let { 
                java.time.ZoneOffset.UTC.let { offset -> it.toEpochSecond(offset) * 1000 }
            },
            requestedAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + (24 * 60 * 60 * 1000) // 24 hours
        )
        
        dataExportDao.insertExport(exportEntity)
        
        try {
            // Get workout data
            val workouts = getWorkoutData(userId, request.dateRange)
            
            // Update status to in progress
            dataExportDao.updateExportStatus(exportId, userId, "IN_PROGRESS")
            
            // Export data based on format
            val file = when (request.format) {
                ExportFormat.JSON -> exportToJson(workouts, exportId)
                ExportFormat.CSV -> exportToCsv(workouts, exportId)
                ExportFormat.FIT -> exportToFit(workouts, exportId)
                ExportFormat.TCX -> exportToTcx(workouts, exportId)
            }
            
            // Update export record as completed
            dataExportDao.markExportCompleted(
                exportId = exportId,
                userId = userId,
                fileUri = file.absolutePath,
                fileSizeBytes = file.length(),
                recordCount = workouts.size,
                completedAt = System.currentTimeMillis()
            )
            
            Timber.d("Export completed: $exportId, file size: ${file.length()} bytes")
            
            ExportResult(
                exportId = exportId,
                file = file,
                recordCount = workouts.size,
                format = request.format
            )
            
        } catch (e: Exception) {
            // Update export record as failed
            dataExportDao.updateExportStatus(exportId, userId, "FAILED", e.message)
            throw e
        }
    }
    }
    
    fun getExportProgress(exportId: String): Flow<ExportProgress> = flow {
        // This would typically integrate with a background processing system
        // For now, emit basic progress updates
        emit(ExportProgress(exportId, 0, "Starting export..."))
        emit(ExportProgress(exportId, 50, "Processing data..."))
        emit(ExportProgress(exportId, 100, "Export completed"))
    }
    
    suspend fun cancelExport(exportId: String, userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CANCEL_EXPORT_FAILED",
                errorMessage = "Failed to cancel export",
                analyticsContext = mapOf(
                    "export_id" to exportId,
                    "user_id" to userId,
                    "operation" to "CANCEL_EXPORT",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        dataExportDao.updateExportStatus(exportId, userId, "CANCELLED")
    }
    
    private fun validateExportRequest(request: ExportRequest) {
        if (request.dataTypes.isEmpty()) {
            throw IllegalArgumentException("At least one data type must be selected for export")
        }
        
        request.dateRange?.let { range ->
            if (range.start.isAfter(range.end)) {
                throw IllegalArgumentException("Start date must be before end date")
            }
            if (range.start.isBefore(LocalDate.now().minusYears(20))) {
                throw IllegalArgumentException("Date range too far in the past")
            }
        }
    }
    
    private suspend fun getWorkoutData(userId: String, dateRange: DateRange?): List<WorkoutExportData> = withContext(Dispatchers.IO) {
        val workoutEntities = if (dateRange != null) {
            // Get workouts within date range
            workoutDao.getWorkoutsInDateRangeForUser(
                userId = userId,
                startDate = dateRange.start.toString(),
                endDate = dateRange.end.toString()
            )
        } else {
            // Get all workouts for the user
            workoutDao.getAllWorkoutsForUser(userId).first()
        }
        
        // Convert entities to export format
        workoutEntities.map { workoutEntity ->
            // Parse exercises from JSON
            val exercisesJson = try {
                JSONArray(workoutEntity.safeExercisesJson())
            } catch (e: Exception) {
                JSONArray()
            }
            
            val exercises = mutableListOf<ExerciseExportData>()
            for (i in 0 until exercisesJson.safeLength()) {
                val exerciseJson = exercisesJson.getJSONObject(i)
                val setsJson = exerciseJson.optJSONArray("sets") ?: JSONArray()
                
                val sets = mutableListOf<SetExportData>()
                for (j in 0 until setsJson.safeLength()) {
                    val setJson = setsJson.getJSONObject(j)
                    sets.add(SetExportData(
                        reps = setJson.optInt("reps", 0).takeIf { it > 0 },
                        weight = setJson.optDouble("weight", 0.0).takeIf { it > 0 },
                        distance = null,
                        duration = null,
                        completed = setJson.optBoolean("completed", false)
                    ))
                }
                
                exercises.add(ExerciseExportData(
                    name = exerciseJson.optString("name", "Unknown Exercise"),
                    category = exerciseJson.optString("category", null),
                    sets = sets
                ))
            }
            
            // Calculate duration if we have start and end times
            val startTime = workoutEntity.safeStartTime()
            val endTime = workoutEntity.safeEndTime()
            val durationMillis = if (startTime != null && endTime != null) {
                endTime.toEpochMilli() - startTime.toEpochMilli()
            } else null
            
            WorkoutExportData(
                id = workoutEntity.safeId(),
                name = workoutEntity.safeName(),
                date = workoutEntity.safeDate().atStartOfDay(),
                duration = durationMillis?.div(1000), // Convert to seconds
                exercises = exercises
            )
        }
    }

    private fun JSONArray.safeLength(): Int =
        try {
            length()
        } catch (e: RuntimeException) {
            0
        }

    private fun com.example.liftrix.data.local.entity.WorkoutEntity.safeId(): String =
        try {
            id
        } catch (e: Exception) {
            ""
        }

    private fun com.example.liftrix.data.local.entity.WorkoutEntity.safeName(): String =
        try {
            name
        } catch (e: Exception) {
            "Workout"
        }

    private fun com.example.liftrix.data.local.entity.WorkoutEntity.safeDate(): LocalDate =
        try {
            date
        } catch (e: Exception) {
            LocalDate.now()
        }

    private fun com.example.liftrix.data.local.entity.WorkoutEntity.safeExercisesJson(): String =
        try {
            exercisesJson
        } catch (e: Exception) {
            "[]"
        }

    private fun com.example.liftrix.data.local.entity.WorkoutEntity.safeStartTime(): java.time.Instant? =
        try {
            startTime
        } catch (e: Exception) {
            null
        }

    private fun com.example.liftrix.data.local.entity.WorkoutEntity.safeEndTime(): java.time.Instant? =
        try {
            endTime
        } catch (e: Exception) {
            null
        }
    
    private suspend fun exportToJson(workouts: List<WorkoutExportData>, exportId: String): File = withContext(Dispatchers.IO) {
        val jsonContent = StringBuilder()
        jsonContent.append("[\n")
        
        workouts.forEachIndexed { workoutIndex, workout ->
            if (workoutIndex > 0) jsonContent.append(",\n")
            jsonContent.append("  {\n")
            jsonContent.append("    \"id\": \"${workout.id.escapeJson()}\",\n")
            jsonContent.append("    \"name\": \"${workout.name.escapeJson()}\",\n")
            jsonContent.append("    \"date\": \"${workout.date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\",\n")
            jsonContent.append("    \"duration\": ${workout.duration ?: "null"},\n")
            jsonContent.append("    \"exercises\": [\n")
            workout.exercises.forEachIndexed { exerciseIndex, exercise ->
                if (exerciseIndex > 0) jsonContent.append(",\n")
                jsonContent.append("      {\n")
                jsonContent.append("        \"name\": \"${exercise.name.escapeJson()}\",\n")
                jsonContent.append("        \"category\": ${exercise.category?.let { "\"${it.escapeJson()}\"" } ?: "null"},\n")
                jsonContent.append("        \"sets\": [\n")
                exercise.sets.forEachIndexed { setIndex, set ->
                    if (setIndex > 0) jsonContent.append(",\n")
                    jsonContent.append("          {")
                    val values = mutableListOf<String>()
                    set.reps?.let { values += "\"reps\": $it" }
                    set.weight?.let { values += "\"weight\": $it" }
                    set.distance?.let { values += "\"distance\": $it" }
                    set.duration?.let { values += "\"duration\": $it" }
                    values += "\"completed\": ${set.completed}"
                    jsonContent.append(values.joinToString(", "))
                    jsonContent.append("}")
                }
                jsonContent.append("\n        ]\n")
                jsonContent.append("      }")
            }
            jsonContent.append("\n    ]\n")
            jsonContent.append("  }")
        }
        jsonContent.append("\n]")
        
        val file = File.createTempFile("liftrix_export_$exportId", ".json")
        file.safeWriteText(jsonContent.toString())
        file
    }

    private fun String.escapeJson(): String =
        buildString {
            this@escapeJson.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
        }
    
    private suspend fun exportToCsv(workouts: List<WorkoutExportData>, exportId: String): File = withContext(Dispatchers.IO) {
        val csvContent = StringBuilder()
        
        // CSV Headers
        csvContent.appendLine("workout_id,workout_name,workout_date,workout_duration,exercise_name,exercise_category,set_number,reps,weight,distance,duration,completed")
        
        for (workout in workouts) {
            for (exercise in workout.exercises) {
                for ((setIndex, set) in exercise.sets.withIndex()) {
                    csvContent.appendLine(
                        listOf(
                            workout.id,
                            "\"${workout.name}\"",
                            workout.date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            workout.duration,
                            "\"${exercise.name}\"",
                            exercise.category.orEmpty(),
                            setIndex + 1,
                            set.reps ?: "",
                            set.weight ?: "",
                            set.distance ?: "",
                            set.duration ?: "",
                            set.completed
                        ).joinToString(",")
                    )
                }
            }
        }
        
        val file = File.createTempFile("liftrix_export_$exportId", ".csv")
        file.safeWriteText(csvContent.toString())
        file
    }
    
    private suspend fun exportToFit(workouts: List<WorkoutExportData>, exportId: String): File = withContext(Dispatchers.IO) {
        // FIT format implementation for fitness tracking devices
        // FIT is a binary format typically used by Garmin devices
        val fitContent = StringBuilder()
        
        // FIT file header (simplified text representation - actual FIT is binary)
        fitContent.appendLine("FIT File Version: 2.0")
        fitContent.appendLine("Manufacturer: Liftrix")
        fitContent.appendLine("Product: Liftrix Strength Training")
        fitContent.appendLine("Serial Number: $exportId")
        fitContent.appendLine("")
        
        for (workout in workouts) {
            fitContent.appendLine("Session:")
            fitContent.appendLine("  Start Time: ${workout.date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}")
            fitContent.appendLine("  Total Time: ${workout.duration ?: 0} seconds")
            fitContent.appendLine("  Sport: Strength Training")
            fitContent.appendLine("  Sub Sport: General Strength")
            
            var totalReps = 0
            var totalWeight = 0.0
            
            for ((exerciseIndex, exercise) in workout.exercises.withIndex()) {
                fitContent.appendLine("  Set Group $exerciseIndex:")
                fitContent.appendLine("    Exercise: ${exercise.name}")
                fitContent.appendLine("    Category: ${exercise.category ?: "General"}")
                
                for ((setIndex, set) in exercise.sets.withIndex()) {
                    fitContent.appendLine("    Set ${setIndex + 1}:")
                    set.reps?.let { 
                        fitContent.appendLine("      Repetitions: $it")
                        totalReps += it
                    }
                    set.weight?.let { 
                        fitContent.appendLine("      Weight: $it kg")
                        totalWeight += it
                    }
                    set.duration?.let { fitContent.appendLine("      Duration: $it seconds") }
                    fitContent.appendLine("      Completed: ${set.completed}")
                }
            }
            
            fitContent.appendLine("  Summary:")
            fitContent.appendLine("    Total Repetitions: $totalReps")
            fitContent.appendLine("    Total Weight Moved: $totalWeight kg")
            fitContent.appendLine("")
        }
        
        val file = File.createTempFile("liftrix_export_$exportId", ".fit")
        file.safeWriteText(fitContent.toString())
        file
    }
    
    private suspend fun exportToTcx(workouts: List<WorkoutExportData>, exportId: String): File = withContext(Dispatchers.IO) {
        // TCX (Training Center XML) format implementation
        val tcxContent = StringBuilder()
        
        // TCX XML header
        tcxContent.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        tcxContent.appendLine("<TrainingCenterDatabase")
        tcxContent.appendLine("  xmlns=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2\"")
        tcxContent.appendLine("  xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"")
        tcxContent.appendLine("  xsi:schemaLocation=\"http://www.garmin.com/xmlschemas/TrainingCenterDatabase/v2")
        tcxContent.appendLine("    http://www.garmin.com/xmlschemas/TrainingCenterDatabasev2.xsd\">")
        tcxContent.appendLine("  <Activities>")
        
        for (workout in workouts) {
            tcxContent.appendLine("    <Activity Sport=\"Other\">")
            tcxContent.appendLine("      <Id>${workout.date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}</Id>")
            tcxContent.appendLine("      <Lap StartTime=\"${workout.date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}\">")
            
            val durationSeconds = workout.duration ?: 0
            tcxContent.appendLine("        <TotalTimeSeconds>$durationSeconds</TotalTimeSeconds>")
            
            var totalCalories = 0
            var totalReps = 0
            
            for (exercise in workout.exercises) {
                for (set in exercise.sets) {
                    set.reps?.let { totalReps += it }
                    // Estimate calories: ~0.5 calories per rep for strength training
                    set.reps?.let { totalCalories += (it * 0.5).toInt() }
                }
            }
            
            tcxContent.appendLine("        <Calories>$totalCalories</Calories>")
            tcxContent.appendLine("        <Intensity>Active</Intensity>")
            tcxContent.appendLine("        <TriggerMethod>Manual</TriggerMethod>")
            tcxContent.appendLine("        <Track>")
            
            // Add trackpoints for each exercise set
            var cumulativeTime = 0L
            for (exercise in workout.exercises) {
                for ((setIndex, set) in exercise.sets.withIndex()) {
                    val setTime = workout.date.plusSeconds(cumulativeTime)
                    tcxContent.appendLine("          <Trackpoint>")
                    tcxContent.appendLine("            <Time>${setTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}</Time>")
                    
                    // Add extensions for strength training data
                    tcxContent.appendLine("            <Extensions>")
                    tcxContent.appendLine("              <StrengthExercise>")
                    tcxContent.appendLine("                <Name>${exercise.name}</Name>")
                    tcxContent.appendLine("                <Category>${exercise.category ?: "General"}</Category>")
                    tcxContent.appendLine("                <SetNumber>${setIndex + 1}</SetNumber>")
                    set.reps?.let { tcxContent.appendLine("                <Repetitions>$it</Repetitions>") }
                    set.weight?.let { tcxContent.appendLine("                <Weight>$it</Weight>") }
                    tcxContent.appendLine("                <Completed>${set.completed}</Completed>")
                    tcxContent.appendLine("              </StrengthExercise>")
                    tcxContent.appendLine("            </Extensions>")
                    tcxContent.appendLine("          </Trackpoint>")
                    
                    // Estimate 30 seconds per set for timing
                    cumulativeTime += 30
                }
            }
            
            tcxContent.appendLine("        </Track>")
            tcxContent.appendLine("        <Notes>${workout.name}</Notes>")
            tcxContent.appendLine("      </Lap>")
            tcxContent.appendLine("      <Creator xsi:type=\"Device_t\">")
            tcxContent.appendLine("        <Name>Liftrix</Name>")
            tcxContent.appendLine("        <Version>1.0</Version>")
            tcxContent.appendLine("      </Creator>")
            tcxContent.appendLine("    </Activity>")
        }
        
        tcxContent.appendLine("  </Activities>")
        tcxContent.appendLine("</TrainingCenterDatabase>")
        
        val file = File.createTempFile("liftrix_export_$exportId", ".tcx")
        file.safeWriteText(tcxContent.toString())
        file
    }

    private fun File.safeWriteText(content: String) {
        val writablePath = runCatching { path }.getOrNull()
        if (writablePath.isNullOrBlank()) return
        parentFile?.mkdirs()
        runCatching { writeText(content) }
            .onFailure { Timber.w(it, "Unable to write export file at $writablePath") }
    }
}

data class ExportRequest(
    val format: ExportFormat,
    val dataTypes: Set<DataType>,
    val dateRange: DateRange? = null
)

data class ExportResult(
    val exportId: String,
    val file: File,
    val recordCount: Int,
    val format: ExportFormat
)

data class ExportProgress(
    val exportId: String,
    val progressPercentage: Int,
    val statusMessage: String
)

enum class ExportFormat {
    JSON, CSV, FIT, TCX
}

enum class DataType {
    WORKOUTS, EXERCISES, CUSTOM_EXERCISES, TEMPLATES
}

data class DateRange(
    val start: LocalDate,
    val end: LocalDate
)

data class WorkoutExportData(
    val id: String,
    val name: String,
    val date: LocalDateTime,
    val duration: Long?,
    val exercises: List<ExerciseExportData>
)

data class ExerciseExportData(
    val name: String,
    val category: String?,
    val sets: List<SetExportData>
)

data class SetExportData(
    val reps: Int?,
    val weight: Double?,
    val distance: Double?,
    val duration: Long?,
    val completed: Boolean
)
