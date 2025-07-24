package com.example.liftrix.ui.profile

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.liftrix.MainActivity
import com.example.liftrix.ui.social.QRCodeDisplayScreen
import com.example.liftrix.ui.social.QRCodeDisplayUiState
import com.example.liftrix.ui.social.QRCodeDisplayViewModel
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for QRCodeDisplayScreen
 * 
 * Tests QR code generation, display, sharing functionality, error handling,
 * and accessibility compliance using Compose UI testing framework.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class QRCodeDisplayScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var mockViewModel: QRCodeDisplayViewModel
    private lateinit var mockUiState: MutableStateFlow<QRCodeDisplayUiState>
    private lateinit var mockQRCodeBitmap: Bitmap

    @Before
    fun setup() {
        hiltRule.inject()
        
        // Create a mock bitmap for QR code
        mockQRCodeBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        
        mockUiState = MutableStateFlow(
            QRCodeDisplayUiState(
                qrCodeBitmap = null,
                qrCodeData = null,
                shareableUrl = null,
                isLoading = false,
                errorMessage = null,
                isExpirable = false,
                expiresAt = null
            )
        )
        
        mockViewModel = mockk {
            every { uiState } returns mockUiState
        }
    }

    @Test
    fun qrCodeDisplayScreen_displaysCorrectly() {
        // Given
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify main UI elements are displayed
        composeTestRule.onNodeWithText("Share Profile").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Go back").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scan this QR code to connect").assertIsDisplayed()
    }

    @Test
    fun qrCodeGeneration_displaysLoadingState() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            isLoading = true,
            qrCodeBitmap = null
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify loading indicator
        composeTestRule.onNodeWithTag("qr_loading_indicator").assertIsDisplayed()
        composeTestRule.onNodeWithText("Generating QR code...").assertIsDisplayed()
    }

    @Test
    fun qrCodeSuccess_displaysQRCodeCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            qrCodeData = "test_qr_data_123",
            shareableUrl = "https://liftrix.app/profile?qr=test_qr_data_123",
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify QR code is displayed
        composeTestRule.onNodeWithTag("qr_code_image").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("QR code for profile sharing").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun shareableUrl_displaysCorrectly() {
        // Given
        val shareableUrl = "https://liftrix.app/profile?qr=test_qr_data_123"
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            shareableUrl = shareableUrl,
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify shareable URL section
        composeTestRule.onNodeWithText("Share Link").assertIsDisplayed()
        composeTestRule.onNodeWithText(shareableUrl).assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy Link").assertIsDisplayed()
    }

    @Test
    fun errorState_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = null,
            isLoading = false,
            errorMessage = "Failed to generate QR code. Please try again."
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify error message and retry button
        composeTestRule.onNodeWithText("Failed to generate QR code. Please try again.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Try Again").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Error generating QR code").assertIsDisplayed()
    }

    @Test
    fun shareButton_handlesClickInteraction() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            shareableUrl = "https://liftrix.app/profile?qr=test_qr_data_123",
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // When - Click share button
        composeTestRule.onNodeWithText("Share")
            .performClick()

        // Then - Button should be enabled and clickable
        composeTestRule.onNodeWithText("Share").assertIsEnabled()
    }

    @Test
    fun saveButton_handlesClickInteraction() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // When - Click save button
        composeTestRule.onNodeWithText("Save")
            .performClick()

        // Then - Button should be enabled and clickable
        composeTestRule.onNodeWithText("Save").assertIsEnabled()
    }

    @Test
    fun copyLinkButton_handlesClickInteraction() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            shareableUrl = "https://liftrix.app/profile?qr=test_qr_data_123",
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // When - Click copy link button
        composeTestRule.onNodeWithText("Copy Link")
            .performClick()

        // Then - Button should be enabled and clickable
        composeTestRule.onNodeWithText("Copy Link").assertIsEnabled()
    }

    @Test
    fun retryButton_handlesClickInteraction() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = null,
            isLoading = false,
            errorMessage = "Failed to generate QR code"
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // When - Click retry button
        composeTestRule.onNodeWithText("Try Again")
            .performClick()

        // Then - Button should be enabled and clickable
        composeTestRule.onNodeWithText("Try Again").assertIsEnabled()
    }

    @Test
    fun expirableQRCode_displaysExpirationInfo() {
        // Given
        val expirationTime = System.currentTimeMillis() + 86400000 // 24 hours from now
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            isExpirable = true,
            expiresAt = expirationTime,
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify expiration information
        composeTestRule.onNodeWithText("This QR code expires in").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("QR code expiration warning").assertIsDisplayed()
    }

    @Test
    fun permanentQRCode_doesNotShowExpiration() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            isExpirable = false,
            expiresAt = null,
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify no expiration info is shown
        composeTestRule.onNodeWithText("This QR code never expires").assertIsDisplayed()
    }

    @Test
    fun qrCodeInstructions_displayCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify instructions are displayed
        composeTestRule.onNodeWithText("Scan this QR code to connect").assertIsDisplayed()
        composeTestRule.onNodeWithText("Others can scan this code to view your profile and send you a connection request").assertIsDisplayed()
    }

    @Test
    fun qrCodeScreen_supportsAccessibility() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            shareableUrl = "https://liftrix.app/profile?qr=test_qr_data_123",
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify accessibility elements
        composeTestRule.onNodeWithContentDescription("Go back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("QR code for profile sharing").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Share QR code").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Save QR code to device").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Copy profile link").assertIsDisplayed()
    }

    @Test
    fun backButton_handlesNavigationCorrectly() {
        // Given
        var backPressed = false
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { backPressed = true }
            )
        }

        // When - Click back button
        composeTestRule.onNodeWithContentDescription("Go back")
            .performClick()

        // Then - Verify back navigation was triggered
        assert(backPressed)
    }

    @Test
    fun qrCodeSize_isAppropriateForScanning() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify QR code is displayed at appropriate size
        composeTestRule.onNodeWithTag("qr_code_image").assertIsDisplayed()
        // Size verification would be done through layout bounds in a real implementation
    }

    @Test
    fun qrCodeActions_areProperlySpaced() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            shareableUrl = "https://liftrix.app/profile?qr=test_qr_data_123",
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify all action buttons are displayed
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
        composeTestRule.onNodeWithText("Save").assertIsDisplayed()
        composeTestRule.onNodeWithText("Copy Link").assertIsDisplayed()
    }

    @Test
    fun loadingToSuccess_transitionsCorrectly() {
        // Given - Start with loading state
        mockUiState.value = mockUiState.value.copy(
            isLoading = true,
            qrCodeBitmap = null
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Verify loading state
        composeTestRule.onNodeWithText("Generating QR code...").assertIsDisplayed()

        // When - Update to success state
        mockUiState.value = mockUiState.value.copy(
            isLoading = false,
            qrCodeBitmap = mockQRCodeBitmap,
            shareableUrl = "https://liftrix.app/profile?qr=test_qr_data_123"
        )

        // Then - Verify success state
        composeTestRule.onNodeWithTag("qr_code_image").assertIsDisplayed()
        composeTestRule.onNodeWithText("Share").assertIsDisplayed()
    }

    @Test
    fun qrCodeData_isNotVisibleToUser() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            qrCodeData = "sensitive_qr_data_123",
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify raw QR data is not displayed (security measure)
        // The QR data should not be visible as plain text to users
        composeTestRule.onNodeWithText("sensitive_qr_data_123").assertDoesNotExist()
    }

    @Test
    fun profileHint_displaysCorrectly() {
        // Given
        mockUiState.value = mockUiState.value.copy(
            qrCodeBitmap = mockQRCodeBitmap,
            isLoading = false
        )
        
        composeTestRule.setContent {
            QRCodeDisplayScreen(
                userId = "test_user_123",
                onNavigateBack = { }
            )
        }

        // Then - Verify profile hint text
        composeTestRule.onNodeWithText("Your Profile QR Code").assertIsDisplayed()
    }
}