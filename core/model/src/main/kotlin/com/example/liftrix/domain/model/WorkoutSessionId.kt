package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@JvmInline
value class WorkoutSessionId(val value: String) {
    companion object {
        fun generate(): WorkoutSessionId = WorkoutSessionId(UUID.randomUUID().toString())
    }
}