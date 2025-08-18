package com.example.liftrix.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.liftrix.data.local.entity.UserAccountEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

/**
 * Data Access Object for user account management operations.
 * 
 * All queries are user-scoped to prevent data leakage between users.
 * Implements the standard sync and CRUD patterns used throughout the app.
 */
@Dao
interface UserAccountDao {
    
    // Primary account queries with user scoping
    
    @Query("SELECT * FROM user_accounts WHERE user_id = :userId")
    fun getAccountForUser(userId: String): Flow<UserAccountEntity?>
    
    @Query("SELECT * FROM user_accounts WHERE user_id = :userId")
    suspend fun getAccountForUserSuspend(userId: String): UserAccountEntity?
    
    @Query("SELECT email FROM user_accounts WHERE user_id = :userId")
    suspend fun getEmailForUser(userId: String): String?
    
    @Query("SELECT username FROM user_accounts WHERE user_id = :userId")
    suspend fun getUsernameForUser(userId: String): String?
    
    @Query("SELECT email_verified FROM user_accounts WHERE user_id = :userId")
    suspend fun isEmailVerifiedForUser(userId: String): Boolean?
    
    // Username availability and uniqueness checks
    
    @Query("SELECT EXISTS(SELECT 1 FROM user_accounts WHERE username = :username)")
    suspend fun isUsernameExists(username: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM user_accounts WHERE username = :username AND user_id != :excludeUserId)")
    suspend fun isUsernameExistsForOtherUser(username: String, excludeUserId: String): Boolean
    
    @Query("SELECT user_id FROM user_accounts WHERE username = :username")
    suspend fun getUserIdByUsername(username: String): String?
    
    // Account management operations
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: UserAccountEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<UserAccountEntity>): List<Long>
    
    @Update
    suspend fun updateAccount(account: UserAccountEntity): Int
    
    @Query("UPDATE user_accounts SET email = :email, last_email_update = :updateTime WHERE user_id = :userId")
    suspend fun updateEmail(userId: String, email: String, updateTime: LocalDateTime): Int
    
    @Query("UPDATE user_accounts SET username = :username WHERE user_id = :userId")
    suspend fun updateUsername(userId: String, username: String?): Int
    
    @Query("UPDATE user_accounts SET email_verified = :isVerified WHERE user_id = :userId")
    suspend fun updateEmailVerified(userId: String, isVerified: Boolean): Int
    
    @Query("UPDATE user_accounts SET display_name = :displayName WHERE user_id = :userId")
    suspend fun updateDisplayName(userId: String, displayName: String?): Int
    
    @Query("UPDATE user_accounts SET last_password_change = :changeTime WHERE user_id = :userId")
    suspend fun updatePasswordChangeTime(userId: String, changeTime: LocalDateTime): Int
    
    // Account deletion management
    
    @Query("UPDATE user_accounts SET deletion_requested_at = :requestTime WHERE user_id = :userId")
    suspend fun markForDeletion(userId: String, requestTime: LocalDateTime): Int
    
    @Query("UPDATE user_accounts SET deletion_requested_at = NULL WHERE user_id = :userId")
    suspend fun cancelDeletion(userId: String): Int
    
    @Query("SELECT * FROM user_accounts WHERE deletion_requested_at IS NOT NULL")
    suspend fun getAccountsMarkedForDeletion(): List<UserAccountEntity>
    
    @Query("SELECT * FROM user_accounts WHERE deletion_requested_at <= :cutoffTime")
    suspend fun getAccountsReadyForDeletion(cutoffTime: LocalDateTime): List<UserAccountEntity>
    
    @Delete
    suspend fun deleteAccount(account: UserAccountEntity): Int
    
    @Query("DELETE FROM user_accounts WHERE user_id = :userId")
    suspend fun deleteAccountForUser(userId: String): Int
    
    // Sync management
    
    @Query("SELECT * FROM user_accounts WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedAccount(userId: String): UserAccountEntity?
    
    @Query("SELECT COUNT(*) FROM user_accounts WHERE user_id = :userId AND is_synced = 0")
    suspend fun getUnsyncedAccountCount(userId: String): Int
    
    @Query("UPDATE user_accounts SET is_synced = :isSynced, sync_version = :version WHERE user_id = :userId")
    suspend fun updateSyncStatus(userId: String, isSynced: Boolean, version: Long): Int
    
    @Query("UPDATE user_accounts SET is_synced = 1, sync_version = :version WHERE user_id = :userId")
    suspend fun markAccountAsSynced(userId: String, version: Long): Int
    
    // Existence and validation checks
    
    @Query("SELECT EXISTS(SELECT 1 FROM user_accounts WHERE user_id = :userId)")
    suspend fun hasAccount(userId: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM user_accounts WHERE email = :email)")
    suspend fun isEmailExists(email: String): Boolean
    
    @Query("SELECT EXISTS(SELECT 1 FROM user_accounts WHERE email = :email AND user_id != :excludeUserId)")
    suspend fun isEmailExistsForOtherUser(email: String, excludeUserId: String): Boolean
    
    // Statistics and analytics
    
    @Query("SELECT COUNT(*) FROM user_accounts WHERE account_created_at >= :sinceTime")
    suspend fun getAccountsCreatedSince(sinceTime: LocalDateTime): Int
    
    @Query("SELECT COUNT(*) FROM user_accounts WHERE username IS NOT NULL")
    suspend fun getAccountsWithUsernames(): Int
    
    @Query("SELECT COUNT(*) FROM user_accounts WHERE email_verified = 1")
    suspend fun getVerifiedAccountsCount(): Int
    
    // Debug and maintenance
    
    @Query("DELETE FROM user_accounts")
    suspend fun deleteAllAccounts(): Int
    
    @Query("SELECT * FROM user_accounts ORDER BY account_created_at DESC")
    suspend fun getAllAccounts(): List<UserAccountEntity>
    
    @Query("SELECT COUNT(*) FROM user_accounts")
    suspend fun getTotalAccountsCount(): Int
}