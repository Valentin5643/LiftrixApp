package com.example.liftrix.core.identity

/**
 * Extension functions for UserId type for common patterns.
 */

// Extension for String to UserId conversion
fun String.toUserId(): UserId = UserId(this)

// Extension for nullable String
fun String?.toUserIdOrNull(): UserId? = this?.let { UserId(it) }

// Extension: Analytics context with explicit unwrapping
fun UserId.toAnalyticsContext(key: String = "user_id"): Map<String, String> =
    mapOf(key to value)

// Extension: Firestore document path construction
fun UserId.toFirestorePath(collection: String = "users"): String =
    "$collection/$value"

fun UserId.toWorkoutsPath(): String = "users/$value/workouts"
fun UserId.toTemplatesPath(): String = "users/$value/templates"
fun UserId.toAchievementsPath(): String = "users/$value/achievements"
fun UserId.toGymBuddiesPath(): String = "users/$value/gym_buddies"
fun UserId.toSettingsPath(): String = "users/$value/settings"
