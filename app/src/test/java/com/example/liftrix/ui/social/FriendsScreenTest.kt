package com.example.liftrix.ui.social

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.domain.model.*
import com.example.liftrix.ui.theme.LiftrixTheme
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * Comprehensive UI tests for FriendsScreen composable.
 * 
 * Tests social feed rendering, friend interactions, and navigation
 * as specified in task SOCIAL-TEST-003.
 * 
 * Coverage includes:
 * - All UI states (friends list, empty state, loading, error)
 * - Tab navigation between friends and requests
 * - Friend search functionality
 * - Friend request accept/decline flows
 * - Navigation behavior and callback verification
 * - Accessibility features and semantic markup
 * - ViewModel integration and event handling
 */
@RunWith(AndroidJUnit4::class)
class FriendsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Mock dependencies
    private lateinit var mockViewModel: SocialViewModel
    private lateinit var mockOnNavigateBack: () -> Unit
    private lateinit var uiStateFlow: MutableStateFlow<SocialUiState>

    // Test data
    private val testFriends = listOf(
        createTestFriend("user1", "Alice Johnson", "alice@example.com"),
        createTestFriend("user2", "Bob Smith", "bob@example.com"),
        createTestFriend("user3", "Charlie Brown", "charlie@example.com")
    )

    private val testFriendRequests = listOf(
        createTestFriend("user4", "David Wilson", "david@example.com", FriendStatus.PENDING),
        createTestFriend("user5", "Eve Davis", "eve@example.com", FriendStatus.PENDING)
    )

    private val testSearchResults = listOf(
        createTestUser("user6", "Frank Miller", "frank@example.com"),
        createTestUser("user7", "Grace Lee", "grace@example.com")
    )

    @Before
    fun setUp() {
        mockViewModel = mockk(relaxed = true)
        mockOnNavigateBack = mockk(relaxed = true)
        
        // Create mutable state flow for UI state
        uiStateFlow = MutableStateFlow(SocialUiState())
        every { mockViewModel.uiState } returns uiStateFlow
        every { mockViewModel.onEvent(any()) } just Runs
    }

    @Test
    fun friendsScreen_displaysCorrectly() {
        // Given - Default state with friends
        val contentState = SocialUiState(
            friends = testFriends,
            friendRequests = testFriendRequests,
            isLoading = false,
            error = null
        )
        uiStateFlow.value = contentState

        // When
        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // Then - Main UI elements are displayed
        composeTestRule.onNodeWithText("Friends").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Navigate back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Refresh friends").assertIsDisplayed()
        
        // Verify tabs are displayed
        composeTestRule.onNodeWithText("Friends").assertIsDisplayed()
        composeTestRule.onNodeWithText("Requests").assertIsDisplayed()
        
        // Verify friend request badge is shown
        composeTestRule.onNodeWithText(testFriendRequests.size.toString()).assertIsDisplayed()
    }

    @Test
    fun friendsScreen_friendsTab_displaysFreindsCorrectly() {
        // Given - Content state with friends
        val contentState = SocialUiState(
            friends = testFriends,
            isLoading = false,
            error = null
        )
        uiStateFlow.value = contentState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // Then - Friends are displayed
        composeTestRule.onNodeWithText("Alice Johnson").assertIsDisplayed()
        composeTestRule.onNodeWithText("alice@example.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bob Smith").assertIsDisplayed()
        composeTestRule.onNodeWithText("bob@example.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("Charlie Brown").assertIsDisplayed()
        composeTestRule.onNodeWithText("charlie@example.com").assertIsDisplayed()
        
        // Verify search field is displayed
        composeTestRule.onNodeWithText("Search friends by name or email").assertIsDisplayed()
    }

    @Test
    fun friendsScreen_emptyFriendsState_displaysCorrectly() {
        // Given - Empty friends state
        val emptyState = SocialUiState(
            friends = emptyList(),
            isLoading = false,
            error = null
        )
        uiStateFlow.value = emptyState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // Then - Empty state is displayed
        composeTestRule.onNodeWithText("No friends yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search for friends above to start building your fitness community!").assertIsDisplayed()
    }

    @Test
    fun friendsScreen_loadingState_displaysCorrectly() {
        // Given - Loading state
        val loadingState = SocialUiState(
            friends = emptyList(),
            isLoading = true,
            error = null
        )
        uiStateFlow.value = loadingState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // Then - Loading indicator is displayed
        composeTestRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        ).assertExists()
    }

    @Test
    fun friendsScreen_errorState_displaysCorrectly() {
        // Given - Error state
        val errorState = SocialUiState(
            friends = emptyList(),
            isLoading = false,
            error = "Failed to load friends"
        )
        uiStateFlow.value = errorState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // Then - Error message is displayed
        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
        composeTestRule.onNodeWithText("Failed to load friends").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try Again").assertIsDisplayed()
    }

    @Test
    fun friendsScreen_requestsTab_displaysFriendRequests() {
        // Given - State with friend requests
        val contentState = SocialUiState(
            friends = testFriends,
            friendRequests = testFriendRequests,
            isLoading = false,
            error = null
        )
        uiStateFlow.value = contentState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // When - Click on requests tab
        composeTestRule.onAllNodesWithText("Requests")[1].performClick()

        // Then - Friend requests are displayed
        composeTestRule.onNodeWithText("David Wilson").assertIsDisplayed()
        composeTestRule.onNodeWithText("wants to be friends").assertIsDisplayed()
        composeTestRule.onNodeWithText("Accept").assertIsDisplayed()
        composeTestRule.onNodeWithText("Decline").assertIsDisplayed()
        
        composeTestRule.onNodeWithText("Eve Davis").assertIsDisplayed()
    }

    @Test
    fun friendsScreen_emptyRequestsState_displaysCorrectly() {
        // Given - Empty requests state
        val emptyState = SocialUiState(
            friends = testFriends,
            friendRequests = emptyList(),
            isLoading = false,
            error = null
        )
        uiStateFlow.value = emptyState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // When - Click on requests tab
        composeTestRule.onAllNodesWithText("Requests")[1].performClick()

        // Then - Empty requests state is displayed
        composeTestRule.onNodeWithText("No friend requests").assertIsDisplayed()
        composeTestRule.onNodeWithText("When people send you friend requests, they'll appear here.").assertIsDisplayed()
    }

    @Test
    fun friendsScreen_searchFunctionality_worksCorrectly() {
        // Given - Content state
        val contentState = SocialUiState(
            friends = testFriends,
            searchQuery = "",
            searchResults = emptyList(),
            isLoading = false,
            error = null
        )
        uiStateFlow.value = contentState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // When - Type in search field
        composeTestRule.onNodeWithText("Search friends by name or email").performTextInput("john")

        // Then - Search event is triggered
        verify { mockViewModel.onEvent(SocialEvent.SearchFriends("john")) }
    }

    @Test
    fun friendsScreen_searchResults_displaysCorrectly() {
        // Given - State with search results
        val searchState = SocialUiState(
            friends = testFriends,
            searchQuery = "frank",
            searchResults = testSearchResults,
            isLoading = false,
            error = null
        )
        uiStateFlow.value = searchState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // Then - Search results are displayed
        composeTestRule.onNodeWithText("Search Results").assertIsDisplayed()
        composeTestRule.onNodeWithText("Frank Miller").assertIsDisplayed()
        composeTestRule.onNodeWithText("frank@example.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("Grace Lee").assertIsDisplayed()
        composeTestRule.onNodeWithText("grace@example.com").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add").assertIsDisplayed()
    }

    @Test
    fun friendsScreen_sendFriendRequest_triggersCorrectEvent() {
        // Given - State with search results
        val searchState = SocialUiState(
            friends = testFriends,
            searchQuery = "frank",
            searchResults = testSearchResults,
            isLoading = false,
            error = null
        )
        uiStateFlow.value = searchState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // When - Click add button for first search result
        composeTestRule.onAllNodesWithText("Add")[0].performClick()

        // Then - Send friend request event is triggered
        verify { mockViewModel.onEvent(SocialEvent.SendFriendRequest("user6")) }
    }

    @Test
    fun friendsScreen_acceptFriendRequest_triggersCorrectEvent() {
        // Given - State with friend requests
        val contentState = SocialUiState(
            friends = testFriends,
            friendRequests = testFriendRequests,
            isLoading = false,
            error = null
        )
        uiStateFlow.value = contentState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // When - Switch to requests tab and accept first request
        composeTestRule.onAllNodesWithText("Requests")[1].performClick()
        composeTestRule.onAllNodesWithText("Accept")[0].performClick()

        // Then - Accept friend request event is triggered
        verify { mockViewModel.onEvent(SocialEvent.AcceptFriendRequest("user4")) }
    }

    @Test
    fun friendsScreen_declineFriendRequest_triggersCorrectEvent() {
        // Given - State with friend requests
        val contentState = SocialUiState(
            friends = testFriends,
            friendRequests = testFriendRequests,
            isLoading = false,
            error = null
        )
        uiStateFlow.value = contentState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // When - Switch to requests tab and decline first request
        composeTestRule.onAllNodesWithText("Requests")[1].performClick()
        composeTestRule.onAllNodesWithText("Decline")[0].performClick()

        // Then - Decline friend request event is triggered
        verify { mockViewModel.onEvent(SocialEvent.DeclineFriendRequest("user4")) }
    }

    @Test
    fun friendsScreen_navigationBack_worksCorrectly() {
        // Given - Default state
        uiStateFlow.value = SocialUiState()

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // When - Click back button
        composeTestRule.onNodeWithContentDescription("Navigate back").performClick()

        // Then - Navigation callback is invoked
        verify { mockOnNavigateBack() }
    }

    @Test
    fun friendsScreen_refreshButton_triggersCorrectEvent() {
        // Given - Default state
        uiStateFlow.value = SocialUiState()

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // When - Click refresh button
        composeTestRule.onNodeWithContentDescription("Refresh friends").performClick()

        // Then - Refresh event is triggered
        verify { mockViewModel.onEvent(SocialEvent.Refresh) }
    }

    @Test
    fun friendsScreen_clearSearch_triggersCorrectEvent() {
        // Given - State with search query
        val searchState = SocialUiState(
            friends = testFriends,
            searchQuery = "test query",
            searchResults = emptyList(),
            isLoading = false,
            error = null
        )
        uiStateFlow.value = searchState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // When - Click clear search button
        composeTestRule.onNodeWithContentDescription("Clear search").performClick()

        // Then - Clear search event is triggered
        verify { mockViewModel.onEvent(SocialEvent.SearchFriends("")) }
    }

    @Test
    fun friendsScreen_errorRetry_triggersCorrectEvent() {
        // Given - Error state
        val errorState = SocialUiState(
            friends = emptyList(),
            isLoading = false,
            error = "Network error"
        )
        uiStateFlow.value = errorState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // When - Click try again button
        composeTestRule.onNodeWithText("Try Again").performClick()

        // Then - Load friends event is triggered
        verify { mockViewModel.onEvent(SocialEvent.LoadFriends) }
    }

    @Test
    fun friendsScreen_tabNavigation_worksCorrectly() {
        // Given - State with friends and requests
        val contentState = SocialUiState(
            friends = testFriends,
            friendRequests = testFriendRequests,
            isLoading = false,
            error = null
        )
        uiStateFlow.value = contentState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // Then - Friends tab is selected by default
        composeTestRule.onNodeWithText("Alice Johnson").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search friends by name or email").assertIsDisplayed()

        // When - Click on requests tab
        composeTestRule.onAllNodesWithText("Requests")[1].performClick()

        // Then - Requests tab content is displayed
        composeTestRule.onNodeWithText("David Wilson").assertIsDisplayed()
        composeTestRule.onNodeWithText("wants to be friends").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice Johnson").assertIsNotDisplayed()

        // When - Click back on friends tab
        composeTestRule.onAllNodesWithText("Friends")[1].performClick()

        // Then - Friends tab content is displayed again
        composeTestRule.onNodeWithText("Alice Johnson").assertIsDisplayed()
        composeTestRule.onNodeWithText("David Wilson").assertIsNotDisplayed()
    }

    @Test
    fun friendsScreen_accessibility_hasCorrectContentDescriptions() {
        // Given - Content state
        val contentState = SocialUiState(
            friends = testFriends,
            friendRequests = testFriendRequests,
            isLoading = false,
            error = null
        )
        uiStateFlow.value = contentState

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // Then - Accessibility content descriptions are present
        composeTestRule.onNodeWithContentDescription("Navigate back").assertExists()
        composeTestRule.onNodeWithContentDescription("Refresh friends").assertExists()
        composeTestRule.onNodeWithContentDescription("Clear search").assertDoesNotExist() // Only appears with search query
    }

    @Test
    fun friendsScreen_handlesStateTransitions() {
        // Given - Start with loading state
        uiStateFlow.value = SocialUiState(isLoading = true)

        composeTestRule.setContent {
            LiftrixTheme {
                FriendsScreen(
                    onNavigateBack = mockOnNavigateBack,
                    viewModel = mockViewModel
                )
            }
        }

        // Then - Loading state is shown
        composeTestRule.onNode(
            hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        ).assertExists()

        // When - Transition to content state
        uiStateFlow.value = SocialUiState(
            friends = testFriends,
            isLoading = false,
            error = null
        )

        // Then - Content state is shown
        composeTestRule.onNodeWithText("Alice Johnson").assertIsDisplayed()

        // When - Transition to error state
        uiStateFlow.value = SocialUiState(
            friends = emptyList(),
            isLoading = false,
            error = "Connection failed"
        )

        // Then - Error state is shown
        composeTestRule.onNodeWithText("Something went wrong").assertIsDisplayed()
        composeTestRule.onNodeWithText("Alice Johnson").assertIsNotDisplayed()
    }

    // Helper functions to create test data

    private fun createTestFriend(
        userId: String,
        displayName: String,
        email: String,
        status: FriendStatus = FriendStatus.ACCEPTED
    ): Friend {
        return Friend(
            userId = userId,
            displayName = displayName,
            email = email,
            avatarUrl = null,
            status = status,
            presence = null,
            friendSince = Instant.now().minusSeconds(86400) // 1 day ago
        )
    }

    private fun createTestUser(
        uid: String,
        displayName: String,
        email: String
    ): User {
        return User(
            uid = uid,
            email = email,
            displayName = displayName,
            photoUrl = null,
            isAnonymous = false,
            subscriptionTier = SubscriptionTier.FREE,
            subscriptionStatus = SubscriptionStatus.ACTIVE,
            subscriptionExpiresAt = null,
            premiumFeaturesEnabled = false,
            onboardingCompleted = true,
            profileVersion = 1,
            createdAt = java.time.LocalDateTime.now().minusDays(1),
            lastSignInAt = java.time.LocalDateTime.now().minusHours(1),
            updatedAt = java.time.LocalDateTime.now().minusHours(1)
        )
    }
}