package com.example.liftrix.domain.model

/**
 * Enum representing the fitness goals a user can have.
 * Each enum constant has a user-friendly display name.
 */
enum class FitnessGoal(val displayName: String) {
    LOSE_WEIGHT("Lose Weight"),
    BUILD_MUSCLE("Build Muscle"),
    IMPROVE_ENDURANCE("Improve Endurance"),
    INCREASE_STRENGTH("Increase Strength"),
    IMPROVE_FLEXIBILITY("Improve Flexibility"),
    GENERAL_FITNESS("General Fitness"),
    SPORT_SPECIFIC("Sport-Specific Training")
} 