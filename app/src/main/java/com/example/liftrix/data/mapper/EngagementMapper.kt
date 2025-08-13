package com.example.liftrix.data.mapper

import com.example.liftrix.data.local.entity.PostLikeEntity
import com.example.liftrix.data.local.entity.PostCommentEntity
import com.example.liftrix.data.local.entity.SavedPostEntity
import com.example.liftrix.domain.model.social.PostLike
import com.example.liftrix.domain.model.social.PostComment
import com.example.liftrix.domain.model.social.SavedPost
import com.example.liftrix.domain.model.social.CreateCommentRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mapper for converting between engagement domain models and data layer entities.
 * Part of social feed and engagement system from SPEC-20250113-social-feed-engagement.
 */
@Singleton
class EngagementMapper @Inject constructor() {
    
    // ==========================================
    // Post Like Mapping
    // ==========================================
    
    /**
     * Converts PostLikeEntity to PostLike domain model
     */
    fun toDomain(entity: PostLikeEntity): PostLike {
        return PostLike(
            id = entity.id,
            postId = entity.postId,
            userId = entity.userId,
            createdAt = entity.createdAt
        )
    }
    
    /**
     * Converts PostLike domain model to PostLikeEntity
     */
    fun toEntity(domain: PostLike): PostLikeEntity {
        return PostLikeEntity(
            id = domain.id,
            postId = domain.postId,
            userId = domain.userId,
            createdAt = domain.createdAt,
            isSynced = false
        )
    }
    
    /**
     * Creates PostLikeEntity for a new like
     */
    fun createLikeEntity(postId: String, userId: String): PostLikeEntity {
        return PostLikeEntity(
            id = UUID.randomUUID().toString(),
            postId = postId,
            userId = userId,
            createdAt = System.currentTimeMillis(),
            isSynced = false
        )
    }
    
    // ==========================================
    // Post Comment Mapping
    // ==========================================
    
    /**
     * Converts PostCommentEntity to PostComment domain model
     */
    fun toDomain(
        entity: PostCommentEntity,
        authorUsername: String? = null,
        authorDisplayName: String? = null,
        authorProfilePhotoUrl: String? = null,
        replies: List<PostComment> = emptyList(),
        hasMoreReplies: Boolean = false,
        isLikedByViewer: Boolean = false
    ): PostComment {
        return PostComment(
            id = entity.id,
            postId = entity.postId,
            userId = entity.userId,
            content = entity.content,
            replyToCommentId = entity.replyToCommentId,
            likeCount = entity.likeCount,
            isLikedByCurrentUser = isLikedByViewer,
            isLikedByViewer = isLikedByViewer,
            isEdited = entity.isEdited,
            createdAt = entity.createdAt,
            editedAt = entity.editedAt,
            updatedAt = entity.updatedAt,
            authorDisplayName = authorDisplayName ?: "",
            authorUsername = authorUsername ?: "",
            authorProfilePhotoUrl = authorProfilePhotoUrl,
            replies = replies,
            hasMoreReplies = hasMoreReplies
        )
    }
    
    /**
     * Converts PostComment domain model to PostCommentEntity
     */
    fun toEntity(domain: PostComment): PostCommentEntity {
        return PostCommentEntity(
            id = domain.id,
            postId = domain.postId,
            userId = domain.userId,
            content = domain.content,
            replyToCommentId = domain.replyToCommentId,
            likeCount = domain.likeCount,
            isEdited = domain.isEdited,
            createdAt = domain.createdAt,
            editedAt = domain.editedAt,
            updatedAt = domain.updatedAt,
            isSynced = false,
            syncVersion = 0
        )
    }
    
    /**
     * Creates PostCommentEntity from CreateCommentRequest
     */
    fun createCommentEntity(
        userId: String,
        request: CreateCommentRequest
    ): PostCommentEntity {
        val currentTime = System.currentTimeMillis()
        
        return PostCommentEntity(
            id = UUID.randomUUID().toString(),
            postId = request.postId,
            userId = userId,
            content = request.content,
            replyToCommentId = request.parentCommentId, // Use parentCommentId from request
            likeCount = 0,
            isEdited = false,
            createdAt = currentTime,
            editedAt = null,
            updatedAt = currentTime,
            isSynced = false,
            syncVersion = 0
        )
    }
    
    // ==========================================
    // Saved Post Mapping
    // ==========================================
    
    /**
     * Converts SavedPostEntity to SavedPost domain model
     */
    fun toDomain(entity: SavedPostEntity): SavedPost {
        return SavedPost(
            id = entity.id,
            userId = entity.userId,
            postId = entity.postId,
            savedAt = entity.savedAt
        )
    }
    
    /**
     * Converts SavedPost domain model to SavedPostEntity
     */
    fun toEntity(domain: SavedPost): SavedPostEntity {
        return SavedPostEntity(
            id = domain.id,
            userId = domain.userId,
            postId = domain.postId,
            savedAt = domain.savedAt
        )
    }
    
    /**
     * Creates SavedPostEntity for a new save
     */
    fun createSavedPostEntity(postId: String, userId: String): SavedPostEntity {
        return SavedPostEntity(
            id = UUID.randomUUID().toString(),
            userId = userId,
            postId = postId,
            savedAt = System.currentTimeMillis()
        )
    }
}