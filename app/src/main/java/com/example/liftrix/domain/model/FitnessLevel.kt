package com.example.liftrix.domain.model

/**
 * Enum representing the fitness level of a user.
 * Used for matching users with similar experience levels and workout recommendations.
 */
enum class FitnessLevel(val displayName: String, val description: String) {
    BEGINNER(
        displayName = "Beginner",
        description = "New to fitness or returning after a long break"
    ),
    INTERMEDIATE(
        displayName = "Intermediate", 
        description = "Regular workout routine for 6+ months"
    ),
    ADVANCED(
        displayName = "Advanced",
        description = "Experienced with consistent training for 2+ years"
    ),
    EXPERT(
        displayName = "Expert",
        description = "Professional athlete or trainer level"
    )
}