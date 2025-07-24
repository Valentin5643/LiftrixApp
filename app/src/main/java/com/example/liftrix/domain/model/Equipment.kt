package com.example.liftrix.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

/**
 * Enum representing the types of fitness equipment available to a user.
 * Each enum constant has a user-friendly display name.
 */
@Parcelize
@Serializable
enum class Equipment(val displayName: String) : Parcelable {
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

/**
 * Type alias for equipment identifier
 */
typealias EquipmentId = String

/**
 * Enum representing equipment categories for grouping
 */
@Parcelize
@Serializable
enum class EquipmentCategory(val displayName: String) : Parcelable {
    WEIGHTS("Weights"),
    CARDIO("Cardio"),
    BODYWEIGHT("Bodyweight"),
    ACCESSORIES("Accessories")
} 