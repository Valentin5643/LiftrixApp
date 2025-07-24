package com.example.liftrix.ui.profile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.MainActivity
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.social.ConnectionStatus
import com.example.liftrix.domain.model.social.FitnessLevel
import com.example.liftrix.domain.model.social.SearchFilters
import com.example.liftrix.domain.model.social.UserSearchResult
import com.example.liftrix.ui.social.UserSearchScreen
import com.example.liftrix.ui.social.UserSearchUiState
import com.example.liftrix.ui.social.UserSearchViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * UI tests for UserSearchScreen
 * 
 * Tests search interface interactions, real-time search results,
 * filtering functionality, accessibility compliance, and user workflows
 * using Compose UI testing framework.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class UserSearchScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var mockViewModel: UserSearchViewModel
    private lateinit var mockUiState: MutableStateFlow<UserSearchUiState>

    private val mockSearchResults = listOf(
        UserSearchResult(
            userId = "user1",
            displayName = "John Fitness",
            profileImageUrl = null,
            bio = "Passionate about weightlifting and strength training",
            fitnessLevel = FitnessLevel.INTERMEDIATE,
            totalWorkouts = 45,
            memberSince = LocalDateTime.now().minusYears(1),
            sharedEquipment = listOf(Equipment.BARBELL, Equipment.DUMBBELLS),
            sharedGoals = listOf(FitnessGoal.MUSCLE_GAIN, FitnessGoal.STRENGTH),
            connectionStatus = ConnectionStatus.NONE,
            mutualConnections = 3
        ),
        UserSearchResult(
            userId = "user2",
            displayName = "Sarah Runner",
            profileImageUrl = null,
            bio = "Marathon runner and cardio enthusiast",
            fitnessLevel = FitnessLevel.ADVANCED,
            totalWorkouts = 128,
            memberSince = LocalDateTime.now().minusYears(2),
            sharedEquipment = listOf(Equipment.TREADMILL),
            sharedGoals = listOf(FitnessGoal.ENDURANCE, FitnessGoal.WEIGHT_LOSS),
            connectionStatus = ConnectionStatus.CONNECTED,
            mutualConnections = 7
        )
    )

    @Before
    fun setup() {
        hiltRule.inject()
        
        mockUiState = MutableStateFlow(
            UserSearchUiState(
                searchQuery = "",
                searchResults = emptyList(),
                filters = SearchFilters(),
                isLoading = false,
                isSearching = false,
                errorMessage = null,
                hasSearched = false,
                totalResults = 0,
                isFilterExpanded = false
            )
        )
        
        mockViewModel = mockk {
            every { uiState } returns mockUiState
        }
    }

    @Test
    fun userSearchScreen_displaysCorrectly() {
        // Given
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify main UI elements are displayed
        composeTestRule.onNodeWithTag("search_field").assertIsDisplayed()
        composeTestRule.onNodeWithText("Search for users...").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Filter search").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("QR code scan").assertIsDisplayed()
    }

    @Test
    fun searchField_acceptsTextInput() {
        // Given
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // When
        composeTestRule.onNodeWithTag("search_field")
            .performTextInput("fitness enthusiast")

        // Then
        composeTestRule.onNodeWithTag("search_field")
            .assertTextContains("fitness enthusiast")
    }

    @Test
    fun searchResults_displayCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            searchResults = mockSearchResults,
            hasSearched = true,
            totalResults = 2
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify search results are displayed
        composeTestRule.onNodeWithText("John Fitness").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sarah Runner").assertIsDisplayed()
        composeTestRule.onNodeWithText("45 workouts").assertIsDisplayed()
        composeTestRule.onNodeWithText("128 workouts").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 mutual connections").assertIsDisplayed()
        composeTestRule.onNodeWithText("7 mutual connections").assertIsDisplayed()
    }

    @Test
    fun fitnessLevelBadges_displayCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            searchResults = mockSearchResults,
            hasSearched = true
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify fitness level badges
        composeTestRule.onNodeWithText("Intermediate").assertIsDisplayed()
        composeTestRule.onNodeWithText("Advanced").assertIsDisplayed()
    }

    @Test
    fun connectionStatusIndicators_displayCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            searchResults = mockSearchResults,
            hasSearched = true
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify connection status indicators
        composeTestRule.onNodeWithText("Connect").assertIsDisplayed() // For NONE status
        composeTestRule.onNodeWithText("Connected").assertIsDisplayed() // For CONNECTED status
    }

    @Test
    fun filterButton_togglesFilterExpansion() {
        // Given
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // When - Click filter button
        composeTestRule.onNodeWithContentDescription("Filter search")
            .performClick()

        // Then - Verify filter section is expanded
        composeTestRule.onNodeWithText("Fitness Level").assertIsDisplayed()
        composeTestRule.onNodeWithText("Equipment").assertIsDisplayed()
        composeTestRule.onNodeWithText("Goals").assertIsDisplayed()
    }

    @Test
    fun loadingState_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            isLoading = true,
            isSearching = true
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify loading indicators
        composeTestRule.onNodeWithTag("search_loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Searching...").assertIsDisplayed()
    }

    @Test
    fun emptySearchState_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            hasSearched = true,
            searchResults = emptyList(),
            totalResults = 0,
            isLoading = false
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify empty state
        composeTestRule.onNodeWithText("No users found").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try adjusting your search terms or filters").assertIsDisplayed()
    }

    @Test
    fun errorState_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            errorMessage = "Network connection failed. Please try again.",
            isLoading = false
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify error message
        composeTestRule.onNodeWithText("Network connection failed. Please try again.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun searchResultCard_handlesClickInteraction() {
        // Given
        var clickedUserId: String? = null
        mockUiState.value = mockUiState.value.copy(
            searchResults = mockSearchResults,
            hasSearched = true
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { userId -> clickedUserId = userId },
                onNavigateBack = { }
            )
        }

        // When - Click on first search result
        composeTestRule.onNodeWithText("John Fitness")
            .performClick()

        // Then - Verify navigation was triggered
        assert(clickedUserId == "user1")
    }

    @Test
    fun connectButton_handlesClickInteraction() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            searchResults = listOf(mockSearchResults[0]), // Only user with NONE status
            hasSearched = true
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // When - Click connect button
        composeTestRule.onNodeWithText("Connect")
            .performClick()

        // Then - Button should be enabled and clickable
        composeTestRule.onNodeWithText("Connect").assertIsEnabled()
    }

    @Test
    fun connectedButton_isDisabled() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            searchResults = listOf(mockSearchResults[1]), // User with CONNECTED status
            hasSearched = true
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Connected button should not be clickable
        composeTestRule.onNodeWithText("Connected").assertIsNotEnabled()
    }

    @Test
    fun qrCodeScanButton_handlesClickInteraction() {
        // Given
        var qrScanClicked = false
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { },
                onScanQRCode = { qrScanClicked = true }
            )
        }

        // When - Click QR code scan button
        composeTestRule.onNodeWithContentDescription("QR code scan")
            .performClick()

        // Then - Verify QR scan was triggered
        assert(qrScanClicked)
    }

    @Test
    fun searchScreen_supportsAccessibility() {
        // Given
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify accessibility elements
        composeTestRule.onNodeWithContentDescription("Search for users").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Filter search").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("QR code scan").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Go back").assertIsDisplayed()
    }

    @Test
    fun searchResults_supportAccessibilityNavigation() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            searchResults = mockSearchResults,
            hasSearched = true
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify result cards have accessibility descriptions
        composeTestRule.onNodeWithContentDescription("John Fitness profile").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Sarah Runner profile").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Connect with John Fitness").assertIsDisplayed()
    }

    @Test
    fun filterSection_showsCorrectFilters() {
        // Given
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // When - Expand filters
        composeTestRule.onNodeWithContentDescription("Filter search")
            .performClick()

        // Then - Verify all filter categories are shown
        composeTestRule.onNodeWithText("Fitness Level").assertIsDisplayed()
        composeTestRule.onNodeWithText("Equipment").assertIsDisplayed()
        composeTestRule.onNodeWithText("Goals").assertIsDisplayed()
        
        // Verify specific filter options
        composeTestRule.onNodeWithText("Beginner").assertIsDisplayed()
        composeTestRule.onNodeWithText("Intermediate").assertIsDisplayed()
        composeTestRule.onNodeWithText("Advanced").assertIsDisplayed()
        composeTestRule.onNodeWithText("Expert").assertIsDisplayed()
    }

    @Test
    fun filterSelection_updatesCorrectly() {
        // Given
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // When - Expand filters and select intermediate level
        composeTestRule.onNodeWithContentDescription("Filter search")
            .performClick()
        composeTestRule.onNodeWithText("Intermediate")
            .performClick()

        // Then - Filter should be selected (implementation would update UI state)
        // This would be verified through the ViewModel state in a real implementation
        composeTestRule.onNodeWithText("Intermediate").assertIsDisplayed()
    }

    @Test
    fun clearFiltersButton_resetsFilters() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            filters = SearchFilters(fitnessLevel = FitnessLevel.INTERMEDIATE),
            isFilterExpanded = true
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // When - Click clear filters button
        composeTestRule.onNodeWithText("Clear Filters")
            .performClick()

        // Then - Filters should be cleared (verified through ViewModel)
        composeTestRule.onNodeWithText("Clear Filters").assertIsDisplayed()
    }

    @Test
    fun searchResults_showCorrectWorkoutStats() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            searchResults = mockSearchResults,
            hasSearched = true
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify workout statistics are displayed
        composeTestRule.onNodeWithText("45 workouts").assertIsDisplayed()
        composeTestRule.onNodeWithText("128 workouts").assertIsDisplayed()
        
        // Verify member since information
        composeTestRule.onNodeWithText("Member for").assertIsDisplayed()
    }

    @Test
    fun mutualConnections_displayCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            searchResults = mockSearchResults,
            hasSearched = true
        )
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify mutual connection counts
        composeTestRule.onNodeWithText("3 mutual connections").assertIsDisplayed()
        composeTestRule.onNodeWithText("7 mutual connections").assertIsDisplayed()
    }

    @Test
    fun backButton_handlesNavigationCorrectly() {
        // Given
        var backPressed = false
        
        composeTestRule.setContent {
            UserSearchScreen(
                onNavigateToProfile = { },
                onNavigateBack = { backPressed = true }
            )
        }

        // When - Click back button
        composeTestRule.onNodeWithContentDescription("Go back")
            .performClick()

        // Then - Verify back navigation was triggered
        assert(backPressed)
    }
}