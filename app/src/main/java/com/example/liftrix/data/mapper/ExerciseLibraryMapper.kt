package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.ExerciseLibraryEntity
import com.example.liftrix.domain.model.ExerciseLibrary
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between ExerciseLibraryEntity and ExerciseLibrary domain model
 */
@Singleton
class ExerciseLibraryMapper @Inject constructor() {
    
    /**
     * Convert entity to domain model
     */
    fun toDomain(entity: ExerciseLibraryEntity): ExerciseLibrary {
        return ExerciseLibrary(
            id = entity.id,
            name = entity.name,
            primaryMuscleGroup = entity.primaryMuscleGroup,
            equipment = entity.equipment,
            secondaryMuscleGroups = entity.secondaryMuscleGroups,
            movementPattern = entity.movementPattern,
            difficultyLevel = entity.difficultyLevel,
            instructions = entity.instructions,
            isCompound = entity.isCompound,
            searchableTerms = entity.searchableTerms
        )
    }
    
    /**
     * Convert list of entities to list of domain models
     */
    fun toDomainList(entities: List<ExerciseLibraryEntity>): List<ExerciseLibrary> {
        return entities.map { toDomain(it) }
    }
    
    /**
     * Convert domain model to entity
     */
    fun toEntity(domain: ExerciseLibrary): ExerciseLibraryEntity {
        return ExerciseLibraryEntity(
            id = domain.id,
            name = domain.name,
            primaryMuscleGroup = domain.primaryMuscleGroup,
            equipment = domain.equipment,
            secondaryMuscleGroups = domain.secondaryMuscleGroups,
            movementPattern = domain.movementPattern,
            difficultyLevel = domain.difficultyLevel,
            instructions = domain.instructions,
            isCompound = domain.isCompound,
            searchableTerms = domain.searchableTerms
        )
    }
    
    /**
     * Convert list of domain models to list of entities
     */
    fun toEntityList(domains: List<ExerciseLibrary>): List<ExerciseLibraryEntity> {
        return domains.map { toEntity(it) }
    }
} 