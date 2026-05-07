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
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
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
        return flow {
            val exercises = try {
                // First, check if database is populated
                val dbExercises = dao.getAllExercises().first()
                Timber.d("🔥 REPO-DEBUG: Database contains ${dbExercises.size} exercises")
                
                // Additional debug logging
                if (dbExercises.isEmpty()) {
                    Timber.w("🔥 REPO-DEBUG: Database is empty - triggering population")
                } else {
                    Timber.d("🔥 REPO-DEBUG: Database has exercises - sample: ${dbExercises.take(3).map { it.name }}")
                }
                
                if (dbExercises.isEmpty()) {
                    Timber.d("Database empty during search, triggering population")
                    
                    // CRITICAL FIX: Ensure population completes before proceeding
                    Timber.d("🔥 REPO-DEBUG: Starting database population...")
                    exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                    
                    // Get updated exercises from database after population
                    val updatedExercises = dao.getAllExercises().first()
                    Timber.d("🔥 REPO-DEBUG: After population: ${updatedExercises.size} exercises")
                    
                    if (updatedExercises.isEmpty()) {
                        Timber.e("🔥 REPO-DEBUG: Population failed - still no exercises in database!")
                    } else {
                        Timber.d("🔥 REPO-DEBUG: Population successful - sample: ${updatedExercises.take(3).map { it.name }}")
                    }
                    
                    if (updatedExercises.isNotEmpty()) {
                        // Now perform search on populated database
                        val searchResults = if (query.isBlank()) {
                            // CRITICAL FIX: For empty query, return ALL exercises
                            updatedExercises
                        } else {
                            dao.searchExercises(query).first()
                        }
                        Timber.d("ExerciseLibraryRepo: Search results for '$query': ${searchResults.size} exercises")
                        mapper.toDomainList(searchResults)
                    } else {
                        // Fallback to placeholder exercises if population failed
                        Timber.w("ExerciseLibraryRepo: Population failed, using placeholders")
                        val placeholderExercises = placeholderService.getPlaceholderExercises()
                        val filteredPlaceholders = if (query.isBlank()) {
                            placeholderExercises
                        } else {
                            implementFuzzySearch(query, placeholderExercises)
                        }
                        Timber.d("ExerciseLibraryRepo: Returning ${filteredPlaceholders.size} placeholder exercises")
                        filteredPlaceholders
                    }
                } else {
                    // Database has exercises, perform normal search
                    val searchResults = if (query.isBlank()) {
                        // CRITICAL FIX: For empty query, return ALL exercises
                        dbExercises
                    } else {
                        dao.searchExercises(query).first()
                    }
                    Timber.d("ExerciseLibraryRepo: Search results for '$query': ${searchResults.size} exercises")
                    mapper.toDomainList(searchResults)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error during search, falling back to placeholders")
                // Fallback to placeholder exercises if search fails
                val placeholderExercises = placeholderService.getPlaceholderExercises()
                val filteredPlaceholders = if (query.isBlank()) {
                    placeholderExercises
                } else {
                    implementFuzzySearch(query, placeholderExercises)
                }
                Timber.d("ExerciseLibraryRepo: Returning ${filteredPlaceholders.size} placeholder exercises after error")
                filteredPlaceholders
            }
            
            emit(exercises)
        }
    }
    
    override suspend fun searchExercises(
        query: String,
        equipment: Set<Equipment>?,
        muscleGroups: Set<ExerciseCategory>?
    ): LiftrixResult<List<ExerciseLibrary>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to search exercises",
                    operation = "READ",
                    table = "exercise_library",
                    analyticsContext = mapOf(
                        "query" to query,
                        "equipment_count" to (equipment?.size?.toString() ?: "0"),
                        "muscle_groups_count" to (muscleGroups?.size?.toString() ?: "0")
                    )
                )
            }
        ) {
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
            
            filteredExercises
        }
    }
    
    override suspend fun getRecentExercises(userId: String, limit: Int): LiftrixResult<List<ExerciseLibrary>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get recent exercises",
                    operation = "READ",
                    table = "exercise_usage_history",
                    analyticsContext = mapOf(
                        "user_id" to userId,
                        "limit" to limit.toString()
                    )
                )
            }
        ) {
            val recentExerciseIds = usageHistoryDao.getRecentExerciseIds(userId, limit)
            val allExercises = dao.getAllExercises().first()
            val domainExercises = mapper.toDomainList(allExercises)
            
            // Filter exercises based on recent usage and maintain order
            val recentExercises = recentExerciseIds.mapNotNull { exerciseId ->
                domainExercises.find { it.id == exerciseId }
            }
            
            recentExercises
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
            val exercises = try {
                // First, try to get exercises from database
                val dbExercises = dao.getAllExercises().first()
                Timber.d("ExerciseLibraryRepo.getAllExercises: Database contains ${dbExercises.size} exercises")
                
                if (dbExercises.isEmpty()) {
                    Timber.d("Database empty, triggering population")
                    
                    // Trigger database population in background
                    exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                    
                    // Get updated exercises from database
                    val updatedExercises = dao.getAllExercises().first()
                    Timber.d("ExerciseLibraryRepo.getAllExercises: After population: ${updatedExercises.size} exercises")
                    
                    if (updatedExercises.isNotEmpty()) {
                        val domainExercises = mapper.toDomainList(updatedExercises)
                        Timber.d("ExerciseLibraryRepo.getAllExercises: Returning ${domainExercises.size} domain exercises")
                        domainExercises
                    } else {
                        // Use placeholders if population failed
                        Timber.w("ExerciseLibraryRepo.getAllExercises: Population failed, using placeholders")
                        val placeholderExercises = placeholderService.getPlaceholderExercises()
                        Timber.d("ExerciseLibraryRepo.getAllExercises: Returning ${placeholderExercises.size} placeholder exercises")
                        placeholderExercises
                    }
                } else {
                    // Database has exercises, use them
                    val domainExercises = mapper.toDomainList(dbExercises)
                    Timber.d("ExerciseLibraryRepo.getAllExercises: Returning ${domainExercises.size} domain exercises from DB")
                    domainExercises
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading exercises, falling back to placeholders")
                // Use placeholders if database fails
                val placeholderExercises = placeholderService.getPlaceholderExercises()
                Timber.d("ExerciseLibraryRepo.getAllExercises: Returning ${placeholderExercises.size} placeholder exercises after error")
                placeholderExercises
            }
            
            emit(exercises)
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