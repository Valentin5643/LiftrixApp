package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.SubscriptionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for subscription-related database operations.
 * Provides CRUD operations for user subscription status tracking.
 */
@Dao
interface SubscriptionDao {

    /**
     * Get subscription status for a specific user
     */
    @Query("SELECT * FROM user_subscriptions WHERE user_id = :userId")
    suspend fun getSubscription(userId: String): SubscriptionEntity?

    /**
     * Get subscription status as Flow for reactive updates
     */
    @Query("SELECT * FROM user_subscriptions WHERE user_id = :userId")
    fun getSubscriptionFlow(userId: String): Flow<SubscriptionEntity?>

    /**
     * Insert or update subscription data
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity)

    /**
     * Update existing subscription
     */
    @Update
    suspend fun updateSubscription(subscription: SubscriptionEntity)

    /**
     * Delete subscription for a user
     */
    @Query("DELETE FROM user_subscriptions WHERE user_id = :userId")
    suspend fun deleteSubscription(userId: String)

    /**
     * Get all active subscriptions (for admin/sync purposes)
     */
    @Query("SELECT * FROM user_subscriptions WHERE status = 'active'")
    suspend fun getActiveSubscriptions(): List<SubscriptionEntity>

    /**
     * Get subscriptions that need sync (for background sync)
     */
    @Query("SELECT * FROM user_subscriptions WHERE is_synced = 0")
    suspend fun getUnsyncedSubscriptions(): List<SubscriptionEntity>

    /**
     * Mark subscription as synced
     */
    @Query("UPDATE user_subscriptions SET is_synced = 1, sync_version = :syncVersion WHERE user_id = :userId")
    suspend fun markAsSynced(userId: String, syncVersion: Long)

    /**
     * Get subscription count for user (should be 0 or 1)
     */
    @Query("SELECT COUNT(*) FROM user_subscriptions WHERE user_id = :userId")
    suspend fun getSubscriptionCount(userId: String): Int

    /**
     * Check if user has active premium subscription
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM user_subscriptions 
            WHERE user_id = :userId 
            AND tier != 'FREE' 
            AND status IN ('active', 'trial')
            AND (expires_at IS NULL OR expires_at > :currentTime)
        )
    """)
    suspend fun hasActivePremiumSubscription(userId: String, currentTime: Long): Boolean
}