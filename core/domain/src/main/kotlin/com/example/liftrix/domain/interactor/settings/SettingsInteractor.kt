package com.example.liftrix.domain.interactor.settings

import com.example.liftrix.domain.model.SubscriptionStatus
import com.example.liftrix.domain.model.UserSettings
import com.example.liftrix.domain.model.WeightUnit
import com.example.liftrix.domain.usecase.settings.SettingsQueryUseCase
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingsInteractor @Inject constructor(
    private val settingsQueryUseCase: SettingsQueryUseCase
) {
    suspend fun userSettings(userId: String): Flow<Result<UserSettings>> =
        settingsQueryUseCase(userId)

    fun weightUnitPreference(userId: String): Flow<WeightUnit> =
        settingsQueryUseCase.getWeightUnitPreference(userId)

    suspend fun subscriptionStatus(userId: String): Flow<Result<SubscriptionStatus>> =
        settingsQueryUseCase.getSubscriptionStatus(userId)

    suspend fun hasUserSettings(userId: String): Boolean =
        settingsQueryUseCase.hasUserSettings(userId)

    suspend fun hasActivePremiumSubscription(userId: String): Flow<Result<Boolean>> =
        settingsQueryUseCase.hasActivePremiumSubscription(userId)

    suspend fun syncUserSettings(userId: String): Result<Unit> =
        settingsQueryUseCase.syncUserSettings(userId)

    suspend fun syncSubscriptionStatus(userId: String): Result<Unit> =
        settingsQueryUseCase.syncSubscriptionStatus(userId)
}
