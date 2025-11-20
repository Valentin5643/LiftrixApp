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
import com.example.liftrix.service.ProfileCompletionCalculator
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Consolidated use case for all profile command (write) operations.
 *
 * **Replaces**:
 * - SaveProfileUseCase.kt
 * - ValidateProfileInputUseCase.kt (validation logic)
 * - SaveUserProfileUseCase.kt
 * - SyncOnboardingProfileAfterLoginUseCase.kt (sync logic)
 *
 * **Design Philosophy**:
 * - CQRS pattern for write operations
 * - Consistent LiftrixResult error handling
 * - Comprehensive validation before persistence
 * - User scoping enforced for all operations
 *
 * **Usage Examples**:
 * ```kotlin
 * // Save profile (replaces SaveProfileUseCase)
 * val result = profileCommandUseCase.saveProfile(profile)
 *
 * // Update partial profile
 * val updateResult = profileCommandUseCase.updatePartial(
 *     userId = userId,
 *     updates = mapOf("age" to 25, "weight" to 75.0)
 * )
 *
 * // Delete profile
 * val deleteResult = profileCommandUseCase.deleteProfile(userId)
 *
 * // Sync profile
 * val syncResult = profileCommandUseCase.syncProfile(userId)
 * ```
 *
 * @property profileRepository Repository for profile data access
 */
@Singleton
class ProfileCommandUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val profileCompletionCalculator: ProfileCompletionCalculator
) {

    /**
     * Saves a complete user profile with validation and completion calculation.
     * Replaces SaveProfileUseCase.invoke() and SaveUserProfileUseCase.invoke()
     *
     * @param profile The UserProfile to save
     * @param strictValidation If true, enforces required fields (age, weight). Default = false for partial profiles.
     * @return LiftrixResult<Unit> indicating success or validation/persistence error
     */
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

        // Validate profile before saving
        val validationError = validateProfile(profile, strictValidation)
        if (validationError != null) {
            Timber.e("Profile validation failed for user ${profile.userId}: $validationError")
            throw IllegalArgumentException(validationError)
        }

        // Calculate profile completion percentage
        val completionPercentage = profileCompletionCalculator.calculateCompletion(profile)

        // Update profile with completion percentage and timestamp
        val enhancedProfile = profile.copy(
            profileCompletionPercentage = completionPercentage,
            updatedAt = LocalDateTime.now(),
            completedAt = if (completionPercentage >= 100) LocalDateTime.now() else profile.completedAt
        )

        // Save profile through repository
        profileRepository.saveUserProfile(enhancedProfile).getOrThrow()

        Timber.d("Profile saved successfully for user: ${profile.userId}, completion: $completionPercentage%")
    }

    /**
     * Updates partial profile fields.
     * New method for progressive updates.
     *
     * @param userId The user ID whose profile to update
     * @param updates Map of field names to values
     * @return LiftrixResult<Unit> indicating success or error
     */
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

    /**
     * Deletes a user's profile.
     *
     * @param userId The user ID whose profile to delete
     * @return LiftrixResult<Unit> indicating success or error
     */
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

    /**
     * Triggers immediate profile sync.
     * Replaces SyncOnboardingProfileAfterLoginUseCase logic.
     *
     * @param userId The user ID whose profile to sync
     * @return LiftrixResult<Unit> indicating success or error
     */
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

    /**
     * Queues background profile sync.
     * New method for non-urgent sync operations.
     *
     * @param userId The user ID whose profile to sync
     * @return LiftrixResult<Unit> indicating success or error
     */
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

    /**
     * Updates profile completion percentage.
     * New method for tracking onboarding progress.
     *
     * @param userId The user ID to update
     * @return LiftrixResult<Int> with completion percentage
     */
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

    /**
     * Calculates and updates streak data.
     * New method for gamification features.
     *
     * @param userId The user ID to calculate for
     * @return LiftrixResult<StreakData> with calculated streak
     */
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

    /**
     * Updates privacy settings for profile.
     * New method for privacy controls.
     *
     * @param userId The user ID to update
     * @param isPublic Whether profile should be public
     * @return LiftrixResult<Unit> indicating success or error
     */
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

    /**
     * Validates all profile fields according to business rules.
     * Combines validation logic from SaveProfileUseCase and SaveUserProfileUseCase
     *
     * @param profile The profile to validate
     * @param strictValidation If true, enforces required fields (age, weight)
     * @return Error message if validation fails, null if valid
     */
    private fun validateProfile(profile: UserProfile, strictValidation: Boolean = false): String? {
        // Validate display name (always required for SaveUserProfileUseCase)
        val displayNameValidation = validateDisplayName(profile.displayName)
        if (displayNameValidation != null) return displayNameValidation

        // Validate bio if provided
        val bioValidation = validateBio(profile.bio)
        if (bioValidation != null) return bioValidation

        // Validate age
        val ageValidation = validateAge(profile.age, strictValidation)
        if (ageValidation != null) return ageValidation

        // Validate weight
        val weightValidation = validateWeight(profile.weight, strictValidation)
        if (weightValidation != null) return weightValidation

        // Validate equipment selection
        val equipmentValidation = validateEquipment(profile.availableEquipment, strictValidation)
        if (equipmentValidation != null) return equipmentValidation

        // Validate other equipment description
        val otherEquipmentValidation = validateOtherEquipment(profile.otherEquipment)
        if (otherEquipmentValidation != null) return otherEquipmentValidation

        // Validate fitness goals
        val goalsValidation = validateGoals(profile.fitnessGoals, strictValidation)
        if (goalsValidation != null) return goalsValidation

        // Validate goal priorities
        val priorityValidation = validateGoalsPriority(profile.fitnessGoals, profile.goalsPriority)
        if (priorityValidation != null) return priorityValidation

        // Validate streak data consistency
        val streakValidation = validateStreakData(profile)
        if (streakValidation != null) return streakValidation

        return null // All validations passed
    }

    /**
     * Validates display name field.
     * From SaveUserProfileUseCase
     */
    private fun validateDisplayName(displayName: String): String? {
        return when {
            displayName.isBlank() -> "Display name is required"
            displayName.length > MAX_DISPLAY_NAME_LENGTH -> "Display name cannot exceed $MAX_DISPLAY_NAME_LENGTH characters"
            displayName.length < MIN_DISPLAY_NAME_LENGTH -> "Display name must be at least $MIN_DISPLAY_NAME_LENGTH characters"
            !displayName.matches(DISPLAY_NAME_REGEX) -> "Display name can only contain letters, numbers, spaces, and basic punctuation"
            else -> null
        }
    }

    /**
     * Validates bio field if provided.
     * From SaveUserProfileUseCase
     */
    private fun validateBio(bio: String?): String? {
        if (bio == null) return null // Bio is optional

        return when {
            bio.length > MAX_BIO_LENGTH -> "Bio cannot exceed $MAX_BIO_LENGTH characters"
            bio.isBlank() -> "Bio cannot be blank if provided"
            else -> null
        }
    }

    /**
     * Validates age field according to business rules.
     * Combines logic from SaveProfileUseCase (strict) and SaveUserProfileUseCase (optional)
     */
    private fun validateAge(age: Int?, strictValidation: Boolean): String? {
        return when {
            age == null && strictValidation -> "Age is required for profile completion"
            age == null -> null // Age optional for partial profiles
            age < UserProfile.MIN_AGE -> "Age must be at least ${UserProfile.MIN_AGE} years old"
            age > UserProfile.MAX_AGE -> "Age cannot exceed ${UserProfile.MAX_AGE} years"
            else -> null
        }
    }

    /**
     * Validates weight field and value constraints.
     * Combines logic from SaveProfileUseCase (strict) and SaveUserProfileUseCase (optional)
     */
    private fun validateWeight(weight: Weight?, strictValidation: Boolean): String? {
        return when {
            weight == null && strictValidation -> "Weight is required for profile completion"
            weight == null -> null // Weight optional for partial profiles
            weight.kilograms <= 0.0 -> "Weight must be a positive value"
            weight.kilograms > Weight.MAX_WEIGHT_KG -> "Weight cannot exceed ${Weight.MAX_WEIGHT_KG} kg"
            else -> null
        }
    }

    /**
     * Validates equipment selection for duplicates and reasonable limits.
     * Combines logic from both use cases
     */
    private fun validateEquipment(equipment: List<Equipment>, strictValidation: Boolean): String? {
        return when {
            equipment.isEmpty() && strictValidation -> "At least one equipment type must be selected"
            equipment.distinct().size != equipment.size -> "Equipment list contains duplicates"
            equipment.size > MAX_EQUIPMENT_SELECTIONS -> "Cannot select more than $MAX_EQUIPMENT_SELECTIONS equipment types"
            else -> null
        }
    }

    /**
     * Validates other equipment description length and content.
     * From both use cases
     */
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

    /**
     * Validates fitness goals selection.
     * Combines logic from both use cases
     */
    private fun validateGoals(goals: List<FitnessGoal>, strictValidation: Boolean): String? {
        return when {
            goals.isEmpty() && strictValidation -> "At least one fitness goal must be selected"
            goals.distinct().size != goals.size -> "Fitness goals list contains duplicates"
            goals.size > MAX_GOAL_SELECTIONS -> "Cannot select more than $MAX_GOAL_SELECTIONS fitness goals"
            else -> null
        }
    }

    /**
     * Validates goal priority mappings.
     * From both use cases with complete logic
     */
    private fun validateGoalsPriority(goals: List<FitnessGoal>, priority: Map<FitnessGoal, Int>?): String? {
        if (priority == null) return null // Optional field

        return when {
            !priority.keys.containsAll(goals) -> {
                "Priority map must contain all selected fitness goals"
            }
            priority.values.any { it < MIN_PRIORITY || it > MAX_PRIORITY } -> {
                "Goal priorities must be between $MIN_PRIORITY and $MAX_PRIORITY"
            }
            priority.values.distinct().size != priority.values.size -> {
                "Goal priorities must be unique values"
            }
            else -> null
        }
    }

    /**
     * Validates streak data consistency.
     * From SaveUserProfileUseCase
     */
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

    companion object {
        private const val MAX_DISPLAY_NAME_LENGTH = 50
        private const val MIN_DISPLAY_NAME_LENGTH = 2
        private const val MAX_BIO_LENGTH = 500
        private const val MAX_EQUIPMENT_SELECTIONS = 10
        private const val MAX_GOAL_SELECTIONS = 7
        private const val MIN_PRIORITY = 1
        private const val MAX_PRIORITY = 5

        private val DISPLAY_NAME_REGEX = Regex("^[a-zA-Z0-9\\s._-]+$")
    }
}
