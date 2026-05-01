package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class WorkoutStatus(val displayName: String) {
    PLANNED("Planned"),
    IN_PROGRESS("In Progress"),
    PAUSED("Paused"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled")
}