package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.CustomExerciseEntity
import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.CustomExerciseId
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
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
            primaryMuscle = entity.primaryMuscleGroup,
            equipment = entity.equipment,
            secondaryMuscles = entity.secondaryMuscleGroups?.toSet() ?: emptySet(),
            difficulty = entity.difficulty,
            notes = entity.notes,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
    
    /**
     * Converts CustomExercise domain model to CustomExerciseEntity
     */
    fun toEntity(domain: CustomExercise, isSynced: Boolean = false, syncVersion: Int = 1): CustomExerciseEntity {
        return CustomExerciseEntity(
            id = domain.id.value,
            userId = domain.userId,
            name = domain.name,
            primaryMuscleGroup = domain.primaryMuscle,
            equipment = domain.equipment,
            secondaryMuscleGroups = domain.secondaryMuscles.takeIf { it.isNotEmpty() }?.toList(),
            difficulty = domain.difficulty,
            notes = domain.notes,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            isSynced = isSynced,
            syncVersion = syncVersion
        )
    }
    
    /**
     * Creates a new CustomExerciseEntity from creation parameters
     */
    fun createEntity(
        userId: String,
        name: String,
        primaryMuscle: ExerciseCategory,
        equipment: Equipment,
        secondaryMuscles: Set<ExerciseCategory> = emptySet(),
        difficulty: Int? = null,
        notes: String? = null,
        isSynced: Boolean = false
    ): CustomExerciseEntity {
        val now = Instant.now()
        return CustomExerciseEntity(
            id = CustomExerciseId.generate().value,
            userId = userId,
            name = name,
            primaryMuscleGroup = primaryMuscle,
            equipment = equipment,
            secondaryMuscleGroups = secondaryMuscles.takeIf { it.isNotEmpty() }?.toList(),
            difficulty = difficulty,
            notes = notes,
            createdAt = now,
            updatedAt = now,
            isSynced = isSynced,
            syncVersion = 1
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
            primaryMuscleGroup = updatedDomain.primaryMuscle,
            equipment = updatedDomain.equipment,
            secondaryMuscleGroups = updatedDomain.secondaryMuscles.takeIf { it.isNotEmpty() }?.toList(),
            difficulty = updatedDomain.difficulty,
            notes = updatedDomain.notes,
            updatedAt = updatedDomain.updatedAt,
            isSynced = false, // Mark as unsynced when updated
            syncVersion = existingEntity.syncVersion + 1
        )
    }
} 