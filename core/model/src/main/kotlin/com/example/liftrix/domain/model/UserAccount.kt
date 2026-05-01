package com.example.liftrix.domain.model

import java.time.LocalDateTime

/**
 * Domain model representing a user's account information.
 * 
 * Contains account-level data including credentials, username, and account status.
 * This is separate from the main User model to handle account management operations.
 */
data class UserAccount(
    val userId: String,
    val email: String,
    val username: String? = null,
    val emailVerified: Boolean = false,
    val displayName: String? = null,
    val lastPasswordChange: LocalDateTime? = null,
    val accountCreatedAt: LocalDateTime,
    val lastEmailUpdate: LocalDateTime? = null,
    val deletionRequestedAt: LocalDateTime? = null
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(email.isNotBlank() && email.contains("@")) { "Valid email is required" }
        username?.let { name ->
            require(name.length in 3..20) { "Username must be between 3 and 20 characters" }
            require(name.matches(Regex("^[a-zA-Z0-9_]+$"))) { "Username can only contain letters, numbers, and underscores" }
        }
    }
    
    /**
     * Indicates if the account is scheduled for deletion
     */
    val isDeletionPending: Boolean = deletionRequestedAt != null
    
    /**
     * Returns the time remaining until deletion (in hours) if deletion is scheduled
     */
    val deletionTimeRemainingHours: Long? = deletionRequestedAt?.let { requestTime ->
        val deletionTime = requestTime.plusHours(24) // 24-hour grace period
        val now = LocalDateTime.now()
        if (now.isBefore(deletionTime)) {
            java.time.Duration.between(now, deletionTime).toHours()
        } else {
            0L // Ready for deletion
        }
    }
    
    /**
     * Indicates if the account can be deleted (grace period has passed)
     */
    val canBeDeleted: Boolean = deletionRequestedAt?.let { requestTime ->
        LocalDateTime.now().isAfter(requestTime.plusHours(24))
    } ?: false
    
    /**
     * Returns a copy with updated email address
     */
    fun withUpdatedEmail(newEmail: String): UserAccount = copy(
        email = newEmail,
        lastEmailUpdate = LocalDateTime.now(),
        emailVerified = false // Email verification reset on change
    )
    
    /**
     * Returns a copy with updated username
     */
    fun withUpdatedUsername(newUsername: String?): UserAccount = copy(
        username = newUsername
    )
    
    /**
     * Returns a copy with updated email verification status
     */
    fun withEmailVerified(verified: Boolean): UserAccount = copy(
        emailVerified = verified
    )
    
    /**
     * Returns a copy with updated display name
     */
    fun withUpdatedDisplayName(newDisplayName: String?): UserAccount = copy(
        displayName = newDisplayName
    )
    
    /**
     * Returns a copy with password change timestamp updated
     */
    fun withPasswordChanged(): UserAccount = copy(
        lastPasswordChange = LocalDateTime.now()
    )
    
    /**
     * Returns a copy with deletion scheduled
     */
    fun withDeletionScheduled(): UserAccount = copy(
        deletionRequestedAt = LocalDateTime.now()
    )
    
    /**
     * Returns a copy with deletion cancelled
     */
    fun withDeletionCancelled(): UserAccount = copy(
        deletionRequestedAt = null
    )
    
    companion object {
        /**
         * Creates a new UserAccount from basic user information
         */
        fun create(
            userId: String,
            email: String,
            displayName: String? = null,
            username: String? = null
        ): UserAccount = UserAccount(
            userId = userId,
            email = email,
            username = username,
            emailVerified = false,
            displayName = displayName,
            accountCreatedAt = LocalDateTime.now()
        )
    }
}