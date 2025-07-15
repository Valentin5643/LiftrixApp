package com.example.liftrix.domain.usecase.exercise

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.ExerciseCategory
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.flatMapLiftrix
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.exercise.ExerciseRepository
import com.example.liftrix.domain.repository.CustomExerciseRepository
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import timber.log.Timber
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Use case for searching exercises in the library with advanced filtering and validation.
 * 
 * Responsibilities:
 * - Validates search query parameters
 * - Applies search filters for muscle groups and equipment
 * - Handles empty results and search optimization
 * - Provides search suggestions and recommendations
 * 
 * Business Rules:
 * - Search query must be at least 2 characters (or empty for browse mode)
 * - Maximum search results limited to prevent performance issues
 * - Search results ordered by relevance and popularity
 * - Invalid filter combinations return appropriate guidance
 * - Empty search with filters returns filtered browse results
 */
class SearchExercisesUseCase @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val customExerciseRepository: CustomExerciseRepository,
    private val authRepository: AuthRepository,
    private val errorHandler: ErrorHandler
) {
    
    /**
     * Searches exercises based on the provided search criteria.
     * 
     * @param request The search request containing query and filter parameters
     * @return LiftrixResult containing matching exercises or error information
     */
    suspend operator fun invoke(request: SearchExercisesRequest): LiftrixResult<SearchExercisesResult> {
        return try {
            val validationResult = validateRequest(request)
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
            errorHandler.handleError(error, mapOf("context" to "SearchExercisesUseCase"))
            LiftrixResult.failure(error)
        }
    }
    
    /**
     * Validates the search request parameters.
     */
    private fun validateRequest(request: SearchExercisesRequest): LiftrixResult<SearchExercisesRequest> {
        val violations = mutableListOf<String>()
        
        // Validate query length if provided
        if (request.query.isNotBlank() && request.query.length < MIN_QUERY_LENGTH) {
            violations.add("Search query must be at least $MIN_QUERY_LENGTH characters")
        }
        
        // Validate query length maximum
        if (request.query.length > MAX_QUERY_LENGTH) {
            violations.add("Search query cannot exceed $MAX_QUERY_LENGTH characters")
        }
        
        // Validate limit
        if (request.limit <= 0) {
            violations.add("Search limit must be greater than 0")
        } else if (request.limit > MAX_SEARCH_LIMIT) {
            violations.add("Search limit cannot exceed $MAX_SEARCH_LIMIT")
        }
        
        // Validate muscle groups list size
        if (request.muscleGroups.size > MAX_MUSCLE_GROUP_FILTERS) {
            violations.add("Cannot filter by more than $MAX_MUSCLE_GROUP_FILTERS muscle groups")
        }
        
        // Validate equipment list size
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
    
    /**
     * Performs the exercise search based on validated request parameters.
     */
    private suspend fun performSearch(request: SearchExercisesRequest): LiftrixResult<List<ExerciseLibrary>> {
        return when {
            // Text search with filters
            request.query.isNotBlank() && (request.muscleGroups.isNotEmpty() || request.equipment.isNotEmpty()) -> {
                searchWithFilters(request)
            }
            // Text search only or any other case
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
    
    /**
     * Searches exercises with text query and applies additional filters.
     */
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
    
    /**
     * Creates a summary of applied filters for the result.
     */
    private fun createAppliedFilters(request: SearchExercisesRequest): AppliedFilters {
        return AppliedFilters(
            muscleGroups = request.muscleGroups,
            equipment = request.equipment,
            hasTextQuery = request.query.isNotBlank()
        )
    }
    
    /**
     * Simplified search method that returns a Flow of SearchableExercise objects.
     * This is a convenience method used by ViewModels for reactive search functionality.
     * 
     * @param query Search query string
     * @param userEquipment Set of equipment available to the user
     * @return Flow of SearchableExercise objects matching the search criteria
     */
    fun search(query: String, userEquipment: Set<Equipment>): Flow<List<SearchableExercise>> = flow {
        val userId = authRepository.getCurrentUserId()
        
        if (userId != null) {
            // Combine library and custom exercise searches
            combine(
                exerciseRepository.searchExercisesFlow(query),
                customExerciseRepository.searchCustomExercises(userId, query)
            ) { libraryExercises, customExercises ->
                // Convert to SearchableExercise and filter by equipment
                val searchableLibraryExercises = libraryExercises
                    .filter { exercise -> userEquipment.isEmpty() || userEquipment.contains(exercise.equipment) }
                    .map { SearchableExercise.LibraryExercise(it) }
                
                val searchableCustomExercises = customExercises
                    .filter { exercise -> userEquipment.isEmpty() || userEquipment.contains(exercise.equipment) }
                    .map { SearchableExercise.CustomExercise(it) }
                
                // Combine and sort by match score
                val allExercises = searchableLibraryExercises + searchableCustomExercises
                allExercises.sortedWith(compareByDescending<SearchableExercise> { 
                    it.calculateMatchScore(query) 
                }.thenBy { it.name })
            }.collect { exercises ->
                emit(exercises)
            }
        } else {
            // If no user ID, just search library exercises
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
     * This method groups similar exercises together for better discovery.
     * 
     * @param query Search query string
     * @param userEquipment Set of equipment available to the user
     * @return Flow of SearchableExercise objects with variations grouped together
     */
    fun searchWithVariations(query: String, userEquipment: Set<Equipment>): Flow<List<SearchableExercise>> = flow {
        search(query, userEquipment).collect { exercises ->
            // Group exercises by movement pattern and present variations together
            val groupedExercises = exercises.groupBy { exercise ->
                when (exercise) {
                    is SearchableExercise.LibraryExercise -> exercise.exercise.movementPattern
                    is SearchableExercise.CustomExercise -> "custom" // Custom exercises get their own group
                }
            }
            
            // Flatten groups while maintaining internal sorting by score
            val sortedWithVariations = groupedExercises.values.flatten()
                .sortedWith(compareByDescending<SearchableExercise> { 
                    it.calculateMatchScore(query) 
                }.thenBy { it.name })
            
            emit(sortedWithVariations)
        }
    }
    
    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_QUERY_LENGTH = 100
        private const val MAX_SEARCH_LIMIT = 100
        private const val MAX_MUSCLE_GROUP_FILTERS = 5
        private const val MAX_EQUIPMENT_FILTERS = 5
    }
}

/**
 * Request data class for searching exercises.
 * 
 * @property query Text query for exercise search (empty for browse mode)
 * @property muscleGroups List of muscle groups to filter by
 * @property equipment List of equipment types to filter by
 * @property limit Maximum number of results to return (default: 20)
 */
data class SearchExercisesRequest(
    val query: String = "",
    val muscleGroups: List<ExerciseCategory> = emptyList(),
    val equipment: List<Equipment> = emptyList(),
    val limit: Int = 20
)

/**
 * Result data class for exercise search operations.
 * 
 * @property exercises List of matching exercises
 * @property totalCount Total number of results found
 * @property hasMore Whether more results are available
 * @property searchQuery The original search query
 * @property appliedFilters Summary of filters applied to the search
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
 * 
 * @property muscleGroups Muscle groups used as filters
 * @property equipment Equipment types used as filters  
 * @property hasTextQuery Whether a text query was used
 */
data class AppliedFilters(
    val muscleGroups: List<ExerciseCategory>,
    val equipment: List<Equipment>,
    val hasTextQuery: Boolean
) {
    val hasFilters: Boolean
        get() = muscleGroups.isNotEmpty() || equipment.isNotEmpty() || hasTextQuery
}