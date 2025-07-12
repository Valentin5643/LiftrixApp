package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.WorkoutTemplateEntity
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.TemplateExercise
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between WorkoutTemplate domain model and WorkoutTemplateEntity
 */
@Singleton
class WorkoutTemplateMapper @Inject constructor() {
    
    private val json = Json { 
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    
    /**
     * Converts WorkoutTemplateEntity to WorkoutTemplate domain model
     */
    fun toDomain(entity: WorkoutTemplateEntity): WorkoutTemplate {
        val exercises = try {
            if (entity.templateExercisesJson.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString<List<TemplateExercise>>(entity.templateExercisesJson)
            }
        } catch (e: Exception) {
            // Log error and return empty list to prevent app crash
            timber.log.Timber.w(e, "Failed to deserialize template exercises for template ${entity.id}")
            emptyList()
        }
        
        return WorkoutTemplate(
            id = WorkoutTemplateId(entity.id),
            userId = entity.userId,
            name = entity.name,
            description = entity.description,
            exercises = exercises,
            estimatedDurationMinutes = entity.estimatedDurationMinutes,
            difficultyLevel = entity.difficultyLevel,
            folderId = entity.folderId,
            usageCount = entity.usageCount,
            lastUsedAt = entity.lastUsedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
    
    /**
     * Converts WorkoutTemplate domain model to WorkoutTemplateEntity
     */
    fun toEntity(domain: WorkoutTemplate, isSynced: Boolean = false, syncVersion: Int = 1): WorkoutTemplateEntity {
        val exercisesJson = try {
            if (domain.exercises.isEmpty()) {
                ""
            } else {
                json.encodeToString(domain.exercises)
            }
        } catch (e: Exception) {
            // Log error and use empty JSON to prevent data loss
            timber.log.Timber.e(e, "Failed to serialize template exercises for template ${domain.id}")
            ""
        }
        
        return WorkoutTemplateEntity(
            id = domain.id.value,
            userId = domain.userId,
            name = domain.name,
            description = domain.description,
            templateExercisesJson = exercisesJson,
            estimatedDurationMinutes = domain.estimatedDurationMinutes,
            difficultyLevel = domain.difficultyLevel,
            folderId = domain.folderId,
            usageCount = domain.usageCount,
            lastUsedAt = domain.lastUsedAt,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            isSynced = isSynced,
            syncVersion = syncVersion
        )
    }
    
    /**
     * Converts list of entities to domain models
     */
    fun toDomainList(entities: List<WorkoutTemplateEntity>): List<WorkoutTemplate> {
        return entities.map { toDomain(it) }
    }
    
    /**
     * Converts list of domain models to entities
     */
    fun toEntityList(domainList: List<WorkoutTemplate>, isSynced: Boolean = false): List<WorkoutTemplateEntity> {
        return domainList.map { toEntity(it, isSynced) }
    }
    
    /**
     * Updates an entity with new domain data while preserving sync information
     */
    fun updateEntity(
        existingEntity: WorkoutTemplateEntity,
        updatedDomain: WorkoutTemplate
    ): WorkoutTemplateEntity {
        val exercisesJson = try {
            if (updatedDomain.exercises.isEmpty()) {
                ""
            } else {
                json.encodeToString(updatedDomain.exercises)
            }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to serialize updated template exercises for template ${updatedDomain.id}")
            existingEntity.templateExercisesJson // Keep existing JSON on error
        }
        
        return existingEntity.copy(
            name = updatedDomain.name,
            description = updatedDomain.description,
            templateExercisesJson = exercisesJson,
            estimatedDurationMinutes = updatedDomain.estimatedDurationMinutes,
            difficultyLevel = updatedDomain.difficultyLevel,
            folderId = updatedDomain.folderId,
            usageCount = updatedDomain.usageCount,
            lastUsedAt = updatedDomain.lastUsedAt,
            updatedAt = updatedDomain.updatedAt,
            isSynced = false, // Mark as unsynced when updated
            syncVersion = existingEntity.syncVersion + 1
        )
    }
}