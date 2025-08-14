package com.example.liftrix.data.repository.social

import com.example.liftrix.data.local.dao.GymBuddyDao
import com.example.liftrix.data.local.entity.GymBuddyEntity
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.social.GymBuddy
import com.example.liftrix.domain.repository.social.GymBuddyRepository
import com.example.liftrix.domain.repository.social.GymBuddyStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of GymBuddyRepository with enhanced QR pairing and limit enforcement
 */
@Singleton
class GymBuddyRepositoryImpl @Inject constructor(
    private val gymBuddyDao: GymBuddyDao
) : GymBuddyRepository {

    companion object {
        private const val MAX_GYM_BUDDIES = 5
    }

    override suspend fun getGymBuddies(userId: String): LiftrixResult<List<GymBuddy>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to retrieve gym buddies",
                operation = "GET_GYM_BUDDIES",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        Timber.d("Getting gym buddies for user: $userId")
        val entities = gymBuddyDao.getGymBuddies(userId)
        entities.map { it.toDomain() }
    }

    override fun observeGymBuddies(userId: String): Flow<List<GymBuddy>> {
        return gymBuddyDao.observeGymBuddies(userId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getGymBuddy(userId: String, buddyId: String): LiftrixResult<GymBuddy?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to retrieve gym buddy",
                operation = "GET_GYM_BUDDY",
                analyticsContext = mapOf("user_id" to userId, "buddy_id" to buddyId)
            )
        }
    ) {
        val entity = gymBuddyDao.getGymBuddy(userId, buddyId)
        entity?.toDomain()
    }

    override suspend fun areMutualGymBuddies(userId: String, buddyId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to check mutual gym buddy relationship",
                operation = "CHECK_MUTUAL_GYM_BUDDIES",
                analyticsContext = mapOf("user_id" to userId, "buddy_id" to buddyId)
            )
        }
    ) {
        gymBuddyDao.areMutualGymBuddies(userId, buddyId)
    }

    override suspend fun getGymBuddyCount(userId: String): LiftrixResult<Int> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get gym buddy count",
                operation = "GET_GYM_BUDDY_COUNT",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        gymBuddyDao.getGymBuddyCount(userId)
    }

    override suspend fun createMutualConnection(
        userId1: String,
        userId2: String,
        viaQr: Boolean,
        location: String?
    ): LiftrixResult<Pair<GymBuddy, GymBuddy>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "CREATE_MUTUAL_CONNECTION_FAILED",
                errorMessage = "Failed to create mutual gym buddy connection: ${throwable.message}",
                analyticsContext = mapOf(
                    "user_id_1" to userId1,
                    "user_id_2" to userId2,
                    "via_qr" to viaQr.toString()
                )
            )
        }
    ) {
        Timber.d("Creating mutual gym buddy connection: $userId1 <-> $userId2, viaQr=$viaQr")
        
        // Validate inputs
        if (userId1 == userId2) {
            throw LiftrixError.ValidationError(
                field = "user_ids",
                violations = listOf("Cannot add yourself as a gym buddy"),
                errorMessage = "Cannot add yourself as a gym buddy"
            )
        }
        
        // Check if already connected
        val alreadyConnected = gymBuddyDao.areMutualGymBuddies(userId1, userId2)
        if (alreadyConnected) {
            throw LiftrixError.BusinessLogicError(
                code = "ALREADY_CONNECTED",
                errorMessage = "Users are already gym buddies",
                analyticsContext = mapOf("user_id_1" to userId1, "user_id_2" to userId2)
            )
        }
        
        // Check buddy limits for both users
        val count1 = gymBuddyDao.getGymBuddyCount(userId1)
        val count2 = gymBuddyDao.getGymBuddyCount(userId2)
        
        if (count1 >= MAX_GYM_BUDDIES) {
            throw LiftrixError.BusinessLogicError(
                code = "BUDDY_LIMIT_EXCEEDED",
                errorMessage = "User already has maximum gym buddies ($MAX_GYM_BUDDIES)",
                analyticsContext = mapOf("user_id" to userId1, "current_count" to count1.toString())
            )
        }
        
        if (count2 >= MAX_GYM_BUDDIES) {
            throw LiftrixError.BusinessLogicError(
                code = "BUDDY_LIMIT_EXCEEDED",
                errorMessage = "Target user already has maximum gym buddies ($MAX_GYM_BUDDIES)",
                analyticsContext = mapOf("user_id" to userId2, "current_count" to count2.toString())
            )
        }
        
        // Create the mutual connections
        val currentTime = System.currentTimeMillis()
        
        val buddy1Entity = GymBuddyEntity(
            id = UUID.randomUUID().toString(),
            userId = userId1,
            buddyId = userId2,
            buddyNickname = null,
            createdAt = currentTime,
            lastPrNotificationSent = null,
            notificationCooldownHours = 24,
            pairedViaQr = viaQr,
            pairingLocation = location,
            isSynced = false,
            syncVersion = 1
        )
        
        val buddy2Entity = GymBuddyEntity(
            id = UUID.randomUUID().toString(),
            userId = userId2,
            buddyId = userId1,
            buddyNickname = null,
            createdAt = currentTime,
            lastPrNotificationSent = null,
            notificationCooldownHours = 24,
            pairedViaQr = viaQr,
            pairingLocation = location,
            isSynced = false,
            syncVersion = 1
        )
        
        // Insert both connections
        gymBuddyDao.insertGymBuddy(buddy1Entity)
        gymBuddyDao.insertGymBuddy(buddy2Entity)
        
        Timber.d("Created mutual gym buddy connection successfully")
        
        Pair(buddy1Entity.toDomain(), buddy2Entity.toDomain())
    }

    override suspend fun removeGymBuddy(userId: String, buddyId: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "REMOVE_GYM_BUDDY_FAILED",
                errorMessage = "Failed to remove gym buddy: ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId, "buddy_id" to buddyId)
            )
        }
    ) {
        Timber.d("Removing gym buddy: $userId -> $buddyId")
        val rowsAffected = gymBuddyDao.deleteGymBuddy(userId, buddyId)
        
        if (rowsAffected == 0) {
            throw LiftrixError.BusinessLogicError(
                code = "GYM_BUDDY_NOT_FOUND",
                errorMessage = "Gym buddy relationship not found",
                analyticsContext = mapOf("user_id" to userId, "buddy_id" to buddyId)
            )
        }
        
        Timber.d("Removed gym buddy successfully")
    }

    override suspend fun removeMutualConnection(userId1: String, userId2: String): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "REMOVE_MUTUAL_CONNECTION_FAILED",
                errorMessage = "Failed to remove mutual connection: ${throwable.message}",
                analyticsContext = mapOf("user_id_1" to userId1, "user_id_2" to userId2)
            )
        }
    ) {
        Timber.d("Removing mutual gym buddy connection: $userId1 <-> $userId2")
        
        val rowsAffected1 = gymBuddyDao.deleteGymBuddy(userId1, userId2)
        val rowsAffected2 = gymBuddyDao.deleteGymBuddy(userId2, userId1)
        
        if (rowsAffected1 == 0 && rowsAffected2 == 0) {
            throw LiftrixError.BusinessLogicError(
                code = "MUTUAL_CONNECTION_NOT_FOUND",
                errorMessage = "No mutual gym buddy connection found",
                analyticsContext = mapOf("user_id_1" to userId1, "user_id_2" to userId2)
            )
        }
        
        Timber.d("Removed mutual connection successfully")
    }

    override suspend fun updateBuddyNickname(
        userId: String,
        buddyId: String,
        nickname: String?
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UPDATE_BUDDY_NICKNAME_FAILED",
                errorMessage = "Failed to update buddy nickname: ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId, "buddy_id" to buddyId)
            )
        }
    ) {
        val rowsAffected = gymBuddyDao.updateBuddyNickname(userId, buddyId, nickname)
        
        if (rowsAffected == 0) {
            throw LiftrixError.BusinessLogicError(
                code = "GYM_BUDDY_NOT_FOUND",
                errorMessage = "Gym buddy relationship not found",
                analyticsContext = mapOf("user_id" to userId, "buddy_id" to buddyId)
            )
        }
    }

    override suspend fun getBuddiesEligibleForPrNotification(userId: String): LiftrixResult<List<GymBuddy>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get buddies eligible for PR notification",
                operation = "GET_ELIGIBLE_BUDDIES",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val entities = gymBuddyDao.getBuddiesEligibleForPrNotification(userId)
        entities.map { it.toDomain() }
    }

    override suspend fun updatePrNotificationSent(
        userId: String,
        buddyId: String,
        timestamp: Long
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UPDATE_PR_NOTIFICATION_FAILED",
                errorMessage = "Failed to update PR notification timestamp: ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId, "buddy_id" to buddyId)
            )
        }
    ) {
        val rowsAffected = gymBuddyDao.updatePrNotificationSent(userId, buddyId, timestamp)
        
        if (rowsAffected == 0) {
            throw LiftrixError.BusinessLogicError(
                code = "GYM_BUDDY_NOT_FOUND",
                errorMessage = "Gym buddy relationship not found",
                analyticsContext = mapOf("user_id" to userId, "buddy_id" to buddyId)
            )
        }
    }

    override suspend fun updateNotificationCooldown(
        userId: String,
        buddyId: String,
        cooldownHours: Int
    ): LiftrixResult<Unit> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "UPDATE_NOTIFICATION_COOLDOWN_FAILED",
                errorMessage = "Failed to update notification cooldown: ${throwable.message}",
                analyticsContext = mapOf("user_id" to userId, "buddy_id" to buddyId)
            )
        }
    ) {
        if (cooldownHours < 1 || cooldownHours > 168) { // 1 hour to 7 days
            throw LiftrixError.ValidationError(
                field = "cooldownHours",
                violations = listOf("Cooldown must be between 1 and 168 hours"),
                errorMessage = "Invalid cooldown hours"
            )
        }
        
        val rowsAffected = gymBuddyDao.updateNotificationCooldown(userId, buddyId, cooldownHours)
        
        if (rowsAffected == 0) {
            throw LiftrixError.BusinessLogicError(
                code = "GYM_BUDDY_NOT_FOUND",
                errorMessage = "Gym buddy relationship not found",
                analyticsContext = mapOf("user_id" to userId, "buddy_id" to buddyId)
            )
        }
    }

    override suspend fun getQrPairedBuddies(userId: String): LiftrixResult<List<GymBuddy>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get QR paired buddies",
                operation = "GET_QR_PAIRED_BUDDIES",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val entities = gymBuddyDao.getQrPairedBuddies(userId)
        entities.map { it.toDomain() }
    }

    override suspend fun canAddMoreBuddies(userId: String): LiftrixResult<Boolean> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to check buddy limit",
                operation = "CHECK_BUDDY_LIMIT",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val currentCount = gymBuddyDao.getGymBuddyCount(userId)
        currentCount < MAX_GYM_BUDDIES
    }

    override suspend fun getGymBuddyStats(userId: String): LiftrixResult<GymBuddyStats> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.DataRetrievalError(
                errorMessage = "Failed to get gym buddy statistics",
                operation = "GET_GYM_BUDDY_STATS",
                analyticsContext = mapOf("user_id" to userId)
            )
        }
    ) {
        val totalBuddies = gymBuddyDao.getGymBuddyCount(userId)
        val qrPairedBuddies = gymBuddyDao.getQrPairedBuddyCount(userId)
        val buddiesWithNotifications = gymBuddyDao.getNotifiedBuddyCount(userId)
        val averageCooldownHours = gymBuddyDao.getAverageCooldownHours(userId) ?: 24.0
        
        GymBuddyStats(
            totalBuddies = totalBuddies,
            qrPairedBuddies = qrPairedBuddies,
            buddiesWithNotifications = buddiesWithNotifications,
            averageCooldownHours = averageCooldownHours
        )
    }

    /**
     * Converts GymBuddyEntity to domain model
     */
    private fun GymBuddyEntity.toDomain(): GymBuddy {
        return GymBuddy(
            id = id,
            userId = userId,
            buddyId = buddyId,
            buddyNickname = buddyNickname,
            createdAt = createdAt,
            lastPrNotificationSent = lastPrNotificationSent,
            notificationCooldownHours = notificationCooldownHours,
            pairedViaQr = pairedViaQr,
            pairingLocation = pairingLocation
        )
    }
}