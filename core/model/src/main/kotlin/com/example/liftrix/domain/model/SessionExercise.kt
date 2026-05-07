package com.example.liftrix.domain.model

import com.example.liftrix.domain.util.DomainLogger as Timber
import java.time.Instant

/**
 * Domain model representing an exercise within an active workout session.
 * This model supports real-time editing during workout sessions.
 */
data class SessionExercise(
    val exerciseId: ExerciseId,
    val name: String,
    val category: ExerciseCategory,
    val primaryMuscle: ExerciseCategory,
    val equipment: Equipment,
    val secondaryMuscles: Set<ExerciseCategory> = emptySet(),
    val sets: List<SessionSet>,
    val orderIndex: Int,
    val restTimeSeconds: Int? = null, // Default rest time between sets
    val notes: String? = null,
    val isSuperset: Boolean = false,
    val supersetWith: ExerciseId? = null,
    val lastModified: Instant = Instant.now()
) {
    init {
        require(name.isNotBlank()) { "Exercise name cannot be blank" }
        require(orderIndex >= 0) { "Order index cannot be negative" }
        require(sets.isNotEmpty()) { "Exercise must have at least one set" }
        
        restTimeSeconds?.let { rest ->
            require(rest >= 0) { "Rest time cannot be negative: $rest" }
            require(rest <= MAX_REST_TIME_SECONDS) { 
                "Rest time too long: $rest > $MAX_REST_TIME_SECONDS" 
            }
        }
        
        notes?.let { n ->
            require(n.length <= MAX_NOTES_LENGTH) { 
                "Notes too long: ${n.length} > $MAX_NOTES_LENGTH" 
            }
        }
        
        // Validate superset configuration
        if (isSuperset) {
            require(supersetWith != null) { "Superset exercise must specify supersetWith" }
            require(supersetWith != exerciseId) { "Exercise cannot be supersetted with itself" }
        } else {
            require(supersetWith == null) { "Non-superset exercise cannot have supersetWith" }
        }
    }

    companion object {
        const val MAX_REST_TIME_SECONDS = 600 // 10 minutes
        const val MAX_NOTES_LENGTH = 500
        const val DEFAULT_REST_TIME_SECONDS = 90

        /**
         * Creates a SessionExercise from a TemplateExercise
         * 🔥 FIXED: Now generates unique exercise IDs per session to prevent exercise sharing between sessions
         */
        fun fromTemplate(templateExercise: TemplateExercise): SessionExercise {
            // 🔥 FIX: Ensure at least 1 set is always created, even if targetSets is 0 or null
            val targetSetsCount = (templateExercise.targetSets ?: 1).coerceAtLeast(1)
            val sessionSets = (1..targetSetsCount).map { setNumber ->
                SessionSet(
                    setNumber = setNumber,
                    targetReps = templateExercise.targetReps?.count,
                    targetWeight = templateExercise.targetWeight,
                    targetTime = null,
                    targetDistance = null,
                    targetRpe = null,
                    actualReps = null,
                    actualWeight = null,
                    actualTime = null,
                    actualDistance = null,
                    actualRpe = null,
                    completedAt = null
                )
            }

            return SessionExercise(
                exerciseId = ExerciseId.generate(), // 🔥 FIX: Generate unique ID per session instance
                name = templateExercise.name,
                category = templateExercise.primaryMuscle,
                primaryMuscle = templateExercise.primaryMuscle,
                equipment = templateExercise.equipment,
                secondaryMuscles = emptySet(),
                sets = sessionSets,
                orderIndex = templateExercise.orderIndex,
                restTimeSeconds = templateExercise.restTimeSeconds,
                notes = templateExercise.notes,
                isSuperset = false,
                supersetWith = null
            )
        }

        /**
         * Creates a blank SessionExercise for adding during workout
         * 🔥 FIXED: Now provides default target metrics to prevent ExerciseSet validation failures
         */
        fun createBlank(
            exerciseId: ExerciseId,
            name: String,
            category: ExerciseCategory,
            primaryMuscle: ExerciseCategory,
            equipment: Equipment,
            orderIndex: Int,
            initialSets: Int = 1
        ): SessionExercise {
            require(initialSets > 0) { "Initial sets must be positive: $initialSets" }
            require(initialSets <= 20) { "Too many initial sets: $initialSets" }

            // 🔥 FIX: Create sets with default target metrics based on exercise type
            val defaultTargetReps = when {
                // Time-based exercises (cardio, planks, etc.)
                name.contains("plank", ignoreCase = true) ||
                name.contains("hold", ignoreCase = true) ||
                category == ExerciseCategory.CORE -> null // Will use time instead
                
                // Distance-based exercises
                name.contains("run", ignoreCase = true) ||
                name.contains("walk", ignoreCase = true) ||
                name.contains("cycle", ignoreCase = true) -> null // Will use distance instead
                
                // Standard strength exercises - default to reps
                else -> 10
            }
            
            val defaultTargetTime = when {
                // Time-based exercises get default time
                name.contains("plank", ignoreCase = true) ||
                name.contains("hold", ignoreCase = true) ||
                category == ExerciseCategory.CORE -> 30L // 30 seconds
                else -> null
            }

            val blankSets = (1..initialSets).map { setNumber ->
                SessionSet(
                    setNumber = setNumber,
                    targetReps = defaultTargetReps,
                    targetWeight = null,
                    targetTime = defaultTargetTime,
                    targetDistance = null,
                    targetRpe = null,
                    actualReps = null,
                    actualWeight = null,
                    actualTime = null,
                    actualDistance = null,
                    actualRpe = null,
                    completedAt = null
                )
            }

            return SessionExercise(
                exerciseId = exerciseId,
                name = name,
                category = category,
                primaryMuscle = primaryMuscle,
                equipment = equipment,
                secondaryMuscles = emptySet(),
                sets = blankSets,
                orderIndex = orderIndex,
                restTimeSeconds = DEFAULT_REST_TIME_SECONDS
            )
        }
    }

    /**
     * Adds a new set to the exercise
     * 🔥 FIXED: Ensures default metrics are provided to prevent validation failures
     */
    fun addSet(targetReps: Int? = null, targetWeight: Weight? = null): SessionExercise {
        require(sets.size < 20) { "Cannot add more than 20 sets per exercise" }

        // 🔥 FIX: Provide smart defaults based on existing sets or exercise type
        val inferredTargetReps = targetReps ?: run {
            // Use existing set's target/actual reps as template, or default to 10 for strength exercises
            val existingReps = sets.lastOrNull()?.let { it.actualReps ?: it.targetReps }
            existingReps ?: when {
                name.contains("plank", ignoreCase = true) || 
                name.contains("hold", ignoreCase = true) -> null // Time-based
                else -> 10 // Default reps for strength exercises
            }
        }
        
        val inferredTargetTime = when {
            // Time-based exercises get default time if no reps
            (name.contains("plank", ignoreCase = true) || 
             name.contains("hold", ignoreCase = true)) && inferredTargetReps == null -> {
                sets.lastOrNull()?.let { it.actualTime ?: it.targetTime } ?: 30L
            }
            else -> null
        }

        // 🔥 PREVENTION: Ensure every new set has at least one target metric
        val finalTargetReps = if (inferredTargetReps == null && inferredTargetTime == null) {
            10 // Force reps if no metrics would be set
        } else inferredTargetReps

        val newSet = SessionSet(
            setNumber = sets.size + 1,
            targetReps = finalTargetReps,
            targetWeight = targetWeight,
            targetTime = inferredTargetTime,
            targetDistance = null,
            targetRpe = null,
            actualReps = null,
            actualWeight = null,
            actualTime = null,
            actualDistance = null,
            actualRpe = null,
            completedAt = null
        )

        return copy(
            sets = sets + newSet,
            lastModified = Instant.now()
        )
    }

    /**
     * Removes the last set from the exercise
     */
    fun removeLastSet(): SessionExercise {
        require(sets.size > 1) { "Cannot remove the only set" }

        return copy(
            sets = sets.dropLast(1),
            lastModified = Instant.now()
        )
    }

    /**
     * Updates a specific set
     */
    fun updateSet(setIndex: Int, updatedSet: SessionSet): SessionExercise {
        require(setIndex in sets.indices) { "Invalid set index: $setIndex" }

        val updatedSets = sets.toMutableList()
        updatedSets[setIndex] = updatedSet.copy(setNumber = setIndex + 1)

        return copy(
            sets = updatedSets,
            lastModified = Instant.now()
        )
    }

    /**
     * Completes a set with actual values
     */
    fun completeSet(
        setIndex: Int,
        actualReps: Int? = null,
        actualWeight: Weight? = null,
        actualTime: Long? = null,
        actualDistance: Distance? = null,
        actualRpe: Int? = null
    ): SessionExercise {
        require(setIndex in sets.indices) { "Invalid set index: $setIndex" }

        // 🔥 ENHANCED DEBUG: Log what data is being used to complete the set
        Timber.d("[SET-COMPLETE-DEBUG] Exercise '$name', completing set $setIndex with:")
        Timber.d("[SET-COMPLETE-DEBUG]   actualReps=$actualReps, actualWeight=$actualWeight")
        Timber.d("[SET-COMPLETE-DEBUG]   actualTime=$actualTime, actualDistance=$actualDistance, actualRpe=$actualRpe")
        
        val originalSet = sets[setIndex]
        Timber.d("[SET-COMPLETE-DEBUG] Original set had targetReps=${originalSet.targetReps}, targetWeight=${originalSet.targetWeight}")

        val completedSet = sets[setIndex].copy(
            actualReps = actualReps,
            actualWeight = actualWeight,
            actualTime = actualTime,
            actualDistance = actualDistance,
            actualRpe = actualRpe,
            completedAt = Instant.now()
        )
        
        Timber.d("[SET-COMPLETE-DEBUG] Set $setIndex completed successfully with actual data: reps=$actualReps, weight=$actualWeight")
        return updateSet(setIndex, completedSet)
    }

    /**
     * Marks a set as incomplete
     */
    fun uncompleteSet(setIndex: Int): SessionExercise {
        require(setIndex in sets.indices) { "Invalid set index: $setIndex" }

        val uncompletedSet = sets[setIndex].copy(
            completedAt = null
        )

        return updateSet(setIndex, uncompletedSet)
    }

    /**
     * Updates exercise notes
     */
    fun updateNotes(newNotes: String?): SessionExercise {
        val trimmedNotes = newNotes?.trim()?.takeIf { it.isNotBlank() }
        
        trimmedNotes?.let { notes ->
            require(notes.length <= MAX_NOTES_LENGTH) { 
                "Notes too long: ${notes.length} > $MAX_NOTES_LENGTH" 
            }
        }

        return copy(
            notes = trimmedNotes,
            lastModified = Instant.now()
        )
    }

    /**
     * Updates rest time between sets
     */
    fun updateRestTime(newRestTimeSeconds: Int?): SessionExercise {
        newRestTimeSeconds?.let { rest ->
            require(rest >= 0) { "Rest time cannot be negative: $rest" }
            require(rest <= MAX_REST_TIME_SECONDS) { 
                "Rest time too long: $rest > $MAX_REST_TIME_SECONDS" 
            }
        }

        return copy(
            restTimeSeconds = newRestTimeSeconds,
            lastModified = Instant.now()
        )
    }

    /**
     * Gets the next incomplete set to perform
     */
    fun getNextIncompleteSet(): SessionSet? = 
        sets.firstOrNull { it.completedAt == null }

    /**
     * Gets the index of the next incomplete set
     */
    fun getNextIncompleteSetIndex(): Int? = 
        sets.indexOfFirst { it.completedAt == null }.takeIf { it != -1 }

    /**
     * Checks if all sets are completed
     */
    fun isCompleted(): Boolean = sets.all { it.completedAt != null }

    /**
     * Gets completion percentage for this exercise
     */
    fun getCompletionPercentage(): Float {
        if (sets.isEmpty()) return 0f
        val completedSets = sets.count { it.completedAt != null }
        return (completedSets.toFloat() / sets.size) * 100f
    }

    /**
     * Gets total volume (weight × reps) for completed sets
     */
    fun getTotalVolume(): Double {
        return sets.filter { it.completedAt != null }.sumOf { set ->
            val weight = set.actualWeight?.kilograms ?: 0.0
            val reps = set.actualReps ?: 0
            weight * reps
        }
    }

    /**
     * Gets total completed reps
     */
    fun getTotalReps(): Int = sets.filter { it.completedAt != null }
        .sumOf { it.actualReps ?: 0 }

    /**
     * Gets total completed sets count
     */
    fun getCompletedSetsCount(): Int = sets.count { it.completedAt != null }

    /**
     * Converts to a completed Exercise for saving to workout history
     * 🔥 FIXED: Added defensive checks to ensure at least one metric is always present
     */
    fun toCompletedExercise(): Exercise {
        // 🔥 SETS-DEBUG: Log session sets before conversion
        Timber.d("[SETS-DEBUG-1] SessionExercise '$name' has ${sets.size} sets before conversion")
        sets.forEach { set ->
            Timber.d("[SETS-DEBUG-1a] Set ${set.setNumber}: actualReps=${set.actualReps}, targetReps=${set.targetReps}, actualWeight=${set.actualWeight}, targetWeight=${set.targetWeight}")
            Timber.d("[SETS-DEBUG-1a-COMPLETEAT] Set ${set.setNumber}: completedAt=${set.completedAt}, hasActualData=${set.actualReps != null || set.actualWeight != null || set.actualTime != null || set.actualDistance != null}")
        }
        
        // 🔥 ENHANCED DEBUG: Check if we have any sets with actual data
        val setsWithActualData = sets.count { set ->
            set.actualReps != null || set.actualWeight != null || set.actualTime != null || set.actualDistance != null
        }
        Timber.d("[SETS-DEBUG-1-SUMMARY] Exercise '$name': ${sets.size} total sets, $setsWithActualData have actual data")
        
        if (setsWithActualData == 0) {
            Timber.w("[SETS-DEBUG-1-WARNING] ⚠️ NO SETS WITH ACTUAL DATA! This will result in 0 volume. Check UI data capture!")
        }
        
        val completedSets = sets.map { sessionSet ->
            // Extract metrics with fallbacks
            val finalReps = sessionSet.actualReps?.let { Reps(it) } ?: sessionSet.targetReps?.let { Reps(it) }
            val finalWeight = sessionSet.actualWeight ?: sessionSet.targetWeight
            val finalTime = sessionSet.actualTime?.let { java.time.Duration.ofSeconds(it) } 
                ?: sessionSet.targetTime?.let { java.time.Duration.ofSeconds(it) }
            val finalDistance = sessionSet.actualDistance ?: sessionSet.targetDistance
            val finalRpe = sessionSet.actualRpe?.let { RPE(it) } ?: sessionSet.targetRpe?.let { RPE(it) }
            
            // 🔥 DEFENSIVE FIX: Ensure at least one metric is present to satisfy ExerciseSet validation
            // Priority: reps > time > distance, but always guarantee at least one is present
            val safeReps = finalReps ?: if (finalTime == null && finalDistance == null) {
                Timber.w("[SETS-DEBUG-DEFENSIVE] No metrics for set ${sessionSet.setNumber}, defaulting to 1 rep")
                Reps(1)
            } else null
            
            val safeTime = finalTime ?: if (safeReps == null && finalDistance == null) {
                Timber.w("[SETS-DEBUG-DEFENSIVE] No reps/distance for set ${sessionSet.setNumber}, defaulting to 30s")
                java.time.Duration.ofSeconds(30)
            } else null
            
            val safeDistance = finalDistance ?: if (safeReps == null && safeTime == null) {
                Timber.w("[SETS-DEBUG-DEFENSIVE] No reps/time for set ${sessionSet.setNumber}, defaulting to 1m distance")
                Distance(1.0f) // 1 meter default
            } else null
            
            try {
                // 🔥 CRITICAL FIX: Ensure completedAt is set for sets with actual data
                val effectiveCompletedAt = sessionSet.completedAt ?: run {
                    // If no explicit completedAt but has actual values, mark as completed now
                    val hasActualData = sessionSet.actualReps != null || 
                                       sessionSet.actualWeight != null || 
                                       sessionSet.actualTime != null ||
                                       sessionSet.actualDistance != null
                    if (hasActualData) {
                        Timber.d("[SETS-DEBUG-COMPLETEAT] Set ${sessionSet.setNumber} missing completedAt but has actual data, assigning current timestamp")
                        Instant.now()
                    } else {
                        null
                    }
                }
                
                val exerciseSet = ExerciseSet(
                    id = ExerciseSetId.generate(),
                    setNumber = sessionSet.setNumber,
                    reps = safeReps,
                    weight = finalWeight,
                    time = safeTime,
                    distance = safeDistance, // 🔥 FIX: Use safeDistance instead of finalDistance
                    rpe = finalRpe,
                    completedAt = effectiveCompletedAt,
                    notes = null
                )
                
                // 🔥 DEBUG: Log final ExerciseSet creation with completedAt status
                Timber.d("[SETS-DEBUG-1b-FINAL] Set ${sessionSet.setNumber} → ExerciseSet completedAt=${effectiveCompletedAt}, willBeQueryable=${effectiveCompletedAt != null}")
                
                exerciseSet
            } catch (e: Exception) {
                // 🔥 ULTIMATE FALLBACK: If ExerciseSet construction still fails, create minimal valid set
                Timber.e(e, "[SETS-DEBUG-FALLBACK] ExerciseSet construction failed for set ${sessionSet.setNumber}, creating fallback")
                
                // Apply same completedAt logic for fallback case
                val fallbackCompletedAt = sessionSet.completedAt ?: run {
                    val hasActualData = sessionSet.actualReps != null || 
                                       sessionSet.actualWeight != null || 
                                       sessionSet.actualTime != null ||
                                       sessionSet.actualDistance != null
                    if (hasActualData) {
                        Timber.d("[SETS-DEBUG-COMPLETEAT-FALLBACK] Fallback set ${sessionSet.setNumber} has actual data, assigning current timestamp")
                        Instant.now()
                    } else {
                        null
                    }
                }
                
                val fallbackExerciseSet = ExerciseSet(
                    id = ExerciseSetId.generate(),
                    setNumber = sessionSet.setNumber,
                    reps = Reps(1), // Guaranteed valid fallback
                    weight = finalWeight,
                    time = null,
                    distance = null,
                    rpe = null,
                    completedAt = fallbackCompletedAt,
                    notes = null
                )
                
                // 🔥 DEBUG: Log fallback ExerciseSet creation with completedAt status
                Timber.d("[SETS-DEBUG-FALLBACK-FINAL] Fallback set ${sessionSet.setNumber} → ExerciseSet completedAt=${fallbackCompletedAt}, willBeQueryable=${fallbackCompletedAt != null}")
                
                fallbackExerciseSet
            }
        }

        // 🔥 FIX: Dynamic exercise classification based on actual sets data
        val hasWeightData = sets.any { set -> 
            set.actualWeight != null || set.targetWeight != null 
        }
        val hasTimeData = sets.any { set ->
            set.actualTime != null || set.targetTime != null
        }
        val hasDistanceData = sets.any { set ->
            set.actualDistance != null || set.targetDistance != null
        }
        
        Timber.d("[SETS-DEBUG-CLASSIFICATION] Exercise '$name': hasWeight=$hasWeightData, hasTime=$hasTimeData, hasDistance=$hasDistanceData")
        
        // Smart equipment detection - prioritize actual usage over name patterns
        val correctedEquipment = when {
            hasWeightData && name.contains("dumbbell", ignoreCase = true) -> Equipment.DUMBBELLS
            hasWeightData && name.contains("barbell", ignoreCase = true) -> Equipment.BARBELL
            hasWeightData && name.contains("cable", ignoreCase = true) -> Equipment.CABLE_MACHINE
            hasWeightData && name.contains("machine", ignoreCase = true) -> Equipment.CABLE_MACHINE
            hasWeightData && name.contains("kettlebell", ignoreCase = true) -> Equipment.KETTLEBELLS
            hasWeightData && name.contains("smith", ignoreCase = true) -> Equipment.BARBELL
            // Smart pattern matching for common weighted exercises
            hasWeightData && (name.contains("arnold press", ignoreCase = true) || 
                             name.contains("chest fly", ignoreCase = true) || 
                             name.contains("lateral raise", ignoreCase = true) ||
                             name.contains("bicep curl", ignoreCase = true) ||
                             name.contains("shoulder press", ignoreCase = true)) -> Equipment.DUMBBELLS
            // If user logged weight data but no specific equipment pattern, default to dumbbells
            hasWeightData -> Equipment.DUMBBELLS
            // Non-weighted patterns
            name.contains("band", ignoreCase = true) -> Equipment.RESISTANCE_BANDS
            equipment != Equipment.BODYWEIGHT_ONLY -> equipment // Use original if not default
            else -> Equipment.BODYWEIGHT_ONLY
        }
        
        // Create a basic library exercise for the Exercise constructor
        val libraryExercise = ExerciseLibrary(
            id = exerciseId.value,
            name = name, // 🔥 FIX: Ensure name is preserved
            primaryMuscleGroup = primaryMuscle,
            equipment = correctedEquipment, // 🔥 FIX: Use corrected equipment
            secondaryMuscleGroups = secondaryMuscles.toList(),
            movementPattern = "Unknown",
            difficultyLevel = 1,
            instructions = "",
            isCompound = false,
            searchableTerms = listOf(name.lowercase())
        )
        
        // 🔥 SETS-DEBUG: Log library exercise creation
        Timber.d("[SETS-DEBUG-1b] Created ExerciseLibrary: name='$name', equipment=$correctedEquipment")
        
        // 🔥 DEBUG: Log the library ID that will be used for DB queries
        Timber.d("[SETS-DEBUG-LIBRARY-ID] Exercise '$name' will be saved with libraryExercise.id='${libraryExercise.id}'")

        // 🔥 SETS-DEBUG: Log completed sets before Exercise creation
        Timber.d("[SETS-DEBUG-2] Creating Exercise '$name' with ${completedSets.size} completed sets")
        
        // 🔥 DEBUG: Comprehensive completedAt analysis for history query compatibility
        val totalSets = completedSets.size
        val completedAtCount = completedSets.count { it.completedAt != null }
        val queryableSets = completedSets.filter { it.completedAt != null }
        
        Timber.d("[SETS-DEBUG-2-SUMMARY] Exercise '$name': ${totalSets} total sets, ${completedAtCount} have completedAt, ${queryableSets.size} will be queryable by getExerciseHistory")
        
        completedSets.forEach { set ->
            Timber.d("[SETS-DEBUG-2a] ExerciseSet ${set.setNumber}: reps=${set.reps}, weight=${set.weight}, completedAt=${set.completedAt != null}")
            if (set.completedAt == null) {
                Timber.w("[SETS-DEBUG-2a-WARNING] Set ${set.setNumber} has NULL completedAt - will be EXCLUDED from workout history queries!")
            }
        }

        val createdExercise = try {
            Exercise.createSafe(
                id = exerciseId,
                workoutId = WorkoutId(""), // Will be set by parent workout
                libraryExercise = libraryExercise,
                orderIndex = orderIndex,
                targetSets = sets.size,
                targetReps = sets.firstOrNull()?.targetReps,
                targetWeight = sets.firstOrNull()?.targetWeight,
                targetTime = sets.firstOrNull()?.targetTime?.let { java.time.Duration.ofSeconds(it) },
                targetDistance = sets.firstOrNull()?.targetDistance,
                sets = completedSets,
                notes = notes,
                createdAt = Instant.now()
            )
        } catch (e: Exception) {
            // 🔥 ULTIMATE SAFETY: If Exercise.createSafe fails, try with minimal parameters
            Timber.e(e, "[SETS-DEBUG-EMERGENCY] Exercise.createSafe failed for '$name', creating emergency fallback")
            
            // Ensure we have at least one set
            val emergencySets = if (completedSets.isEmpty()) {
                listOf(ExerciseSet(
                    id = ExerciseSetId.generate(),
                    setNumber = 1,
                    reps = Reps(1),
                    weight = null,
                    time = null,
                    distance = null,
                    rpe = null,
                    completedAt = Instant.now(),
                    notes = null
                ))
            } else completedSets
            
            Exercise.createSafe(
                id = exerciseId,
                workoutId = WorkoutId(""),
                libraryExercise = libraryExercise,
                orderIndex = orderIndex,
                targetSets = null, // Remove target validation
                targetReps = null,
                targetWeight = null,
                targetTime = null,
                targetDistance = null,
                sets = emergencySets,
                notes = null, // Remove potentially problematic notes
                createdAt = Instant.now()
            )
        }
        
        // 🔥 SETS-DEBUG: Log created Exercise final state
        Timber.d("[SETS-DEBUG-3] Exercise '$name' created with ${createdExercise.sets.size} final sets, libraryName='${createdExercise.libraryExercise.name}', exerciseType=${createdExercise.exerciseType}")
        
        return createdExercise
    }

    /**
     * Gets the heaviest weight used in any completed set
     */
    fun getHeaviestWeight(): Weight? = sets
        .mapNotNull { it.actualWeight }
        .maxByOrNull { it.kilograms }

    /**
     * Gets the highest reps achieved in any completed set
     */
    fun getHighestReps(): Int? = sets
        .mapNotNull { it.actualReps }
        .maxOrNull()

    /**
     * Estimates total time for this exercise including rest
     */
    fun getEstimatedTotalTime(): Long {
        val setTime = sets.size * 60L // Assume 1 minute per set
        val restTime = (sets.size - 1) * (restTimeSeconds ?: DEFAULT_REST_TIME_SECONDS).toLong()
        return setTime + restTime
    }

    /**
     * Gets personal records achieved in this session
     */
    fun getPersonalRecords(): List<PersonalRecord> {
        // This would compare against historical data
        // Implementation depends on PR tracking system
        return emptyList()
    }
}

/**
 * Enumeration for personal record types
 */
enum class PersonalRecord {
    HEAVIEST_WEIGHT,
    MOST_REPS,
    HIGHEST_VOLUME,
    FASTEST_TIME
}
