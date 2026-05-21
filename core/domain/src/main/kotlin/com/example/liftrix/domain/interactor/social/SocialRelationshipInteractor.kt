package com.example.liftrix.domain.interactor.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.FollowStatus
import com.example.liftrix.domain.model.social.ReportReason
import com.example.liftrix.domain.usecase.social.FollowAction
import com.example.liftrix.domain.usecase.social.SocialRelationshipUseCase
import javax.inject.Inject

class SocialRelationshipInteractor @Inject constructor(
    private val socialRelationshipUseCase: SocialRelationshipUseCase
) {
    suspend fun followAction(
        targetUserId: String,
        action: FollowAction,
        context: String = "PROFILE_VIEW"
    ): LiftrixResult<FollowStatus> =
        socialRelationshipUseCase.followAction(targetUserId, action, context)

    suspend fun blockUser(
        targetUserId: String,
        shouldBlock: Boolean
    ): LiftrixResult<Unit> =
        socialRelationshipUseCase.blockUser(targetUserId, shouldBlock)

    suspend fun reportUser(
        targetUserId: String,
        reason: ReportReason,
        description: String? = null
    ): LiftrixResult<Unit> =
        socialRelationshipUseCase.reportUser(targetUserId, reason, description)
}
