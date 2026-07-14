package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.AiUsageEntity

@Dao
interface AiUsageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: AiUsageEntity): Long

    @Query("SELECT COUNT(*) FROM ai_usage WHERE user_id = :userId AND created_at >= :since")
    suspend fun countCallsSince(userId: String, since: Long): Int

    @Query("SELECT COALESCE(SUM(total_tokens), 0) FROM ai_usage WHERE user_id = :userId AND created_at >= :since")
    suspend fun tokenUsageSince(userId: String, since: Long): Int
}
