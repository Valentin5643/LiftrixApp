package com.example.liftrix.domain.usecase.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.onboarding.OnboardingDataSnapshot
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.repository.social.FollowRepository
import com.example.liftrix.domain.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

class ApplyOfficialOnboardingFollowsUseCase @Inject constructor(
    private val seedWorkoutPostsUseCase: SeedWorkoutPostsUseCase,
    private val followRepository: FollowRepository,
    private val syncScheduler: SyncScheduler
) {

    suspend operator fun invoke(
        userId: String,
        onboardingData: OnboardingDataSnapshot
    ): LiftrixResult<List<String>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "APPLY_OFFICIAL_ONBOARDING_FOLLOWS_FAILED",
                errorMessage = "Failed to apply official onboarding follows: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "APPLY_OFFICIAL_ONBOARDING_FOLLOWS",
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            seedWorkoutPostsUseCase(userId).getOrThrow()

            val targetAccountIds = OfficialLiftrixAccountCatalog.matchAccountIds(
                selectedEquipment = onboardingData.selectedEquipment,
                selectedGoals = onboardingData.selectedGoals
            )

            val followedAccountIds = mutableListOf<String>()
            for (targetAccountId in targetAccountIds) {
                val existingStatus = followRepository.getFollowStatus(userId, targetAccountId).getOrThrow()
                if (existingStatus == FollowStatus.FOLLOWING || existingStatus == FollowStatus.PENDING_SENT) {
                    Timber.d("Official onboarding follow already exists: user=$userId target=$targetAccountId status=$existingStatus")
                    continue
                }

                followRepository.sendFollowRequest(
                    followerId = userId,
                    targetUserId = targetAccountId,
                    requestSource = REQUEST_SOURCE
                ).getOrThrow()
                followedAccountIds += targetAccountId
            }

            if (followedAccountIds.isNotEmpty()) {
                syncScheduler.enqueueFollowRelationshipSync(userId = userId, forceSync = true)
            }

            followedAccountIds
        }
    }

    private companion object {
        private const val REQUEST_SOURCE = "ONBOARDING_INTERESTS"
    }
}
