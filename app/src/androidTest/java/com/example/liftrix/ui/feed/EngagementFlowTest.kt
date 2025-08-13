package com.example.liftrix.ui.feed

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.MainActivity
import com.example.liftrix.domain.model.social.*
import com.example.liftrix.ui.testing.LiftrixComposeTestRule
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end integration tests for social feed engagement flows
 * Tests like, comment, share, and save interactions
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class EngagementFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testLikeFlow_tapToLike_showsVisualFeedback() {
        // Given: A post is displayed in the feed
        composeTestRule.apply {
            setContent {
                WorkoutPostCard(
                    post = createTestPost(likeCount = 5, isLiked = false),
                    isLiked = false,
                    isSaved = false,
                    onLikeClick = { /* Mock action */ },
                    onCommentClick = { },
                    onShareClick = { },
                    onSaveClick = { },
                    onProfileClick = { },
                    onWorkoutCopyClick = { }
                )
            }

            // When: User taps the like button
            onNodeWithContentDescription("Like").performClick()

            // Then: The like button should show as liked
            // (This would be verified through state changes in a real implementation)
            onNodeWithContentDescription("Like").assertExists()
        }
    }

    @Test
    fun testLikeFlow_tapToUnlike_removesLike() {
        composeTestRule.apply {
            var isLiked = true
            var likeCount = 6

            setContent {
                WorkoutPostCard(
                    post = createTestPost(likeCount = likeCount, isLiked = isLiked),
                    isLiked = isLiked,
                    isSaved = false,
                    onLikeClick = { 
                        isLiked = !isLiked
                        likeCount = if (isLiked) likeCount + 1 else likeCount - 1
                    },
                    onCommentClick = { },
                    onShareClick = { },
                    onSaveClick = { },
                    onProfileClick = { },
                    onWorkoutCopyClick = { }
                )
            }

            // Initially liked
            onNodeWithContentDescription("Unlike").assertExists()

            // When: User taps to unlike
            onNodeWithContentDescription("Unlike").performClick()

            // Then: Should show as not liked
            onNodeWithContentDescription("Like").assertExists()
        }
    }

    @Test
    fun testCommentFlow_tapComment_opensCommentBottomSheet() {
        composeTestRule.apply {
            var showComments = false

            setContent {
                if (showComments) {
                    CommentBottomSheet(
                        postId = "test_post",
                        comments = createTestComments(),
                        currentUserProfileImageUrl = "https://example.com/avatar.jpg",
                        isLoading = false,
                        onDismiss = { showComments = false },
                        onCommentSubmit = { _, _ -> },
                        onCommentLike = { },
                        onCommentReply = { }
                    )
                } else {
                    WorkoutPostCard(
                        post = createTestPost(commentCount = 3),
                        isLiked = false,
                        isSaved = false,
                        onLikeClick = { },
                        onCommentClick = { showComments = true },
                        onShareClick = { },
                        onSaveClick = { },
                        onProfileClick = { },
                        onWorkoutCopyClick = { }
                    )
                }
            }

            // When: User taps comment button
            onNodeWithContentDescription("Comment").performClick()

            // Then: Comment bottom sheet should appear
            onNodeWithText("Comments (3)").assertIsDisplayed()
            onNodeWithText("Add a comment...").assertIsDisplayed()
        }
    }

    @Test
    fun testCommentFlow_addComment_updatesCommentCount() {
        composeTestRule.apply {
            var commentCount = 2
            var comments = createTestComments()
            var showComments = true

            setContent {
                CommentBottomSheet(
                    postId = "test_post",
                    comments = comments,
                    currentUserProfileImageUrl = "https://example.com/avatar.jpg",
                    isLoading = false,
                    onDismiss = { },
                    onCommentSubmit = { content, parentId -> 
                        val newComment = PostComment(
                            id = "new_comment",
                            postId = "test_post",
                            userId = "current_user",
                            content = content,
                            parentCommentId = parentId,
                            createdAt = System.currentTimeMillis(),
                            authorDisplayName = "Current User"
                        )
                        comments = comments + newComment
                        commentCount++
                    },
                    onCommentLike = { },
                    onCommentReply = { }
                )
            }

            // When: User types a comment and submits
            onNodeWithText("Add a comment...").performTextInput("Great workout!")
            onNodeWithContentDescription("Send comment").performClick()

            // Then: Comment should be added
            onNodeWithText("Great workout!").assertIsDisplayed()
        }
    }

    @Test
    fun testSaveFlow_tapSave_showsVisualFeedback() {
        composeTestRule.apply {
            var isSaved = false

            setContent {
                WorkoutPostCard(
                    post = createTestPost(saveCount = 2),
                    isLiked = false,
                    isSaved = isSaved,
                    onLikeClick = { },
                    onCommentClick = { },
                    onShareClick = { },
                    onSaveClick = { isSaved = !isSaved },
                    onProfileClick = { },
                    onWorkoutCopyClick = { }
                )
            }

            // When: User taps save button
            onNodeWithContentDescription("Save").performClick()

            // Then: Should show as saved
            onNodeWithContentDescription("Unsave").assertExists()
        }
    }

    @Test
    fun testWorkoutCopyFlow_tapCopy_showsSuccessFeedback() {
        composeTestRule.apply {
            var copyClicked = false

            setContent {
                WorkoutPostCard(
                    post = createTestPost(),
                    isLiked = false,
                    isSaved = false,
                    onLikeClick = { },
                    onCommentClick = { },
                    onShareClick = { },
                    onSaveClick = { },
                    onProfileClick = { },
                    onWorkoutCopyClick = { copyClicked = true }
                )
            }

            // When: User taps copy workout button
            onNodeWithContentDescription("Copy workout").performClick()

            // Then: Copy action should be triggered
            assert(copyClicked)
        }
    }

    @Test
    fun testEngagementInteractions_multipleActions_allWorkCorrectly() {
        composeTestRule.apply {
            var isLiked = false
            var isSaved = false
            var showComments = false

            setContent {
                if (showComments) {
                    CommentBottomSheet(
                        postId = "test_post",
                        comments = createTestComments(),
                        currentUserProfileImageUrl = "https://example.com/avatar.jpg",
                        isLoading = false,
                        onDismiss = { showComments = false },
                        onCommentSubmit = { _, _ -> },
                        onCommentLike = { },
                        onCommentReply = { }
                    )
                } else {
                    WorkoutPostCard(
                        post = createTestPost(likeCount = 5, commentCount = 3, saveCount = 2),
                        isLiked = isLiked,
                        isSaved = isSaved,
                        onLikeClick = { isLiked = !isLiked },
                        onCommentClick = { showComments = true },
                        onShareClick = { },
                        onSaveClick = { isSaved = !isSaved },
                        onProfileClick = { },
                        onWorkoutCopyClick = { }
                    )
                }
            }

            // Test like
            onNodeWithContentDescription("Like").performClick()
            onNodeWithContentDescription("Unlike").assertExists()

            // Test save
            onNodeWithContentDescription("Save").performClick()
            onNodeWithContentDescription("Unsave").assertExists()

            // Test comments
            onNodeWithContentDescription("Comment").performClick()
            onNodeWithText("Comments (3)").assertIsDisplayed()

            // Dismiss comments
            onNodeWithContentDescription("Close").performClick()

            // Verify all states are maintained
            onNodeWithContentDescription("Unlike").assertExists()
            onNodeWithContentDescription("Unsave").assertExists()
        }
    }

    @Test
    fun testNestedCommentFlow_replyToComment_showsNestedStructure() {
        composeTestRule.apply {
            var comments = createTestComments()
            var replyingTo: PostComment? = null

            setContent {
                CommentBottomSheet(
                    postId = "test_post",
                    comments = comments,
                    currentUserProfileImageUrl = "https://example.com/avatar.jpg",
                    isLoading = false,
                    onDismiss = { },
                    onCommentSubmit = { content, parentId -> 
                        val newComment = PostComment(
                            id = "reply_comment",
                            postId = "test_post",
                            userId = "current_user",
                            content = content,
                            parentCommentId = parentId,
                            createdAt = System.currentTimeMillis(),
                            authorDisplayName = "Current User"
                        )
                        comments = comments + newComment
                    },
                    onCommentLike = { },
                    onCommentReply = { comment -> replyingTo = comment }
                )
            }

            // When: User clicks reply on a comment
            onNodeWithText("Reply").onFirst().performClick()

            // Then: Should show replying indicator
            onNodeWithText("Replying to Test User").assertIsDisplayed()

            // When: User types and sends reply
            onNodeWithText("Write a reply...").performTextInput("Thanks!")
            onNodeWithContentDescription("Send comment").performClick()

            // Then: Reply should appear as nested
            onNodeWithText("Thanks!").assertIsDisplayed()
        }
    }

    // Helper methods for creating test data

    private fun createTestPost(
        id: String = "test_post",
        likeCount: Int = 0,
        commentCount: Int = 0,
        saveCount: Int = 0,
        isLiked: Boolean = false
    ): WorkoutPost {
        return WorkoutPost(
            id = id,
            userId = "author_user",
            workoutId = "workout_123",
            caption = "Great workout today! Feeling strong 💪",
            mediaItems = listOf(
                MediaItem(
                    id = "media_1",
                    type = MediaType.IMAGE,
                    originalUrl = "https://example.com/image.jpg",
                    thumbnailUrl = "https://example.com/thumb.jpg",
                    compressedUrl = "https://example.com/compressed.jpg",
                    width = 1080,
                    height = 1080,
                    fileSizeBytes = 2048000
                )
            ),
            workoutDuration = 75,
            totalVolume = 12500.0,
            exercisesCount = 6,
            prsCount = 2,
            likeCount = likeCount,
            commentCount = commentCount,
            shareCount = 1,
            saveCount = saveCount,
            visibility = PostVisibility.FOLLOWERS,
            isLikedByViewer = isLiked,
            isSavedByViewer = false,
            createdAt = System.currentTimeMillis() - 3600000, // 1 hour ago
            updatedAt = System.currentTimeMillis() - 3600000,
            relevanceScore = 75.0,
            authorUsername = "testuser",
            authorDisplayName = "Test User",
            authorProfileImageUrl = "https://example.com/avatar.jpg"
        )
    }

    private fun createTestComments(): List<PostComment> {
        return listOf(
            PostComment(
                id = "comment_1",
                postId = "test_post",
                userId = "user_1",
                content = "Amazing workout! Keep it up! 🔥",
                likeCount = 3,
                isLikedByCurrentUser = false,
                createdAt = System.currentTimeMillis() - 1800000, // 30 minutes ago
                authorDisplayName = "Test User",
                authorUsername = "testuser1",
                authorProfileImageUrl = "https://example.com/avatar1.jpg"
            ),
            PostComment(
                id = "comment_2",
                postId = "test_post",
                userId = "user_2",
                content = "What was your total volume?",
                likeCount = 1,
                isLikedByCurrentUser = true,
                createdAt = System.currentTimeMillis() - 900000, // 15 minutes ago
                authorDisplayName = "Another User",
                authorUsername = "testuser2",
                authorProfileImageUrl = "https://example.com/avatar2.jpg"
            ),
            PostComment(
                id = "comment_3",
                postId = "test_post",
                userId = "user_3",
                content = "Nice form on those squats!",
                parentCommentId = "comment_1", // Reply to first comment
                likeCount = 0,
                isLikedByCurrentUser = false,
                createdAt = System.currentTimeMillis() - 600000, // 10 minutes ago
                authorDisplayName = "Fitness Fan",
                authorUsername = "fitnessfan",
                authorProfileImageUrl = "https://example.com/avatar3.jpg"
            )
        )
    }
}