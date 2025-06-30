package com.example.liftrix.domain.model

import java.time.Duration
import java.time.Instant

/**
 * Domain model representing a user's real-time presence status
 */
data class UserPresence(
    val status: PresenceStatus,
    val lastActive: Instant,
    val currentWorkoutId: String? = null
) {
    init {
        require(lastActive.isBefore(Instant.now().plusSeconds(60))) { 
            "Last active time cannot be in the future: $lastActive" 
        }
        
        if (status == PresenceStatus.WORKING_OUT) {
            require(currentWorkoutId != null) { 
                "Current workout ID must be provided when status is WORKING_OUT" 
            }
        }
        
        if (status != PresenceStatus.WORKING_OUT && currentWorkoutId != null) {
            require(currentWorkoutId.isBlank()) { 
                "Current workout ID should be null when not working out" 
            }
        }
    }
    
    /**
     * Checks if the user is currently active (online or working out)
     */
    fun isActive(): Boolean = status == PresenceStatus.ONLINE || status == PresenceStatus.WORKING_OUT
    
    /**
     * Checks if the user is currently working out
     */
    fun isWorkingOut(): Boolean = status == PresenceStatus.WORKING_OUT && currentWorkoutId != null
    
    /**
     * Gets the time since last activity
     */
    fun getTimeSinceLastActive(): Duration = Duration.between(lastActive, Instant.now())
    
    /**
     * Checks if the user has been inactive for more than the specified duration
     */
    fun isInactiveFor(duration: Duration): Boolean = getTimeSinceLastActive() > duration
    
    /**
     * Gets a formatted string representation of the last active time
     */
    fun getFormattedLastActive(): String {
        val timeSince = getTimeSinceLastActive()
        
        return when {
            timeSince.toMinutes() < 1 -> "Just now"
            timeSince.toMinutes() < 60 -> "${timeSince.toMinutes()}m ago"
            timeSince.toHours() < 24 -> "${timeSince.toHours()}h ago"
            else -> "${timeSince.toDays()}d ago"
        }
    }
    
    /**
     * Creates a new presence with updated status
     */
    fun updateStatus(newStatus: PresenceStatus, workoutId: String? = null): UserPresence = copy(
        status = newStatus,
        lastActive = Instant.now(),
        currentWorkoutId = if (newStatus == PresenceStatus.WORKING_OUT) workoutId else null
    )
    
    companion object {
        /**
         * Creates an offline presence
         */
        fun offline(): UserPresence = UserPresence(
            status = PresenceStatus.OFFLINE,
            lastActive = Instant.now()
        )
        
        /**
         * Creates an online presence
         */
        fun online(): UserPresence = UserPresence(
            status = PresenceStatus.ONLINE,
            lastActive = Instant.now()
        )
        
        /**
         * Creates a working out presence
         */
        fun workingOut(workoutId: String): UserPresence = UserPresence(
            status = PresenceStatus.WORKING_OUT,
            lastActive = Instant.now(),
            currentWorkoutId = workoutId
        )
    }
}

/**
 * User presence status enumeration
 */
enum class PresenceStatus(val displayName: String) {
    ONLINE("Online"),
    WORKING_OUT("Working out"),
    IDLE("Away"),
    OFFLINE("Offline")
}