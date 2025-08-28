package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.CustomExerciseEntity
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.CustomExerciseId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseType
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between CustomExercise domain model and CustomExerciseEntity
 */
@Singleton
class CustomExerciseMapper @Inject constructor() {
    
    /**
     * Converts CustomExerciseEntity to CustomExercise domain model
     */
    fun toDomain(entity: CustomExerciseEntity): CustomExercise {
        return CustomExercise(
            id = CustomExerciseId(entity.id),
            userId = entity.userId,
            name = entity.name,
            description = entity.description,
            exerciseType = entity.exerciseType,
            primaryMuscle = entity.primaryMuscleGroup,
            secondaryMuscles = entity.secondaryMuscleGroups?.toSet() ?: emptySet(),
            equipment = entity.equipment,
            difficulty = entity.difficulty,
            instructions = entity.instructions ?: emptyList(),
            mainImageUrl = entity.mainImageUrl,
            additionalImageUrls = entity.additionalImageUrls ?: emptyList(),
            videoUrl = entity.videoUrl,
            tags = entity.tags ?: emptyList(),
            categories = entity.categories ?: emptyList(),
            notes = entity.notes,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
    
    /**
     * Converts CustomExercise domain model to CustomExerciseEntity
     */
    fun toEntity(domain: CustomExercise, isSynced: Boolean = false, syncVersion: Long = 1): CustomExerciseEntity {
        return CustomExerciseEntity(
            id = domain.id.value,
            userId = domain.userId,
            name = domain.name,
            exerciseType = domain.exerciseType,
            description = domain.description,
            primaryMuscleGroup = domain.primaryMuscle,
            secondaryMuscleGroups = domain.secondaryMuscles.takeIf { it.isNotEmpty() }?.toList(),
            equipment = domain.equipment,
            difficulty = domain.difficulty,
            instructions = domain.instructions.takeIf { it.isNotEmpty() },
            mainImageUrl = domain.mainImageUrl,
            additionalImageUrls = domain.additionalImageUrls.takeIf { it.isNotEmpty() },
            videoUrl = domain.videoUrl,
            tags = domain.tags.takeIf { it.isNotEmpty() },
            categories = domain.categories.takeIf { it.isNotEmpty() },
            notes = domain.notes,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            isSynced = isSynced,
            syncVersion = syncVersion,
            lastModified = domain.updatedAt.toEpochMilli()
        )
    }
    
    /**
     * Creates a new CustomExerciseEntity from creation parameters
     */
    fun createEntity(
        userId: String,
        name: String,
        description: String? = null,
        exerciseType: ExerciseType,
        primaryMuscle: ExerciseCategory,
        equipment: Equipment,
        secondaryMuscles: Set<ExerciseCategory> = emptySet(),
        difficulty: Int? = null,
        instructions: List<String> = emptyList(),
        mainImageUrl: String? = null,
        additionalImageUrls: List<String> = emptyList(),
        videoUrl: String? = null,
        tags: List<String> = emptyList(),
        categories: List<ExerciseCategory> = emptyList(),
        notes: String? = null,
        isSynced: Boolean = false
    ): CustomExerciseEntity {
        val now = Instant.now()
        return CustomExerciseEntity(
            id = CustomExerciseId.generate().value,
            userId = userId,
            name = name,
            exerciseType = exerciseType,
            description = description,
            primaryMuscleGroup = primaryMuscle,
            secondaryMuscleGroups = secondaryMuscles.takeIf { it.isNotEmpty() }?.toList(),
            equipment = equipment,
            difficulty = difficulty,
            instructions = instructions.takeIf { it.isNotEmpty() },
            mainImageUrl = mainImageUrl,
            additionalImageUrls = additionalImageUrls.takeIf { it.isNotEmpty() },
            videoUrl = videoUrl,
            tags = tags.takeIf { it.isNotEmpty() },
            categories = categories.takeIf { it.isNotEmpty() },
            notes = notes,
            createdAt = now,
            updatedAt = now,
            isSynced = isSynced,
            syncVersion = 1,
            lastModified = now.toEpochMilli()
        )
    }
    
    /**
     * Converts list of entities to domain models
     */
    fun toDomainList(entities: List<CustomExerciseEntity>): List<CustomExercise> {
        return entities.map { toDomain(it) }
    }
    
    /**
     * Converts list of domain models to entities
     */
    fun toEntityList(domainList: List<CustomExercise>, isSynced: Boolean = false): List<CustomExerciseEntity> {
        return domainList.map { toEntity(it, isSynced) }
    }
    
    /**
     * Updates an entity with new domain data while preserving sync information
     */
    fun updateEntity(
        existingEntity: CustomExerciseEntity,
        updatedDomain: CustomExercise
    ): CustomExerciseEntity {
        return existingEntity.copy(
            name = updatedDomain.name,
            description = updatedDomain.description,
            exerciseType = updatedDomain.exerciseType,
            primaryMuscleGroup = updatedDomain.primaryMuscle,
            secondaryMuscleGroups = updatedDomain.secondaryMuscles.takeIf { it.isNotEmpty() }?.toList(),
            equipment = updatedDomain.equipment,
            difficulty = updatedDomain.difficulty,
            instructions = updatedDomain.instructions.takeIf { it.isNotEmpty() },
            mainImageUrl = updatedDomain.mainImageUrl,
            additionalImageUrls = updatedDomain.additionalImageUrls.takeIf { it.isNotEmpty() },
            videoUrl = updatedDomain.videoUrl,
            tags = updatedDomain.tags.takeIf { it.isNotEmpty() },
            categories = updatedDomain.categories.takeIf { it.isNotEmpty() },
            notes = updatedDomain.notes,
            updatedAt = updatedDomain.updatedAt,
            isSynced = false, // Mark as unsynced when updated
            syncVersion = existingEntity.syncVersion + 1,
            lastModified = updatedDomain.updatedAt.toEpochMilli()
        )
    }
} 