@file:JvmName("UserIdKt")

package com.example.liftrix.domain.model

/**
 * Value class representing a unique user identifier
 */
@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "User ID cannot be blank" }
    }
    
    companion object {
        fun fromString(value: String): UserId = UserId(value)
    }
    
    override fun toString(): String = value
}