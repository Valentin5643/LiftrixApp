package com.example.liftrix.domain.model

/**
 * Enum representing different difficulty levels for exercises
 */
enum class ExerciseDifficulty(val displayName: String, val level: Int) {
    BEGINNER("Beginner", 1),
    INTERMEDIATE("Intermediate", 2),
    ADVANCED("Advanced", 3);
    
    companion object {
        fun fromLevel(level: Int): ExerciseDifficulty {
            return values().find { it.level == level } ?: BEGINNER
        }
    }
}