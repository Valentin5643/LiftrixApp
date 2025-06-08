package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.mapper.ExerciseLibraryMapper
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for exercise library operations with local storage
 */
@Singleton
class ExerciseLibraryRepositoryImpl @Inject constructor(
    private val dao: ExerciseLibraryDao,
    private val mapper: ExerciseLibraryMapper
) : ExerciseLibraryRepository {
    
    override fun searchExercises(query: String): Flow<List<ExerciseLibrary>> {
        return dao.searchExercises(query)
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override fun getAllExercises(): Flow<List<ExerciseLibrary>> {
        return dao.getAllExercises()
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override fun getExercisesByMuscleGroup(muscleGroup: ExerciseCategory): Flow<List<ExerciseLibrary>> {
        return dao.getExercisesByMuscleGroup(muscleGroup)
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override fun getExercisesByEquipment(equipment: Equipment): Flow<List<ExerciseLibrary>> {
        return dao.getExercisesByEquipment(equipment)
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override fun getVariationsByMovement(
        movementPattern: String,
        availableEquipment: Set<Equipment>
    ): Flow<List<ExerciseLibrary>> {
        return dao.getVariationsByMovement(movementPattern, availableEquipment.toList())
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override fun getFilteredExercises(
        muscleGroup: ExerciseCategory?,
        equipment: Equipment?,
        isCompound: Boolean?,
        maxDifficulty: Int?
    ): Flow<List<ExerciseLibrary>> {
        return dao.getFilteredExercises(muscleGroup, equipment, isCompound, maxDifficulty)
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override fun getCompoundExercisesForMuscle(muscleGroup: ExerciseCategory): Flow<List<ExerciseLibrary>> {
        return dao.getCompoundExercisesForMuscle(muscleGroup)
            .map { entities -> mapper.toDomainList(entities) }
    }
} 