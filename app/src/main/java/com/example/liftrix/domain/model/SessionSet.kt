package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Domain model representing a single set within a workout session.
 * Supports both target values (from template) and actual values (performed during workout).
 */
data class SessionSet(
    val setNumber: Int,
    // Target values (from template or user input)
    val targetReps: Int? = null,
    val targetWeight: Weight? = null,
    val targetTime: Long? = null, // seconds
    val targetDistance: Distance? = null,
    val targetRpe: Int? = null, // Rate of Perceived Exertion (1-10)
    
    // Actual values (recorded during workout)
    val actualReps: Int? = null,
    val actualWeight: Weight? = null,
    val actualTime: Long? = null, // seconds
    val actualDistance: Distance? = null,
    val actualRpe: Int? = null, // Rate of Perceived Exertion (1-10)
    
    // Completion tracking
    val completedAt: Instant? = null,
    val skipped: Boolean = false,
    val notes: String? = null
) {
    init {
        require(setNumber > 0) { "Set number must be positive: $setNumber" }
        
        // Validate target values
        targetReps?.let { reps ->
            require(reps > 0) { "Target reps must be positive: $reps" }
            require(reps <= MAX_REPS) { "Target reps too high: $reps > $MAX_REPS" }
        }
        
        targetTime?.let { time ->
            require(time > 0) { "Target time must be positive: $time" }
            require(time <= MAX_TIME_SECONDS) { "Target time too long: $time > $MAX_TIME_SECONDS" }
        }
        
        targetRpe?.let { rpe ->
            require(rpe in MIN_RPE..MAX_RPE) { "Target RPE must be between $MIN_RPE and $MAX_RPE: $rpe" }
        }
        
        // Validate actual values
        actualReps?.let { reps ->
            require(reps >= 0) { "Actual reps cannot be negative: $reps" }
            require(reps <= MAX_REPS) { "Actual reps too high: $reps > $MAX_REPS" }
        }
        
        actualTime?.let { time ->
            require(time >= 0) { "Actual time cannot be negative: $time" }
            require(time <= MAX_TIME_SECONDS) { "Actual time too long: $time > $MAX_TIME_SECONDS" }
        }
        
        actualRpe?.let { rpe ->
            require(rpe in MIN_RPE..MAX_RPE) { "Actual RPE must be between $MIN_RPE and $MAX_RPE: $rpe" }
        }
        
        notes?.let { n ->
            require(n.length <= MAX_NOTES_LENGTH) { "Notes too long: ${n.length} > $MAX_NOTES_LENGTH" }
        }
        
        // Logical validations
        if (skipped) {
            require(completedAt == null) { "Skipped set cannot have completion time" }
        }
    }

    companion object {
        const val MAX_REPS = 500
        const val MAX_TIME_SECONDS = 3600L // 1 hour
        const val MIN_RPE = 1
        const val MAX_RPE = 10
        const val MAX_NOTES_LENGTH = 200

        /**
         * Creates a blank set for manual entry
         */
        fun createBlank(setNumber: Int): SessionSet {
            return SessionSet(setNumber = setNumber)
        }

        /**
         * Creates a set with target values
         */
        fun withTargets(
            setNumber: Int,
            targetReps: Int? = null,
            targetWeight: Weight? = null,
            targetTime: Long? = null,
            targetDistance: Distance? = null,
            targetRpe: Int? = null
        ): SessionSet {
            return SessionSet(
                setNumber = setNumber,
                targetReps = targetReps,
                targetWeight = targetWeight,
                targetTime = targetTime,
                targetDistance = targetDistance,
                targetRpe = targetRpe
            )
        }
    }

    /**
     * Checks if this set is completed
     */
    fun isCompleted(): Boolean = completedAt != null && !skipped

    /**
     * Checks if this set is in progress (has some actual values but not completed)
     */
    fun isInProgress(): Boolean = !isCompleted() && !skipped && hasActualValues()

    /**
     * Checks if this set has any actual values recorded
     */
    fun hasActualValues(): Boolean = actualReps != null || 
        actualWeight != null || 
        actualTime != null || 
        actualDistance != null || 
        actualRpe != null

    /**
     * Checks if this set has target values
     */
    fun hasTargetValues(): Boolean = targetReps != null || 
        targetWeight != null || 
        targetTime != null || 
        targetDistance != null || 
        targetRpe != null

    /**
     * Completes the set with current actual values
     */
    fun complete(): SessionSet {
        require(!skipped) { "Cannot complete a skipped set" }
        return copy(completedAt = Instant.now())
    }

    /**
     * Marks the set as skipped
     */
    fun skip(): SessionSet {
        return copy(
            skipped = true,
            completedAt = null
        )
    }

    /**
     * Unskips the set
     */
    fun unskip(): SessionSet {
        require(skipped) { "Set is not skipped" }
        return copy(skipped = false)
    }

    /**
     * Resets completion status
     */
    fun uncomplete(): SessionSet {
        return copy(completedAt = null)
    }

    /**
     * Updates actual reps
     */
    fun updateActualReps(reps: Int?): SessionSet {
        reps?.let { r ->
            require(r >= 0) { "Actual reps cannot be negative: $r" }
            require(r <= MAX_REPS) { "Actual reps too high: $r > $MAX_REPS" }
        }
        return copy(actualReps = reps)
    }

    /**
     * Updates actual weight
     */
    fun updateActualWeight(weight: Weight?): SessionSet {
        return copy(actualWeight = weight)
    }

    /**
     * Updates actual time
     */
    fun updateActualTime(timeSeconds: Long?): SessionSet {
        timeSeconds?.let { time ->
            require(time >= 0) { "Actual time cannot be negative: $time" }
            require(time <= MAX_TIME_SECONDS) { "Actual time too long: $time > $MAX_TIME_SECONDS" }
        }
        return copy(actualTime = timeSeconds)
    }

    /**
     * Updates actual distance
     */
    fun updateActualDistance(distance: Distance?): SessionSet {
        return copy(actualDistance = distance)
    }

    /**
     * Updates actual RPE
     */
    fun updateActualRpe(rpe: Int?): SessionSet {
        rpe?.let { r ->
            require(r in MIN_RPE..MAX_RPE) { "Actual RPE must be between $MIN_RPE and $MAX_RPE: $r" }
        }
        return copy(actualRpe = rpe)
    }

    /**
     * Updates set notes
     */
    fun updateNotes(newNotes: String?): SessionSet {
        val trimmedNotes = newNotes?.trim()?.takeIf { it.isNotBlank() }
        
        trimmedNotes?.let { notes ->
            require(notes.length <= MAX_NOTES_LENGTH) { 
                "Notes too long: ${notes.length} > $MAX_NOTES_LENGTH" 
            }
        }

        return copy(notes = trimmedNotes)
    }

    /**
     * Gets the volume (weight × reps) for this set
     */
    fun getVolume(): Double {
        val weight = actualWeight?.kilograms ?: targetWeight?.kilograms ?: 0.0
        val reps = actualReps ?: targetReps ?: 0
        return weight * reps
    }

    /**
     * Gets the effective reps (actual or target)
     */
    fun getEffectiveReps(): Int? = actualReps ?: targetReps

    /**
     * Gets the effective weight (actual or target)
     */
    fun getEffectiveWeight(): Weight? = actualWeight ?: targetWeight

    /**
     * Gets the effective time (actual or target)
     */
    fun getEffectiveTime(): Long? = actualTime ?: targetTime

    /**
     * Gets the effective distance (actual or target)
     */
    fun getEffectiveDistance(): Distance? = actualDistance ?: targetDistance

    /**
     * Gets the effective RPE (actual or target)
     */
    fun getEffectiveRpe(): Int? = actualRpe ?: targetRpe

    /**
     * Checks if the set was performed as planned (actual matches target)
     */
    fun wasPerformedAsPlanned(): Boolean {
        if (!isCompleted()) return false
        
        return (targetReps == null || actualReps == targetReps) &&
               (targetWeight == null || actualWeight == targetWeight) &&
               (targetTime == null || actualTime == targetTime) &&
               (targetDistance == null || actualDistance == targetDistance) &&
               (targetRpe == null || actualRpe == targetRpe)
    }

    /**
     * Gets performance compared to target (0.0 = missed target, 1.0 = hit target, >1.0 = exceeded)
     */
    fun getPerformanceRatio(): Double? {
        if (!isCompleted() || targetReps == null || actualReps == null) return null
        if (targetReps == 0) return null
        return actualReps.toDouble() / targetReps
    }

    /**
     * Gets display text for the set based on current state
     */
    fun getDisplayText(): String {
        return when {
            skipped -> "Skipped"
            isCompleted() -> {
                val weight = actualWeight?.let { "${it.kilograms}kg" } ?: ""
                val reps = actualReps?.let { "${it}x" } ?: ""
                val time = actualTime?.let { "${it}s" } ?: ""
                val distance = actualDistance?.let { "${it.meters}m" } ?: ""
                
                listOf(weight, reps, time, distance).filter { it.isNotEmpty() }.joinToString(" ")
            }
            hasTargetValues() -> {
                val weight = targetWeight?.let { "${it.kilograms}kg" } ?: ""
                val reps = targetReps?.let { "${it}x" } ?: ""
                val time = targetTime?.let { "${it}s" } ?: ""
                val distance = targetDistance?.let { "${it.meters}m" } ?: ""
                
                listOf(weight, reps, time, distance).filter { it.isNotEmpty() }.joinToString(" ")
            }
            else -> "Set ${setNumber}"
        }
    }

    /**
     * Creates a copy with all targets filled from actuals (for use in templates)
     */
    fun copyWithTargetsFromActuals(): SessionSet {
        return copy(
            targetReps = actualReps ?: targetReps,
            targetWeight = actualWeight ?: targetWeight,
            targetTime = actualTime ?: targetTime,
            targetDistance = actualDistance ?: targetDistance,
            targetRpe = actualRpe ?: targetRpe
        )
    }
}