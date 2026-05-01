package com.example.liftrix.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@JvmInline
value class TemplateExerciseId(val value: String) {
    companion object {
        fun generate(): TemplateExerciseId = TemplateExerciseId(UUID.randomUUID().toString())
    }
}