@file:JvmName("GoalIdKt")

package com.example.liftrix.domain.model

import java.util.UUID

/**
 * Value class representing a unique goal identifier
 */
@JvmInline
value class GoalId(val value: String) {
    companion object {
        fun generate(): GoalId = GoalId(UUID.randomUUID().toString())
        fun fromString(value: String): GoalId = GoalId(value)
    }
    
    override fun toString(): String = value
}