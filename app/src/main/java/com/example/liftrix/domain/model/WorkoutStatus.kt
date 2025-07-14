package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class WorkoutStatus {
    DRAFT,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}