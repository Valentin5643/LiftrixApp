package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.ActiveWorkoutSessionEntity
import com.example.liftrix.data.local.entity.SessionRecoveryData
import com.example.liftrix.domain.model.ActiveWorkoutSession
import com.example.liftrix.domain.model.SessionExercise
import com.example.liftrix.domain.model.WorkoutSessionId
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between ActiveWorkoutSession domain model and ActiveWorkoutSessionEntity
 */
@Singleton
class ActiveWorkoutSessionMapper @Inject constructor(
    private val gson: Gson
) {
    
    /**
     * Converts ActiveWorkoutSessionEntity to ActiveWorkoutSession domain model
     */
    fun toDomain(entity: ActiveWorkoutSessionEntity): ActiveWorkoutSession {
        val exercises = try {
            if (entity.exercisesJson.isBlank()) {
                emptyList()
            } else {
                val exercisesType = object : TypeToken<List<SessionExercise>>() {}.type
                gson.fromJson<List<SessionExercise>>(entity.exercisesJson, exercisesType) ?: emptyList()
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to deserialize session exercises for session ${entity.id}")
            emptyList()
        }
        
        val sessionState = when (entity.sessionState) {
            ActiveWorkoutSessionEntity.STATE_ACTIVE -> ActiveWorkoutSession.SessionState.ACTIVE
            ActiveWorkoutSessionEntity.STATE_PAUSED -> ActiveWorkoutSession.SessionState.PAUSED
            ActiveWorkoutSessionEntity.STATE_REST -> ActiveWorkoutSession.SessionState.REST
            else -> {
                Timber.w("Unknown session state: ${entity.sessionState}, defaulting to ACTIVE")
                ActiveWorkoutSession.SessionState.ACTIVE
            }
        }
        
        return ActiveWorkoutSession(
            id = WorkoutSessionId(entity.id),
            userId = entity.userId,
            name = entity.name,
            templateId = entity.templateId?.let { WorkoutTemplateId(it) },
            exercises = exercises,
            currentExerciseIndex = entity.currentExerciseIndex,
            sessionState = sessionState,
            startedAt = entity.startedAt,
            pausedAt = entity.pausedAt,
            resumedAt = entity.resumedAt,
            totalPausedDuration = entity.totalPausedDuration,
            notes = entity.notes,
            lastModified = entity.lastModified
        )
    }
    
    /**
     * Converts ActiveWorkoutSession domain model to ActiveWorkoutSessionEntity
     */
    fun toEntity(domain: ActiveWorkoutSession): ActiveWorkoutSessionEntity {
        val exercisesJson = try {
            if (domain.exercises.isEmpty()) {
                ""
            } else {
                gson.toJson(domain.exercises)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to serialize session exercises for session ${domain.id}")
            ""
        }
        
        val sessionState = when (domain.sessionState) {
            ActiveWorkoutSession.SessionState.ACTIVE -> ActiveWorkoutSessionEntity.STATE_ACTIVE
            ActiveWorkoutSession.SessionState.PAUSED -> ActiveWorkoutSessionEntity.STATE_PAUSED
            ActiveWorkoutSession.SessionState.REST -> ActiveWorkoutSessionEntity.STATE_REST
        }
        
        return ActiveWorkoutSessionEntity(
            id = domain.id.value,
            userId = domain.userId,
            name = domain.name,
            templateId = domain.templateId?.value,
            exercisesJson = exercisesJson,
            currentExerciseIndex = domain.currentExerciseIndex,
            sessionState = sessionState,
            startedAt = domain.startedAt,
            pausedAt = domain.pausedAt,
            resumedAt = domain.resumedAt,
            totalPausedDuration = domain.totalPausedDuration,
            notes = domain.notes,
            lastModified = domain.lastModified,
            // Default values for additional entity fields
            restTimerStartTime = null,
            restTimerDurationSeconds = null,
            restTimerPausedAt = null,
            autoSaveEnabled = true,
            lastAutoSave = null,
            recoveryDataJson = null,
            isSynced = false,
            syncVersion = 1
        )
    }
    
    /**
     * Converts list of entities to domain models
     */
    fun toDomainList(entities: List<ActiveWorkoutSessionEntity>): List<ActiveWorkoutSession> {
        return entities.map { toDomain(it) }
    }
    
    /**
     * Converts list of domain models to entities
     */
    fun toEntityList(domainList: List<ActiveWorkoutSession>): List<ActiveWorkoutSessionEntity> {
        return domainList.map { toEntity(it) }
    }
    
    /**
     * Updates an entity with new domain data while preserving sync and recovery information
     */
    fun updateEntity(
        existingEntity: ActiveWorkoutSessionEntity,
        updatedDomain: ActiveWorkoutSession
    ): ActiveWorkoutSessionEntity {
        val newEntity = toEntity(updatedDomain)
        
        return newEntity.copy(
            // Preserve sync information
            isSynced = existingEntity.isSynced,
            syncVersion = existingEntity.syncVersion,
            // Preserve recovery data
            restTimerStartTime = existingEntity.restTimerStartTime,
            restTimerDurationSeconds = existingEntity.restTimerDurationSeconds,
            restTimerPausedAt = existingEntity.restTimerPausedAt,
            autoSaveEnabled = existingEntity.autoSaveEnabled,
            lastAutoSave = existingEntity.lastAutoSave,
            recoveryDataJson = existingEntity.recoveryDataJson
        )
    }
    
    /**
     * Creates a recovery data JSON string for additional session metadata
     */
    fun createRecoveryDataJson(
        deviceInfo: String? = null,
        appVersion: String? = null,
        completionPercentage: Float = 0f,
        totalVolume: Double = 0.0
    ): String? {
        return try {
            val recoveryData = SessionRecoveryData(
                deviceInfo = deviceInfo,
                appVersion = appVersion,
                completionPercentage = completionPercentage,
                totalVolume = totalVolume
            )
            gson.toJson(recoveryData)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create recovery data JSON")
            null
        }
    }
    
    /**
     * Parses recovery data JSON string
     */
    fun parseRecoveryData(recoveryDataJson: String?): SessionRecoveryData? {
        return try {
            if (recoveryDataJson.isNullOrBlank()) {
                null
            } else {
                gson.fromJson(recoveryDataJson, SessionRecoveryData::class.java)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to parse recovery data JSON")
            null
        }
    }
} 