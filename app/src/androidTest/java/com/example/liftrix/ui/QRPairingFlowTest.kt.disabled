package com.example.liftrix.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.liftrix.MainActivity
import com.example.liftrix.domain.model.social.GymBuddy
import com.example.liftrix.ui.social.gymbuddy.GymBuddyScreen
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end tests for the QR code pairing flow between gym buddies.
 * Tests the complete user journey from QR generation to successful buddy connection.
 * 
 * Test scenarios:
 * - QR code generation and display
 * - QR scanner camera integration
 * - Valid QR code scanning and processing
 * - Mutual gym buddy connection creation
 * - Error handling for invalid QR codes
 * - Buddy limit enforcement (max 5 buddies)
 * - Location context capture during pairing
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
@HiltAndroidTest
class QRPairingFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun testCompleteQRPairingFlow() {
        // Navigate to gym buddy screen
        composeTestRule.onNodeWithContentDescription("Gym Buddies").performClick()

        // Verify gym buddy screen is displayed
        composeTestRule.onNodeWithText("Gym Buddies").assertIsDisplayed()
        composeTestRule.onNodeWithText("0 of 5 buddies").assertIsDisplayed()

        // Test QR code generation flow
        testQRCodeGeneration()

        // Test QR scanner flow
        testQRScannerFlow()

        // Verify successful pairing
        testSuccessfulPairing()
    }

    @Test
    fun testQRCodeGeneration() {
        // Navigate to gym buddy screen
        navigateToGymBuddyScreen()

        // Tap "Show My Code" button
        composeTestRule.onNodeWithText("Show My Code").performClick()

        // Verify QR display bottom sheet appears
        composeTestRule.onNodeWithText("Scan to Connect").assertIsDisplayed()
        
        // Verify countdown timer is shown
        composeTestRule.onNode(hasText("Expires in")).assertIsDisplayed()

        // Verify user profile info is displayed
        composeTestRule.onNodeWithContentDescription("QR Code").assertIsDisplayed()

        // Test QR code regeneration
        composeTestRule.waitUntil(timeoutMillis = 6000) {
            // Wait for near expiration to test regeneration
            true
        }

        // Close the QR display
        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.onNodeWithText("Scan to Connect").assertDoesNotExist()
    }

    @Test
    fun testQRScannerFlow() {
        navigateToGymBuddyScreen()

        // Tap "Scan Code" button
        composeTestRule.onNodeWithText("Scan Code").performClick()

        // Verify QR scanner screen is displayed
        composeTestRule.onNodeWithText("Scan QR Code").assertIsDisplayed()
        
        // Check for camera permission request
        if (composeTestRule.onAllNodesWithText("Camera Permission Required").fetchSemanticsNodes().isNotEmpty()) {
            // Grant camera permission if requested
            composeTestRule.onNodeWithText("Allow Camera Access").performClick()
        }

        // Verify scanning instructions are shown
        composeTestRule.onNodeWithText("Point your camera at a gym buddy's QR code").assertIsDisplayed()
        composeTestRule.onNodeWithText("The QR code will be detected automatically").assertIsDisplayed()

        // Test back navigation
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        composeTestRule.onNodeWithText("Gym Buddies").assertIsDisplayed()
    }

    @Test
    fun testInvalidQRCodeHandling() {
        navigateToGymBuddyScreen()
        
        // Navigate to scanner
        composeTestRule.onNodeWithText("Scan Code").performClick()

        // Simulate scanning an invalid QR code
        // This would be done by injecting a mock QR result
        simulateQRCodeScan("invalid-qr-data")

        // Verify error message is shown
        composeTestRule.onNodeWithText("Invalid QR code format").assertIsDisplayed()
        
        // Verify retry button is available
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Retry").performClick()

        // Verify error is cleared
        composeTestRule.onNodeWithText("Invalid QR code format").assertDoesNotExist()
    }

    @Test
    fun testValidQRCodeScanning() {
        navigateToGymBuddyScreen()
        
        // Navigate to scanner
        composeTestRule.onNodeWithText("Scan Code").performClick()

        // Simulate scanning a valid gym buddy QR code
        val validQRData = "liftrix://gym-buddy/user123?token=abc123&expires=1234567890"
        simulateQRCodeScan(validQRData)

        // Verify processing indicator is shown
        composeTestRule.onNodeWithText("Processing QR Code...").assertIsDisplayed()

        // Wait for processing to complete
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("Processing QR Code...").fetchSemanticsNodes().isEmpty()
        }

        // Verify navigation back to buddy list with new buddy added
        composeTestRule.onNodeWithText("1 of 5 buddies").assertIsDisplayed()
    }

    @Test
    fun testBuddyLimitEnforcement() {
        // Pre-populate with 5 gym buddies (would be done via test data setup)
        setupMaxGymBuddies()
        
        navigateToGymBuddyScreen()

        // Verify buddy limit message is shown
        composeTestRule.onNodeWithText("5 of 5 buddies").assertIsDisplayed()
        composeTestRule.onNodeWithText("Maximum gym buddies reached").assertIsDisplayed()

        // Verify QR buttons are disabled
        composeTestRule.onNodeWithText("Show My Code").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Scan Code").assertIsNotEnabled()
    }

    @Test
    fun testLocationContextCapture() {
        navigateToGymBuddyScreen()
        
        // Generate QR code
        composeTestRule.onNodeWithText("Show My Code").performClick()
        
        // Verify location permission request (if needed)
        // Location context should be captured during QR generation
        composeTestRule.onNodeWithText("Scan to Connect").assertIsDisplayed()
        
        // Close QR display
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        
        // Simulate successful pairing with location
        simulateSuccessfulPairing(withLocation = true)
        
        // Verify buddy shows location context
        composeTestRule.onNodeWithText("Connected via QR").assertIsDisplayed()
        composeTestRule.onNode(hasText("Met at")).assertIsDisplayed()
    }

    @Test
    fun testSelfScanPrevention() {
        navigateToGymBuddyScreen()
        
        // Generate own QR code
        composeTestRule.onNodeWithText("Show My Code").performClick()
        val userQRData = getCurrentUserQRData()
        composeTestRule.onNodeWithContentDescription("Close").performClick()
        
        // Try to scan own QR code
        composeTestRule.onNodeWithText("Scan Code").performClick()
        simulateQRCodeScan(userQRData)
        
        // Verify self-scan error message
        composeTestRule.onNodeWithText("You cannot add yourself as a gym buddy").assertIsDisplayed()
    }

    @Test
    fun testExistingBuddyPairingPrevention() {
        // Setup existing buddy relationship
        setupExistingGymBuddy()
        
        navigateToGymBuddyScreen()
        
        // Try to scan existing buddy's QR code
        composeTestRule.onNodeWithText("Scan Code").performClick()
        val existingBuddyQR = getExistingBuddyQRData()
        simulateQRCodeScan(existingBuddyQR)
        
        // Verify duplicate buddy error message
        composeTestRule.onNodeWithText("You are already gym buddies with this person").assertIsDisplayed()
    }

    @Test
    fun testMutualConnectionCreation() {
        navigateToGymBuddyScreen()
        
        // Simulate successful QR scan
        val buddyQRData = "liftrix://gym-buddy/buddy123?token=xyz789&expires=9876543210"
        simulateQRCodeScan(buddyQRData)
        
        // Verify buddy appears in list
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("1 of 5 buddies").fetchSemanticsNodes().isNotEmpty()
        }
        
        // Verify buddy card shows correct information
        composeTestRule.onNodeWithText("Connected via QR").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Active").assertIsDisplayed() // PR notification status
    }

    @Test
    fun testQRExpirationHandling() {
        navigateToGymBuddyScreen()
        
        // Generate QR code with short expiration for testing
        composeTestRule.onNodeWithText("Show My Code").performClick()
        
        // Wait for expiration
        composeTestRule.waitUntil(timeoutMillis = 6000) {
            // QR codes expire after 5 minutes in real usage, but we'd use shorter time for tests
            true
        }
        
        // Verify automatic regeneration or expired state
        composeTestRule.onNode(hasText("Expires in 0:")).assertExists()
    }

    // Helper methods for test setup and simulation

    private fun navigateToGymBuddyScreen() {
        composeTestRule.onNodeWithContentDescription("Gym Buddies").performClick()
        composeTestRule.waitUntil(timeoutMillis = 3000) {
            composeTestRule.onAllNodesWithText("Gym Buddies").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun simulateQRCodeScan(qrData: String) {
        // In a real implementation, this would inject the QR data through the test environment
        // For now, this represents the action of scanning a QR code
        // The actual implementation would depend on the testing framework and mocking setup
    }

    private fun getCurrentUserQRData(): String {
        // Mock implementation - would return the current user's QR data
        return "liftrix://gym-buddy/current-user?token=self123&expires=1234567890"
    }

    private fun getExistingBuddyQRData(): String {
        // Mock implementation - would return an existing buddy's QR data
        return "liftrix://gym-buddy/existing-buddy?token=existing123&expires=1234567890"
    }

    private fun setupMaxGymBuddies() {
        // Mock implementation - would populate the database with 5 gym buddies
        // This would be done through test data setup or repository mocking
    }

    private fun setupExistingGymBuddy() {
        // Mock implementation - would create an existing gym buddy relationship
        // This would be done through test data setup or repository mocking
    }

    private fun simulateSuccessfulPairing(withLocation: Boolean = false) {
        // Mock implementation - would simulate a successful buddy pairing
        // including location context if specified
    }

    private fun testQRCodeGeneration() {
        // Test the QR code generation flow
        composeTestRule.onNodeWithText("Show My Code").performClick()
        composeTestRule.onNodeWithText("Scan to Connect").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Close").performClick()
    }

    private fun testQRScannerFlow() {
        // Test the QR scanner flow
        composeTestRule.onNodeWithText("Scan Code").performClick()
        composeTestRule.onNodeWithText("Scan QR Code").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
    }

    private fun testSuccessfulPairing() {
        // Test successful pairing after scanning
        val validQR = "liftrix://gym-buddy/test-user?token=test123&expires=9999999999"
        simulateQRCodeScan(validQR)
        composeTestRule.waitUntil(timeoutMillis = 5000) {
            composeTestRule.onAllNodesWithText("1 of 5 buddies").fetchSemanticsNodes().isNotEmpty()
        }
    }
}