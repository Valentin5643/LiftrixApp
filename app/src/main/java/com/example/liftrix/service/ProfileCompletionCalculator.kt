package com.example.liftrix.service

import com.example.liftrix.domain.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for calculating user profile completion percentage.
 * Evaluates completeness based on filled fields and their relative importance.
 */
@Singleton
class ProfileCompletionCalculator @Inject constructor() {

    /**
     * Calculates the completion percentage of a user profile.
     * 
     * @param profile The user profile to evaluate
     * @return Completion percentage (0-100)
     */
    fun calculateCompletion(profile: UserProfile): Int {
        var totalScore = 0
        var maxPossibleScore = 0

        // Essential fields (high weight)
        ESSENTIAL_FIELDS.forEach { (field, weight) ->
            maxPossibleScore += weight
            if (isFieldComplete(profile, field)) {
                totalScore += weight
            }
        }

        // Important fields (medium weight)
        IMPORTANT_FIELDS.forEach { (field, weight) ->
            maxPossibleScore += weight
            if (isFieldComplete(profile, field)) {
                totalScore += weight
            }
        }

        // Optional fields (low weight)
        OPTIONAL_FIELDS.forEach { (field, weight) ->
            maxPossibleScore += weight
            if (isFieldComplete(profile, field)) {
                totalScore += weight
            }
        }

        // Calculate percentage, ensuring it doesn't exceed 100%
        return if (maxPossibleScore > 0) {
            ((totalScore.toFloat() / maxPossibleScore.toFloat()) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }
    }

    /**
     * Checks if a specific field is considered complete for the given profile.
     * 
     * @param profile The user profile to check
     * @param field The field identifier to evaluate
     * @return True if the field is complete, false otherwise
     */
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
            FIELD_OTHER_EQUIPMENT -> if (profile.availableEquipment.any { it.name.contains("Other", ignoreCase = true) }) {
                !profile.otherEquipment.isNullOrBlank()
            } else true // Not required if "Other" equipment not selected
            else -> false
        }
    }

    /**
     * Gets a detailed breakdown of profile completion by field category.
     * 
     * @param profile The user profile to analyze
     * @return Map of category to completion information
     */
    fun getCompletionBreakdown(profile: UserProfile): Map<String, CompletionInfo> {
        val breakdown = mutableMapOf<String, CompletionInfo>()

        // Calculate essential fields completion
        val essentialCompleted = ESSENTIAL_FIELDS.count { (field, _) -> isFieldComplete(profile, field) }
        breakdown[CATEGORY_ESSENTIAL] = CompletionInfo(
            completed = essentialCompleted,
            total = ESSENTIAL_FIELDS.size,
            percentage = (essentialCompleted.toFloat() / ESSENTIAL_FIELDS.size * 100).toInt()
        )

        // Calculate important fields completion
        val importantCompleted = IMPORTANT_FIELDS.count { (field, _) -> isFieldComplete(profile, field) }
        breakdown[CATEGORY_IMPORTANT] = CompletionInfo(
            completed = importantCompleted,
            total = IMPORTANT_FIELDS.size,
            percentage = (importantCompleted.toFloat() / IMPORTANT_FIELDS.size * 100).toInt()
        )

        // Calculate optional fields completion
        val optionalCompleted = OPTIONAL_FIELDS.count { (field, _) -> isFieldComplete(profile, field) }
        breakdown[CATEGORY_OPTIONAL] = CompletionInfo(
            completed = optionalCompleted,
            total = OPTIONAL_FIELDS.size,
            percentage = if (OPTIONAL_FIELDS.isNotEmpty()) {
                (optionalCompleted.toFloat() / OPTIONAL_FIELDS.size * 100).toInt()
            } else 100
        )

        return breakdown
    }

    /**
     * Gets suggestions for improving profile completion.
     * 
     * @param profile The user profile to analyze
     * @return List of suggestions for incomplete fields
     */
    fun getCompletionSuggestions(profile: UserProfile): List<String> {
        val suggestions = mutableListOf<String>()

        // Check essential fields first
        ESSENTIAL_FIELDS.forEach { (field, _) ->
            if (!isFieldComplete(profile, field)) {
                suggestions.add(getFieldSuggestion(field))
            }
        }

        // Then important fields
        IMPORTANT_FIELDS.forEach { (field, _) ->
            if (!isFieldComplete(profile, field)) {
                suggestions.add(getFieldSuggestion(field))
            }
        }

        // Finally optional fields (only if essential and important are complete)
        if (suggestions.isEmpty()) {
            OPTIONAL_FIELDS.forEach { (field, _) ->
                if (!isFieldComplete(profile, field)) {
                    suggestions.add(getFieldSuggestion(field))
                }
            }
        }

        return suggestions
    }

    /**
     * Gets a user-friendly suggestion message for a specific field.
     */
    private fun getFieldSuggestion(field: String): String {
        return when (field) {
            FIELD_DISPLAY_NAME -> "Add a display name to help others identify you"
            FIELD_AGE -> "Add your age to get personalized workout recommendations"
            FIELD_WEIGHT -> "Add your weight for accurate calorie and progress tracking"
            FIELD_FITNESS_GOALS -> "Select your fitness goals to customize your experience"
            FIELD_EQUIPMENT -> "Select available equipment to get relevant workout suggestions"
            FIELD_BIO -> "Add a bio to tell others about your fitness journey"
            FIELD_GOALS_PRIORITY -> "Prioritize your fitness goals for better recommendations"
            FIELD_OTHER_EQUIPMENT -> "Describe your other equipment for more accurate suggestions"
            else -> "Complete this field to improve your profile"
        }
    }

    /**
     * Data class representing completion information for a category.
     */
    data class CompletionInfo(
        val completed: Int,
        val total: Int,
        val percentage: Int
    )

    companion object {
        // Field identifiers
        private const val FIELD_DISPLAY_NAME = "display_name"
        private const val FIELD_AGE = "age"
        private const val FIELD_WEIGHT = "weight"
        private const val FIELD_FITNESS_GOALS = "fitness_goals"
        private const val FIELD_EQUIPMENT = "equipment"
        private const val FIELD_BIO = "bio"
        private const val FIELD_GOALS_PRIORITY = "goals_priority"
        private const val FIELD_OTHER_EQUIPMENT = "other_equipment"

        // Category names
        private const val CATEGORY_ESSENTIAL = "Essential"
        private const val CATEGORY_IMPORTANT = "Important"
        private const val CATEGORY_OPTIONAL = "Optional"

        // Minimum bio length to be considered complete
        private const val MIN_BIO_LENGTH = 10

        // Field weights for completion calculation
        private val ESSENTIAL_FIELDS = mapOf(
            FIELD_DISPLAY_NAME to 20,  // Required for identification
            FIELD_FITNESS_GOALS to 25, // Core for personalization
            FIELD_EQUIPMENT to 20      // Essential for workout recommendations
        )

        private val IMPORTANT_FIELDS = mapOf(
            FIELD_AGE to 15,           // Important for safety and personalization
            FIELD_WEIGHT to 15         // Important for tracking and calculations
        )

        private val OPTIONAL_FIELDS = mapOf(
            FIELD_BIO to 10,           // Nice to have for social features
            FIELD_GOALS_PRIORITY to 8, // Helpful for fine-tuning recommendations
            FIELD_OTHER_EQUIPMENT to 7  // Conditional on equipment selection
        )
    }
}