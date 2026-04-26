package com.example.liftrix.core.identity

import kotlinx.serialization.Serializable
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError

/**
 * Type-safe wrapper for Firebase user IDs to prevent data leakage bugs.
 *
 * Guarantees at compile-time:
 * - Non-blank Firebase UID
 * - Explicit construction (prevents String parameter mixing)
 * - Zero runtime overhead (inline value class)
 *
 * Usage:
 * ```kotlin
 * // ✅ Correct - explicit construction
 * val userId = UserId(firebaseAuth.currentUser.uid)
 * workoutRepository.getWorkouts(userId)
 *
 * // ❌ Compile error - cannot pass String
 * workoutRepository.getWorkouts("user123")
 *
 * // ❌ Compile error - cannot pass wrong String variable
 * val workoutName = "Morning Workout"
 * workoutRepository.getWorkouts(workoutName)
 * ```
 *
 * @property value The underlying Firebase UID string
 */
@Serializable
@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "UserId cannot be blank. Firebase UIDs must be non-empty. Received: '$value'"
        }
        require(value.length >= 10) {
            "UserId too short. Firebase UIDs are typically 28+ characters. Received length: ${value.length}"
        }
    }

    companion object {
        /**
         * Creates UserId from nullable String with LiftrixResult error handling.
         * Use in repositories/use cases for graceful error handling.
         *
         * @param value Raw Firebase UID string (nullable)
         * @return LiftrixResult.Success(UserId) or LiftrixResult.Error
         */
        fun fromString(value: String?): LiftrixResult<UserId> {
            if (value.isNullOrBlank()) {
                return LiftrixResult.failure(
                    LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf("User ID cannot be blank"),
                        analyticsContext = mapOf("operation" to "USERID_FROM_STRING")
                    )
                )
            }
            return try {
                LiftrixResult.success(UserId(value))
            } catch (e: IllegalArgumentException) {
                LiftrixResult.failure(
                    LiftrixError.ValidationError(
                        field = "userId",
                        violations = listOf(e.message ?: "Invalid user ID format"),
                        analyticsContext = mapOf("operation" to "USERID_FROM_STRING")
                    )
                )
            }
        }
    }

    override fun toString(): String = "UserId(***${value.takeLast(4)})"  // Redact for logs
}
