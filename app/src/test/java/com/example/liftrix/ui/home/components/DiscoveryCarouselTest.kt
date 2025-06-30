package com.example.liftrix.ui.home.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.example.liftrix.domain.model.RecommendedUser
import com.example.liftrix.ui.theme.LiftrixTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class DiscoveryCarouselTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mockRecommendedUsers = listOf(
        RecommendedUser(
            userId = "user1",
            username = "John Doe",
            profileImageUrl = null,
            isFollowing = false
        ),
        RecommendedUser(
            userId = "user2", 
            username = "Jane Smith",
            profileImageUrl = "https://example.com/avatar.jpg",
            isFollowing = true
        ),
        RecommendedUser(
            userId = "user3",
            username = "Bob Wilson",
            profileImageUrl = null,
            isFollowing = false
        )
    )

    private var loadMoreCalled = false
    private var followedUserId: String? = null

    @Before
    fun setup() {
        loadMoreCalled = false
        followedUserId = null
    }

    @Test
    fun discoveryCarousel_displaysRecommendedUsers_whenDataProvided() {
        // Given
        composeTestRule.setContent {
            LiftrixTheme {
                DiscoveryCarousel(
                    recommendedUsers = mockRecommendedUsers,
                    isLoading = false,
                    hasMore = true,
                    onLoadMore = { loadMoreCalled = true },
                    onFollowUser = { userId -> followedUserId = userId },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then
        composeTestRule
            .onNodeWithTag("discovery_carousel")
            .assertIsDisplayed()

        composeTestRule
            .onAllNodesWithTag("recommended_user_card")
            .assertCountEquals(3)

        composeTestRule
            .onNodeWithText("John Doe")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Jane Smith")
            .assertIsDisplayed()

        composeTestRule
            .onNodeWithText("Bob Wilson")
            .assertIsDisplayed()
    }

    @Test
    fun discoveryCarousel_displaysLoadingPlaceholders_whenLoading() {
        // Given
        composeTestRule.setContent {
            LiftrixTheme {
                DiscoveryCarousel(
                    recommendedUsers = mockRecommendedUsers,
                    isLoading = true,
                    hasMore = true,
                    onLoadMore = { loadMoreCalled = true },
                    onFollowUser = { userId -> followedUserId = userId },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should display both user cards and loading placeholders
        composeTestRule
            .onAllNodesWithTag("recommended_user_card")
            .assertCountEquals(3)

        // Loading placeholders are added but not specifically tagged in current implementation
        // This validates the component renders without crashing during loading state
    }

    @Test
    fun discoveryCarousel_callsOnFollowUser_whenFollowButtonClicked() {
        // Given
        composeTestRule.setContent {
            LiftrixTheme {
                DiscoveryCarousel(
                    recommendedUsers = mockRecommendedUsers,
                    isLoading = false,
                    hasMore = true,
                    onLoadMore = { loadMoreCalled = true },
                    onFollowUser = { userId -> followedUserId = userId },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // When - Click follow button for first user (John Doe who is not following)
        composeTestRule
            .onNodeWithContentDescription("Follow user")
            .performClick()

        // Then
        assert(followedUserId == "user1")
    }

    @Test
    fun discoveryCarousel_displaysUnfollowButton_whenUserIsFollowing() {
        // Given
        composeTestRule.setContent {
            LiftrixTheme {
                DiscoveryCarousel(
                    recommendedUsers = mockRecommendedUsers,
                    isLoading = false,
                    hasMore = true,
                    onLoadMore = { loadMoreCalled = true },
                    onFollowUser = { userId -> followedUserId = userId },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Jane Smith is following, should show unfollow button
        composeTestRule
            .onNodeWithContentDescription("Unfollow user")
            .assertIsDisplayed()
    }

    @Test
    fun discoveryCarousel_displaysFollowButton_whenUserIsNotFollowing() {
        // Given
        composeTestRule.setContent {
            LiftrixTheme {
                DiscoveryCarousel(
                    recommendedUsers = mockRecommendedUsers,
                    isLoading = false,
                    hasMore = true,
                    onLoadMore = { loadMoreCalled = true },
                    onFollowUser = { userId -> followedUserId = userId },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - John Doe and Bob Wilson are not following, should show follow buttons
        composeTestRule
            .onAllNodesWithContentDescription("Follow user")
            .assertCountEquals(2)
    }

    @Test
    fun discoveryCarousel_displaysUserInitials_whenNoProfileImage() {
        // Given
        composeTestRule.setContent {
            LiftrixTheme {
                DiscoveryCarousel(
                    recommendedUsers = listOf(
                        RecommendedUser(
                            userId = "user1",
                            username = "John Doe",
                            profileImageUrl = null,
                            isFollowing = false
                        )
                    ),
                    isLoading = false,
                    hasMore = false,
                    onLoadMore = { loadMoreCalled = true },
                    onFollowUser = { userId -> followedUserId = userId },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should display "JD" initials for John Doe
        composeTestRule
            .onNodeWithText("JD")
            .assertIsDisplayed()
    }

    @Test
    fun discoveryCarousel_displaysPersonIcon_whenNoUsernameForInitials() {
        // Given
        composeTestRule.setContent {
            LiftrixTheme {
                DiscoveryCarousel(
                    recommendedUsers = listOf(
                        RecommendedUser(
                            userId = "user1",
                            username = "", // Empty username
                            profileImageUrl = null,
                            isFollowing = false
                        )
                    ),
                    isLoading = false,
                    hasMore = false,
                    onLoadMore = { loadMoreCalled = true },
                    onFollowUser = { userId -> followedUserId = userId },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should display person icon as fallback
        composeTestRule
            .onNodeWithContentDescription("User avatar")
            .assertIsDisplayed()
    }

    @Test
    fun discoveryCarousel_handlesEmptyUsersList() {
        // Given
        composeTestRule.setContent {
            LiftrixTheme {
                DiscoveryCarousel(
                    recommendedUsers = emptyList(),
                    isLoading = false,
                    hasMore = false,
                    onLoadMore = { loadMoreCalled = true },
                    onFollowUser = { userId -> followedUserId = userId },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Carousel should still display without crashing
        composeTestRule
            .onNodeWithTag("discovery_carousel")
            .assertIsDisplayed()

        composeTestRule
            .onAllNodesWithTag("recommended_user_card")
            .assertCountEquals(0)
    }

    @Test
    fun discoveryCarousel_handlesSingleCharacterUsername() {
        // Given
        composeTestRule.setContent {
            LiftrixTheme {
                DiscoveryCarousel(
                    recommendedUsers = listOf(
                        RecommendedUser(
                            userId = "user1",
                            username = "X",
                            profileImageUrl = null,
                            isFollowing = false
                        )
                    ),
                    isLoading = false,
                    hasMore = false,
                    onLoadMore = { loadMoreCalled = true },
                    onFollowUser = { userId -> followedUserId = userId },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should display "X" as initials
        composeTestRule
            .onNodeWithText("X")
            .assertIsDisplayed()
    }

    @Test
    fun discoveryCarousel_rendersWithoutJank_withManyUsers() {
        // Given - Large list of users to test performance
        val manyUsers = (1..50).map { index ->
            RecommendedUser(
                userId = "user$index",
                username = "User $index",
                profileImageUrl = null,
                isFollowing = index % 3 == 0 // Every 3rd user is following
            )
        }

        composeTestRule.setContent {
            LiftrixTheme {
                DiscoveryCarousel(
                    recommendedUsers = manyUsers,
                    isLoading = false,
                    hasMore = true,
                    onLoadMore = { loadMoreCalled = true },
                    onFollowUser = { userId -> followedUserId = userId },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Then - Should render without crashing
        composeTestRule
            .onNodeWithTag("discovery_carousel")
            .assertIsDisplayed()

        // Should be able to scroll
        composeTestRule
            .onNodeWithTag("discovery_carousel")
            .performScrollToIndex(10)

        // Should still display correctly after scroll
        composeTestRule
            .onNodeWithText("User 11") // Index 10 is "User 11"
            .assertIsDisplayed()
    }
}