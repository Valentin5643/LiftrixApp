package com.example.liftrix.data.repository.exercise

import com.example.liftrix.data.fallback.ExercisePlaceholderService
import com.example.liftrix.data.local.LiftrixDatabase
import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.data.local.dao.ExerciseUsageHistoryDao
import com.example.liftrix.data.local.seed.ExerciseLibrarySeedData
import com.example.liftrix.data.mapper.ExerciseLibraryMapper
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ExerciseRepository focused on exercise library operations and search functionality.
 * 
 * Responsibilities:
 * - Exercise library search and filtering operations with fuzzy matching
 * - Data mapping between domain models and entities
 * - Database operations and seeding through DAOs
 * - Placeholder service integration for offline fallback
 * - Recent exercise tracking for users
 * - Error handling with LiftrixError hierarchy
 * 
 * Does NOT contain:
 * - Business logic (delegated to use cases)
 * - Exercise progression calculations (handled in domain layer)
 * - Custom exercise creation (handled by dedicated repository)
 * - Workout exercise instance management (separate repository)
 */
@Singleton
class ExerciseRepositoryImpl @Inject constructor(
    private val database: LiftrixDatabase,
    private val exerciseLibraryDao: ExerciseLibraryDao,
    private val usageHistoryDao: ExerciseUsageHistoryDao,
    private val exerciseLibraryMapper: ExerciseLibraryMapper,
    private val exerciseLibrarySeedData: ExerciseLibrarySeedData,
    private val placeholderService: ExercisePlaceholderService
) : ExerciseRepository {

    override suspend fun searchExercises(query: String, limit: Int): LiftrixResult<List<ExerciseLibrary>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to search exercises",
                    operation = "READ",
                    table = "exercise_library",
                    analyticsContext = mapOf(
                        "query" to query,
                        "limit" to limit.toString()
                    )
                )
            }
        ) {
            if (limit <= 0) {
                throw IllegalArgumentException("Limit must be positive: $limit")
            }
            
            // Ensure database is populated before searching
            val dbExercises = exerciseLibraryDao.getAllExercises().first()
            Timber.d("ExerciseRepo: Database contains ${dbExercises.size} exercises")
            
            if (dbExercises.isEmpty()) {
                Timber.d("Database empty during search, triggering population")
                exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                
                // Get updated exercises after population
                val updatedExercises = exerciseLibraryDao.getAllExercises().first()
                Timber.d("ExerciseRepo: After population: ${updatedExercises.size} exercises")
                
                if (updatedExercises.isEmpty()) {
                    // Fallback to placeholder exercises
                    Timber.w("ExerciseRepo: Population failed, using placeholders")
                    val placeholderExercises = placeholderService.getPlaceholderExercises()
                    return@liftrixCatching if (query.isBlank()) {
                        placeholderExercises.take(limit)
                    } else {
                        implementFuzzySearch(query, placeholderExercises).take(limit)
                    }
                }
            }
            
            if (query.isBlank()) {
                // For empty query, return all exercises (browse mode)
                val entities = exerciseLibraryDao.getAllExercises().first()
                return@liftrixCatching entities.take(limit).map { exerciseLibraryMapper.toDomain(it) }
            }
            
            // Get all exercises and apply fuzzy search with scoring
            val allEntities = exerciseLibraryDao.getAllExercises().first()
            val domainExercises = allEntities.map { exerciseLibraryMapper.toDomain(it) }
            
            implementFuzzySearch(query, domainExercises).take(limit)
        }
    }

    override fun getAllExercises(): Flow<LiftrixResult<List<ExerciseLibrary>>> {
        return exerciseLibraryDao.getAllExercises()
            .map { entities ->
                try {
                    val exercises = entities.map { exerciseLibraryMapper.toDomain(it) }
                    LiftrixResult.success(exercises)
                } catch (throwable: Throwable) {
                    Timber.e(throwable, "Failed to map exercise library entities to domain models")
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Failed to retrieve exercise library",
                                    operation = "READ",
                            table = "exercise_library"
                        )
                    )
                }
            }
            .catch { throwable ->
                Timber.e(throwable, "Database flow error for exercise library")
                emit(
                    LiftrixResult.failure(
                        LiftrixError.DatabaseError(
                            errorMessage = "Database connection error while retrieving exercise library",
                                    operation = "READ",
                            table = "exercise_library"
                        )
                    )
                )
            }
    }

    override suspend fun getExercisesByMuscleGroup(muscleGroup: ExerciseCategory): LiftrixResult<List<ExerciseLibrary>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get exercises by muscle group",
                    operation = "READ",
                    table = "exercise_library",
                    analyticsContext = mapOf("muscle_group" to muscleGroup.name)
                )
            }
        ) {
            val entities = exerciseLibraryDao.getExercisesByMuscleGroup(muscleGroup).first()
            entities.map { exerciseLibraryMapper.toDomain(it) }
        }
    }

    override suspend fun getExercisesByEquipment(equipment: Equipment): LiftrixResult<List<ExerciseLibrary>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get exercises by equipment",
                    operation = "READ",
                    table = "exercise_library",
                    analyticsContext = mapOf("equipment" to equipment.name)
                )
            }
        ) {
            val entities = exerciseLibraryDao.getExercisesByEquipment(equipment).first()
            entities.map { exerciseLibraryMapper.toDomain(it) }
        }
    }

    override suspend fun getExercisesByMuscleGroups(muscleGroups: List<ExerciseCategory>): LiftrixResult<List<ExerciseLibrary>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get exercises by muscle groups",
                    operation = "READ",
                    table = "exercise_library",
                    analyticsContext = mapOf(
                        "muscle_groups" to muscleGroups.joinToString(",") { it.name },
                        "muscle_group_count" to muscleGroups.size.toString()
                    )
                )
            }
        ) {
            if (muscleGroups.isEmpty()) {
                throw IllegalArgumentException("Muscle groups list cannot be empty")
            }
            
            val entities = exerciseLibraryDao.getExercisesByMuscleGroups(muscleGroups).first()
            entities.map { exerciseLibraryMapper.toDomain(it) }
        }
    }

    override suspend fun getExercisesByEquipmentList(equipmentList: List<Equipment>): LiftrixResult<List<ExerciseLibrary>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get exercises by equipment list",
                    operation = "READ",
                    table = "exercise_library",
                    analyticsContext = mapOf(
                        "equipment_types" to equipmentList.joinToString(",") { it.name },
                        "equipment_count" to equipmentList.size.toString()
                    )
                )
            }
        ) {
            if (equipmentList.isEmpty()) {
                throw IllegalArgumentException("Equipment list cannot be empty")
            }
            
            val entities = exerciseLibraryDao.getExercisesByEquipmentList(equipmentList).first()
            entities.map { exerciseLibraryMapper.toDomain(it) }
        }
    }

    override suspend fun getExerciseById(exerciseId: String): LiftrixResult<ExerciseLibrary?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get exercise by ID",
                    operation = "READ",
                    table = "exercise_library",
                    analyticsContext = mapOf("exercise_id" to exerciseId)
                )
            }
        ) {
            if (exerciseId.isBlank()) {
                throw IllegalArgumentException("Exercise ID cannot be blank")
            }
            
            val entity = exerciseLibraryDao.getExerciseById(exerciseId)
            entity?.let { exerciseLibraryMapper.toDomain(it) }
        }
    }

    override suspend fun getExercisesByDifficulty(difficultyLevel: Int): LiftrixResult<List<ExerciseLibrary>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get exercises by difficulty level",
                    operation = "READ",
                    table = "exercise_library",
                    analyticsContext = mapOf("difficulty_level" to difficultyLevel.toString())
                )
            }
        ) {
            if (difficultyLevel !in 1..5) {
                throw IllegalArgumentException("Difficulty level must be between 1 and 5: $difficultyLevel")
            }
            
            val entities = exerciseLibraryDao.getExercisesByDifficulty(difficultyLevel)
            entities.map { exerciseLibraryMapper.toDomain(it) }
        }
    }

    override suspend fun getAllMuscleGroups(): LiftrixResult<List<ExerciseCategory>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get all muscle groups",
                    operation = "READ",
                    table = "exercise_library"
                )
            }
        ) {
            exerciseLibraryDao.getAllMuscleGroups()
        }
    }

    override suspend fun getAllEquipment(): LiftrixResult<List<Equipment>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get all equipment types",
                    operation = "READ",
                    table = "exercise_library"
                )
            }
        ) {
            exerciseLibraryDao.getAllEquipment()
        }
    }

    override suspend fun getRecommendedExercises(
        muscleGroups: List<ExerciseCategory>,
        excludeEquipment: List<Equipment>,
        limit: Int
    ): LiftrixResult<List<ExerciseLibrary>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get recommended exercises",
                    operation = "READ",
                    table = "exercise_library",
                    analyticsContext = mapOf(
                        "target_muscle_groups" to muscleGroups.joinToString(",") { it.name },
                        "excluded_equipment" to excludeEquipment.joinToString(",") { it.name },
                        "limit" to limit.toString()
                    )
                )
            }
        ) {
            if (muscleGroups.isEmpty()) {
                throw IllegalArgumentException("Target muscle groups cannot be empty for recommendations")
            }
            
            if (limit <= 0) {
                throw IllegalArgumentException("Limit must be positive: $limit")
            }
            
            val entities = exerciseLibraryDao.getRecommendedExercises(
                muscleGroups = muscleGroups,
                excludeEquipment = excludeEquipment,
                limit = limit
            )
            
            entities.map { exerciseLibraryMapper.toDomain(it) }
        }
    }

    override suspend fun exerciseExists(exerciseId: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check exercise existence",
                    operation = "READ",
                    table = "exercise_library",
                    analyticsContext = mapOf("exercise_id" to exerciseId)
                )
            }
        ) {
            if (exerciseId.isBlank()) {
                throw IllegalArgumentException("Exercise ID cannot be blank")
            }
            
            exerciseLibraryDao.exerciseExists(exerciseId)
        }
    }

    override suspend fun getExerciseCount(): LiftrixResult<Int> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to get exercise count",
                    operation = "READ",
                    table = "exercise_library"
                )
            }
        ) {
            exerciseLibraryDao.getExerciseCount()
        }
    }
    
    // ========================================
    // NEW METHODS FOR FEATURE PARITY
    // ========================================
    
    override fun searchExercisesFlow(query: String): Flow<List<ExerciseLibrary>> {
        return flow {
            // First, check if database is populated
            val dbExercises = exerciseLibraryDao.getAllExercises().first()
            Timber.d("ExerciseRepo.searchFlow: Database contains ${dbExercises.size} exercises")
            
            val exercises = if (dbExercises.isEmpty()) {
                Timber.d("Database empty during flow search, triggering population")
                exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                
                // Get updated exercises from database after population
                val updatedExercises = exerciseLibraryDao.getAllExercises().first()
                Timber.d("ExerciseRepo.searchFlow: After population: ${updatedExercises.size} exercises")
                
                if (updatedExercises.isNotEmpty()) {
                    val domainExercises = updatedExercises.map { exerciseLibraryMapper.toDomain(it) }
                    if (query.isBlank()) {
                        domainExercises
                    } else {
                        implementFuzzySearch(query, domainExercises)
                    }
                } else {
                    // Fallback to placeholder exercises if population failed
                    Timber.w("ExerciseRepo.searchFlow: Population failed, using placeholders")
                    val placeholderExercises = placeholderService.getPlaceholderExercises()
                    if (query.isBlank()) {
                        placeholderExercises
                    } else {
                        implementFuzzySearch(query, placeholderExercises)
                    }
                }
            } else {
                // Database has exercises, perform normal search
                val domainExercises = dbExercises.map { exerciseLibraryMapper.toDomain(it) }
                if (query.isBlank()) {
                    domainExercises
                } else {
                    implementFuzzySearch(query, domainExercises)
                }
            }
            
            emit(exercises)
        }.catch { e ->
            Timber.e(e, "Error during flow search, falling back to placeholders")
            // Fallback to placeholder exercises if search fails
            val placeholderExercises = placeholderService.getPlaceholderExercises()
            val fallbackExercises = if (query.isBlank()) {
                placeholderExercises
            } else {
                implementFuzzySearch(query, placeholderExercises)
            }
            emit(fallbackExercises)
        }
    }
    
    override suspend fun searchExercisesAdvanced(
        query: String,
        equipment: Set<Equipment>?,
        muscleGroups: Set<ExerciseCategory>?
    ): LiftrixResult<List<ExerciseLibrary>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to search exercises with filters",
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
            val allExercises = exerciseLibraryDao.getAllExercises().first()
            val domainExercises = allExercises.map { exerciseLibraryMapper.toDomain(it) }
            
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
            val allExercises = exerciseLibraryDao.getAllExercises().first()
            val domainExercises = allExercises.map { exerciseLibraryMapper.toDomain(it) }
            
            // Filter exercises based on recent usage and maintain order
            val recentExercises = recentExerciseIds.mapNotNull { exerciseId ->
                domainExercises.find { it.id == exerciseId }
            }
            
            recentExercises
        }
    }
    
    override fun getAllExercisesFlow(): Flow<List<ExerciseLibrary>> {
        return flow {
            // First, try to get exercises from database
            val dbExercises = exerciseLibraryDao.getAllExercises().first()
            Timber.d("ExerciseRepo.getAllFlow: Database contains ${dbExercises.size} exercises")
            
            val exercises = if (dbExercises.isEmpty()) {
                Timber.d("Database empty in getAllFlow, triggering population")
                
                // Trigger database population in background
                exerciseLibrarySeedData.populateExerciseLibraryIfNeeded(database)
                
                // Get updated exercises from database
                val updatedExercises = exerciseLibraryDao.getAllExercises().first()
                Timber.d("ExerciseRepo.getAllFlow: After population: ${updatedExercises.size} exercises")
                
                if (updatedExercises.isNotEmpty()) {
                    val domainExercises = updatedExercises.map { exerciseLibraryMapper.toDomain(it) }
                    Timber.d("ExerciseRepo.getAllFlow: Returning ${domainExercises.size} domain exercises")
                    domainExercises
                } else {
                    // Use placeholders if population failed
                    Timber.w("ExerciseRepo.getAllFlow: Population failed, using placeholders")
                    val placeholderExercises = placeholderService.getPlaceholderExercises()
                    Timber.d("ExerciseRepo.getAllFlow: Returning ${placeholderExercises.size} placeholder exercises")
                    placeholderExercises
                }
            } else {
                // Database has exercises, use them
                val domainExercises = dbExercises.map { exerciseLibraryMapper.toDomain(it) }
                Timber.d("ExerciseRepo.getAllFlow: Returning ${domainExercises.size} domain exercises from DB")
                domainExercises
            }
            
            emit(exercises)
        }.catch { e ->
            Timber.e(e, "Error loading exercises in getAllFlow, falling back to placeholders")
            // Use placeholders if database fails
            val placeholderExercises = placeholderService.getPlaceholderExercises()
            Timber.d("ExerciseRepo.getAllFlow: Returning ${placeholderExercises.size} placeholder exercises after error")
            emit(placeholderExercises)
        }
    }
    
    override fun getExercisesByMuscleGroupFlow(muscleGroup: ExerciseCategory): Flow<List<ExerciseLibrary>> {
        return exerciseLibraryDao.getExercisesByMuscleGroup(muscleGroup)
            .map { entities -> entities.map { exerciseLibraryMapper.toDomain(it) } }
    }
    
    override fun getExercisesByEquipmentFlow(equipment: Equipment): Flow<List<ExerciseLibrary>> {
        return exerciseLibraryDao.getExercisesByEquipment(equipment)
            .map { entities -> entities.map { exerciseLibraryMapper.toDomain(it) } }
    }
    
    override fun getVariationsByMovement(
        movementPattern: String,
        availableEquipment: Set<Equipment>
    ): Flow<List<ExerciseLibrary>> {
        return exerciseLibraryDao.getVariationsByMovement(movementPattern, availableEquipment.toList())
            .map { entities -> entities.map { exerciseLibraryMapper.toDomain(it) } }
    }
    
    override fun getFilteredExercises(
        muscleGroup: ExerciseCategory?,
        equipment: Equipment?,
        isCompound: Boolean?,
        maxDifficulty: Int?
    ): Flow<List<ExerciseLibrary>> {
        return exerciseLibraryDao.getFilteredExercises(muscleGroup, equipment, isCompound, maxDifficulty)
            .map { entities -> entities.map { exerciseLibraryMapper.toDomain(it) } }
    }
    
    override fun getCompoundExercisesForMuscle(muscleGroup: ExerciseCategory): Flow<List<ExerciseLibrary>> {
        return exerciseLibraryDao.getCompoundExercisesForMuscle(muscleGroup)
            .map { entities -> entities.map { exerciseLibraryMapper.toDomain(it) } }
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
}