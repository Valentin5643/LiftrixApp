package com.example.liftrix.domain.model

/**
 * Enum representing the types of fitness equipment available to a user.
 * Each enum constant has a user-friendly display name.
 */
enum class Equipment(val displayName: String) {
    DUMBBELLS("Dumbbells"),
    BARBELL("Barbell"),
    RESISTANCE_BANDS("Resistance Bands"),
    KETTLEBELLS("Kettlebells"),
    PULL_UP_BAR("Pull-up Bar"),
    BENCH("Bench"),
    CABLE_MACHINE("Cable Machine"),
    TREADMILL("Treadmill"),
    EXERCISE_BIKE("Exercise Bike"),
    BODYWEIGHT_ONLY("Bodyweight Only")
} 