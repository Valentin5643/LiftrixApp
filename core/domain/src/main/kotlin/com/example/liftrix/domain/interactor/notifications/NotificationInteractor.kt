package com.example.liftrix.domain.interactor.notifications

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.notifications.NotificationPreferences
import com.example.liftrix.domain.usecase.notifications.GetMutedUsersCountUseCase
import com.example.liftrix.domain.usecase.notifications.NotificationPreferencesUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class NotificationInteractor @Inject constructor(
    private val notificationPreferencesUseCase: NotificationPreferencesUseCase,
    private val getMutedUsersCountUseCase: GetMutedUsersCountUseCase
) {
    suspend fun preferences(userId: String): LiftrixResult<NotificationPreferences> =
        notificationPreferencesUseCase(userId)

    suspend fun isCategoryEnabled(userId: String, category: String): LiftrixResult<Boolean> =
        notificationPreferencesUseCase.isCategoryEnabled(userId, category)

    suspend fun mutedUsers(userId: String): LiftrixResult<List<String>> =
        notificationPreferencesUseCase.getMutedUsers(userId)

    suspend fun isUserMuted(userId: String, targetUserId: String): LiftrixResult<Boolean> =
        notificationPreferencesUseCase.isUserMuted(userId, targetUserId)

    suspend fun mutedUsersCount(userId: String): LiftrixResult<Int> =
        notificationPreferencesUseCase.getMutedUsersCount(userId)

    suspend fun update(preferences: NotificationPreferences): LiftrixResult<Unit> =
        notificationPreferencesUseCase.update(preferences)

    suspend fun reset(userId: String): LiftrixResult<Unit> =
        notificationPreferencesUseCase.reset(userId)

    suspend fun toggleCategory(
        userId: String,
        category: String,
        enabled: Boolean
    ): LiftrixResult<Unit> = notificationPreferencesUseCase.toggleCategory(userId, category, enabled)

    suspend fun toggleNotifications(userId: String, enabled: Boolean): LiftrixResult<Unit> =
        notificationPreferencesUseCase.toggleNotifications(userId, enabled)

    suspend fun updateQuietHours(
        userId: String,
        enabled: Boolean,
        startHour: Int,
        endHour: Int
    ): LiftrixResult<Unit> =
        notificationPreferencesUseCase.updateQuietHours(userId, enabled, startHour, endHour)

    suspend fun muteUser(userId: String, targetUserId: String): LiftrixResult<Unit> =
        notificationPreferencesUseCase.muteUser(userId, targetUserId)

    suspend fun unmuteUser(userId: String, targetUserId: String): LiftrixResult<Unit> =
        notificationPreferencesUseCase.unmuteUser(userId, targetUserId)

    suspend fun clearAllMutedUsers(userId: String): LiftrixResult<Unit> =
        notificationPreferencesUseCase.clearAllMutedUsers(userId)

    suspend fun observeMutedUsersCount(): Flow<LiftrixResult<Int>> =
        getMutedUsersCountUseCase()

    suspend fun observeMutedUsersCountForUser(userId: String): Flow<LiftrixResult<Int>> =
        getMutedUsersCountUseCase.getMutedUsersCountForUser(userId)

    suspend fun currentMutedUsersCount(): LiftrixResult<Int> =
        getMutedUsersCountUseCase.getCurrentCount()

    suspend fun hasAnyMutedUsers(): LiftrixResult<Boolean> =
        getMutedUsersCountUseCase.hasAnyMutedUsers()

    suspend fun observeMutedUsersList(): Flow<LiftrixResult<List<String>>> =
        getMutedUsersCountUseCase.getMutedUsersList()
}
