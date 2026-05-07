package com.example.liftrix.domain.usecase.exercise

import com.example.liftrix.domain.model.*
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.flatMapLiftrix
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.domain.repository.ExerciseLibraryRepository
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.example.liftrix.domain.util.DomainLogger as Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Consolidated query use case for exercise operations.
 *
 * Replaces:
 * - GetExerciseLibraryUseCase.kt
 * - SearchExercisesUseCase.kt
 * - GetExerciseDefaultsUseCase.kt
 * - GetExerciseVariationsUseCase.kt
 *
 * Provides methods for:
 * - Retrieving exercise library
 * - Searching exercises with advanced filters
 * - Getting intelligent defaults based on history
 * - Getting exercise variations by movement pattern
 *
 * All operations include proper user scoping and error handling.
 *
 * @property exerciseLibraryRepository Repository for exercise library access
 * @property exerciseRepository Repository for exercise operations
 * @property customExerciseRepository Repository for custom exercises
 * @property authRepository Repository for authentication
 * @property workoutRepository Repository for workout history
 * @property errorHandler Error handler for consistent error processing
 */
@Singleton
class ExerciseQueryUseCase(
    private val exerciseLibraryRepository: ExerciseLibraryRepository,
    private val exerciseRepository: ExerciseRepository,
    private val customExerciseRepository: CustomExerciseRepository,
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository,
    private val errorHandler: ErrorHandler
) {

    // ===== Library Operations (from GetExerciseLibraryUseCase) =====

    /**
     * Retrieves all exercises from the exercise library.
     * Replaces GetExerciseLibraryUseCase.invoke()
     *
     * @return LiftrixResult containing list of all available exercises or error
     */
    suspend operator fun invoke(): LiftrixResult<List<ExerciseLibrary>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "EXERCISE_LIBRARY_FETCH_FAILED",
                    errorMessage = "Failed to retrieve exercise library: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "GET_EXERCISE_LIBRARY"
                    )
                )
            }
        ) {
            Timber.d("Retrieving exercise library")

            val exercises = exerciseLibraryRepository.getAllExercises().first()

            Timber.d("Retrieved ${exercises.size} exercises from library")
            exercises
        }
    }

    /**
     * Retrieves exercises filtered by user's recent usage.
     * Replaces GetExerciseLibraryUseCase.getRecentExercises()
     *
     * @param userId User ID for scoping recent exercises
     * @param limit Maximum number of exercises to return
     * @return LiftrixResult containing list of recent exercises or error
     */
    suspend fun getRecentExercises(userId: String, limit: Int = 10): LiftrixResult<List<ExerciseLibrary>> = withContext(Dispatchers.IO) {
        return@withContext liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "RECENT_EXERCISES_FETCH_FAILED",
                    errorMessage = "Failed to retrieve recent exercises for user $userId: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "GET_RECENT_EXERCISES",
                        "userId" to userId,
                        "limit" to limit.toString()
                    )
                )
            }
        ) {
            if (userId.isBlank()) {
                throw IllegalArgumentException("User ID cannot be blank")
            }

            Timber.d("Retrieving recent exercises for user: $userId, limit: $limit")

            val result = exerciseLibraryRepository.getRecentExercises(userId, limit)
            result.fold(
                onSuccess = { exercises ->
                    Timber.d("Retrieved ${exercises.size} recent exercises for user: $userId")
                    exercises
                },
                onFailure = { error ->
                    Timber.e("Failed to get recent exercises: $error")
                    throw Exception("Failed to get recent exercises: $error")
                }
            )
        }
    }

    // ===== Search Operations (from SearchExercisesUseCase) =====

    /**
     * Searches exercises based on the provided search criteria.
     * Replaces SearchExercisesUseCase.invoke()
     *
     * @param request The search request containing query and filter parameters
     * @return LiftrixResult containing matching exercises or error information
     */
    suspend fun searchExercises(request: SearchExercisesRequest): LiftrixResult<SearchExercisesResult> {
        return try {
            val validationResult = validateSearchRequest(request)
            if (validationResult.isFailure) {
                return validationResult as LiftrixResult<SearchExercisesResult>
            }

            val searchResult = performSearch(request)
            if (searchResult.isFailure) {
                return searchResult as LiftrixResult<SearchExercisesResult>
            }

            val exercises = searchResult.getOrThrow()
            LiftrixResult.success(
                SearchExercisesResult(
                    exercises = exercises,
                    totalCount = exercises.size,
                    hasMore = exercises.size >= request.limit,
                    searchQuery = request.query,
                    appliedFilters = createAppliedFilters(request)
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error during exercise search")
            val error = LiftrixError.UnknownError("Exercise search failed: ${e.message}")
            errorHandler.handleError(error, mapOf("context" to "ExerciseQueryUseCase"))
            LiftrixResult.failure(error)
        }
    }

    /**
     * Simplified search method that returns a Flow of SearchableExercise objects.
     * Replaces SearchExercisesUseCase.search()
     *
     * @param query Search query string
     * @param userEquipment Set of equipment available to the user
     * @return Flow of SearchableExercise objects matching the search criteria
     */
    fun search(query: String, userEquipment: Set<Equipment>): Flow<List<SearchableExercise>> = flow {
        val userId = authRepository.getCurrentUserId()

        if (userId != null) {
            combine(
                exerciseRepository.searchExercisesFlow(query),
                customExerciseRepository.searchCustomExercises(userId.value, query)
            ) { libraryExercises, customExercises ->
                val searchableLibraryExercises = libraryExercises
                    .filter { exercise -> userEquipment.isEmpty() || userEquipment.contains(exercise.equipment) }
                    .map { SearchableExercise.LibraryExercise(it) }

                val searchableCustomExercises = customExercises
                    .filter { exercise -> userEquipment.isEmpty() || userEquipment.contains(exercise.equipment) }
                    .map { SearchableExercise.CustomExercise(it) }

                val allExercises = searchableLibraryExercises + searchableCustomExercises
                allExercises.sortedWith(compareByDescending<SearchableExercise> {
                    it.calculateMatchScore(query)
                }.thenBy { it.name })
            }.collect { exercises ->
                emit(exercises)
            }
        } else {
            exerciseRepository.searchExercisesFlow(query).collect { libraryExercises ->
                val searchableExercises = libraryExercises
                    .filter { exercise -> userEquipment.isEmpty() || userEquipment.contains(exercise.equipment) }
                    .map { SearchableExercise.LibraryExercise(it) }
                    .sortedWith(compareByDescending<SearchableExercise> {
                        it.calculateMatchScore(query)
                    }.thenBy { it.name })

                emit(searchableExercises)
            }
        }
    }

    /**
     * Searches exercises with variations grouped by movement pattern.
     * Replaces SearchExercisesUseCase.searchWithVariations()
     *
     * @param query Search query string
     * @param userEquipment Set of equipment available to the user
     * @return Flow of SearchableExercise objects with variations grouped together
     */
    fun searchWithVariations(query: String, userEquipment: Set<Equipment>): Flow<List<SearchableExercise>> = flow {
        search(query, userEquipment).collect { exercises ->
            val groupedExercises = exercises.groupBy { exercise ->
                when (exercise) {
                    is SearchableExercise.LibraryExercise -> exercise.exercise.movementPattern
                    is SearchableExercise.CustomExercise -> "custom"
                }
            }

            val sortedWithVariations = groupedExercises.values.flatten()
                .sortedWith(compareByDescending<SearchableExercise> {
                    it.calculateMatchScore(query)
                }.thenBy { it.name })

            emit(sortedWithVariations)
        }
    }

    // ===== Defaults Operations (from GetExerciseDefaultsUseCase) =====

    /**
     * Gets intelligent defaults for an exercise based on user history and exercise characteristics.
     * Replaces GetExerciseDefaultsUseCase.invoke()
     *
     * @param exerciseId The exercise to get defaults for
     * @param userId The user's ID to analyze history for
     * @param exerciseLibrary The exercise library entry for type analysis
     * @return ExerciseDefaults with intelligent suggestions
     */
    suspend fun getExerciseDefaults(
        exerciseId: ExerciseId,
        userId: String,
        exerciseLibrary: ExerciseLibrary
    ): Result<ExerciseDefaults> {
        return try {
            Timber.d("Getting exercise defaults for exercise: ${exerciseLibrary.name}, user: $userId")

            val historyDefaults = calculateHistoryBasedDefaults(exerciseId, userId, exerciseLibrary)

            if (historyDefaults != null) {
                Timber.d("Using history-based defaults for ${exerciseLibrary.name}")
                Result.success(historyDefaults)
            } else {
                Timber.d("No sufficient history found, using exercise type defaults for ${exerciseLibrary.name}")
                val typeDefaults = getExerciseTypeDefaults(exerciseLibrary)
                Result.success(typeDefaults)
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to get exercise defaults for ${exerciseLibrary.name}")
            Result.success(ExerciseDefaults.createFallback())
        }
    }

    // ===== Variations Operations (from GetExerciseVariationsUseCase) =====

    /**
     * Get variations for a specific exercise by movement pattern.
     * Replaces GetExerciseVariationsUseCase.getVariations()
     *
     * @param exerciseId The exercise ID to find variations for
     * @param userEquipment Set of equipment available to the user
     * @return Flow of ExerciseGroup containing library and custom variations
     */
    suspend fun getVariations(
        exerciseId: String,
        userEquipment: Set<Equipment> = Equipment.entries.toSet()
    ): Flow<ExerciseGroup> {
        val userId = authRepository.getCurrentUserId()

        return if (userId != null) {
            combine(
                exerciseRepository.getAllExercisesFlow(),
                customExerciseRepository.getAllCustomExercises(userId.value)
            ) { libraryExercises, customExercises ->
                val baseExercise = libraryExercises.find { it.id == exerciseId }

                if (baseExercise != null) {
                    val variations = libraryExercises
                        .filter {
                            it.movementPattern == baseExercise.movementPattern &&
                            userEquipment.contains(it.equipment)
                        }
                        .sortedBy { it.difficultyLevel }

                    val customVariations = customExercises.filter {
                        it.notes?.contains(baseExercise.movementPattern, ignoreCase = true) == true &&
                        userEquipment.contains(it.equipment)
                    }

                    ExerciseGroup(
                        movementPattern = baseExercise.movementPattern,
                        libraryVariations = variations,
                        customVariations = customVariations
                    )
                } else {
                    ExerciseGroup(
                        movementPattern = "unknown",
                        libraryVariations = emptyList(),
                        customVariations = emptyList()
                    )
                }
            }
        } else {
            exerciseRepository.getAllExercisesFlow().map { libraryExercises ->
                val baseExercise = libraryExercises.find { it.id == exerciseId }

                if (baseExercise != null) {
                    val variations = libraryExercises
                        .filter {
                            it.movementPattern == baseExercise.movementPattern &&
                            userEquipment.contains(it.equipment)
                        }
                        .sortedBy { it.difficultyLevel }

                    ExerciseGroup(
                        movementPattern = baseExercise.movementPattern,
                        libraryVariations = variations,
                        customVariations = emptyList()
                    )
                } else {
                    ExerciseGroup(
                        movementPattern = "unknown",
                        libraryVariations = emptyList(),
                        customVariations = emptyList()
                    )
                }
            }
        }
    }

    /**
     * Get variations by movement pattern name.
     * Replaces GetExerciseVariationsUseCase.getVariationsByMovement()
     *
     * @param movementPattern The movement pattern to find variations for
     * @param userEquipment Set of equipment available to the user
     * @return Flow of ExerciseGroup containing library and custom variations
     */
    suspend fun getVariationsByMovement(
        movementPattern: String,
        userEquipment: Set<Equipment> = Equipment.entries.toSet()
    ): Flow<ExerciseGroup> {
        val userId = authRepository.getCurrentUserId()

        return if (userId != null) {
            combine(
                exerciseRepository.getVariationsByMovement(movementPattern, userEquipment),
                customExerciseRepository.getAllCustomExercises(userId.value)
            ) { libraryVariations, customExercises ->
                val customVariations = customExercises.filter {
                    it.notes?.contains(movementPattern, ignoreCase = true) == true &&
                    userEquipment.contains(it.equipment)
                }

                ExerciseGroup(
                    movementPattern = movementPattern,
                    libraryVariations = libraryVariations,
                    customVariations = customVariations
                )
            }
        } else {
            exerciseRepository.getVariationsByMovement(movementPattern, userEquipment).map { variations ->
                ExerciseGroup(
                    movementPattern = movementPattern,
                    libraryVariations = variations,
                    customVariations = emptyList()
                )
            }
        }
    }

    /**
     * Get all available movement patterns.
     * Replaces GetExerciseVariationsUseCase.getAvailableMovementPatterns()
     *
     * @return Flow of list of distinct movement pattern names
     */
    fun getAvailableMovementPatterns(): Flow<List<String>> {
        return exerciseRepository.getAllExercisesFlow().map { exercises ->
            exercises.map { it.movementPattern }.distinct().sorted()
        }
    }

    /**
     * Get variations filtered by difficulty.
     * Replaces GetExerciseVariationsUseCase.getVariationsByDifficulty()
     *
     * @param movementPattern The movement pattern to filter
     * @param maxDifficulty Maximum difficulty level
     * @param userEquipment Set of equipment available to the user
     * @return Flow of ExerciseGroup with filtered variations
     */
    suspend fun getVariationsByDifficulty(
        movementPattern: String,
        maxDifficulty: Int,
        userEquipment: Set<Equipment> = Equipment.entries.toSet()
    ): Flow<ExerciseGroup> {
        val userId = authRepository.getCurrentUserId()

        return if (userId != null) {
            combine(
                exerciseRepository.getFilteredExercises(
                    muscleGroup = null,
                    equipment = null,
                    isCompound = null,
                    maxDifficulty = maxDifficulty
                ),
                customExerciseRepository.getAllCustomExercises(userId.value)
            ) { libraryExercises, customExercises ->
                val filteredLibrary = libraryExercises
                    .filter {
                        it.movementPattern == movementPattern &&
                        userEquipment.contains(it.equipment)
                    }
                    .sortedBy { it.difficultyLevel }

                val customVariations = customExercises.filter {
                    it.notes?.contains(movementPattern, ignoreCase = true) == true &&
                    userEquipment.contains(it.equipment) &&
                    (it.difficulty ?: 1) <= maxDifficulty
                }

                ExerciseGroup(
                    movementPattern = movementPattern,
                    libraryVariations = filteredLibrary,
                    customVariations = customVariations
                )
            }
        } else {
            exerciseRepository.getFilteredExercises(
                muscleGroup = null,
                equipment = null,
                isCompound = null,
                maxDifficulty = maxDifficulty
            ).map { libraryExercises ->
                val filteredLibrary = libraryExercises
                    .filter {
                        it.movementPattern == movementPattern &&
                        userEquipment.contains(it.equipment)
                    }
                    .sortedBy { it.difficultyLevel }

                ExerciseGroup(
                    movementPattern = movementPattern,
                    libraryVariations = filteredLibrary,
                    customVariations = emptyList()
                )
            }
        }
    }

    // ===== Private Helper Methods =====

    private fun validateSearchRequest(request: SearchExercisesRequest): LiftrixResult<SearchExercisesRequest> {
        val violations = mutableListOf<String>()

        if (request.query.isNotBlank() && request.query.length < MIN_QUERY_LENGTH) {
            violations.add("Search query must be at least $MIN_QUERY_LENGTH characters")
        }

        if (request.query.length > MAX_QUERY_LENGTH) {
            violations.add("Search query cannot exceed $MAX_QUERY_LENGTH characters")
        }

        if (request.limit <= 0) {
            violations.add("Search limit must be greater than 0")
        } else if (request.limit > MAX_SEARCH_LIMIT) {
            violations.add("Search limit cannot exceed $MAX_SEARCH_LIMIT")
        }

        if (request.muscleGroups.size > MAX_MUSCLE_GROUP_FILTERS) {
            violations.add("Cannot filter by more than $MAX_MUSCLE_GROUP_FILTERS muscle groups")
        }

        if (request.equipment.size > MAX_EQUIPMENT_FILTERS) {
            violations.add("Cannot filter by more than $MAX_EQUIPMENT_FILTERS equipment types")
        }

        return if (violations.isEmpty()) {
            LiftrixResult.success(request)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "SearchExercisesRequest",
                    violations = violations
                )
            )
        }
    }

    private suspend fun performSearch(request: SearchExercisesRequest): LiftrixResult<List<ExerciseLibrary>> {
        return when {
            request.query.isNotBlank() && (request.muscleGroups.isNotEmpty() || request.equipment.isNotEmpty()) -> {
                searchWithFilters(request)
            }
            else -> {
                val result = exerciseRepository.searchExercisesAdvanced(
                    query = request.query,
                    equipment = request.equipment.toSet(),
                    muscleGroups = request.muscleGroups.toSet()
                )
                result.flatMapLiftrix { exercises ->
                    LiftrixResult.success(exercises.take(request.limit))
                }
            }
        }
    }

    private suspend fun searchWithFilters(request: SearchExercisesRequest): LiftrixResult<List<ExerciseLibrary>> {
        val result = exerciseRepository.searchExercisesAdvanced(
            query = request.query,
            equipment = request.equipment.toSet(),
            muscleGroups = request.muscleGroups.toSet()
        )
        return result.flatMapLiftrix { exercises ->
            LiftrixResult.success(exercises.take(request.limit))
        }
    }

    private fun createAppliedFilters(request: SearchExercisesRequest): AppliedFilters {
        return AppliedFilters(
            muscleGroups = request.muscleGroups,
            equipment = request.equipment,
            hasTextQuery = request.query.isNotBlank()
        )
    }

    private suspend fun calculateHistoryBasedDefaults(
        exerciseId: ExerciseId,
        userId: String,
        exerciseLibrary: ExerciseLibrary
    ): ExerciseDefaults? {
        Timber.d("History defaults require app data implementation; using exercise type defaults for ${exerciseLibrary.name}")
        return null
    }

    private fun getExerciseTypeDefaults(exerciseLibrary: ExerciseLibrary): ExerciseDefaults {
        val exerciseType = ExerciseType.fromLibraryExercise(exerciseLibrary)

        return ExerciseDefaults.fromExerciseType(
            exerciseType = exerciseType,
            primaryMuscle = exerciseLibrary.primaryMuscleGroup,
            isCompound = exerciseLibrary.isCompound
        )
    }

    private fun calculateWeightedAverage(values: List<Double>, timestamps: List<Long>): Double {
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return values.first()

        val now = System.currentTimeMillis()
        val maxAge = timestamps.maxOf { now - it }

        var weightedSum = 0.0
        var totalWeight = 0.0

        values.forEachIndexed { index, value ->
            val age = now - timestamps[index]
            val recencyFactor = if (maxAge > 0) {
                1.0 - (age.toDouble() / maxAge) * (1.0 - RECENCY_WEIGHT)
            } else {
                1.0
            }

            weightedSum += value * recencyFactor
            totalWeight += recencyFactor
        }

        return if (totalWeight > 0) weightedSum / totalWeight else values.average()
    }

    private fun determineRestTimeFromHistory(
        exerciseLibrary: ExerciseLibrary,
        averageWeight: Double?
    ): Int {
        return when {
            exerciseLibrary.isCompound -> {
                when (exerciseLibrary.primaryMuscleGroup) {
                    ExerciseCategory.LEGS -> 180
                    ExerciseCategory.BACK, ExerciseCategory.CHEST -> 150
                    else -> 120
                }
            }
            exerciseLibrary.equipment == Equipment.BODYWEIGHT_ONLY -> {
                when (exerciseLibrary.primaryMuscleGroup) {
                    ExerciseCategory.CORE -> 45
                    ExerciseCategory.CARDIO -> 30
                    else -> 60
                }
            }
            averageWeight != null && averageWeight > 0 -> {
                if (averageWeight > 50.0) 120 else 90
            }
            else -> {
                when (exerciseLibrary.primaryMuscleGroup) {
                    ExerciseCategory.ARMS, ExerciseCategory.BICEPS, ExerciseCategory.TRICEPS -> 60
                    ExerciseCategory.SHOULDERS -> 75
                    ExerciseCategory.CORE -> 45
                    ExerciseCategory.CARDIO -> 30
                    else -> 90
                }
            }
        }.coerceIn(ExerciseDefaults.MIN_REST_SECONDS, ExerciseDefaults.MAX_REST_SECONDS)
    }

    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_QUERY_LENGTH = 100
        private const val MAX_SEARCH_LIMIT = 100
        private const val MAX_MUSCLE_GROUP_FILTERS = 5
        private const val MAX_EQUIPMENT_FILTERS = 5
        private const val RECENCY_WEIGHT = 0.7f
    }
}

// ===== Data Classes =====

/**
 * Data class to hold historical exercise performance data
 */
private data class ExerciseHistoryData(
    val sets: Int,
    val averageReps: Int,
    val averageWeight: Double?,
    val timestamp: Long
)

/**
 * Request data class for searching exercises.
 */
data class SearchExercisesRequest(
    val query: String = "",
    val muscleGroups: List<ExerciseCategory> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val limit: Int = 20
)

/**
 * Result data class for exercise search operations.
 */
data class SearchExercisesResult(
    val exercises: List<ExerciseLibrary>,
    val totalCount: Int,
    val hasMore: Boolean,
    val searchQuery: String,
    val appliedFilters: AppliedFilters
)

/**
 * Summary of filters applied to an exercise search.
 */
data class AppliedFilters(
    val muscleGroups: List<ExerciseCategory>,
    val equipment: List<Equipment>,
    val hasTextQuery: Boolean
) {
    val hasFilters: Boolean
        get() = muscleGroups.isNotEmpty() || equipment.isNotEmpty() || hasTextQuery
}
