package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionStatus {
    ACTIVE,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED
}