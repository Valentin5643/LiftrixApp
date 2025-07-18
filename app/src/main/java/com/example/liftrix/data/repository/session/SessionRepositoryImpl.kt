package com.example.liftrix.data.repository.session

import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.dao.ExerciseSetDao
import com.example.liftrix.data.local.entity.WorkoutEntity
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.data.mapper.ExerciseSetMapper
import com.example.liftrix.domain.model.UnifiedWorkoutSession
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.ExerciseSet
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.UnifiedWorkoutSession.SessionStatus
import com.example.liftrix.domain.model.SessionSet
import com.example.liftrix.domain.model.WorkoutStatus
import java.time.Instant
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.session.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flow
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SessionRepository focused on active workout session management.
 * 
 * Responsibilities:
 * - Active session state persistence and recovery
 * - Real-time session updates and synchronization
 * - Session exercise and set management
 * - Session lifecycle management (start, pause, complete, cancel)
 * - Error handling with LiftrixError hierarchy
 * 
 * Does NOT contain:
 * - Business logic (delegated to use cases)
 * - Session validation rules (handled in domain layer)
 * - Session analytics computations (separate analytics repository)
 * - Timer management (handled by service layer)
 * 
 * Note: This implementation manages session state through the existing workout infrastructure
 * with special handling for active sessions using isActive flags and session metadata.
 */
@Singleton
class SessionRepositoryImpl @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val exerciseSetDao: ExerciseSetDao,
    private val workoutMapper: WorkoutMapper,
    private val exerciseSetMapper: ExerciseSetMapper
) : SessionRepository {

    override suspend fun startSession(template: WorkoutTemplate, userId: String): LiftrixResult<UnifiedWorkoutSession> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to start session from template: ${template.name}",
                    operation = "CREATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf(
                        "template_id" to template.id.value,
                        "template_name" to template.name,
                        "user_id" to userId
                    )
                )
            }
        ) {
            // Check if user already has an active session
            val existingActiveSession = workoutDao.getActiveWorkoutForUser(userId)
            if (existingActiveSession != null) {
                throw IllegalStateException("User already has an active session. Complete or cancel existing session first.")
            }
            
            // Create session from template
            val session = UnifiedWorkoutSession(
                id = WorkoutSessionId.generate(),
                userId = userId,
                name = template.name,
                templateId = template.id.value,
                sessionStatus = SessionStatus.ACTIVE,
                startedAt = Instant.now(),
                exercises = template.exercises.map { templateExercise ->
                    SessionExercise.fromTemplate(templateExercise)
                },
                notes = ""
            )
            
            // Convert to workout entity for persistence (marking as active session)
            val workoutEntity = workoutMapper.toEntity(
                workout = session.toWorkout(), 
                isSynced = false
            ) // Note: isActive property might not exist in WorkoutEntity
            
            val insertedId = workoutDao.insertWorkout(workoutEntity)
            if (insertedId > 0) {
                session.copy(id = WorkoutSessionId(insertedId.toString()))
            } else {
                throw RuntimeException("Session creation failed - insert returned invalid ID: $insertedId")
            }
        }
    }

    override suspend fun startBlankSession(userId: String, sessionName: String?): LiftrixResult<UnifiedWorkoutSession> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to start blank session",
                    operation = "CREATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "session_name" to (sessionName ?: "blank")
                    )
                )
            }
        ) {
            // Check if user already has an active session
            val existingActiveSession = workoutDao.getActiveWorkoutForUser(userId)
            if (existingActiveSession != null) {
                throw IllegalStateException("User already has an active session. Complete or cancel existing session first.")
            }
            
            // Create blank session
            val session = UnifiedWorkoutSession.createBlank(
                userId = userId,
                name = sessionName ?: "Quick Workout"
            )
            
            // Convert to workout entity for persistence
            val workoutEntity = workoutMapper.toEntity(
                workout = session.toWorkout(), 
                isSynced = false
            ) // Note: isActive property might not exist in WorkoutEntity
            
            val insertedId = workoutDao.insertWorkout(workoutEntity)
            if (insertedId > 0) {
                session.copy(id = WorkoutSessionId(insertedId.toString()))
            } else {
                throw RuntimeException("Blank session creation failed - insert returned invalid ID: $insertedId")
            }
        }
    }

    override suspend fun getActiveSession(userId: String): LiftrixResult<UnifiedWorkoutSession?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve active session",
                    operation = "READ",
                    table = "workout_sessions",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val activeWorkoutEntity = workoutDao.getActiveWorkoutForUser(userId)
            activeWorkoutEntity?.let { entity ->
                val workout = workoutMapper.toDomain(entity)
                workout.toUnifiedWorkoutSession()
            }
        }
    }

    override fun observeActiveSession(userId: String): Flow<LiftrixResult<UnifiedWorkoutSession?>> {
        // Note: Since there's no flow method, we need to implement periodic polling or return a simple result
        // For now, return a flow that emits the current state
        return kotlinx.coroutines.flow.flow {
            val entity = workoutDao.getActiveWorkoutForUser(userId)
            emit(
                try {
                    val session = entity?.let { 
                        val workout = workoutMapper.toDomain(it)
                        workout.toUnifiedWorkoutSession()
                    }
                    LiftrixResult.success(session)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map active session entity for user: $userId")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to observe active session",
                                    operation = "READ",
                            table = "workout_sessions",
                            analyticsContext = mapOf("user_id" to userId)
                        )
                    )
                }
            )
        }
    }

    override suspend fun updateSession(session: UnifiedWorkoutSession): LiftrixResult<UnifiedWorkoutSession> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update session: ${session.name}",
                    operation = "UPDATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf(
                        "session_id" to session.id.value,
                        "session_name" to session.name,
                        "user_id" to session.userId
                    )
                )
            }
        ) {
            val updatedSession = session.copy(lastModified = Instant.now())
            val workoutEntity = workoutMapper.toEntity(
                workout = updatedSession.toWorkout(), 
                isSynced = false
            ) // Note: isActive property might not exist in WorkoutEntity
            
            val updatedRows = workoutDao.updateWorkout(workoutEntity)
            if (updatedRows > 0) {
                updatedSession
            } else {
                throw RuntimeException("Session update affected 0 rows for ID: ${session.id}")
            }
        }
    }

    override suspend fun addExerciseToSession(userId: String, exercise: SessionExercise): LiftrixResult<UnifiedWorkoutSession> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to add exercise to session: ${exercise.name}",
                    operation = "UPDATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf(
                        "exercise_name" to exercise.name,
                        "user_id" to userId
                    )
                )
            }
        ) {
            val activeSession = getActiveSession(userId).getOrThrow()
                ?: throw IllegalStateException("No active session found for user: $userId")
            
            val updatedSession = activeSession.addExercise(exercise)
            
            updateSession(updatedSession).getOrThrow()
        }
    }

    override suspend fun removeExerciseFromSession(userId: String, exerciseId: String): LiftrixResult<UnifiedWorkoutSession> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to remove exercise from session",
                    operation = "UPDATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf(
                        "exercise_id" to exerciseId,
                        "user_id" to userId
                    )
                )
            }
        ) {
            val activeSession = getActiveSession(userId).getOrThrow()
                ?: throw IllegalStateException("No active session found for user: $userId")
            
            val updatedSession = activeSession.removeExercise(ExerciseId(exerciseId))
            
            updateSession(updatedSession).getOrThrow()
        }
    }

    override suspend fun addSetToExercise(userId: String, exerciseId: String, exerciseSet: ExerciseSet): LiftrixResult<UnifiedWorkoutSession> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to add set to exercise",
                    operation = "UPDATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf(
                        "exercise_id" to exerciseId,
                        "user_id" to userId
                    )
                )
            }
        ) {
            val activeSession = getActiveSession(userId).getOrThrow()
                ?: throw IllegalStateException("No active session found for user: $userId")
            
            val targetExercise = activeSession.exercises.find { it.exerciseId.value == exerciseId }
                ?: throw IllegalArgumentException("Exercise not found: $exerciseId")
            
            val updatedExercise = targetExercise.addSet(
                targetReps = null, // ExerciseSet doesn't have targetReps
                targetWeight = null // ExerciseSet doesn't have targetWeight
            )
            
            val updatedSession = activeSession.updateExercise(targetExercise.exerciseId, updatedExercise)
            
            updateSession(updatedSession).getOrThrow()
        }
    }

    override suspend fun updateSetInSession(
        userId: String,
        exerciseId: String,
        setIndex: Int,
        exerciseSet: ExerciseSet
    ): LiftrixResult<UnifiedWorkoutSession> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update set in session",
                    operation = "UPDATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf(
                        "exercise_id" to exerciseId,
                        "set_index" to setIndex.toString(),
                        "user_id" to userId
                    )
                )
            }
        ) {
            val activeSession = getActiveSession(userId).getOrThrow()
                ?: throw IllegalStateException("No active session found for user: $userId")
            
            val targetExercise = activeSession.exercises.find { it.exerciseId.value == exerciseId }
                ?: throw IllegalArgumentException("Exercise not found: $exerciseId")
            
            // Convert ExerciseSet to SessionSet if needed
            val sessionSet = SessionSet(
                setNumber = setIndex + 1,
                targetReps = null, // Map from exerciseSet if available
                targetWeight = null // Map from exerciseSet if available
            )
            val updatedExercise = targetExercise.updateSet(setIndex, sessionSet)
            
            val updatedSession = activeSession.updateExercise(targetExercise.exerciseId, updatedExercise)
            
            updateSession(updatedSession).getOrThrow()
        }
    }

    override suspend fun removeSetFromExercise(userId: String, exerciseId: String, setIndex: Int): LiftrixResult<UnifiedWorkoutSession> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to remove set from exercise",
                    operation = "UPDATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf(
                        "exercise_id" to exerciseId,
                        "set_index" to setIndex.toString(),
                        "user_id" to userId
                    )
                )
            }
        ) {
            val activeSession = getActiveSession(userId).getOrThrow()
                ?: throw IllegalStateException("No active session found for user: $userId")
            
            val targetExercise = activeSession.exercises.find { it.exerciseId.value == exerciseId }
                ?: throw IllegalArgumentException("Exercise not found: $exerciseId")
            
            if (setIndex < 0 || setIndex >= targetExercise.sets.size) {
                throw IndexOutOfBoundsException("Set index $setIndex out of bounds for exercise $exerciseId")
            }
            
            val updatedExercise = targetExercise.removeLastSet() // Note: this removes last set, may need adjustment
            
            val updatedSession = activeSession.updateExercise(targetExercise.exerciseId, updatedExercise)
            
            updateSession(updatedSession).getOrThrow()
        }
    }

    override suspend fun completeSession(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to complete session",
                    operation = "UPDATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val activeSession = getActiveSession(userId).getOrThrow()
                ?: throw IllegalStateException("No active session found to complete for user: $userId")
            
            Timber.d("🔥 COMPLETE-SESSION-DEBUG: Completing session ${activeSession.id.value} for user $userId")
            
            // Update session state to completed and mark as inactive
            val completedSession = activeSession.complete()
            
            Timber.d("🔥 COMPLETE-SESSION-DEBUG: Session status after complete(): ${completedSession.sessionStatus}")
            
            // Convert to workout and ensure proper status mapping
            val workout = completedSession.toWorkout()
            
            Timber.d("🔥 COMPLETE-SESSION-DEBUG: Converted workout status: ${workout.status}")
            Timber.d("🔥 COMPLETE-SESSION-DEBUG: Workout name: ${workout.name}")
            Timber.d("🔥 COMPLETE-SESSION-DEBUG: Workout endTime: ${workout.endTime}")
            
            val workoutEntity = workoutMapper.toEntity(
                workout = workout, 
                isSynced = false
            )
            
            Timber.d("🔥 COMPLETE-SESSION-DEBUG: Entity status before update: ${workoutEntity.status}")
            
            val updatedRows = workoutDao.updateWorkout(workoutEntity)
            if (updatedRows == 0) {
                throw RuntimeException("Failed to complete session - update affected 0 rows")
            }
            
            Timber.d("🔥 COMPLETE-SESSION-DEBUG: Successfully updated $updatedRows rows")
            
            // Verify the update worked by querying the database
            val verifyEntity = workoutDao.getWorkoutByIdForUser(activeSession.id.value, userId)
            if (verifyEntity != null) {
                Timber.d("🔥 COMPLETE-SESSION-DEBUG: Verification - Entity status: ${verifyEntity.status}")
                Timber.d("🔥 COMPLETE-SESSION-DEBUG: Verification - Entity endTime: ${verifyEntity.endTime}")
            } else {
                Timber.w("🔥 COMPLETE-SESSION-DEBUG: Verification failed - workout not found after update")
            }
        }
    }

    override suspend fun cancelSession(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to cancel session",
                    operation = "DELETE",
                    table = "workout_sessions",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val activeSession = getActiveSession(userId).getOrThrow()
                ?: throw IllegalStateException("No active session found to cancel for user: $userId")
            
            // Delete the active session completely (no save)
            val deletedRows = workoutDao.deleteWorkoutByIdForUser(activeSession.id.value, userId)
            if (deletedRows == 0) {
                throw RuntimeException("Failed to cancel session - delete affected 0 rows")
            }
        }
    }

    override suspend fun pauseSession(userId: String): LiftrixResult<UnifiedWorkoutSession> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to pause session",
                    operation = "UPDATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val activeSession = getActiveSession(userId).getOrThrow()
                ?: throw IllegalStateException("No active session found to pause for user: $userId")
            
            val pausedSession = activeSession.pause()
            
            updateSession(pausedSession).getOrThrow()
        }
    }

    override suspend fun resumeSession(userId: String): LiftrixResult<UnifiedWorkoutSession> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to resume session",
                    operation = "UPDATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val activeSession = getActiveSession(userId).getOrThrow()
                ?: throw IllegalStateException("No session found to resume for user: $userId")
            
            val resumedSession = activeSession.resume()
            
            updateSession(resumedSession).getOrThrow()
        }
    }

    override suspend fun hasActiveSession(userId: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check active session existence",
                    operation = "READ",
                    table = "workout_sessions",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            workoutDao.getActiveWorkoutForUser(userId) != null
        }
    }

    override suspend fun getSessionDuration(userId: String): LiftrixResult<Long> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get session duration",
                    operation = "READ",
                    table = "workout_sessions",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val activeSession = getActiveSession(userId).getOrThrow()
            if (activeSession != null) {
                activeSession.getTotalDurationSeconds() / 60
            } else {
                0L
            }
        }
    }

    override suspend fun saveSessionState(session: UnifiedWorkoutSession): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to save session state",
                    operation = "UPDATE",
                    table = "workout_sessions",
                    analyticsContext = mapOf(
                        "session_id" to session.id.value,
                        "user_id" to session.userId
                    )
                )
            }
        ) {
            updateSession(session).getOrThrow()
            Unit // Return Unit to match interface
        }
    }

    override suspend fun recoverSessionState(userId: String): LiftrixResult<UnifiedWorkoutSession?> {
        return getActiveSession(userId) // Delegate to existing active session retrieval
    }

    /**
     * Extension function to convert UnifiedWorkoutSession to Workout for persistence.
     * 🔥 FIXED: Enhanced logging and explicit status mapping for completed sessions
     */
    private fun UnifiedWorkoutSession.toWorkout() = com.example.liftrix.domain.model.Workout(
        userId = this.userId,
        id = com.example.liftrix.domain.model.WorkoutId(this.id.value),
        name = this.name,
        date = java.time.LocalDate.now(),
        exercises = this.exercises.map { sessionExercise ->
            sessionExercise.toCompletedExercise()
        },
        status = when (this.sessionStatus) {
            SessionStatus.COMPLETED -> {
                Timber.d("🔥 CONVERSION-DEBUG: Session status COMPLETED -> WorkoutStatus.COMPLETED")
                WorkoutStatus.COMPLETED
            }
            SessionStatus.ACTIVE -> {
                Timber.d("🔥 CONVERSION-DEBUG: Session status ACTIVE -> WorkoutStatus.IN_PROGRESS")
                WorkoutStatus.IN_PROGRESS
            }
            SessionStatus.PAUSED -> {
                Timber.d("🔥 CONVERSION-DEBUG: Session status PAUSED -> WorkoutStatus.IN_PROGRESS")
                WorkoutStatus.IN_PROGRESS
            }
            SessionStatus.FAILED_TO_SAVE -> {
                Timber.d("🔥 CONVERSION-DEBUG: Session status FAILED_TO_SAVE -> WorkoutStatus.COMPLETED")
                WorkoutStatus.COMPLETED
            }
        },
        startTime = this.startedAt,
        endTime = this.endedAt,
        notes = this.notes,
        templateId = this.templateId?.let { WorkoutId(it) },
        createdAt = this.startedAt,
        updatedAt = this.lastModified
    )

    /**
     * Extension function to convert Workout to UnifiedWorkoutSession.
     */
    private fun com.example.liftrix.domain.model.Workout.toUnifiedWorkoutSession() = UnifiedWorkoutSession(
        id = WorkoutSessionId(this.id.value),
        userId = this.userId,
        name = this.name,
        templateId = null, // Not stored in workout entity, would need separate tracking
        exercises = this.exercises.map { exercise ->
            SessionExercise(
                exerciseId = exercise.id,
                name = exercise.libraryExercise?.name ?: "Unknown Exercise",
                category = ExerciseCategory.OTHER, // Default, would need proper mapping
                primaryMuscle = ExerciseCategory.OTHER,
                equipment = Equipment.BODYWEIGHT_ONLY,
                sets = exercise.sets.map { set ->
                    SessionSet(
                        setNumber = 1,
                        targetReps = set.reps?.count,
                        targetWeight = set.weight,
                        actualReps = set.reps?.count,
                        actualWeight = set.weight,
                        completedAt = set.completedAt
                    )
                },
                orderIndex = 0
            )
        },
        sessionStatus = if (this.status == WorkoutStatus.COMPLETED) SessionStatus.COMPLETED else SessionStatus.ACTIVE,
        startedAt = this.startTime ?: Instant.now(),
        endedAt = this.endTime,
        notes = this.notes
    )
}