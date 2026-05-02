package com.example.liftrix.domain.usecase.profile

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.StreakData
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.Weight
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.ProfileRepository
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated use case for all profile command operations.
 */
@Singleton
class ProfileCommandUseCase @Inject constructor(
    private val profileRepository: ProfileRepository
) {
    suspend fun saveProfile(
        profile: UserProfile,
        strictValidation: Boolean = false
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_SAVE_FAILED",
                errorMessage = "Failed to save profile: ${throwable.message}",
                analyticsContext = mapOf("userId" to profile.userId)
            )
        }
    ) {
        require(profile.userId.isNotBlank()) { "Profile must have a valid user ID" }

        val validationError = validateProfile(profile, strictValidation)
        if (validationError != null) {
            throw IllegalArgumentException(validationError)
        }

        val completionPercentage = calculateCompletion(profile)
        val now = LocalDateTime.now()
        val enhancedProfile = profile.copy(
            profileCompletionPercentage = completionPercentage,
            updatedAt = now,
            completedAt = if (completionPercentage >= 100) now else profile.completedAt
        )

        profileRepository.saveUserProfile(enhancedProfile).getOrThrow()
    }

    suspend fun updatePartial(
        userId: String,
        updates: Map<String, Any>
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_UPDATE_FAILED",
                errorMessage = "Failed to update profile: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId, "fieldsUpdated" to updates.keys.toString())
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(updates.isNotEmpty()) { "Updates cannot be empty" }

        profileRepository.updatePartialProfile(userId, updates).getOrThrow()
    }

    suspend fun deleteProfile(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_DELETE_FAILED",
                errorMessage = "Failed to delete profile: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId)
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        profileRepository.deleteProfile(userId).getOrThrow()
    }

    suspend fun syncProfile(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_SYNC_FAILED",
                errorMessage = "Failed to sync profile: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId)
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        profileRepository.syncNow(userId).getOrThrow()
    }

    suspend fun queueSync(userId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_QUEUE_SYNC_FAILED",
                errorMessage = "Failed to queue profile sync: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId)
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        profileRepository.queueSync(userId).getOrThrow()
    }

    suspend fun updateCompletion(userId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_COMPLETION_UPDATE_FAILED",
                errorMessage = "Failed to update profile completion: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId)
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        profileRepository.updateProfileCompletion(userId).getOrThrow()
    }

    suspend fun calculateStreak(userId: String): LiftrixResult<StreakData> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "STREAK_CALCULATION_FAILED",
                errorMessage = "Failed to calculate streak: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId)
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        profileRepository.calculateStreakData(userId).getOrThrow()
    }

    suspend fun updatePrivacy(
        userId: String,
        isPublic: Boolean
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_PRIVACY_UPDATE_FAILED",
                errorMessage = "Failed to update privacy settings: ${throwable.message}",
                analyticsContext = mapOf("userId" to userId, "isPublic" to isPublic.toString())
            )
        }
    ) {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        profileRepository.updatePrivacySettings(userId, isPublic).getOrThrow()
    }

    private fun validateProfile(profile: UserProfile, strictValidation: Boolean = false): String? {
        return validateDisplayName(profile.displayName)
            ?: validateBio(profile.bio)
            ?: validateAge(profile.age, strictValidation)
            ?: validateWeight(profile.weight, strictValidation)
            ?: validateEquipment(profile.availableEquipment, strictValidation)
            ?: validateOtherEquipment(profile.otherEquipment)
            ?: validateGoals(profile.fitnessGoals, strictValidation)
            ?: validateGoalsPriority(profile.fitnessGoals, profile.goalsPriority)
            ?: validateStreakData(profile)
    }

    private fun validateDisplayName(displayName: String): String? {
        return when {
            displayName.isBlank() -> "Display name is required"
            displayName.length > MAX_DISPLAY_NAME_LENGTH -> "Display name cannot exceed $MAX_DISPLAY_NAME_LENGTH characters"
            displayName.length < MIN_DISPLAY_NAME_LENGTH -> "Display name must be at least $MIN_DISPLAY_NAME_LENGTH characters"
            !displayName.matches(DISPLAY_NAME_REGEX) -> "Display name can only contain letters, numbers, spaces, and basic punctuation"
            else -> null
        }
    }

    private fun validateBio(bio: String?): String? {
        if (bio == null) return null
        return when {
            bio.length > MAX_BIO_LENGTH -> "Bio cannot exceed $MAX_BIO_LENGTH characters"
            bio.isBlank() -> "Bio cannot be blank if provided"
            else -> null
        }
    }

    private fun validateAge(age: Int?, strictValidation: Boolean): String? {
        return when {
            age == null && strictValidation -> "Age is required for profile completion"
            age == null -> null
            age < UserProfile.MIN_AGE -> "Age must be at least ${UserProfile.MIN_AGE} years old"
            age > UserProfile.MAX_AGE -> "Age cannot exceed ${UserProfile.MAX_AGE} years"
            else -> null
        }
    }

    private fun validateWeight(weight: Weight?, strictValidation: Boolean): String? {
        return when {
            weight == null && strictValidation -> "Weight is required for profile completion"
            weight == null -> null
            weight.kilograms <= 0.0 -> "Weight must be a positive value"
            weight.kilograms > Weight.MAX_WEIGHT_KG -> "Weight cannot exceed ${Weight.MAX_WEIGHT_KG} kg"
            else -> null
        }
    }

    private fun validateEquipment(equipment: List<Equipment>, strictValidation: Boolean): String? {
        return when {
            equipment.isEmpty() && strictValidation -> "At least one equipment type must be selected"
            equipment.distinct().size != equipment.size -> "Equipment list contains duplicates"
            equipment.size > MAX_EQUIPMENT_SELECTIONS -> "Cannot select more than $MAX_EQUIPMENT_SELECTIONS equipment types"
            else -> null
        }
    }

    private fun validateOtherEquipment(otherEquipment: String?): String? {
        return when {
            otherEquipment != null && otherEquipment.length > UserProfile.MAX_OTHER_EQUIPMENT_LENGTH -> {
                "Other equipment description cannot exceed ${UserProfile.MAX_OTHER_EQUIPMENT_LENGTH} characters"
            }
            otherEquipment != null && otherEquipment.isBlank() -> {
                "Other equipment description cannot be blank if provided"
            }
            else -> null
        }
    }

    private fun validateGoals(goals: List<FitnessGoal>, strictValidation: Boolean): String? {
        return when {
            goals.isEmpty() && strictValidation -> "At least one fitness goal must be selected"
            goals.distinct().size != goals.size -> "Fitness goals list contains duplicates"
            goals.size > MAX_GOAL_SELECTIONS -> "Cannot select more than $MAX_GOAL_SELECTIONS fitness goals"
            else -> null
        }
    }

    private fun validateGoalsPriority(goals: List<FitnessGoal>, priority: Map<FitnessGoal, Int>?): String? {
        if (priority == null) return null

        return when {
            !priority.keys.containsAll(goals) -> "Priority map must contain all selected fitness goals"
            priority.values.any { it < MIN_PRIORITY || it > MAX_PRIORITY } -> {
                "Goal priorities must be between $MIN_PRIORITY and $MAX_PRIORITY"
            }
            priority.values.distinct().size != priority.values.size -> "Goal priorities must be unique values"
            else -> null
        }
    }

    private fun validateStreakData(profile: UserProfile): String? {
        return when {
            profile.currentStreak < 0 -> "Current streak cannot be negative"
            profile.longestStreak < 0 -> "Longest streak cannot be negative"
            profile.totalWorkouts < 0 -> "Total workouts cannot be negative"
            profile.currentStreak > profile.longestStreak -> "Current streak cannot exceed longest streak"
            profile.profileCompletionPercentage < 0 || profile.profileCompletionPercentage > 100 -> {
                "Profile completion percentage must be between 0 and 100"
            }
            else -> null
        }
    }

    private fun calculateCompletion(profile: UserProfile): Int {
        var totalScore = 0
        var maxPossibleScore = 0

        COMPLETION_FIELDS.forEach { (field, weight) ->
            maxPossibleScore += weight
            if (isFieldComplete(profile, field)) {
                totalScore += weight
            }
        }

        return if (maxPossibleScore > 0) {
            ((totalScore.toFloat() / maxPossibleScore.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    private fun isFieldComplete(profile: UserProfile, field: String): Boolean {
        return when (field) {
            FIELD_DISPLAY_NAME -> profile.displayName.isNotBlank()
            FIELD_AGE -> profile.age != null && profile.age > 0
            FIELD_WEIGHT -> profile.weight != null && profile.weight.kilograms > 0
            FIELD_FITNESS_GOALS -> profile.fitnessGoals.isNotEmpty()
            FIELD_EQUIPMENT -> profile.availableEquipment.isNotEmpty()
            FIELD_BIO -> !profile.bio.isNullOrBlank() && profile.bio.length >= MIN_BIO_LENGTH
            FIELD_GOALS_PRIORITY -> profile.goalsPriority != null &&
                profile.goalsPriority.keys.containsAll(profile.fitnessGoals)
            FIELD_OTHER_EQUIPMENT -> if (
                profile.availableEquipment.any { it.name.contains("Other", ignoreCase = true) }
            ) {
                !profile.otherEquipment.isNullOrBlank()
            } else {
                true
            }
            else -> false
        }
    }

    companion object {
        private const val MAX_DISPLAY_NAME_LENGTH = 50
        private const val MIN_DISPLAY_NAME_LENGTH = 2
        private const val MAX_BIO_LENGTH = 500
        private const val MAX_EQUIPMENT_SELECTIONS = 10
        private const val MAX_GOAL_SELECTIONS = 7
        private const val MIN_PRIORITY = 1
        private const val MAX_PRIORITY = 5
        private const val MIN_BIO_LENGTH = 10

        private const val FIELD_DISPLAY_NAME = "display_name"
        private const val FIELD_AGE = "age"
        private const val FIELD_WEIGHT = "weight"
        private const val FIELD_FITNESS_GOALS = "fitness_goals"
        private const val FIELD_EQUIPMENT = "equipment"
        private const val FIELD_BIO = "bio"
        private const val FIELD_GOALS_PRIORITY = "goals_priority"
        private const val FIELD_OTHER_EQUIPMENT = "other_equipment"

        private val DISPLAY_NAME_REGEX = Regex("^[a-zA-Z0-9\\s._-]+$")

        private val COMPLETION_FIELDS = mapOf(
            FIELD_DISPLAY_NAME to 20,
            FIELD_FITNESS_GOALS to 25,
            FIELD_EQUIPMENT to 20,
            FIELD_AGE to 15,
            FIELD_WEIGHT to 15,
            FIELD_BIO to 10,
            FIELD_GOALS_PRIORITY to 8,
            FIELD_OTHER_EQUIPMENT to 7
        )
    }
}
