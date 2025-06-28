package com.example.liftrix.data.repository

import com.example.liftrix.data.fallback.ExercisePlaceholderService
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.ExerciseUsageHistoryDao
import com.example.liftrix.data.local.seed.ExerciseLibrarySeedData
import com.example.liftrix.data.mapper.ExerciseLibraryMapper
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for exercise library operations with local storage
 */
@Singleton
class ExerciseLibraryRepositoryImpl @Inject constructor(
    private val database: LiftrixDatabase,
    private val dao: ExerciseLibraryDao,
    private val usageHistoryDao: ExerciseUsageHistoryDao,
    private val mapper: ExerciseLibraryMapper,
    private val exerciseLibrarySeedData: ExerciseLibrarySeedData,
    private val placeholderService: ExercisePlaceholderService
) : ExerciseLibraryRepository {
    
    override fun searchExercises(query: String): Flow<List<ExerciseLibrary>> {
        return dao.searchExercises(query)
            .map { entities -> mapper.toDomainList(entities) }
    }
    
    override suspend fun searchExercises(
        query: String,
        equipment: Set<Equipment>?,
        muscleGroups: Set<ExerciseCategory>?
    ): Result<List<ExerciseLibrary>> {
        return try {
            val allExercises = dao.getAllExercises().first()
            val domainExercises = mapper.toDomainList(allExercises)
            
            val filteredExercises = domainExercises
                .filter { exercise ->
                    // Apply equipment filter if specified
                    val equipmentMatch = equipment?.contains(exercise.equipment) ?: true
                    
                    // Apply muscle group filter if specified
                    val muscleGroupMatch = muscleGroups?.let { groups ->
                        groups.contains(exercise.primaryMuscleGroup) || 
                        exercise.secondaryMuscleGroups.any { groups.contains(it) }
                    } ?: true
                    
                    equipmentMatch && muscleGroupMatch
                }
                .let { exercises ->
                    // Apply fuzzy search if query is not empty
                    if (query.isBlank()) {
                        exercises
                    } else {
                        implementFuzzySearch(query, exercises)
                    }
                }
            
            Result.success(filteredExercises)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getRecentExercises(userId: String, limit: Int): Result<List<ExerciseLibrary>> {
        return try {
            val recentExerciseIds = usageHistoryDao.getRecentExerciseIds(userId, limit)
            val allExercises = dao.getAllExercises().first()
            val domainExercises = mapper.toDomainList(allExercises)
            
            // Filter exercises based on recent usage and maintain order
            val recentExercises = recentExerciseIds.mapNotNull { exerciseId ->
                domainExercises.find { it.id == exerciseId }
            }
            
            Result.success(recentExercises)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Implements fuzzy search with scoring and sorting
     */
    private fun implementFuzzySearch(query: String, exercises: List<ExerciseLibrary>): List<ExerciseLibrary> {
        return exercises
            .map { exercise -> exercise to exercise.calculateMatchScore(query) }
            .filter { (_, score) -> score > 0.0 }
            .sortedByDescending { (_, score) -> score }
            .map { (exercise, _) -> exercise }
    }
    
    override fun getAllExercises(): Flow<List<ExerciseLibrary>> {
        return flow {
            try {
                // First, try to get exercises from database
                val dbExercises = dao.getAllExercises().first()
                
                if (dbExercises.isEmpty()) {
                    Timber.d("Database empty, showing placeholder exercises and triggering population")
                    
                    // Emit placeholder exercises immediately for better UX
                    val placeholderExercises = placeholderService.getPlaceholderExercises()
                    emit(placeholderExercises)
                    
                    // Trigger database population in background
                    exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                    
                    // Emit updated exercises from database
                    val updatedExercises = dao.getAllExercises().first()
                    if (updatedExercises.isNotEmpty()) {
                        emit(mapper.toDomainList(updatedExercises))
                    }
                } else {
                    // Database has exercises, use them
                    emit(mapper.toDomainList(dbExercises))
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading exercises, falling back to placeholders")
                // Fallback to placeholder exercises if database fails
                emit(placeholderService.getPlaceholderExercises())
            }
        }
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