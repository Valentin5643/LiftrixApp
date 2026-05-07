package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.WorkoutId
import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.model.WorkoutTemplateId
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.common.liftrixFailure
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.sharing.SharedTemplatePreview
import com.example.liftrix.domain.model.sharing.TemplateShareEvent
import com.example.liftrix.domain.repository.UserSearchRepository
import com.example.liftrix.domain.repository.sharing.TemplateShareRepository
import com.example.liftrix.domain.repository.template.TemplateRepository
import com.example.liftrix.domain.usecase.common.ErrorHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.example.liftrix.domain.util.DomainLogger as Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated use case for all workout template query operations.
 *
 * This use case consolidates:
 * - GetTemplatesUseCase (342 lines, complex filtering and sorting)
 * - GetWorkoutTemplatesUseCase (28 lines, simple wrapper - duplicate functionality)
 * - GetWorkoutTemplateByIdUseCase (157 lines, by-ID retrieval with conversion)
 *
 * **Consolidation Rationale**:
 * - GetWorkoutTemplatesUseCase is a thin wrapper that duplicates GetTemplatesUseCase functionality
 * - All three use cases query templates, just with different parameters
 * - Consolidating reduces cognitive load and maintains single responsibility
 *
 * **Query Operations**:
 * - Simple query: Get all templates for user (replaces GetWorkoutTemplatesUseCase)
 * - Advanced query: Get templates with filtering/sorting (original GetTemplatesUseCase)
 * - By ID query: Get single template by ID (replaces GetWorkoutTemplateByIdUseCase)
 */
@Singleton
class TemplateQueryUseCase(
    private val templateRepository: TemplateRepository,
    private val errorHandler: ErrorHandler,
    private val templateShareRepository: TemplateShareRepository,
    private val userSearchRepository: UserSearchRepository
) {

    // ========== SIMPLE QUERY OPERATIONS ==========

    /**
     * Simple invocation: Gets all workout templates for a user.
     *
     * **Replaces**: GetWorkoutTemplatesUseCase.invoke(userId)
     *
     * This is the most common use case - getting all templates without filters.
     * Returns Flow<List<WorkoutTemplate>> for compatibility with existing ViewModels.
     *
     * @param userId The ID of the user whose templates to retrieve
     * @return Flow of list of workout templates
     */
    operator fun invoke(userId: String): Flow<List<WorkoutTemplate>> {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        return templateRepository.getAllTemplatesForUser(userId).map { result ->
            val templates = result.getOrElse { emptyList() }
            Timber.tag("StartupRestoreFix").d(
                "[TEMPLATE-LOAD] operation=TEMPLATE_REPOSITORY_FLOW_EMIT layer=TemplateQueryUseCase userId=$userId count=${templates.size} resultSuccess=${result.isSuccess} debounceApplied=false distinctApplied=false cacheApplied=false timestamp=${System.currentTimeMillis()}"
            )
            templates
        }
    }

    /**
     * By ID query: Retrieves a workout template by ID and converts it to a Workout for editing.
     *
     * **Replaces**: GetWorkoutTemplateByIdUseCase.invoke(templateId, userId)
     *
     * This method handles the conversion of WorkoutTemplate to Workout domain object
     * so that templates can be edited using the same EditWorkoutViewModel.
     *
     * @param templateId The template ID (including "template-" prefix)
     * @param userId The user ID for authorization
     * @return LiftrixResult containing the converted workout if found, null if not found, or error
     */
    suspend fun getById(templateId: String, userId: String): LiftrixResult<Workout?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "TEMPLATE_RETRIEVAL_FAILED",
                    errorMessage = "Failed to retrieve workout template: ${throwable.message}",
                    analyticsContext = mapOf(
                        "templateId" to templateId,
                        "userId" to userId,
                        "error" to throwable.message.orEmpty()
                    )
                )
            }
        ) {
            // Validate inputs
            if (userId.isBlank() || templateId.isBlank()) {
                return@liftrixCatching null
            }

            val workoutTemplate = templateRepository.getTemplateById(
                WorkoutTemplateId(templateId),
                userId
            ).getOrThrow() ?: return@liftrixCatching null

            // Debug: Check template exercises
            Timber.d("EDIT-WORKOUT-DEBUG: TemplateQueryUseCase.getById templateId=$templateId has ${workoutTemplate.exercises.size} exercises")

            // Now convert template to workout using proper domain model conversion
            val workout = Workout(
                userId = workoutTemplate.userId,
                id = WorkoutId(workoutTemplate.id.value),
                name = workoutTemplate.name,
                date = java.time.LocalDate.now(),
                exercises = convertTemplateExercisesToExercises(workoutTemplate.exercises, WorkoutId(workoutTemplate.id.value)),
                status = com.example.liftrix.domain.model.WorkoutStatus.PLANNED,
                startTime = null,
                endTime = null,
                notes = workoutTemplate.description ?: "",
                templateId = WorkoutId(workoutTemplate.id.value),
                createdAt = workoutTemplate.createdAt,
                updatedAt = workoutTemplate.updatedAt
            )

            workout
        }
    }

    // ========== ADVANCED QUERY OPERATIONS ==========

    /**
     * Advanced query: Retrieves workout templates with filtering, sorting, and pagination.
     *
     * **Replaces**: GetTemplatesUseCase.invoke(request)
     *
     * This is the advanced query API for complex filtering scenarios like:
     * - Search by name
     * - Filter by folder
     * - Filter by difficulty
     * - Sort by recent/most used/alphabetical/difficulty
     * - Pagination with limit
     *
     * @param request The request containing user ID and filter criteria
     * @return Flow of LiftrixResult containing templates or error information
     */
    fun invoke(request: GetTemplatesRequest): Flow<LiftrixResult<GetTemplatesResult>> {
        return validateRequest(request)
            .let { validationResult ->
                when (validationResult.isSuccess) {
                    true -> performTemplateRetrieval(validationResult.getOrThrow())
                    false -> kotlinx.coroutines.flow.flowOf(
                        liftrixFailure(validationResult.exceptionOrNull() as LiftrixError)
                    )
                }
            }
    }

    suspend fun getPendingSharesFromBuddy(
        senderId: String,
        receiverId: String
    ): LiftrixResult<List<TemplateShareEvent>> {
        if (senderId.isBlank() || receiverId.isBlank()) {
            return liftrixFailure(
                LiftrixError.ValidationError(
                    field = "templateShareLookup",
                    violations = listOf("Sender and receiver IDs are required")
                )
            )
        }

        return templateShareRepository.getPendingSharesFromBuddy(senderId, receiverId)
    }

    suspend fun getSharedTemplatePreview(
        shareId: String,
        receiverId: String
    ): LiftrixResult<SharedTemplatePreview> = liftrixCatching(
        errorMapper = { throwable ->
            when (throwable) {
                is LiftrixError -> throwable
                else -> LiftrixError.DataRetrievalError(
                    errorMessage = "Failed to load shared workout preview",
                    operation = "GET_SHARED_TEMPLATE_PREVIEW"
                )
            }
        }
    ) {
        val share = templateShareRepository.getPendingShareForReceiver(shareId, receiverId).getOrThrow()
            ?: throw LiftrixError.NotFoundError(
                errorMessage = "Shared workout is no longer available",
                resourceType = "TemplateShareEvent",
                resourceId = shareId
            )

        val template = templateRepository.getTemplateById(
            WorkoutTemplateId(share.templateId),
            share.senderId
        ).getOrThrow()

        val senderName = userSearchRepository.getPublicProfile(
            userId = share.senderId,
            viewerId = receiverId
        ).getOrNull()?.displayName ?: "Gym Buddy"

        SharedTemplatePreview(
            shareEvent = share,
            template = template,
            senderName = senderName
        )
    }

    // ========== PRIVATE HELPER METHODS ==========

    /**
     * Validates the request parameters for retrieving templates.
     */
    private fun validateRequest(request: GetTemplatesRequest): LiftrixResult<GetTemplatesRequest> {
        val violations = mutableListOf<String>()

        // Validate user ID
        if (request.userId.isBlank()) {
            violations.add("User ID is required")
        }

        // Validate search query length if provided
        if (request.searchQuery != null && request.searchQuery.length > MAX_SEARCH_QUERY_LENGTH) {
            violations.add("Search query cannot exceed $MAX_SEARCH_QUERY_LENGTH characters")
        }

        // Validate search query minimum length if provided
        if (request.searchQuery != null && request.searchQuery.isNotBlank() && request.searchQuery.length < MIN_SEARCH_QUERY_LENGTH) {
            violations.add("Search query must be at least $MIN_SEARCH_QUERY_LENGTH characters")
        }

        // Validate difficulty level if provided
        if (request.difficultyLevel != null && (request.difficultyLevel < 1 || request.difficultyLevel > 10)) {
            violations.add("Difficulty level must be between 1 and 10")
        }

        // Validate limit
        if (request.limit <= 0) {
            violations.add("Limit must be greater than 0")
        } else if (request.limit > MAX_TEMPLATE_LIMIT) {
            violations.add("Limit cannot exceed $MAX_TEMPLATE_LIMIT")
        }

        return if (violations.isEmpty()) {
            LiftrixResult.success(request)
        } else {
            liftrixFailure(
                LiftrixError.ValidationError(
                    field = "GetTemplatesRequest",
                    violations = violations
                )
            )
        }
    }

    /**
     * Performs the template retrieval based on validated request parameters.
     */
    private fun performTemplateRetrieval(request: GetTemplatesRequest): Flow<LiftrixResult<GetTemplatesResult>> {
        // Use database-level folder filtering when folderId is provided
        return if (!request.folderId.isNullOrBlank()) {
            // Use optimized folder-specific query
            getTemplatesByFolder(request)
        } else {
            // Use existing sorting logic when no folder filter
            when (request.sortBy) {
                TemplateSortBy.RECENT -> getRecentTemplates(request)
                TemplateSortBy.MOST_USED -> getMostUsedTemplates(request)
                TemplateSortBy.ALPHABETICAL -> getAllTemplatesAlphabetical(request)
                TemplateSortBy.DIFFICULTY -> getTemplatesByDifficulty(request)
            }
        }.map { templatesResult ->
            templatesResult.map { templates ->
                val filteredTemplates = applyFilters(templates, request)
                val limitedTemplates = filteredTemplates.take(request.limit)

                GetTemplatesResult(
                    templates = limitedTemplates,
                    totalCount = filteredTemplates.size,
                    hasMore = filteredTemplates.size > request.limit,
                    appliedFilters = createAppliedFilters(request),
                    sortedBy = request.sortBy
                )
            }
        }.catch { throwable ->
            emit(
                liftrixFailure(
                    LiftrixError.DatabaseError(
                        errorMessage = "Failed to retrieve templates",
                        operation = "getTemplates",
                        analyticsContext = mapOf(
                            "userId" to request.userId,
                            "sortBy" to request.sortBy.name
                        )
                    )
                )
            )
        }
    }

    /**
     * Retrieves templates sorted by recent usage.
     */
    private fun getRecentTemplates(request: GetTemplatesRequest): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return if (request.hasFilters()) {
            // If filters are applied, get all templates and filter them
            templateRepository.getAllTemplatesForUser(request.userId)
        } else {
            // Use getAllTemplatesForUser to include newly created templates
            // getRecentlyUsedTemplates excludes templates with usageCount=0 or lastUsedAt=null
            Timber.d("TemplateQueryUseCase: Loading all templates for user ${request.userId} (including new ones)")
            templateRepository.getAllTemplatesForUser(request.userId)
        }
    }

    /**
     * Retrieves templates sorted by usage frequency.
     */
    private fun getMostUsedTemplates(request: GetTemplatesRequest): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return if (request.hasFilters()) {
            // If filters are applied, get all templates and filter them
            templateRepository.getAllTemplatesForUser(request.userId)
        } else {
            // If no filters, use optimized most used templates query
            templateRepository.getMostUsedTemplates(request.userId, request.limit)
        }
    }

    /**
     * Retrieves all templates sorted alphabetically.
     */
    private fun getAllTemplatesAlphabetical(request: GetTemplatesRequest): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return templateRepository.getAllTemplatesForUser(request.userId)
            .map { result ->
                result.map { templates ->
                    templates.sortedBy { it.name.lowercase() }
                }
            }
    }

    /**
     * Retrieves templates filtered by difficulty level.
     */
    private fun getTemplatesByDifficulty(request: GetTemplatesRequest): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        return if (request.difficultyLevel != null) {
            // Use specific difficulty query if provided
            templateRepository.getTemplatesByDifficulty(request.userId, request.difficultyLevel)
        } else {
            // Get all templates if no specific difficulty requested
            templateRepository.getAllTemplatesForUser(request.userId)
        }
    }

    /**
     * Retrieves templates filtered by folder using optimized database query.
     * This avoids loading all templates into memory and filtering them afterwards.
     */
    private fun getTemplatesByFolder(request: GetTemplatesRequest): Flow<LiftrixResult<List<WorkoutTemplate>>> {
        Timber.d("TemplateQueryUseCase: Using database-level folder filtering for folderId: ${request.folderId}")

        return templateRepository.getTemplatesByFolder(request.userId, request.folderId!!)
            .map { result ->
                result.map { templates ->
                    // Apply sorting to folder-specific templates
                    when (request.sortBy) {
                        TemplateSortBy.RECENT -> templates.sortedByDescending { it.lastUsedAt ?: it.createdAt }
                        TemplateSortBy.MOST_USED -> templates.sortedByDescending { it.usageCount }
                        TemplateSortBy.ALPHABETICAL -> templates.sortedBy { it.name.lowercase() }
                        TemplateSortBy.DIFFICULTY -> templates.sortedBy { it.difficultyLevel ?: 0 }
                    }
                }
            }
    }

    /**
     * Applies additional filters to the template list.
     * Note: Folder filtering now handled at database level, not in-memory.
     */
    private fun applyFilters(templates: List<WorkoutTemplate>, request: GetTemplatesRequest): List<WorkoutTemplate> {
        var filteredTemplates = templates

        // Apply search query filter using simple string matching
        if (!request.searchQuery.isNullOrBlank()) {
            val searchQuery = request.searchQuery.lowercase()
            filteredTemplates = filteredTemplates.filter { template ->
                template.name.lowercase().contains(searchQuery) ||
                    template.description?.lowercase()?.contains(searchQuery) == true
            }
        }

        // Apply difficulty filter (if not already applied by repository)
        if (request.difficultyLevel != null && request.sortBy != TemplateSortBy.DIFFICULTY) {
            filteredTemplates = filteredTemplates.filter { template ->
                template.difficultyLevel == request.difficultyLevel
            }
        }

        return filteredTemplates
    }

    /**
     * Creates a summary of applied filters for the result.
     */
    private fun createAppliedFilters(request: GetTemplatesRequest): TemplateFilters {
        return TemplateFilters(
            searchQuery = request.searchQuery,
            folderId = request.folderId,
            difficultyLevel = request.difficultyLevel
        )
    }

    /**
     * Converts template exercises to workout exercises
     */
    private fun convertTemplateExercisesToExercises(
        templateExercises: List<com.example.liftrix.domain.model.TemplateExercise>,
        workoutId: WorkoutId
    ): List<com.example.liftrix.domain.model.Exercise> {
        return templateExercises.mapIndexed { index, templateExercise ->
            // Create default sets based on targetSets or default to 3 sets
            val numberOfSets = templateExercise.targetSets ?: 3
            val defaultSets = (1..numberOfSets).map { setNumber ->
                val safeReps = templateExercise.targetReps
                    ?.takeIf { it.count > 0 }
                    ?: com.example.liftrix.domain.model.Reps(1)
                val isValid = safeReps.count > 0
                Timber.d(
                    "EDIT-WORKOUT-DEBUG: TemplateQueryUseCase.convertTemplateExercisesToExercises " +
                        "templateId=${workoutId.value} exerciseIndex=$index exerciseName='${templateExercise.name}' " +
                        "setIndex=${setNumber - 1} reps=${templateExercise.targetReps?.count} time=null distance=null " +
                        "isValidBeforeNormalization=${templateExercise.targetReps?.count?.let { it > 0 } == true} normalizedReps=${safeReps.count} " +
                        "willConstructValidSet=$isValid validator=ExerciseSet.hasAtLeastOneMetric"
                )
                com.example.liftrix.domain.model.ExerciseSet(
                    id = com.example.liftrix.domain.model.ExerciseSetId(
                        "${workoutId.value}-${index}-set-${setNumber}"
                    ),
                    setNumber = setNumber,
                    // Initialize with target values from template if available
                    reps = safeReps,
                    weight = templateExercise.targetWeight,
                    time = null, // Template doesn't have target time
                    distance = null, // Template doesn't have target distance
                    completedAt = null // Not completed yet since this is for editing
                )
            }

            Timber.d("TemplateQueryUseCase: Creating exercise '${templateExercise.name}' with ${defaultSets.size} default sets")

            com.example.liftrix.domain.model.Exercise(
                id = com.example.liftrix.domain.model.ExerciseId("${workoutId.value}-${index}"),
                workoutId = workoutId,
                libraryExercise = com.example.liftrix.domain.model.ExerciseLibrary(
                    id = templateExercise.exerciseId.value,
                    name = templateExercise.name,
                    primaryMuscleGroup = templateExercise.primaryMuscle,
                    equipment = templateExercise.equipment,
                    secondaryMuscleGroups = emptyList(),
                    movementPattern = "Unknown",
                    difficultyLevel = 1,
                    instructions = null,
                    isCompound = false,
                    searchableTerms = emptyList()
                ),
                orderIndex = templateExercise.orderIndex,
                targetSets = templateExercise.targetSets,
                targetReps = templateExercise.targetReps?.count,
                targetWeight = templateExercise.targetWeight,
                targetTime = null, // Template doesn't have target time
                targetDistance = null, // Template doesn't have target distance
                sets = defaultSets, // Now has default sets instead of empty list
                notes = templateExercise.notes,
                createdAt = java.time.Instant.now()
            )
        }
    }

    companion object {
        private const val MIN_SEARCH_QUERY_LENGTH = 2
        private const val MAX_SEARCH_QUERY_LENGTH = 100
        private const val MAX_TEMPLATE_LIMIT = 100
    }
}

/**
 * Request data class for retrieving workout templates.
 *
 * @property userId The ID of the user requesting templates
 * @property sortBy How to sort the templates
 * @property searchQuery Optional search query for template names
 * @property folderId Optional folder ID to filter templates
 * @property difficultyLevel Optional difficulty level filter (1-10)
 * @property limit Maximum number of templates to return (default: 20)
 */
data class GetTemplatesRequest(
    val userId: String,
    val sortBy: TemplateSortBy = TemplateSortBy.RECENT,
    val searchQuery: String? = null,
    val folderId: String? = null,
    val difficultyLevel: Int? = null,
    val limit: Int = 20
) {
    fun hasFilters(): Boolean = !searchQuery.isNullOrBlank() || !folderId.isNullOrBlank() || difficultyLevel != null
}

/**
 * Result data class for template retrieval operations.
 *
 * @property templates List of retrieved templates
 * @property totalCount Total number of templates found (before limit applied)
 * @property hasMore Whether more templates are available
 * @property appliedFilters Summary of filters applied to the search
 * @property sortedBy The sorting method used
 */
data class GetTemplatesResult(
    val templates: List<WorkoutTemplate>,
    val totalCount: Int,
    val hasMore: Boolean,
    val appliedFilters: TemplateFilters,
    val sortedBy: TemplateSortBy
)

/**
 * Summary of filters applied to template retrieval.
 *
 * @property searchQuery Search query used for filtering
 * @property folderId Folder ID used for filtering
 * @property difficultyLevel Difficulty level used for filtering
 */
data class TemplateFilters(
    val searchQuery: String?,
    val folderId: String?,
    val difficultyLevel: Int?
) {
    val hasFilters: Boolean
        get() = !searchQuery.isNullOrBlank() || !folderId.isNullOrBlank() || difficultyLevel != null
}

/**
 * Enumeration of template sorting options.
 */
enum class TemplateSortBy {
    /**
     * Sort by most recently used templates first
     */
    RECENT,

    /**
     * Sort by most frequently used templates first
     */
    MOST_USED,

    /**
     * Sort alphabetically by template name
     */
    ALPHABETICAL,

    /**
     * Sort by difficulty level (1-10)
     */
    DIFFICULTY
}
