package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExerciseType(val displayName: String) {
    STRENGTH("Strength"),
    CARDIO("Cardio"),
    FLEXIBILITY("Flexibility"),
    MOBILITY("Mobility"),
    BALANCE("Balance"),
    OTHER("Other")
}
