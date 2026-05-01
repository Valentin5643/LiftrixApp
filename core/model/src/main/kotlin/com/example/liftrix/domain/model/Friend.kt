package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Domain model representing a friend relationship
 */
data class Friend(
    val userId: String,
    val displayName: String,
    val email: String?,
    val avatarUrl: String?,
    val status: FriendStatus,
    val presence: UserPresence?,
    val friendSince: Instant,
    val isMutual: Boolean = false // True if both users follow each other
) {
    init {
        require(userId.isNotBlank()) { "Friend user ID cannot be blank" }
        require(displayName.isNotBlank()) { "Friend display name cannot be blank" }
        require(displayName.length <= MAX_DISPLAY_NAME_LENGTH) { 
            "Display name cannot exceed $MAX_DISPLAY_NAME_LENGTH characters: ${displayName.length}" 
        }
        
        email?.let { emailValue ->
            require(emailValue.isNotBlank()) { "Email cannot be blank if provided" }
            require(emailValue.contains("@")) { "Email must be a valid email address" }
        }
    }
    
    companion object {
        const val MAX_DISPLAY_NAME_LENGTH: Int = 50
    }
    
    /**
     * Checks if the friend is currently online
     */
    fun isOnline(): Boolean = presence?.status == PresenceStatus.ONLINE || presence?.status == PresenceStatus.WORKING_OUT
    
    /**
     * Checks if the friend is currently working out
     */
    fun isWorkingOut(): Boolean = presence?.status == PresenceStatus.WORKING_OUT
    
    /**
     * Gets the friend's status for display purposes
     */
    fun getDisplayStatus(): String = status.displayName
    
    /**
     * Gets the friend's presence status for display purposes
     */
    fun getPresenceDisplayStatus(): String = presence?.status?.displayName ?: "Offline"
}

/**
 * Friend relationship status enumeration
 */
enum class FriendStatus(val displayName: String) {
    PENDING("Pending"),
    ACCEPTED("Friend"),
    BLOCKED("Blocked")
}

/**
 * Privacy level enumeration for controlling visibility
 */
enum class PrivacyLevel(val displayName: String) {
    ALL_FRIENDS("All Friends"),
    CLOSE_FRIENDS("Close Friends"),
    NONE("Private")
}

/**
 * Workout sharing default enumeration for privacy settings
 */
enum class SharingDefault(val displayName: String) {
    AUTO_SHARE("Auto Share"),
    ASK_EACH_TIME("Ask Each Time"),
    PRIVATE("Private")
}