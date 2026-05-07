package com.example.liftrix.domain.usecase.guest

import com.example.liftrix.domain.model.GuestSession
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.SignificantInteraction
import com.example.liftrix.domain.repository.GuestSessionRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages guest user sessions including workout limits and nudging logic
 */
@Singleton
class ManageGuestSessionUseCaseImpl @Inject constructor(
    private val guestSessionRepository: GuestSessionRepository
) : ManageGuestSessionUseCase {

    /**
     * Gets the current guest session or creates a new one
     */
    override suspend fun getOrCreateGuestSession(userId: String): LiftrixResult<GuestSession> {
        return try {
            val result = guestSessionRepository.getGuestSession(userId)
            result.fold(
                onSuccess = { guestSession ->
                    guestSession?.let { Result.success(it) } ?: createNewGuestSession(userId)
                },
                onFailure = { createNewGuestSession(userId) }
            )
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to get guest session: ${e.message}"))
        }
    }

    /**
     * Records a completed workout for a guest user
     */
    override suspend fun recordWorkoutCompleted(userId: String): LiftrixResult<GuestSession> {
        return try {
            val sessionResult = getOrCreateGuestSession(userId)
            sessionResult.fold(
                onSuccess = { guestSession ->
                    val updatedSession = guestSession.recordWorkout()
                    guestSessionRepository.saveGuestSession(updatedSession).fold(
                        onSuccess = { Result.success(updatedSession) },
                        onFailure = { error -> Result.failure(error) }
                    )
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to record workout: ${e.message}"))
        }
    }

    /**
     * Records a significant interaction from a guest user
     */
    override suspend fun recordSignificantInteraction(
        userId: String, 
        interaction: SignificantInteraction
    ): LiftrixResult<GuestSession> {
        return try {
            val sessionResult = getOrCreateGuestSession(userId)
            sessionResult.fold(
                onSuccess = { guestSession ->
                    val updatedSession = guestSession.recordSignificantInteraction()
                    guestSessionRepository.saveGuestSession(updatedSession).fold(
                        onSuccess = { Result.success(updatedSession) },
                        onFailure = { error -> Result.failure(error) }
                    )
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to record interaction: ${e.message}"))
        }
    }

    /**
     * Records that a nudge was shown to the user
     */
    override suspend fun recordNudgeShown(userId: String): LiftrixResult<GuestSession> {
        return try {
            val sessionResult = getOrCreateGuestSession(userId)
            sessionResult.fold(
                onSuccess = { guestSession ->
                    val updatedSession = guestSession.recordNudgeShown()
                    guestSessionRepository.saveGuestSession(updatedSession).fold(
                        onSuccess = { Result.success(updatedSession) },
                        onFailure = { error -> Result.failure(error) }
                    )
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to record nudge: ${e.message}"))
        }
    }

    /**
     * Marks that the user has seen the limit warning
     */
    override suspend fun markLimitWarningSeen(userId: String): LiftrixResult<GuestSession> {
        return try {
            val sessionResult = getOrCreateGuestSession(userId)
            sessionResult.fold(
                onSuccess = { guestSession ->
                    val updatedSession = guestSession.markLimitWarningSeen()
                    guestSessionRepository.saveGuestSession(updatedSession).fold(
                        onSuccess = { Result.success(updatedSession) },
                        onFailure = { error -> Result.failure(error) }
                    )
                },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to mark warning seen: ${e.message}"))
        }
    }

    /**
     * Checks if a nudge should be shown to the user
     */
    override suspend fun shouldShowNudge(userId: String): LiftrixResult<Boolean> {
        return try {
            val sessionResult = getOrCreateGuestSession(userId)
            sessionResult.fold(
                onSuccess = { guestSession -> Result.success(guestSession.shouldShowNudge()) },
                onFailure = { Result.success(false) } // Default to not showing nudge on error
            )
        } catch (e: Exception) {
            Result.success(false) // Default to not showing nudge on error
        }
    }

    /**
     * Checks if the limit warning should be shown
     */
    override suspend fun shouldShowLimitWarning(userId: String): LiftrixResult<Boolean> {
        return try {
            val sessionResult = getOrCreateGuestSession(userId)
            sessionResult.fold(
                onSuccess = { guestSession -> Result.success(guestSession.shouldShowLimitWarning()) },
                onFailure = { Result.success(false) }
            )
        } catch (e: Exception) {
            Result.success(false)
        }
    }

    /**
     * Gets the number of workouts remaining for the guest user
     */
    override suspend fun getWorkoutsRemaining(userId: String): LiftrixResult<Int> {
        return try {
            val sessionResult = getOrCreateGuestSession(userId)
            sessionResult.fold(
                onSuccess = { guestSession -> Result.success(guestSession.getWorkoutsRemaining()) },
                onFailure = { Result.success(GuestSession.DEFAULT_MAX_WORKOUTS) }
            )
        } catch (e: Exception) {
            Result.success(GuestSession.DEFAULT_MAX_WORKOUTS)
        }
    }

    /**
     * Checks if the guest session is still active (within limits)
     */
    override suspend fun isGuestSessionActive(userId: String): LiftrixResult<Boolean> {
        return try {
            val sessionResult = getOrCreateGuestSession(userId)
            sessionResult.fold(
                onSuccess = { guestSession -> Result.success(guestSession.isActive()) },
                onFailure = { Result.success(true) } // Default to active on error
            )
        } catch (e: Exception) {
            Result.success(true)
        }
    }

    /**
     * Gets an appropriate nudge message for the current session state
     */
    override suspend fun getNudgeMessage(userId: String): LiftrixResult<String> {
        return try {
            val sessionResult = getOrCreateGuestSession(userId)
            sessionResult.fold(
                onSuccess = { guestSession -> Result.success(guestSession.getNudgeMessage()) },
                onFailure = { Result.success("Create a free account to save your progress.") }
            )
        } catch (e: Exception) {
            Result.success("Create a free account to save your progress.")
        }
    }

    /**
     * Clears the guest session (when user signs up)
     */
    override suspend fun clearGuestSession(userId: String): LiftrixResult<Unit> {
        return try {
            guestSessionRepository.clearGuestSession(userId)
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to clear guest session: ${e.message}"))
        }
    }

    private suspend fun createNewGuestSession(userId: String): LiftrixResult<GuestSession> {
        return try {
            val newSession = GuestSession.create(userId)
            guestSessionRepository.saveGuestSession(newSession).fold(
                onSuccess = { Result.success(newSession) },
                onFailure = { error -> Result.failure(error) }
            )
        } catch (e: Exception) {
            Result.failure(LiftrixError.UnknownError("Failed to create guest session: ${e.message}"))
        }
    }
}
