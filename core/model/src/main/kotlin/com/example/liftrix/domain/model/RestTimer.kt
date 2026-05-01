package com.example.liftrix.domain.model

/**
 * Domain model representing rest timer configuration and state
 */
data class RestTimer(
    val durationSeconds: Int,
    val isEnabled: Boolean = true,
    val autoStart: Boolean = false,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true
) {
    init {
        require(durationSeconds >= 0) { "Rest duration cannot be negative: $durationSeconds" }
        require(durationSeconds <= MAX_DURATION_SECONDS) { 
            "Rest duration cannot exceed $MAX_DURATION_SECONDS seconds: $durationSeconds" 
        }
    }
    
    companion object {
        const val MAX_DURATION_SECONDS: Int = 1800 // 30 minutes
        const val DEFAULT_REST_SECONDS: Int = 60
        const val SHORT_REST_SECONDS: Int = 30
        const val MEDIUM_REST_SECONDS: Int = 90
        const val LONG_REST_SECONDS: Int = 180
        
        /**
         * Creates a default rest timer with 60 seconds
         */
        fun default(): RestTimer = RestTimer(
            durationSeconds = DEFAULT_REST_SECONDS,
            isEnabled = true,
            autoStart = false,
            vibrationEnabled = true,
            soundEnabled = true
        )
        
        /**
         * Creates a rest timer for strength training (longer rest)
         */
        fun forStrength(): RestTimer = RestTimer(
            durationSeconds = LONG_REST_SECONDS,
            isEnabled = true,
            autoStart = false,
            vibrationEnabled = true,
            soundEnabled = true
        )
        
        /**
         * Creates a rest timer for cardio/conditioning (shorter rest)
         */
        fun forCardio(): RestTimer = RestTimer(
            durationSeconds = SHORT_REST_SECONDS,
            isEnabled = true,
            autoStart = false,
            vibrationEnabled = true,
            soundEnabled = true
        )
    }
    
    /**
     * Checks if the timer is configured for strength training
     */
    fun isStrengthTimer(): Boolean = durationSeconds >= LONG_REST_SECONDS
    
    /**
     * Checks if the timer is configured for cardio/conditioning
     */
    fun isCardioTimer(): Boolean = durationSeconds <= SHORT_REST_SECONDS
    
    /**
     * Updates the duration
     */
    fun updateDuration(newDurationSeconds: Int): RestTimer {
        require(newDurationSeconds >= 0) { "Rest duration cannot be negative: $newDurationSeconds" }
        require(newDurationSeconds <= MAX_DURATION_SECONDS) { 
            "Rest duration cannot exceed $MAX_DURATION_SECONDS seconds: $newDurationSeconds" 
        }
        return copy(durationSeconds = newDurationSeconds)
    }
    
    /**
     * Toggles the enabled state
     */
    fun toggle(): RestTimer = copy(isEnabled = !isEnabled)
} 