package com.example.liftrix.domain.model

import com.example.liftrix.domain.util.DomainLogger as Timber
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 
 * This replaces the complex ActiveWorkoutSession + Workout dual system with
 * a single, comprehensive model that handles both active and completed states.
 * 
 * Key principles:
 * - Single source of truth for all session data
 * - Session-scoped exercise lists (no global injection)
 * - Simplified state management
 * - Built-in persistence and recovery support
 */
data class UnifiedWorkoutSession(
    val id: WorkoutSessionId,
    val userId: String,
    val name: String,
    val templateId: String? = null,
    val exercises: List<SessionExercise>,
    val currentExerciseIndex: Int = 0,
    val sessionStatus: SessionStatus,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val elapsedTimeSeconds: Long = 0,
    val notes: String? = null,
    val lastModified: Instant = Instant.now(),
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(name.isNotBlank()) { "Session name cannot be blank" }
        require(name.length <= MAX_SESSION_NAME_LENGTH) { "Session name too long" }
        require(currentExerciseIndex >= 0) { "Current exercise index cannot be negative" }
        require(currentExerciseIndex < exercises.size || exercises.isEmpty()) { 
            "Current exercise index out of bounds" 
        }
        require(elapsedTimeSeconds >= 0) { "Elapsed time cannot be negative" }
        
        // Validate status-specific requirements
        when (sessionStatus) {
            SessionStatus.ACTIVE -> {
                require(endedAt == null) { "Active session cannot have end time" }
            }
            SessionStatus.PAUSED -> {
                require(endedAt == null) { "Paused session cannot have end time" }
            }
            SessionStatus.COMPLETED -> {
                require(endedAt != null) { "Completed session must have end time" }
                require(endedAt!! >= startedAt) { "End time must be after start time" }
            }
            SessionStatus.FAILED_TO_SAVE -> {
                require(endedAt != null) { "Failed to save session must have end time" }
                require(endedAt!! >= startedAt) { "End time must be after start time" }
            }
        }
        
        notes?.let { n ->
            require(n.length <= MAX_NOTES_LENGTH) { "Notes too long" }
        }
    }

    /**
     * Session status - simplified from complex state machine
     */
    enum class SessionStatus {
        ACTIVE,         // Currently working out
        PAUSED,         // Temporarily paused
        COMPLETED,      // Finished and saved
        FAILED_TO_SAVE  // Completed but failed to save, ready for retry
    }

    companion object {
        const val MAX_SESSION_NAME_LENGTH = 100
        const val MAX_NOTES_LENGTH = 1000
        const val MAX_SESSION_DURATION_HOURS = 8

        /**
         * Creates a new session from a template
         */
        fun fromTemplate(
            userId: String,
            template: WorkoutTemplate,
            customName: String? = null
        ): UnifiedWorkoutSession {
            val sessionName = customName?.trim()?.takeIf { it.isNotBlank() }
                ?: "${template.name} - ${LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))}"

            // Convert template exercises to session-scoped exercises
            val sessionExercises = template.exercises.mapIndexed { index, templateExercise ->
                SessionExercise.fromTemplate(templateExercise).copy(orderIndex = index)
            }

            // Store original template metadata for change detection
            val metadata = mapOf(
                "originalExerciseCount" to template.exercises.size.toString(),
                "originalTemplateName" to template.name,
                "templateCreatedFrom" to template.id.value
            )

            return UnifiedWorkoutSession(
                id = WorkoutSessionId.generate(),
                userId = userId,
                name = sessionName,
                templateId = template.id.value, // Unwrap WorkoutTemplateId to String
                exercises = sessionExercises,
                currentExerciseIndex = 0,
                sessionStatus = SessionStatus.ACTIVE,
                startedAt = Instant.now(),
                metadata = metadata
            )
        }

        /**
         * Creates a blank session for custom workout
         */
        fun createBlank(
            userId: String,
            name: String
        ): UnifiedWorkoutSession {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(name.isNotBlank()) { "Session name cannot be blank" }
            require(name.length <= MAX_SESSION_NAME_LENGTH) { "Session name too long" }

            return UnifiedWorkoutSession(
                id = WorkoutSessionId.generate(),
                userId = userId,
                name = name.trim(),
                templateId = null,
                exercises = emptyList(),
                currentExerciseIndex = 0,
                sessionStatus = SessionStatus.ACTIVE,
                startedAt = Instant.now()
            )
        }
    }

    /**
     * Checks if this session is currently active (not completed)
     */
    fun isActive(): Boolean = sessionStatus != SessionStatus.COMPLETED

    /**
     * Checks if this session is live (active or paused)
     */
    fun isLive(): Boolean = sessionStatus == SessionStatus.ACTIVE || sessionStatus == SessionStatus.PAUSED

    /**
     * Gets the current exercise being performed
     */
    fun getCurrentExercise(): SessionExercise? = exercises.getOrNull(currentExerciseIndex)

    /**
     * Gets the next exercise to perform
     */
    fun getNextExercise(): SessionExercise? = exercises.getOrNull(currentExerciseIndex + 1)

    /**
     * Gets total session duration including current elapsed time
     */
    fun getTotalDurationSeconds(): Long {
        return when (sessionStatus) {
            SessionStatus.ACTIVE -> {
                val currentElapsed = Instant.now().epochSecond - startedAt.epochSecond
                elapsedTimeSeconds + currentElapsed
            }
            SessionStatus.PAUSED -> elapsedTimeSeconds
            SessionStatus.COMPLETED -> {
                elapsedTimeSeconds.takeIf { it > 0L } ?: endedAt?.let { end ->
                    end.epochSecond - startedAt.epochSecond
                } ?: 0L
            }
            SessionStatus.FAILED_TO_SAVE -> {
                elapsedTimeSeconds.takeIf { it > 0L } ?: endedAt?.let { end ->
                    end.epochSecond - startedAt.epochSecond
                } ?: 0L
            }
        }
    }

    /**
     * Pauses the session
     */
    fun pause(): UnifiedWorkoutSession {
        require(sessionStatus == SessionStatus.ACTIVE) { "Can only pause active sessions" }
        
        val currentElapsed = Instant.now().epochSecond - startedAt.epochSecond
        
        return copy(
            sessionStatus = SessionStatus.PAUSED,
            elapsedTimeSeconds = elapsedTimeSeconds + currentElapsed,
            lastModified = Instant.now()
        )
    }

    /**
     * Resumes the session
     */
    fun resume(): UnifiedWorkoutSession {
        require(sessionStatus == SessionStatus.PAUSED) { "Can only resume paused sessions" }
        
        return copy(
            sessionStatus = SessionStatus.ACTIVE,
            startedAt = Instant.now(), // Reset start time for new active period
            lastModified = Instant.now()
        )
    }

    /**
     * Completes the session
     */
    fun complete(): UnifiedWorkoutSession {
        require(sessionStatus != SessionStatus.COMPLETED) { "Session already completed" }
        
        val now = Instant.now()
        val totalElapsed = when (sessionStatus) {
            SessionStatus.ACTIVE -> {
                elapsedTimeSeconds + (now.epochSecond - startedAt.epochSecond)
            }
            SessionStatus.PAUSED -> elapsedTimeSeconds
            SessionStatus.COMPLETED -> elapsedTimeSeconds // Should not happen due to require
            SessionStatus.FAILED_TO_SAVE -> elapsedTimeSeconds // Should not happen due to require
        }
        
        return copy(
            sessionStatus = SessionStatus.COMPLETED,
            endedAt = now,
            elapsedTimeSeconds = totalElapsed,
            lastModified = now
        )
    }

    /**
     * Moves to the next exercise
     */
    fun moveToNextExercise(): UnifiedWorkoutSession {
        require(sessionStatus != SessionStatus.COMPLETED) { "Cannot modify completed session" }
        val nextIndex = currentExerciseIndex + 1
        require(nextIndex < exercises.size) { "Already at last exercise" }

        return copy(
            currentExerciseIndex = nextIndex,
            lastModified = Instant.now()
        )
    }

    /**
     * Moves to the previous exercise
     */
    fun moveToPreviousExercise(): UnifiedWorkoutSession {
        require(sessionStatus != SessionStatus.COMPLETED) { "Cannot modify completed session" }
        require(currentExerciseIndex > 0) { "Already at first exercise" }

        return copy(
            currentExerciseIndex = currentExerciseIndex - 1,
            lastModified = Instant.now()
        )
    }

    /**
     */
    fun addExercise(exercise: SessionExercise): UnifiedWorkoutSession {
        require(sessionStatus != SessionStatus.COMPLETED) { "Cannot modify completed session" }
        require(!exercises.any { it.exerciseId == exercise.exerciseId }) { 
            "Exercise already exists in session" 
        }

        val newExercise = exercise.copy(orderIndex = exercises.size)
        
        return copy(
            exercises = exercises + newExercise,
            lastModified = Instant.now()
        )
    }

    /**
     */
    fun removeExercise(exerciseId: ExerciseId): UnifiedWorkoutSession {
        require(sessionStatus != SessionStatus.COMPLETED) { "Cannot modify completed session" }
        val exerciseIndex = exercises.indexOfFirst { it.exerciseId == exerciseId }
        require(exerciseIndex != -1) { "Exercise not found in session" }

        val updatedExercises = exercises.filterNot { it.exerciseId == exerciseId }
            .mapIndexed { index, exercise -> exercise.copy(orderIndex = index) }

        val newCurrentIndex = when {
            updatedExercises.isEmpty() -> 0
            currentExerciseIndex > exerciseIndex -> currentExerciseIndex - 1
            currentExerciseIndex == exerciseIndex && currentExerciseIndex >= updatedExercises.size -> 
                (updatedExercises.size - 1).coerceAtLeast(0)
            else -> currentExerciseIndex
        }

        return copy(
            exercises = updatedExercises,
            currentExerciseIndex = newCurrentIndex,
            lastModified = Instant.now()
        )
    }

    /**
     * Updates an exercise in the session
     */
    fun updateExercise(exerciseId: ExerciseId, updatedExercise: SessionExercise): UnifiedWorkoutSession {
        require(sessionStatus != SessionStatus.COMPLETED) { "Cannot modify completed session" }
        val exerciseIndex = exercises.indexOfFirst { it.exerciseId == exerciseId }
        require(exerciseIndex != -1) { "Exercise not found in session" }

        val updatedExercises = exercises.toMutableList()
        updatedExercises[exerciseIndex] = updatedExercise.copy(orderIndex = exerciseIndex)

        return copy(
            exercises = updatedExercises,
            lastModified = Instant.now()
        )
    }

    /**
     * Updates session notes
     */
    fun updateNotes(newNotes: String?): UnifiedWorkoutSession {
        require(sessionStatus != SessionStatus.COMPLETED) { "Cannot modify completed session" }
        
        val trimmedNotes = newNotes?.trim()?.takeIf { it.isNotBlank() }
        trimmedNotes?.let { notes ->
            require(notes.length <= MAX_NOTES_LENGTH) { "Notes too long" }
        }

        return copy(
            notes = trimmedNotes,
            lastModified = Instant.now()
        )
    }

    /**
     * Gets session completion percentage
     */
    fun getCompletionPercentage(): Float {
        if (exercises.isEmpty()) return 0f
        
        val totalSets = exercises.sumOf { it.sets.size }
        if (totalSets == 0) return 0f
        
        val completedSets = exercises.sumOf { exercise ->
            exercise.sets.count { it.completedAt != null }
        }
        
        return (completedSets.toFloat() / totalSets) * 100f
    }

    /**
     * Checks if all exercises are completed
     */
    fun isAllExercisesCompleted(): Boolean = exercises.isNotEmpty() && 
        exercises.all { exercise -> exercise.sets.all { it.completedAt != null } }

    /**
     * Gets total volume (weight × reps) for the session
     */
    fun getTotalVolume(): Double = exercises.sumOf { it.getTotalVolume() }

    /**
     * Gets total completed sets count
     */
    fun getCompletedSetsCount(): Int = exercises.sumOf { exercise ->
        exercise.sets.count { it.completedAt != null }
    }

    /**
     * Gets total sets count
     */
    fun getTotalSetsCount(): Int = exercises.sumOf { it.sets.size }

    /**
     * No more complex dual conversion - session IS the workout
     */
    fun toCompletedWorkout(): Workout {
        require(sessionStatus == SessionStatus.COMPLETED) { "Session must be completed first" }
        
        // 🔥 SETS-DEBUG: Log session exercises before conversion to completed workout
        Timber.d("[SETS-DEBUG-6] UnifiedWorkoutSession.toCompletedWorkout: Converting ${exercises.size} session exercises")
        exercises.forEach { sessionExercise ->
            Timber.d("[SETS-DEBUG-6a] SessionExercise '${sessionExercise.name}' has ${sessionExercise.sets.size} session sets")
        }
        
        val completedExercises = exercises.mapNotNull { sessionExercise ->
            try {
                val completedExercise = sessionExercise.toCompletedExercise()
                Timber.d("[SETS-DEBUG-6b] Successfully converted '${sessionExercise.name}' to Exercise with ${completedExercise.sets.size} sets")
                completedExercise
            } catch (e: Exception) {
                // 🔥 SETS-DEBUG: Log conversion failures
                Timber.e(e, "[SETS-DEBUG-6c] Failed to convert SessionExercise '${sessionExercise.name}' to Exercise: ${e.message}")
                null
            }
        }
        
        // 🔥 SETS-DEBUG: Log final completed exercises count
        Timber.d("[SETS-DEBUG-6d] Final workout has ${completedExercises.size} completed exercises")


        val completedEndTime = endedAt ?: Instant.now()
        val completedDurationSeconds = getTotalDurationSeconds().coerceAtLeast(0)
        val completedStartTime = when {
            completedDurationSeconds > 0L -> completedEndTime.minusSeconds(completedDurationSeconds)
            completedEndTime.isAfter(startedAt) -> startedAt
            else -> completedEndTime.minusNanos(1_000_000)
        }

        return Workout(
            id = WorkoutId(id.value), // Keep session ID for workout tracking
            userId = userId,
            name = name,
            date = startedAt.atZone(ZoneId.systemDefault()).toLocalDate(),
            exercises = completedExercises,
            status = WorkoutStatus.COMPLETED,
            templateId = templateId?.let { WorkoutId(it.toString()) },
            startTime = completedStartTime,
            endTime = completedEndTime,
            createdAt = startedAt,
            updatedAt = lastModified,
            notes = notes
        )
    }

    /**
     * Creates a workout template from this session
     */
    fun toWorkoutTemplate(
        templateName: String? = null,
        description: String? = null
    ): WorkoutTemplate {
        val templateExercises = exercises.map { sessionExercise ->
            TemplateExercise(
                exerciseId = sessionExercise.libraryExerciseId,
                name = sessionExercise.name,
                primaryMuscle = sessionExercise.primaryMuscle,
                equipment = sessionExercise.equipment,
                targetSets = sessionExercise.sets.size,
                targetReps = sessionExercise.sets.firstOrNull()?.targetReps?.let { Reps(it) },
                targetWeight = sessionExercise.sets.firstOrNull()?.targetWeight,
                restTimeSeconds = sessionExercise.restTimeSeconds,
                notes = sessionExercise.notes,
                orderIndex = sessionExercise.orderIndex
            )
        }

        return WorkoutTemplate(
            id = WorkoutTemplateId.generate(),
            userId = userId,
            name = templateName ?: "$name Template",
            description = description,
            exercises = templateExercises,
            estimatedDurationMinutes = (getTotalDurationSeconds() / 60).toInt(),
            difficultyLevel = 1, // Could be calculated
            folderId = "default", // Default folder
            usageCount = 0,
            lastUsedAt = null,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    /**
     * Gets workout duration in formatted string
     */
    fun getActiveWorkoutDuration(): String {
        val totalSeconds = getTotalDurationSeconds()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * Gets formatted duration string
     */
    fun getFormattedDuration(): String {
        val totalSeconds = getTotalDurationSeconds()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            else -> String.format("%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Gets summary statistics
     */
    fun getSessionStats(): SessionStats {
        return SessionStats(
            totalExercises = exercises.size,
            completedExercises = exercises.count { it.isCompleted() },
            totalSets = getTotalSetsCount(),
            completedSets = getCompletedSetsCount(),
            totalVolume = getTotalVolume(),
            durationSeconds = getTotalDurationSeconds(),
            completionPercentage = getCompletionPercentage()
        )
    }
}

/**
 * Session statistics data class
 */
data class SessionStats(
    val totalExercises: Int,
    val completedExercises: Int,
    val totalSets: Int,
    val completedSets: Int,
    val totalVolume: Double,
    val durationSeconds: Long,
    val completionPercentage: Float
)
