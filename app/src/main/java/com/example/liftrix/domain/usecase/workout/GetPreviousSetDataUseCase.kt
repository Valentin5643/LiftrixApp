package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.workout.WorkoutRepository
import com.example.liftrix.service.UnifiedWorkoutSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.example.liftrix.core.json.ExerciseJsonParser
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Use case to retrieve previous set data for exercises during active workouts.
 * 
 * Provides historical performance context by finding the last completed workout
 * containing each exercise and extracting set data for comparison with current session.
 * 
 * Features:
 * - User-scoped data retrieval with proper security filtering
 * - JSON-based exercise data parsing from WorkoutEntity structure  
 * - Formatted display strings for UI integration ("50kg x 10" format)
 * - Edge case handling for missing data, first workouts, and corrupted JSON
 * - Performance optimization with caching and background processing
 * 
 * Used by: UnifiedActiveWorkoutViewModel to populate RedesignedExerciseCard previous values
 */
@Singleton
class GetPreviousSetDataUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val sessionManager: UnifiedWorkoutSessionManager
) {

    suspend operator fun invoke(request: PreviousSetDataRequest): LiftrixResult<PreviousSetDataResponse> {
        // 🔥 DEBUG: Log query timing for race condition detection
        Timber.d("[PREV_SET_TIMING] Starting Previous Set Data query at ${System.currentTimeMillis()} for exercise: ${request.exerciseId}")

        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "PREVIOUS_SET_DATA_RETRIEVAL_FAILED",
                    errorMessage = "Failed to retrieve previous set data: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "GET_PREVIOUS_SET_DATA",
                        "user_id" to request.userId,
                        "exercise_id" to request.exerciseId,
                        "set_number" to request.setNumber.toString()
                    )
                )
            }
        ) {
            withContext(Dispatchers.IO) {
                // 🔥 DEBUG: Enhanced logging for ID consistency tracking
                Timber.d("[PREV_SET_QUERY] === HISTORY QUERY START ===")
                Timber.d("[PREV_SET_QUERY] Searching for exercise ID: '${request.exerciseId}' (user: ${request.userId})")
                Timber.d("[PREV_SET_QUERY] Expected canonical ID format: 'muscle-exercise-variant' (e.g., 'core-ab-wheel-rollout')")

                // 🔥 NEW: Check current session first for recent data
                val sessionPreviousData = checkCurrentSessionForPreviousData(
                    exerciseId = request.exerciseId,
                    userId = request.userId
                )
                
                if (sessionPreviousData != null) {
                    Timber.d("[PREV_SET_SESSION] Found previous data in current session for exercise '${request.exerciseId}'")
                    return@withContext sessionPreviousData
                }

                // Fallback to database query for historical workouts
                Timber.d("[PREV_SET_DATABASE] No session data, querying database for exercise '${request.exerciseId}'")
                val previousWorkouts = workoutRepository.getLastCompletedWorkoutsWithExercise(
                    userId = request.userId,
                    exerciseId = request.exerciseId,
                    limit = 5,
                    excludeWorkoutId = request.excludeWorkoutId
                )

                previousWorkouts.fold(
                    onSuccess = { workouts ->
                        Timber.d("[PREV_SET_RESULTS] Found ${workouts.size} workouts for exercise '${request.exerciseId}'")
                        
                        // 🔥 DEBUG: Log detailed workout analysis for ID tracking
                        workouts.forEachIndexed { index, workout ->
                            Timber.d("[PREV_SET_WORKOUT_$index] Workout '${workout.name}' from ${workout.date}")
                            Timber.d("[PREV_SET_WORKOUT_$index] JSON sample: ${workout.exercisesJson.take(300)}")
                            
                            // Extract exercise IDs from JSON for verification
                            try {
                                if (workout.exercisesJson.contains("libraryExercise")) {
                                    val libraryIdMatches = Regex("\"libraryExercise\":\\{\"id\":\"([^\"]+)\"").findAll(workout.exercisesJson)
                                    libraryIdMatches.forEach { match ->
                                        Timber.d("[PREV_SET_WORKOUT_$index] Found library ID: '${match.groupValues[1]}'")
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.w("[PREV_SET_WORKOUT_$index] Failed to parse exercise IDs: ${e.message}")
                            }
                        }

                        if (workouts.isEmpty()) {
                            // No previous workouts found - return empty response
                            Timber.d("[PREV_SET_TIMING] Completing Previous Set Data query at ${System.currentTimeMillis()} - NO DATA FOUND")
                            PreviousSetDataResponse(
                                previousSets = emptyMap(),
                                lastWorkoutDate = null,
                                totalPreviousWorkouts = 0
                            )
                        } else {
                            // Parse the most recent workout's exercise data
                            val mostRecentWorkout = workouts.first()
                            val previousSets = parseExerciseSetData(
                                exerciseJson = mostRecentWorkout.exercisesJson,
                                targetExerciseId = request.exerciseId,
                                workoutDate = mostRecentWorkout.date.toKotlinLocalDate(),
                                workoutName = mostRecentWorkout.name
                            )

                            Timber.d("[PREV_SET_TIMING] Completing Previous Set Data query at ${System.currentTimeMillis()} - FOUND ${previousSets.size} sets from ${workouts.size} workouts")
                            PreviousSetDataResponse(
                                previousSets = previousSets,
                                lastWorkoutDate = mostRecentWorkout.date.toKotlinLocalDate(),
                                totalPreviousWorkouts = workouts.size
                            )
                        }
                    },
                    onFailure = { error ->
                        // Propagate the error from repository layer
                        throw Exception("Repository error: ${error.message}")
                    }
                )
            }
        }
    }

    /**
     * 🔥 NEW: Check current session for previous set data
     * 
     * This method looks at the active workout session to find completed sets
     * for the same exercise. This provides immediate feedback when users log
     * sets during an active workout, instead of waiting for database persistence.
     */
    private fun checkCurrentSessionForPreviousData(
        exerciseId: String,
        userId: String
    ): PreviousSetDataResponse? {
        return try {
            val currentSession = sessionManager.currentSession.value
            if (currentSession == null || currentSession.userId != userId) {
                Timber.d("[PREV_SET_SESSION] No active session or user mismatch")
                return null
            }

            // Find the exercise in current session
            val sessionExercise = currentSession.exercises.find { exercise ->
                // Match by exercise ID or name
                exercise.exerciseId.value == exerciseId || 
                exercise.name == exerciseId
            }

            if (sessionExercise == null) {
                Timber.d("[PREV_SET_SESSION] Exercise not found in current session: $exerciseId")
                return null
            }

            // Extract completed sets from session
            val completedSets = sessionExercise.sets.filter { set ->
                set.actualReps != null && set.actualReps!! > 0
            }

            if (completedSets.isEmpty()) {
                Timber.d("[PREV_SET_SESSION] No completed sets found in current session for: $exerciseId")
                return null
            }

            // Convert session sets to PreviousSetInfo format
            val previousSetsMap = completedSets.mapIndexed { index, sessionSet ->
                val setNumber = index + 1
                val setInfo = PreviousSetInfo.create(
                    weight = sessionSet.actualWeight?.kilograms,
                    reps = sessionSet.actualReps,
                    workoutDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
                    workoutName = "Current Session"
                )
                setNumber to setInfo
            }.toMap()

            Timber.d("[PREV_SET_SESSION] Found ${previousSetsMap.size} completed sets in current session")
            
            PreviousSetDataResponse(
                previousSets = previousSetsMap,
                lastWorkoutDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
                totalPreviousWorkouts = 1
            )
        } catch (e: Exception) {
            Timber.w(e, "Error checking current session for previous data")
            null
        }
    }

    /**
     * Parses exercise JSON data to extract set information for the specified exercise.
         *
         * Handles the JSON structure from WorkoutEntity.exercisesJson field and extracts
         * set data (weight, reps) for the target exercise ID.
         *
         * Supports two JSON formats:
         * 1. Wrapped format: {"exercises": [...], "totalVolume": ..., ...} (workouts)
         * 2. Direct array format: [...] (templates or old workouts)
         */
        private fun parseExerciseSetData(
            exerciseJson: String,
            targetExerciseId: String,
            workoutDate: LocalDate,
            workoutName: String
        ): Map<Int, PreviousSetInfo> {
            if (exerciseJson.isBlank()) {
                return emptyMap()
            }

            return try {
                val gson = Gson()

                // Parse JSON element to determine format
                val jsonElement = gson.fromJson(exerciseJson, JsonElement::class.java)

                val exercises = if (jsonElement.isJsonObject) {
                    val jsonObject = jsonElement.asJsonObject
                    if (jsonObject.has("exercises")) {
                        // Wrapped format: {"exercises": [...], ...}
                        ExerciseJsonParser.parseWrappedExercises(exerciseJson, ExerciseJsonData::class.java, "exercises")
                    } else {
                        // Direct object format or simple exercises object
                        ExerciseJsonParser.parseExercises(exerciseJson, ExerciseJsonData::class.java)
                    }
                } else {
                    // Direct array format or other
                    ExerciseJsonParser.parseExercises(exerciseJson, ExerciseJsonData::class.java)
                }

                // Debug logging for JSON exercise parsing
                val exerciseNames = exercises.map { exercise ->
                    "ID:${exercise.id}, Name:${exercise.name}, LibName:${exercise.libraryExercise?.name}"
                }
                Timber.d("[PREV_SET_JSON] Target: '$targetExerciseId', Found exercises: $exerciseNames")

                // Find target exercise and extract set data with comprehensive name matching
                val targetExercise = exercises.find { exercise ->
                    // Check multiple potential matches for exercise identification
                    val directIdMatch = exercise.id == targetExerciseId
                    val exerciseIdMatch = exercise.exerciseId == targetExerciseId
                    val directNameMatch = exercise.name == targetExerciseId
                    val libraryNameMatch = exercise.libraryExercise?.name == targetExerciseId
                    val libraryIdMatch = exercise.libraryExercise?.id == targetExerciseId

                    val isMatch =
                        directIdMatch || exerciseIdMatch || directNameMatch || libraryNameMatch || libraryIdMatch

                    if (isMatch) {
                        Timber.d("[PREV_SET_MATCH] ✅ FOUND exercise - ID: ${exercise.id}, name: ${exercise.name}, libraryName: ${exercise.libraryExercise?.name}")
                    } else {
                        Timber.d("[PREV_SET_MATCH] ❌ Testing exercise - ID: ${exercise.id}, name: ${exercise.name}, libraryName: ${exercise.libraryExercise?.name}")
                    }

                    isMatch
                }

                if (targetExercise == null) {
                    return emptyMap()
                }

                // Extract set data and create PreviousSetInfo map
                val setDataMap = mutableMapOf<Int, PreviousSetInfo>()

                targetExercise.sets?.forEachIndexed { index, setData ->
                    val weight = setData.actualWeight ?: setData.targetWeight
                    val reps = setData.actualReps ?: setData.targetReps

                    if (weight != null || reps != null) {
                        val setInfo = PreviousSetInfo.create(
                            weight = weight,
                            reps = reps,
                            workoutDate = workoutDate,
                            workoutName = workoutName
                        )
                        setDataMap[index + 1] = setInfo // 1-based set numbering
                    }
                }

                setDataMap

            } catch (e: Exception) {
                // Log parsing error but don't fail the entire operation
                Timber.w(e, "Failed to parse exercise JSON for workout $workoutName")
                emptyMap()
            }
        }

        /**
         * Data classes for JSON parsing
         */

        private data class ExerciseJsonData(
            val id: String? = null,
            val exerciseId: String? = null,
            val name: String? = null, // Add name field for direct exercise name matching
            val libraryExercise: LibraryExerciseJsonData? = null,
            val sets: List<SetJsonData>? = null
        )

        private data class LibraryExerciseJsonData(
            val id: String? = null,
            val name: String? = null // Add name field for library exercise name matching
        )

        private data class SetJsonData(
            val actualWeight: Double? = null,
            val targetWeight: Double? = null,
            val actualReps: Int? = null,
            val targetReps: Int? = null,
            val completedAt: Any? = null  // Can be String or Firebase Timestamp object - we ignore it for previous data
        )
    }

    /**
     * Request data for retrieving previous set data for a specific exercise
     */
    data class PreviousSetDataRequest(
        val userId: String,
        val exerciseId: String,
        val setNumber: Int,
        val excludeWorkoutId: String? = null // Exclude current active session if saved
    )

    /**
     * Response containing previous set data organized by set number
     */
    data class PreviousSetDataResponse(
        val previousSets: Map<Int, PreviousSetInfo>, // setNumber -> set info
        val lastWorkoutDate: LocalDate?,
        val totalPreviousWorkouts: Int
    ) {

        /**
         * Get previous set information for a specific set number
         */
        fun getPreviousSetInfo(setNumber: Int): PreviousSetInfo? {
            return previousSets[setNumber]
        }

        /**
         * Check if any previous data is available
         */
        fun hasPreviousData(): Boolean {
            return previousSets.isNotEmpty()
        }
    }

    /**
     * Information about a previous set's performance
     */
    data class PreviousSetInfo(
        val weight: Double?,
        val reps: Int?,
        val formattedDisplay: String, // "50kg x 10" or "Bodyweight x 12"
        val workoutDate: LocalDate,
        val workoutName: String
    ) {

        companion object {
            /**
             * Create PreviousSetInfo with formatted display string
             */
            fun create(
                weight: Double?,
                reps: Int?,
                workoutDate: LocalDate,
                workoutName: String
            ): PreviousSetInfo {
                val formattedDisplay = formatDisplayString(weight, reps)
                return PreviousSetInfo(
                    weight = weight,
                    reps = reps,
                    formattedDisplay = formattedDisplay,
                    workoutDate = workoutDate,
                    workoutName = workoutName
                )
            }

            /**
             * Format weight and reps into display string following SPEC format patterns
             */
            private fun formatDisplayString(weight: Double?, reps: Int?): String {
                return when {
                    // Standard weight + reps format
                    weight != null && reps != null -> {
                        if (weight == 0.0) {
                            "Bodyweight x $reps"
                        } else {
                            "${weight.toInt()}kg x $reps"
                        }
                    }
                    // Weight only
                    weight != null -> "${weight.toInt()}kg"
                    // Reps only
                    reps != null -> "$reps reps"
                    // No data available
                    else -> "-"
                }
            }
        }
    }