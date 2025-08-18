package com.example.liftrix.data.repository

import com.example.liftrix.data.local.dao.UserAccountDao
import com.example.liftrix.data.mapper.UserAccountMapper
import com.example.liftrix.domain.model.UserAccount
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.UserAccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository implementation for user account management operations.
 * 
 * Handles local storage of account information including email, username, and metadata.
 * All operations are user-scoped and include proper error handling.
 */
@Singleton
class UserAccountRepositoryImpl @Inject constructor(
    private val userAccountDao: UserAccountDao
) : UserAccountRepository {

    override fun getAccountInfo(userId: String): Flow<UserAccount?> {
        return userAccountDao.getAccountForUser(userId)
            .map { entity -> entity?.let { UserAccountMapper.toDomain(it) } }
            .catch { exception ->
                Timber.e(exception, "Error reading account info for user: $userId")
                emit(null)
            }
    }

    override suspend fun getAccountInfoSuspend(userId: String): LiftrixResult<UserAccount?> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve account information",
                    operation = "GET_ACCOUNT_INFO",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val entity = userAccountDao.getAccountForUserSuspend(userId)
            entity?.let { UserAccountMapper.toDomain(it) }
        }
    }

    override suspend fun updateUsername(userId: String, username: String?): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                when {
                    throwable.message?.contains("UNIQUE constraint failed") == true -> {
                        LiftrixError.ValidationError(
                            field = "username",
                            violations = listOf("Username is already taken"),
                            analyticsContext = mapOf("user_id" to userId, "username" to (username ?: "null"))
                        )
                    }
                    else -> LiftrixError.DatabaseError(
                        errorMessage = "Failed to update username",
                        operation = "UPDATE_USERNAME",
                        analyticsContext = mapOf("user_id" to userId, "username" to (username ?: "null"))
                    )
                }
            }
        ) {
            // Only check availability if username is not null (not removing username)
            username?.let { nonNullUsername ->
                val isAvailable = !userAccountDao.isUsernameExistsForOtherUser(nonNullUsername, userId)
                if (!isAvailable) {
                    throw LiftrixError.ValidationError(
                        field = "username",
                        violations = listOf("Username is already taken"),
                        analyticsContext = mapOf("user_id" to userId, "username" to nonNullUsername)
                    )
                }
            }
            
            // Update the username
            val rowsUpdated = userAccountDao.updateUsername(userId, username)
            if (rowsUpdated == 0) {
                throw LiftrixError.NotFoundError(
                    errorMessage = "User account not found",
                    resourceType = "UserAccount",
                    resourceId = userId,
                    analyticsContext = mapOf("operation" to "UPDATE_USERNAME")
                )
            }
            
            Timber.d("Username updated successfully for user: $userId to: $username")
        }
    }

    override suspend fun checkUsernameAvailability(username: String): LiftrixResult<Boolean> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to check username availability",
                    operation = "CHECK_USERNAME_AVAILABILITY",
                    analyticsContext = mapOf("username" to username)
                )
            }
        ) {
            !userAccountDao.isUsernameExists(username)
        }
    }

    override suspend fun upsertAccountInfo(userAccount: UserAccount): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to save account information",
                    operation = "UPSERT_ACCOUNT_INFO",
                    analyticsContext = mapOf("user_id" to userAccount.userId)
                )
            }
        ) {
            val existingAccount = userAccountDao.getAccountForUserSuspend(userAccount.userId)
            
            val entity = if (existingAccount != null) {
                // Update existing account
                UserAccountMapper.updateEntity(existingAccount, userAccount, markAsUnsynced = true)
            } else {
                // Create new account
                UserAccountMapper.toEntity(userAccount, isSynced = false, syncVersion = 1L)
            }
            
            userAccountDao.insertAccount(entity)
            Timber.d("Account info upserted for user: ${userAccount.userId}")
        }
    }

    override suspend fun updateEmail(userId: String, email: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update email address",
                    operation = "UPDATE_EMAIL",
                    analyticsContext = mapOf("user_id" to userId, "email" to email)
                )
            }
        ) {
            val rowsUpdated = userAccountDao.updateEmail(userId, email, LocalDateTime.now())
            if (rowsUpdated == 0) {
                throw LiftrixError.NotFoundError(
                    errorMessage = "User account not found",
                    resourceType = "UserAccount",
                    resourceId = userId,
                    analyticsContext = mapOf("operation" to "UPDATE_EMAIL")
                )
            }
            
            Timber.d("Email updated successfully for user: $userId")
        }
    }

    override suspend fun updateEmailVerified(userId: String, isVerified: Boolean): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update email verification status",
                    operation = "UPDATE_EMAIL_VERIFIED",
                    analyticsContext = mapOf("user_id" to userId, "verified" to isVerified.toString())
                )
            }
        ) {
            val rowsUpdated = userAccountDao.updateEmailVerified(userId, isVerified)
            if (rowsUpdated == 0) {
                throw LiftrixError.NotFoundError(
                    errorMessage = "User account not found",
                    resourceType = "UserAccount",
                    resourceId = userId,
                    analyticsContext = mapOf("operation" to "UPDATE_EMAIL_VERIFIED")
                )
            }
            
            Timber.d("Email verification status updated for user: $userId to: $isVerified")
        }
    }

    override suspend fun updateDisplayName(userId: String, displayName: String?): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update display name",
                    operation = "UPDATE_DISPLAY_NAME",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val rowsUpdated = userAccountDao.updateDisplayName(userId, displayName)
            if (rowsUpdated == 0) {
                throw LiftrixError.NotFoundError(
                    errorMessage = "User account not found",
                    resourceType = "UserAccount",
                    resourceId = userId,
                    analyticsContext = mapOf("operation" to "UPDATE_DISPLAY_NAME")
                )
            }
            
            Timber.d("Display name updated successfully for user: $userId")
        }
    }

    override suspend fun updatePasswordChangeTime(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to update password change time",
                    operation = "UPDATE_PASSWORD_CHANGE_TIME",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val rowsUpdated = userAccountDao.updatePasswordChangeTime(userId, LocalDateTime.now())
            if (rowsUpdated == 0) {
                throw LiftrixError.NotFoundError(
                    errorMessage = "User account not found",
                    resourceType = "UserAccount",
                    resourceId = userId,
                    analyticsContext = mapOf("operation" to "UPDATE_PASSWORD_CHANGE_TIME")
                )
            }
            
            Timber.d("Password change time updated for user: $userId")
        }
    }

    override suspend fun scheduleAccountDeletion(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to schedule account deletion",
                    operation = "SCHEDULE_ACCOUNT_DELETION",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val rowsUpdated = userAccountDao.markForDeletion(userId, LocalDateTime.now())
            if (rowsUpdated == 0) {
                throw LiftrixError.NotFoundError(
                    errorMessage = "User account not found",
                    resourceType = "UserAccount",
                    resourceId = userId,
                    analyticsContext = mapOf("operation" to "SCHEDULE_ACCOUNT_DELETION")
                )
            }
            
            Timber.d("Account deletion scheduled for user: $userId")
        }
    }

    override suspend fun cancelAccountDeletion(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to cancel account deletion",
                    operation = "CANCEL_ACCOUNT_DELETION",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val rowsUpdated = userAccountDao.cancelDeletion(userId)
            if (rowsUpdated == 0) {
                throw LiftrixError.NotFoundError(
                    errorMessage = "User account not found",
                    resourceType = "UserAccount",
                    resourceId = userId,
                    analyticsContext = mapOf("operation" to "CANCEL_ACCOUNT_DELETION")
                )
            }
            
            Timber.d("Account deletion cancelled for user: $userId")
        }
    }

    override suspend fun getAccountsReadyForDeletion(): LiftrixResult<List<UserAccount>> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to retrieve accounts ready for deletion",
                    operation = "GET_ACCOUNTS_READY_FOR_DELETION"
                )
            }
        ) {
            val cutoffTime = LocalDateTime.now().minusHours(24) // 24-hour grace period
            val entities = userAccountDao.getAccountsReadyForDeletion(cutoffTime)
            entities.map { UserAccountMapper.toDomain(it) }
        }
    }

    override suspend fun deleteAccount(userId: String): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to delete account",
                    operation = "DELETE_ACCOUNT",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val rowsDeleted = userAccountDao.deleteAccountForUser(userId)
            if (rowsDeleted == 0) {
                throw LiftrixError.NotFoundError(
                    errorMessage = "User account not found",
                    resourceType = "UserAccount",
                    resourceId = userId,
                    analyticsContext = mapOf("operation" to "DELETE_ACCOUNT")
                )
            }
            
            Timber.d("Account permanently deleted for user: $userId")
        }
    }

    override suspend fun syncAccountFromFirebase(
        userId: String,
        email: String,
        displayName: String?
    ): LiftrixResult<Unit> {
        return liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.DatabaseError(
                    errorMessage = "Failed to sync account from Firebase",
                    operation = "SYNC_ACCOUNT_FROM_FIREBASE",
                    analyticsContext = mapOf("user_id" to userId)
                )
            }
        ) {
            val existingAccount = userAccountDao.getAccountForUserSuspend(userId)
            
            val userAccount = if (existingAccount != null) {
                // Update existing account with Firebase data
                val domain = UserAccountMapper.toDomain(existingAccount)
                domain.copy(
                    email = email,
                    displayName = displayName
                )
            } else {
                // Create new account from Firebase data
                UserAccount.create(
                    userId = userId,
                    email = email,
                    displayName = displayName
                )
            }
            
            val entity = UserAccountMapper.toEntity(userAccount, isSynced = true, syncVersion = 1L)
            userAccountDao.insertAccount(entity)
            
            Timber.d("Account synced from Firebase for user: $userId")
        }
    }
}