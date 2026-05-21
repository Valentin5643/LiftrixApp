package com.example.liftrix.domain.interactor.social

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.social.PostComment
import com.example.liftrix.domain.usecase.social.CopyWorkoutFromPostUseCase
import com.example.liftrix.domain.usecase.social.PostEngagementUseCase
import com.example.liftrix.domain.usecase.social.RecordShareUseCase
import javax.inject.Inject

class SocialEngagementInteractor @Inject constructor(
    private val postEngagementUseCase: PostEngagementUseCase,
    private val recordShareUseCase: RecordShareUseCase,
    private val copyWorkoutFromPostUseCase: CopyWorkoutFromPostUseCase
) {
    suspend fun toggleLike(postId: String): LiftrixResult<Boolean> =
        postEngagementUseCase.toggleLike(postId)

    suspend fun toggleSave(postId: String): LiftrixResult<Boolean> =
        postEngagementUseCase.toggleSave(postId)

    suspend fun createComment(
        postId: String,
        content: String,
        parentCommentId: String? = null
    ): LiftrixResult<PostComment> =
        postEngagementUseCase.createComment(postId, content, parentCommentId)

    suspend fun getEngagementStatus(
        postId: String
    ): LiftrixResult<PostEngagementUseCase.PostEngagementStatus> =
        postEngagementUseCase.getEngagementStatus(postId)

    suspend fun recordShare(postId: String, shareMethod: String): LiftrixResult<Unit> =
        recordShareUseCase(postId, shareMethod)

    suspend fun copyWorkoutFromPost(postId: String): LiftrixResult<String> =
        copyWorkoutFromPostUseCase(postId)
}
