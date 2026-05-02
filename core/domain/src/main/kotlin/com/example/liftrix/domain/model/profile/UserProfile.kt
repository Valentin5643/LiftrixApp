package com.example.liftrix.domain.model

import java.time.LocalDateTime

/**
 * Domain model for a user's fitness profile.
 * Enhanced with social features, achievements, and privacy controls.
 */
data class UserProfile(
    val userId: String,
    val displayName: String,
    val bio: String?,
    val age: Int?,
    val weight: Weight?,
    val availableEquipment: List<Equipment>,
    val otherEquipment: String?,
    val fitnessGoals: List<FitnessGoal>,
    val goalsPriority: Map<FitnessGoal, Int>?,
    val isPublic: Boolean = true,
    val lastActiveAt: LocalDateTime?,
    val totalWorkouts: Int = 0,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val memberSince: LocalDateTime,
    val profileCompletionPercentage: Int = 0,
    val achievements: List<UserAchievement> = emptyList(),
    val completedAt: LocalDateTime?,
    val updatedAt: LocalDateTime,
    val profileVersion: Long = 1,
    val profileImageUrl: String? = null,
    val profileImageUpdatedAt: LocalDateTime? = null,
    val hasCustomProfileImage: Boolean = false
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        age?.let {
            require(it in MIN_AGE..MAX_AGE) { "Age must be between $MIN_AGE and $MAX_AGE" }
        }
        otherEquipment?.let {
            require(it.length <= MAX_OTHER_EQUIPMENT_LENGTH) {
                "Other equipment description cannot exceed $MAX_OTHER_EQUIPMENT_LENGTH characters."
            }
        }
        goalsPriority?.let {
            require(it.keys.containsAll(fitnessGoals)) {
                "Priority map must contain all selected fitness goals."
            }
        }
    }

    val isComplete: Boolean
        get() = age != null && weight != null && fitnessGoals.isNotEmpty()

    fun validateAge(): Boolean = age != null && age in MIN_AGE..MAX_AGE

    companion object {
        const val MIN_AGE: Int = 13
        const val MAX_AGE: Int = 100
        const val MAX_OTHER_EQUIPMENT_LENGTH: Int = 200

        /**
         * Creates a minimal user profile with only required fields populated.
         * Used for satisfying foreign key constraints when user data is minimal.
         */
        fun createMinimal(userId: String): UserProfile {
            val now = LocalDateTime.now()
            return UserProfile(
                userId = userId,
                displayName = "User",
                bio = null,
                age = null,
                weight = null,
                availableEquipment = emptyList(),
                otherEquipment = null,
                fitnessGoals = emptyList(),
                goalsPriority = null,
                isPublic = true,
                lastActiveAt = null,
                totalWorkouts = 0,
                currentStreak = 0,
                longestStreak = 0,
                memberSince = now,
                profileCompletionPercentage = 0,
                achievements = emptyList(),
                completedAt = null,
                updatedAt = now,
                profileVersion = 1,
                profileImageUrl = null,
                profileImageUpdatedAt = null,
                hasCustomProfileImage = false
            )
        }
    }
}
