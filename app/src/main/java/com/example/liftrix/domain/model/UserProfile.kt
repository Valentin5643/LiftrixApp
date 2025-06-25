package com.example.liftrix.domain.model

import java.time.LocalDateTime

/**
 * Domain model for a user's fitness profile.
 * Contains all data collected during the onboarding flow.
 */
data class UserProfile(
    val userId: String,
    val age: Int?,
    val weight: Weight?,
    val availableEquipment: List<Equipment>,
    val otherEquipment: String?,
    val fitnessGoals: List<FitnessGoal>,
    val goalsPriority: Map<FitnessGoal, Int>?,
    val completedAt: LocalDateTime?,
    val updatedAt: LocalDateTime,
    val profileVersion: Long = 1
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

    companion object {
        const val MIN_AGE: Int = 13
        const val MAX_AGE: Int = 100
        const val MAX_OTHER_EQUIPMENT_LENGTH: Int = 200
    }

    val isComplete: Boolean
        get() = age != null && weight != null && fitnessGoals.isNotEmpty()

    fun validateAge(): Boolean = age != null && age in MIN_AGE..MAX_AGE
} 