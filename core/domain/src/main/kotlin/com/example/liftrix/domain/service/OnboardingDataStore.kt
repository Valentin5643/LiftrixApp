package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.onboarding.OnboardingDataSnapshot

interface OnboardingDataStore {
    suspend fun storeOnboardingData(profileData: OnboardingDataSnapshot): LiftrixResult<Unit>

    suspend fun hasPendingOnboardingData(): Boolean

    suspend fun retrievePendingOnboardingData(newUserId: String): LiftrixResult<OnboardingDataSnapshot?>

    suspend fun clearPendingOnboardingData(): LiftrixResult<Unit>

    suspend fun getPendingDataSummary(): String
}
