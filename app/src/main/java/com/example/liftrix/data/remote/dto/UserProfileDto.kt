package com.example.liftrix.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firestore DTO for a user's fitness profile.
 * Contains all data for storage in the /user_profiles collection.
 */
data class UserProfileDto(
    @get:PropertyName("userId") @set:PropertyName("userId") var userId: String = "",
    @get:PropertyName("age") @set:PropertyName("age") var age: Int? = null,
    @get:PropertyName("weight") @set:PropertyName("weight") var weight: WeightDto? = null,
    @get:PropertyName("availableEquipment") @set:PropertyName("availableEquipment") var availableEquipment: List<String> = emptyList(),
    @get:PropertyName("otherEquipment") @set:PropertyName("otherEquipment") var otherEquipment: String? = null,
    @get:PropertyName("fitnessGoals") @set:PropertyName("fitnessGoals") var fitnessGoals: List<String> = emptyList(),
    @get:PropertyName("goalsPriority") @set:PropertyName("goalsPriority") var goalsPriority: Map<String, Int>? = null,
    @get:PropertyName("completedAt") @set:PropertyName("completedAt") var completedAt: Timestamp? = null,
    @get:PropertyName("updatedAt") @set:PropertyName("updatedAt") @ServerTimestamp var updatedAt: Timestamp? = null,
    @get:PropertyName("profileVersion") @set:PropertyName("profileVersion") var profileVersion: Long = 1L
) {
    /**
     * Nested DTO for storing weight with its unit.
     */
    data class WeightDto(
        @get:PropertyName("value") @set:PropertyName("value") var value: Double = 0.0,
        @get:PropertyName("unit") @set:PropertyName("unit") var unit: String = "kg"
    ) {
        // No-argument constructor for Firestore deserialization
        constructor() : this(0.0, "kg")
    }

    // No-argument constructor for Firestore deserialization
    constructor() : this(
        userId = "",
        age = null,
        weight = null,
        availableEquipment = emptyList(),
        otherEquipment = null,
        fitnessGoals = emptyList(),
        goalsPriority = null,
        completedAt = null,
        updatedAt = null,
        profileVersion = 1L
    )
} 