package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.WorkoutTemplateEntity
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.Reps
import com.example.liftrix.domain.model.TemplateExercise
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
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
        encodeDefaults = true
    }
    
    /**
     * Converts WorkoutTemplateEntity to WorkoutTemplate domain model
     */
    fun toDomain(entity: WorkoutTemplateEntity): WorkoutTemplate {
        val exercises = try {
            if (entity.templateExercisesJson.isBlank()) {
                emptyList()
            } else {
                normalizeTemplateExercises(
                    templateId = entity.id,
                    exercises = json.decodeFromString<List<TemplateExercise>>(entity.templateExercisesJson),
                    source = "WorkoutTemplateMapper.toDomain"
                )
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
    fun toEntity(domain: WorkoutTemplate, isSynced: Boolean = false, syncVersion: Long = 1L): WorkoutTemplateEntity {
        val exercisesJson = try {
            if (domain.exercises.isEmpty()) {
                ""
            } else {
                json.encodeToString(
                    normalizeTemplateExercises(
                        templateId = domain.id.value,
                        exercises = domain.exercises,
                        source = "WorkoutTemplateMapper.toEntity"
                    )
                )
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

    private fun normalizeTemplateExercises(
        templateId: String,
        exercises: List<TemplateExercise>,
        source: String
    ): List<TemplateExercise> {
        return exercises.mapIndexed { index, exercise ->
            val normalizedExerciseId = exercise.exerciseId.value
                .takeIf { it.isNotBlank() }
                ?.let { exercise.exerciseId }
                ?: ExerciseId.fromString(generateLegacyExerciseId(exercise.name, index))
            val normalizedTargetSets = exercise.targetSets ?: DEFAULT_TEMPLATE_SETS
            val targetReps = exercise.targetReps
            val normalizedTargetReps = targetReps
                ?.takeIf { it.count > 0 }
                ?: DEFAULT_TEMPLATE_REPS

            if (
                exercise.exerciseId.value.isBlank() ||
                exercise.targetSets == null ||
                targetReps == null ||
                targetReps.count <= 0
            ) {
                timber.log.Timber.w(
                    "EDIT-WORKOUT-DEBUG: $source normalized templateId=$templateId " +
                        "exerciseIndex=$index exerciseName='${exercise.name}' " +
                        "exerciseId='${exercise.exerciseId.value}' normalizedExerciseId='${normalizedExerciseId.value}' " +
                        "targetSets=${exercise.targetSets} targetReps=${targetReps?.count} " +
                        "normalizedTargetSets=$normalizedTargetSets normalizedTargetReps=${normalizedTargetReps.count}"
                )
            }

            exercise.copy(
                exerciseId = normalizedExerciseId,
                targetSets = normalizedTargetSets,
                targetReps = normalizedTargetReps
            )
        }
    }

    private fun generateLegacyExerciseId(name: String, index: Int): String {
        val slug = name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .takeIf { it.isNotBlank() }
            ?: "exercise"

        return "legacy_${slug}_$index"
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
                json.encodeToString(
                    normalizeTemplateExercises(
                        templateId = updatedDomain.id.value,
                        exercises = updatedDomain.exercises,
                        source = "WorkoutTemplateMapper.updateEntity"
                    )
                )
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
            syncVersion = existingEntity.syncVersion + 1,
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
    }

    private companion object {
        const val DEFAULT_TEMPLATE_SETS = 3
        val DEFAULT_TEMPLATE_REPS = Reps(1)
    }
}
