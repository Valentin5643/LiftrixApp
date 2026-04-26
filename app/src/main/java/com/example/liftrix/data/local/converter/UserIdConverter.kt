package com.example.liftrix.data.local.converter

import androidx.room.TypeConverter
import com.example.liftrix.core.identity.UserId

/**
 * Room TypeConverter for UserId inline value class.
 * Enables automatic String ↔ UserId conversion in DAO queries.
 *
 * This allows DAOs to accept UserId parameters while Room stores
 * them as String in the database, maintaining backward compatibility
 * with the existing schema.
 */
object UserIdConverter {
    @TypeConverter
    @JvmStatic
    fun fromUserId(userId: UserId?): String? = userId?.value

    @TypeConverter
    @JvmStatic
    fun toUserId(value: String?): UserId? = value?.let { UserId(it) }
}
