package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Domain model representing an active workout session that's currently in progress.
 * This is separate from WorkoutTemplate (reusable blueprint) and Workout (completed session).
 * 
 * Key features:
 * - Real-time session state management
 * - Exercise modification during workout
 * - Set completion tracking
 * - Rest timer integration
 * - Background persistence support
 */
data class ActiveWorkoutSession(
    val id: WorkoutSessionId,
    val userId: String,
    val name: String,
    val templateId: WorkoutTemplateId? = null, // Link to original template if created from one
    val exercises: List<SessionExercise>,
    val currentExerciseIndex: Int = 0, // Which exercise user is currently on
    val sessionState: SessionState,
    val startedAt: Instant,
    val pausedAt: Instant? = null,
    val resumedAt: Instant? = null,
    val totalPausedDuration: Long = 0, // seconds
    val notes: String? = null,
    val lastModified: Instant = Instant.now()
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(name.isNotBlank()) { "Session name cannot be blank" }
        require(currentExerciseIndex >= 0) { "Current exercise index cannot be negative" }
        require(currentExerciseIndex < exercises.size || exercises.isEmpty()) { 
            "Current exercise index out of bounds: $currentExerciseIndex >= ${exercises.size}" 
        }
        require(totalPausedDuration >= 0) { "Total paused duration cannot be negative" }
        
        // Validate state transitions
        when (sessionState) {
            SessionState.ACTIVE -> {
                require(pausedAt == null) { "Active session cannot have pausedAt timestamp" }
            }
            SessionState.PAUSED -> {
                require(pausedAt != null) { "Paused session must have pausedAt timestamp" }
            }
            SessionState.REST -> {
                // Rest state can have pausedAt if rest is paused
            }
        }
    }

    /**
     * Session states for active workout management
     */
    enum class SessionState {
        ACTIVE,   // Currently working out
        PAUSED,   // Session paused by user
        REST      // In rest period between sets
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
        ): ActiveWorkoutSession {
            val sessionName = customName?.trim()?.takeIf { it.isNotBlank() }
                ?: "${template.name} - ${Instant.now().epochSecond}"

            val sessionExercises = template.exercises.map { templateExercise ->
                SessionExercise.fromTemplate(templateExercise)
            }

            return ActiveWorkoutSession(
                id = WorkoutSessionId.generate(),
                userId = userId,
                name = sessionName,
                templateId = template.id,
                exercises = sessionExercises,
                currentExerciseIndex = 0,
                sessionState = SessionState.ACTIVE,
                startedAt = Instant.now()
            )
        }

        /**
         * Creates a blank session for custom workout
         */
        fun createBlank(
            userId: String,
            name: String
        ): ActiveWorkoutSession {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            require(name.isNotBlank()) { "Session name cannot be blank" }
            require(name.length <= MAX_SESSION_NAME_LENGTH) { 
                "Session name too long: ${name.length} > $MAX_SESSION_NAME_LENGTH" 
            }

            return ActiveWorkoutSession(
                id = WorkoutSessionId.generate(),
                userId = userId,
                name = name.trim(),
                templateId = null,
                exercises = emptyList(),
                currentExerciseIndex = 0,
                sessionState = SessionState.ACTIVE,
                startedAt = Instant.now()
            )
        }
    }

    /**
     * Gets the current exercise being performed
     */
    fun getCurrentExercise(): SessionExercise? = 
        exercises.getOrNull(currentExerciseIndex)

    /**
     * Gets total session duration including paused time
     */
    fun getTotalSessionDuration(): Long {
        val now = Instant.now()
        val elapsed = now.epochSecond - startedAt.epochSecond
        return elapsed - totalPausedDuration
    }

    /**
     * Gets active workout duration (excluding paused time)
     */
    fun getActiveWorkoutDuration(): Long {
        val now = Instant.now()
        val totalElapsed = now.epochSecond - startedAt.epochSecond
        
        return when (sessionState) {
            SessionState.PAUSED -> {
                // If currently paused, don't count time since pause
                pausedAt?.let { pauseTime ->
                    totalElapsed - (now.epochSecond - pauseTime.epochSecond) - totalPausedDuration
                } ?: (totalElapsed - totalPausedDuration)
            }
            else -> totalElapsed - totalPausedDuration
        }
    }

    /**
     * Pauses the current session
     */
    fun pause(): ActiveWorkoutSession {
        require(sessionState == SessionState.ACTIVE || sessionState == SessionState.REST) { 
            "Cannot pause session in state: $sessionState" 
        }
        
        return copy(
            sessionState = SessionState.PAUSED,
            pausedAt = Instant.now(),
            lastModified = Instant.now()
        )
    }

    /**
     * Resumes the paused session
     */
    fun resume(): ActiveWorkoutSession {
        require(sessionState == SessionState.PAUSED) { 
            "Cannot resume session in state: $sessionState" 
        }
        require(pausedAt != null) { "Cannot resume session without pausedAt timestamp" }

        val now = Instant.now()
        val pauseDuration = now.epochSecond - pausedAt!!.epochSecond

        return copy(
            sessionState = SessionState.ACTIVE,
            pausedAt = null,
            resumedAt = now,
            totalPausedDuration = totalPausedDuration + pauseDuration,
            lastModified = now
        )
    }

    /**
     * Starts rest period after completing a set
     */
    fun startRest(): ActiveWorkoutSession {
        require(sessionState == SessionState.ACTIVE) { 
            "Cannot start rest from state: $sessionState" 
        }

        return copy(
            sessionState = SessionState.REST,
            lastModified = Instant.now()
        )
    }

    /**
     * Ends rest period and returns to active workout
     */
    fun endRest(): ActiveWorkoutSession {
        require(sessionState == SessionState.REST) { 
            "Cannot end rest from state: $sessionState" 
        }

        return copy(
            sessionState = SessionState.ACTIVE,
            lastModified = Instant.now()
        )
    }

    /**
     * Moves to the next exercise
     */
    fun moveToNextExercise(): ActiveWorkoutSession {
        val nextIndex = currentExerciseIndex + 1
        require(nextIndex < exercises.size) { 
            "Already at last exercise: $currentExerciseIndex" 
        }

        return copy(
            currentExerciseIndex = nextIndex,
            sessionState = SessionState.ACTIVE, // Always active when moving to next exercise
            lastModified = Instant.now()
        )
    }

    /**
     * Moves to the previous exercise
     */
    fun moveToPreviousExercise(): ActiveWorkoutSession {
        require(currentExerciseIndex > 0) { 
            "Already at first exercise: $currentExerciseIndex" 
        }

        return copy(
            currentExerciseIndex = currentExerciseIndex - 1,
            sessionState = SessionState.ACTIVE,
            lastModified = Instant.now()
        )
    }

    /**
     * Adds an exercise to the session
     */
    fun addExercise(exercise: SessionExercise): ActiveWorkoutSession {
        require(!exercises.any { it.exerciseId == exercise.exerciseId }) { 
            "Exercise already exists in session: ${exercise.exerciseId}" 
        }

        val newExercise = exercise.copy(orderIndex = exercises.size)
        
        return copy(
            exercises = exercises + newExercise,
            lastModified = Instant.now()
        )
    }

    /**
     * Removes an exercise from the session
     */
    fun removeExercise(exerciseId: ExerciseId): ActiveWorkoutSession {
        val exerciseIndex = exercises.indexOfFirst { it.exerciseId == exerciseId }
        require(exerciseIndex != -1) { "Exercise not found: $exerciseId" }

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
    fun updateExercise(exerciseId: ExerciseId, updatedExercise: SessionExercise): ActiveWorkoutSession {
        val exerciseIndex = exercises.indexOfFirst { it.exerciseId == exerciseId }
        require(exerciseIndex != -1) { "Exercise not found: $exerciseId" }

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
    fun updateNotes(newNotes: String?): ActiveWorkoutSession {
        val trimmedNotes = newNotes?.trim()?.takeIf { it.isNotBlank() }
        
        trimmedNotes?.let { notes ->
            require(notes.length <= MAX_NOTES_LENGTH) { 
                "Notes too long: ${notes.length} > $MAX_NOTES_LENGTH" 
            }
        }

        return copy(
            notes = trimmedNotes,
            lastModified = Instant.now()
        )
    }

    /**
     * Converts the active session to a completed workout
     * 🔥 FIXED: Added error handling to prevent crashes during exercise conversion
     */
    fun toCompletedWorkout(): Workout {
        val completedExercises = exercises.mapNotNull { sessionExercise ->
            try {
                sessionExercise.toCompletedExercise()
            } catch (e: IllegalArgumentException) {
                // Log the error but don't crash the entire workout conversion
                timber.log.Timber.e(e, "🔥 ERROR: Failed to convert SessionExercise '${sessionExercise.name}' to Exercise. Skipping this exercise.")
                null // Skip this exercise rather than crashing
            }
        }

        return Workout(
            id = WorkoutId(id.value),
            userId = userId,
            name = name,
            date = java.time.LocalDate.now(),
            exercises = completedExercises,
            status = WorkoutStatus.COMPLETED,
            templateId = templateId?.let { WorkoutId(it.value) },
            startTime = startedAt,
            endTime = Instant.now(),
            createdAt = startedAt,
            updatedAt = Instant.now(),
            notes = notes
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
    fun isCompleted(): Boolean = exercises.isNotEmpty() && 
        exercises.all { exercise -> exercise.sets.all { it.completedAt != null } }

    /**
     * Gets the next exercise to perform
     */
    fun getNextExercise(): SessionExercise? = 
        exercises.getOrNull(currentExerciseIndex + 1)

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
}

/**
 * Value class for type-safe workout session IDs
 */
@JvmInline
value class WorkoutSessionId(val value: String) {
    init {
        require(value.isNotBlank()) { "WorkoutSessionId cannot be blank" }
    }

    override fun toString(): String = value

    companion object {
        fun generate(): WorkoutSessionId = WorkoutSessionId("session_${System.currentTimeMillis()}_${(1000..9999).random()}")
        fun fromString(value: String): WorkoutSessionId = WorkoutSessionId(value)
    }
}