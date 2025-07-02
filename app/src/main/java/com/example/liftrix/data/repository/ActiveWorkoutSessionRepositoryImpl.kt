package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.ActiveWorkoutSessionDao
import com.example.liftrix.data.local.dao.WorkoutDao
import com.example.liftrix.data.local.entity.ActiveWorkoutSessionEntity
import com.example.liftrix.data.mapper.ActiveWorkoutSessionMapper
import com.example.liftrix.data.mapper.WorkoutMapper
import com.example.liftrix.domain.model.ActiveWorkoutSession
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.domain.repository.ActiveWorkoutSessionRepository
import com.example.liftrix.domain.repository.SessionStatistics
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ActiveWorkoutSessionRepository using Room database.
 * Handles local persistence and real-time updates for active workout sessions.
 */
@Singleton
class ActiveWorkoutSessionRepositoryImpl @Inject constructor(
    private val activeWorkoutSessionDao: ActiveWorkoutSessionDao,
    private val workoutDao: WorkoutDao,
    private val sessionMapper: ActiveWorkoutSessionMapper,
    private val workoutMapper: WorkoutMapper
) : ActiveWorkoutSessionRepository {

    override suspend fun createSession(session: ActiveWorkoutSession): Result<ActiveWorkoutSession> {
        return try {
            val entity = sessionMapper.toEntity(session)
            activeWorkoutSessionDao.insertSession(entity)
            Timber.i("Active workout session created: ${session.id}")
            Result.success(session)
        } catch (exception: Exception) {
            Timber.e(exception, "Error creating active workout session")
            Result.failure(exception)
        }
    }

    override suspend fun getSessionById(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession?> {
        return try {
            val entity = activeWorkoutSessionDao.getSessionById(sessionId.value)
            val session = entity?.let { sessionMapper.toDomain(it) }
            Result.success(session)
        } catch (exception: Exception) {
            Timber.e(exception, "Error getting session by ID: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun getCurrentSessionForUser(userId: String): Result<ActiveWorkoutSession?> {
        return try {
            val entity = activeWorkoutSessionDao.getCurrentSessionForUser(userId)
            val session = entity?.let { sessionMapper.toDomain(it) }
            Result.success(session)
        } catch (exception: Exception) {
            Timber.e(exception, "Error getting current session for user: $userId")
            Result.failure(exception)
        }
    }

    override fun getCurrentSessionForUserFlow(userId: String): Flow<ActiveWorkoutSession?> {
        return activeWorkoutSessionDao.getCurrentSessionForUserFlow(userId)
            .map { entity -> entity?.let { sessionMapper.toDomain(it) } }
    }

    override suspend fun updateSession(session: ActiveWorkoutSession): Result<ActiveWorkoutSession> {
        return try {
            val entity = sessionMapper.toEntity(session)
            activeWorkoutSessionDao.updateSession(entity)
            Timber.d("Active workout session updated: ${session.id}")
            Result.success(session)
        } catch (exception: Exception) {
            Timber.e(exception, "Error updating active workout session")
            Result.failure(exception)
        }
    }

    override suspend fun saveSessionChanges(session: ActiveWorkoutSession): Result<Unit> {
        return try {
            val entity = sessionMapper.toEntity(session.copy(lastModified = Instant.now()))
            activeWorkoutSessionDao.updateSession(entity)
            activeWorkoutSessionDao.updateAutoSaveTimestamp(session.id.value, Instant.now())
            Timber.d("Session changes saved: ${session.id}")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Error saving session changes")
            Result.failure(exception)
        }
    }

    override suspend fun pauseSession(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow() 
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val pausedSession = session.pause()
            updateSession(pausedSession)
            
            Timber.i("Session paused: $sessionId")
            Result.success(pausedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error pausing session: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun resumeSession(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val resumedSession = session.resume()
            updateSession(resumedSession)
            
            Timber.i("Session resumed: $sessionId")
            Result.success(resumedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error resuming session: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun startRest(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val restSession = session.startRest()
            updateSession(restSession)
            
            Timber.d("Rest started for session: $sessionId")
            Result.success(restSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error starting rest for session: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun endRest(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val activeSession = session.endRest()
            updateSession(activeSession)
            
            Timber.d("Rest ended for session: $sessionId")
            Result.success(activeSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error ending rest for session: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun moveToNextExercise(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val updatedSession = session.moveToNextExercise()
            updateSession(updatedSession)
            
            Timber.d("Moved to next exercise in session: $sessionId")
            Result.success(updatedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error moving to next exercise: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun moveToPreviousExercise(sessionId: WorkoutSessionId): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val updatedSession = session.moveToPreviousExercise()
            updateSession(updatedSession)
            
            Timber.d("Moved to previous exercise in session: $sessionId")
            Result.success(updatedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error moving to previous exercise: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun moveToExercise(sessionId: WorkoutSessionId, exerciseIndex: Int): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            require(exerciseIndex in 0 until session.exercises.size) { 
                "Invalid exercise index: $exerciseIndex" 
            }
            
            val updatedSession = session.copy(
                currentExerciseIndex = exerciseIndex,
                sessionState = ActiveWorkoutSession.SessionState.ACTIVE,
                lastModified = Instant.now()
            )
            updateSession(updatedSession)
            
            Timber.d("Moved to exercise $exerciseIndex in session: $sessionId")
            Result.success(updatedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error moving to exercise $exerciseIndex: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun addExerciseToSession(
        sessionId: WorkoutSessionId,
        exercise: SessionExercise
    ): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val updatedSession = session.addExercise(exercise)
            updateSession(updatedSession)
            
            Timber.i("Exercise added to session: ${exercise.name} -> $sessionId")
            Result.success(updatedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error adding exercise to session: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun removeExerciseFromSession(
        sessionId: WorkoutSessionId,
        exerciseId: ExerciseId
    ): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val updatedSession = session.removeExercise(exerciseId)
            updateSession(updatedSession)
            
            Timber.i("Exercise removed from session: $exerciseId -> $sessionId")
            Result.success(updatedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error removing exercise from session: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun updateExerciseInSession(
        sessionId: WorkoutSessionId,
        exerciseId: ExerciseId,
        updatedExercise: SessionExercise
    ): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val updatedSession = session.updateExercise(exerciseId, updatedExercise)
            updateSession(updatedSession)
            
            Timber.d("Exercise updated in session: $exerciseId -> $sessionId")
            Result.success(updatedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error updating exercise in session: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun completeSet(
        sessionId: WorkoutSessionId,
        exerciseId: ExerciseId,
        setIndex: Int,
        actualReps: Int?,
        actualWeight: Weight?,
        actualRpe: Int?
    ): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val exercise = session.exercises.find { it.exerciseId == exerciseId }
                ?: throw IllegalStateException("Exercise not found: $exerciseId")
            
            val updatedExercise = exercise.completeSet(setIndex, actualReps, actualWeight, null, null, actualRpe)
            val updatedSession = session.updateExercise(exerciseId, updatedExercise)
            
            updateSession(updatedSession)
            
            Timber.d("Set completed: $exerciseId set $setIndex in session $sessionId")
            Result.success(updatedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error completing set: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun uncompleteSet(
        sessionId: WorkoutSessionId,
        exerciseId: ExerciseId,
        setIndex: Int
    ): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val exercise = session.exercises.find { it.exerciseId == exerciseId }
                ?: throw IllegalStateException("Exercise not found: $exerciseId")
            
            val updatedExercise = exercise.uncompleteSet(setIndex)
            val updatedSession = session.updateExercise(exerciseId, updatedExercise)
            
            updateSession(updatedSession)
            
            Timber.d("Set uncompleted: $exerciseId set $setIndex in session $sessionId")
            Result.success(updatedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error uncompleting set: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun addSetToExercise(
        sessionId: WorkoutSessionId,
        exerciseId: ExerciseId
    ): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val exercise = session.exercises.find { it.exerciseId == exerciseId }
                ?: throw IllegalStateException("Exercise not found: $exerciseId")
            
            val updatedExercise = exercise.addSet()
            val updatedSession = session.updateExercise(exerciseId, updatedExercise)
            
            updateSession(updatedSession)
            
            Timber.d("Set added to exercise: $exerciseId in session $sessionId")
            Result.success(updatedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error adding set to exercise: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun removeLastSetFromExercise(
        sessionId: WorkoutSessionId,
        exerciseId: ExerciseId
    ): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val exercise = session.exercises.find { it.exerciseId == exerciseId }
                ?: throw IllegalStateException("Exercise not found: $exerciseId")
            
            val updatedExercise = exercise.removeLastSet()
            val updatedSession = session.updateExercise(exerciseId, updatedExercise)
            
            updateSession(updatedSession)
            
            Timber.d("Last set removed from exercise: $exerciseId in session $sessionId")
            Result.success(updatedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error removing last set from exercise: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun updateSessionNotes(
        sessionId: WorkoutSessionId,
        notes: String?
    ): Result<ActiveWorkoutSession> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            val updatedSession = session.updateNotes(notes)
            updateSession(updatedSession)
            
            Timber.d("Session notes updated: $sessionId")
            Result.success(updatedSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error updating session notes: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun completeSession(sessionId: WorkoutSessionId): Result<Workout> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            // Convert session to completed workout
            val completedWorkout = session.toCompletedWorkout()
            
            // Save the completed workout
            val workoutEntity = workoutMapper.toEntity(completedWorkout)
            workoutDao.insertWorkout(workoutEntity)
            
            // Delete the active session
            activeWorkoutSessionDao.deleteSession(sessionMapper.toEntity(session))
            
            Timber.i("Session completed and converted to workout: $sessionId -> ${completedWorkout.id}")
            Result.success(completedWorkout)
        } catch (exception: Exception) {
            Timber.e(exception, "Error completing session: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun cancelSession(sessionId: WorkoutSessionId): Result<Unit> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            activeWorkoutSessionDao.deleteSession(sessionMapper.toEntity(session))
            
            Timber.i("Session cancelled: $sessionId")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Error cancelling session: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun deleteSession(sessionId: WorkoutSessionId): Result<Unit> {
        return try {
            val session = getSessionById(sessionId).getOrThrow()
                ?: throw IllegalStateException("Session not found: $sessionId")
            
            activeWorkoutSessionDao.deleteSession(sessionMapper.toEntity(session))
            
            Timber.d("Session deleted: $sessionId")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Error deleting session: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun getAllSessionsForUser(userId: String): Result<List<ActiveWorkoutSession>> {
        return try {
            val entities = activeWorkoutSessionDao.getAllSessionsForUser(userId)
            val sessions = entities.map { sessionMapper.toDomain(it) }
            Result.success(sessions)
        } catch (exception: Exception) {
            Timber.e(exception, "Error getting all sessions for user: $userId")
            Result.failure(exception)
        }
    }

    override suspend fun getSessionsNeedingAutoSave(cutoffTime: Instant): Result<List<ActiveWorkoutSession>> {
        return try {
            val entities = activeWorkoutSessionDao.getSessionsNeedingAutoSave(cutoffTime)
            val sessions = entities.map { sessionMapper.toDomain(it) }
            Result.success(sessions)
        } catch (exception: Exception) {
            Timber.e(exception, "Error getting sessions needing auto save")
            Result.failure(exception)
        }
    }

    override suspend fun getStaleActiveSessions(cutoffTime: Instant): Result<List<ActiveWorkoutSession>> {
        return try {
            val entities = activeWorkoutSessionDao.getStaleActiveSessions(cutoffTime)
            val sessions = entities.map { sessionMapper.toDomain(it) }
            Result.success(sessions)
        } catch (exception: Exception) {
            Timber.e(exception, "Error getting stale active sessions")
            Result.failure(exception)
        }
    }

    override suspend fun updateAutoSaveTimestamp(sessionId: WorkoutSessionId): Result<Unit> {
        return try {
            activeWorkoutSessionDao.updateAutoSaveTimestamp(sessionId.value, Instant.now())
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Error updating auto save timestamp: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun updateRecoveryData(
        sessionId: WorkoutSessionId,
        recoveryData: String?
    ): Result<Unit> {
        return try {
            activeWorkoutSessionDao.updateRecoveryData(sessionId.value, recoveryData, Instant.now())
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Error updating recovery data: $sessionId")
            Result.failure(exception)
        }
    }

    override suspend fun hasActiveSession(userId: String): Result<Boolean> {
        return try {
            val hasSession = activeWorkoutSessionDao.hasActiveSession(userId)
            Result.success(hasSession)
        } catch (exception: Exception) {
            Timber.e(exception, "Error checking if user has active session: $userId")
            Result.failure(exception)
        }
    }

    override suspend fun countActiveSessionsForUser(userId: String): Result<Int> {
        return try {
            val count = activeWorkoutSessionDao.countActiveSessionsForUser(userId)
            Result.success(count)
        } catch (exception: Exception) {
            Timber.e(exception, "Error counting active sessions for user: $userId")
            Result.failure(exception)
        }
    }

    override suspend fun getSessionsByTemplate(templateId: String): Result<List<ActiveWorkoutSession>> {
        return try {
            val entities = activeWorkoutSessionDao.getSessionsByTemplate(templateId)
            val sessions = entities.map { sessionMapper.toDomain(it) }
            Result.success(sessions)
        } catch (exception: Exception) {
            Timber.e(exception, "Error getting sessions by template: $templateId")
            Result.failure(exception)
        }
    }

    override suspend fun getSessionsByState(
        userId: String,
        state: ActiveWorkoutSession.SessionState
    ): Result<List<ActiveWorkoutSession>> {
        return try {
            val stateString = when (state) {
                ActiveWorkoutSession.SessionState.ACTIVE -> ActiveWorkoutSessionEntity.STATE_ACTIVE
                ActiveWorkoutSession.SessionState.PAUSED -> ActiveWorkoutSessionEntity.STATE_PAUSED
                ActiveWorkoutSession.SessionState.REST -> ActiveWorkoutSessionEntity.STATE_REST
            }
            
            val entities = activeWorkoutSessionDao.getSessionsByState(userId, stateString)
            val sessions = entities.map { sessionMapper.toDomain(it) }
            Result.success(sessions)
        } catch (exception: Exception) {
            Timber.e(exception, "Error getting sessions by state: $state for user $userId")
            Result.failure(exception)
        }
    }

    override suspend fun getSessionsInDateRange(
        userId: String,
        startDate: Instant,
        endDate: Instant
    ): Result<List<ActiveWorkoutSession>> {
        return try {
            val entities = activeWorkoutSessionDao.getSessionsInDateRange(userId, startDate, endDate)
            val sessions = entities.map { sessionMapper.toDomain(it) }
            Result.success(sessions)
        } catch (exception: Exception) {
            Timber.e(exception, "Error getting sessions in date range for user: $userId")
            Result.failure(exception)
        }
    }

    override suspend fun cleanupOldSessions(cutoffTime: Instant): Result<Int> {
        return try {
            val deletedCount = activeWorkoutSessionDao.deleteSessionsOlderThan(cutoffTime)
            Timber.i("Cleaned up $deletedCount old sessions")
            Result.success(deletedCount)
        } catch (exception: Exception) {
            Timber.e(exception, "Error cleaning up old sessions")
            Result.failure(exception)
        }
    }

    override suspend fun getSessionStatistics(userId: String): Result<SessionStatistics> {
        return try {
            val stats = activeWorkoutSessionDao.getSessionStatistics(userId)
            val sessionStats = stats?.let {
                SessionStatistics(
                    totalSessions = it.totalSessions,
                    pauseRate = it.pauseRate,
                    avgPauseDuration = it.avgPauseDuration,
                    lastSessionTime = it.lastSessionTime,
                    avgSessionDuration = 0.0, // Would need additional calculation
                    mostUsedTemplateId = null, // Would need additional query
                    avgExercisesPerSession = 0.0, // Would need additional calculation
                    completionRate = 0.0 // Would need additional calculation
                )
            } ?: SessionStatistics(
                totalSessions = 0,
                pauseRate = 0.0,
                avgPauseDuration = 0.0,
                lastSessionTime = null,
                avgSessionDuration = 0.0,
                mostUsedTemplateId = null,
                avgExercisesPerSession = 0.0,
                completionRate = 0.0
            )
            
            Result.success(sessionStats)
        } catch (exception: Exception) {
            Timber.e(exception, "Error getting session statistics for user: $userId")
            Result.failure(exception)
        }
    }

    override suspend fun syncSessions(userId: String): Result<Unit> {
        return try {
            // TODO: Implement sync with remote backend
            Timber.d("Session sync not yet implemented for user: $userId")
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Error syncing sessions for user: $userId")
            Result.failure(exception)
        }
    }

    override suspend fun getUnsyncedSessions(): Result<List<ActiveWorkoutSession>> {
        return try {
            val entities = activeWorkoutSessionDao.getUnsyncedSessions()
            val sessions = entities.map { sessionMapper.toDomain(it) }
            Result.success(sessions)
        } catch (exception: Exception) {
            Timber.e(exception, "Error getting unsynced sessions")
            Result.failure(exception)
        }
    }

    override suspend fun updateSyncStatus(
        sessionId: WorkoutSessionId,
        isSynced: Boolean,
        syncVersion: Int
    ): Result<Unit> {
        return try {
            activeWorkoutSessionDao.updateSyncStatus(sessionId.value, isSynced, syncVersion)
            Result.success(Unit)
        } catch (exception: Exception) {
            Timber.e(exception, "Error updating sync status for session: $sessionId")
            Result.failure(exception)
        }
    }
}