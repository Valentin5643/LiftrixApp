package com.example.liftrix.data.service

import com.example.liftrix.data.local.dao.GymBuddyDao
import com.example.liftrix.data.local.dao.PRNotificationDao
import com.example.liftrix.data.local.entity.PRNotificationEntity
import com.example.liftrix.data.local.entity.PersonalRecordEntity
import timber.log.Timber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes saved personal records to the existing gym-buddy PR notification store.
 *
 * Visibility is intentionally limited to mutual gym buddies. A one-sided or removed
 * gym-buddy row is not enough to receive PR updates.
 */
@Singleton
class GymBuddyPRNotificationPublisher @Inject constructor(
    private val gymBuddyDao: GymBuddyDao,
    private val prNotificationDao: PRNotificationDao
) {

    suspend fun publish(personalRecords: List<PersonalRecordEntity>) {
        if (personalRecords.isEmpty()) return

        personalRecords
            .groupBy { it.userId }
            .forEach { (userId, userRecords) ->
                publishForUser(userId, userRecords)
            }
    }

    private suspend fun publishForUser(
        userId: String,
        personalRecords: List<PersonalRecordEntity>
    ) {
        val buddies = gymBuddyDao.getGymBuddies(userId)
            .filter { it.buddyId != userId }
            .filter { gymBuddyDao.areMutualGymBuddies(userId, it.buddyId) }

        if (buddies.isEmpty()) {
            Timber.d("No mutual gym buddies to notify for PRs from user $userId")
            return
        }

        for (personalRecord in personalRecords) {
            for (buddy in buddies) {
                val notification = personalRecord.toNotificationEntity(buddy.buddyId)
                val insertResult = prNotificationDao.insertPRNotification(notification)
                if (insertResult != -1L) {
                    gymBuddyDao.updatePrNotificationSent(userId, buddy.buddyId, notification.sentAt)
                }
            }
        }
    }

    private fun PersonalRecordEntity.toNotificationEntity(toUserId: String): PRNotificationEntity {
        return PRNotificationEntity(
            id = "pr_notification_${UUID.randomUUID()}",
            fromUserId = userId,
            toUserId = toUserId,
            workoutId = workoutId,
            exerciseName = exerciseName,
            prWeight = weightKg,
            prReps = reps,
            prType = prType,
            previousBest = previousBest,
            improvementPercent = improvementPercent,
            sentAt = System.currentTimeMillis(),
            cooldownKey = buildCooldownKey(toUserId)
        )
    }

    private fun PersonalRecordEntity.buildCooldownKey(toUserId: String): String {
        val achievedDate = Instant.ofEpochMilli(achievedAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
        return listOf(
            userId,
            toUserId,
            achievedDate,
            workoutId,
            exerciseName,
            prType
        ).joinToString(separator = ":")
    }
}
