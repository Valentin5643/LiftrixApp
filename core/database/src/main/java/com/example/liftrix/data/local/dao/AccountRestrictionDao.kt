package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.liftrix.data.local.entity.AccountRestrictionEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for managing account restrictions.
 *
 * All queries are user-scoped to prevent data leakage.
 */
@Dao
interface AccountRestrictionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(restriction: AccountRestrictionEntity)

    /**
     * Get all active restrictions for a user.
     * Active = start_time <= now AND (end_time > now OR end_time IS NULL)
     *
     * @param userId The user ID (MANDATORY for user scoping)
     * @param currentTime Current timestamp for filtering active restrictions
     */
    @Query("""
        SELECT * FROM account_restrictions
        WHERE user_id = :userId
          AND start_time <= :currentTime
          AND (end_time > :currentTime OR end_time IS NULL)
        ORDER BY created_at DESC
    """)
    suspend fun getActiveRestrictions(
        userId: String,
        currentTime: Long = System.currentTimeMillis()
    ): List<AccountRestrictionEntity>

    /**
     * Observe active restrictions for a user in real-time.
     *
     * @param userId The user ID (MANDATORY for user scoping)
     * @param currentTime Current timestamp for filtering
     */
    @Query("""
        SELECT * FROM account_restrictions
        WHERE user_id = :userId
          AND start_time <= :currentTime
          AND (end_time > :currentTime OR end_time IS NULL)
        ORDER BY created_at DESC
    """)
    fun observeActiveRestrictions(
        userId: String,
        currentTime: Long = System.currentTimeMillis()
    ): Flow<List<AccountRestrictionEntity>>

    /**
     * Get all restrictions for a user (including expired).
     *
     * @param userId The user ID (MANDATORY for user scoping)
     */
    @Query("""
        SELECT * FROM account_restrictions
        WHERE user_id = :userId
        ORDER BY created_at DESC
    """)
    suspend fun getAllRestrictions(userId: String): List<AccountRestrictionEntity>

    /**
     * Check if user has active restriction of specific type.
     *
     * @param userId The user ID (MANDATORY for user scoping)
     * @param restrictionType The restriction type to check (SUSPENDED, WARNED, RESTRICTED)
     * @param currentTime Current timestamp
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM account_restrictions
        WHERE user_id = :userId
          AND restriction_type = :restrictionType
          AND start_time <= :currentTime
          AND (end_time > :currentTime OR end_time IS NULL)
    """)
    suspend fun hasActiveRestriction(
        userId: String,
        restrictionType: String,
        currentTime: Long = System.currentTimeMillis()
    ): Boolean

    /**
     * Delete restriction by ID.
     *
     * @param restrictionId The restriction ID to delete
     */
    @Query("DELETE FROM account_restrictions WHERE id = :restrictionId")
    suspend fun deleteRestriction(restrictionId: String)

    /**
     * Delete all restrictions for a user (used during account deletion).
     *
     * @param userId The user ID (MANDATORY for user scoping)
     */
    @Query("DELETE FROM account_restrictions WHERE user_id = :userId")
    suspend fun deleteAllForUser(userId: String)

    /**
     * Remove expired restrictions (cleanup).
     *
     * @param currentTime Current timestamp
     */
    @Query("""
        DELETE FROM account_restrictions
        WHERE end_time IS NOT NULL
          AND end_time < :currentTime
    """)
    suspend fun deleteExpiredRestrictions(currentTime: Long = System.currentTimeMillis())
}
