package com.example.liftrix.domain.usecase.guest

import com.example.liftrix.domain.model.GuestSession
import com.example.liftrix.domain.model.SignificantInteraction
import com.example.liftrix.domain.model.common.LiftrixResult

interface ManageGuestSessionUseCase {
    suspend fun getOrCreateGuestSession(userId: String): LiftrixResult<GuestSession>

    suspend fun recordWorkoutCompleted(userId: String): LiftrixResult<GuestSession>

    suspend fun recordSignificantInteraction(
        userId: String,
        interaction: SignificantInteraction
    ): LiftrixResult<GuestSession>

    suspend fun recordNudgeShown(userId: String): LiftrixResult<GuestSession>

    suspend fun markLimitWarningSeen(userId: String): LiftrixResult<GuestSession>

    suspend fun shouldShowNudge(userId: String): LiftrixResult<Boolean>

    suspend fun shouldShowLimitWarning(userId: String): LiftrixResult<Boolean>

    suspend fun getWorkoutsRemaining(userId: String): LiftrixResult<Int>

    suspend fun isGuestSessionActive(userId: String): LiftrixResult<Boolean>

    suspend fun getNudgeMessage(userId: String): LiftrixResult<String>

    suspend fun clearGuestSession(userId: String): LiftrixResult<Unit>
}
