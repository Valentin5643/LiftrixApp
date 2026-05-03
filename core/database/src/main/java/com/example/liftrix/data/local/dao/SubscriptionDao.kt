package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
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

    // ========== OFFLINE-FIRST ARCHITECTURE METHODS (SPEC-20241228) ==========

    /**
     * Upsert subscription from LOCAL origin (user edit).
     * Sets isDirty=true and lastModified, triggering sync queue.
     */
    suspend fun upsertLocal(subscription: SubscriptionEntity) {
        val entity = subscription.copy(
            isDirty = true,
            lastModified = System.currentTimeMillis()
        )
        _insert(entity)
    }

    /**
     * Upsert subscription from REMOTE origin (Firestore listener/sync).
     * Sets isDirty=false, only applies if remote is newer.
     * Does NOT trigger sync queue.
     */
    @Transaction
    suspend fun upsertFromRemote(subscription: SubscriptionEntity) {
        val local = getSubscriptionForSync(subscription.userId)
        if (local == null || subscription.lastModified > local.lastModified) {
            val entity = subscription.copy(
                isDirty = false,
                isSynced = true,
                syncVersion = System.currentTimeMillis()
            )
            _insert(entity)
        }
    }

    /**
     * Internal insert for shared logic.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun _insert(entity: SubscriptionEntity)

    /**
     * Get dirty subscription that need upload to Firestore.
     */
    @Query("SELECT * FROM user_subscriptions WHERE user_id = :userId AND is_dirty = 1 ORDER BY last_modified ASC")
    suspend fun getDirtySubscriptions(userId: String): List<SubscriptionEntity>

    /**
     * Mark subscription as clean after successful Firestore upload.
     */
    @Query("UPDATE user_subscriptions SET is_dirty = 0, is_synced = 1, sync_version = :syncVersion WHERE user_id IN (:ids) AND user_id = :userId")
    suspend fun markAsClean(ids: List<String>, userId: String, syncVersion: Long = System.currentTimeMillis()): Int

    /**
     * Get local subscription for remote deduplication.
     */
    @Query("SELECT * FROM user_subscriptions WHERE user_id = :userId LIMIT 1")
    suspend fun getSubscriptionForSync(userId: String): SubscriptionEntity?

    // ========== END OFFLINE-FIRST METHODS ==========
}
