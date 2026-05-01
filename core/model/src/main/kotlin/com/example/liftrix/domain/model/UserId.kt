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
        fun fromNullable(value: String?): UserId? = value
            ?.takeIf { it.isNotBlank() }
            ?.let(::UserId)

        fun fromStringOrNull(value: String?): UserId? = fromNullable(value)
    }
    
    override fun toString(): String = value
}
