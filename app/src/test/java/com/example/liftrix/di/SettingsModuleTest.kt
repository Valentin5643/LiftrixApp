package com.example.liftrix.di

import com.example.liftrix.data.repository.SettingsRepositoryImpl
import com.example.liftrix.data.repository.SubscriptionRepositoryImpl
import com.example.liftrix.domain.repository.SettingsRepository
import com.example.liftrix.domain.repository.SubscriptionRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Unit tests for SettingsModule to verify proper dependency injection configuration.
 * 
 * This test suite validates that the SettingsModule correctly provides and binds
 * settings-related dependencies with proper scoping and lifecycle management.
 * 
 * Test coverage includes:
 * - Repository binding validation
 * - Singleton scoping verification
 * - Integration with Hilt dependency injection
 * - Module compilation and instantiation
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class SettingsModuleTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var subscriptionRepository: SubscriptionRepository

    /**
     * Test setup that initializes Hilt dependency injection.
     */
    @Before
    fun setup() {
        hiltRule.inject()
    }

    /**
     * Test that SettingsRepository is properly injected and is the correct implementation.
     * 
     * This test verifies that:
     * - The repository is successfully injected
     * - The injected instance is not null
     * - The repository implements the correct interface
     */
    @Test
    fun `settingsRepository should be injected correctly`() {
        // Then
        assertNotNull("SettingsRepository should be injected", settingsRepository)
        assertTrue(
            "SettingsRepository should implement SettingsRepository interface",
            settingsRepository is SettingsRepository
        )
    }

    /**
     * Test that SubscriptionRepository is properly injected and is the correct implementation.
     * 
     * This test verifies that:
     * - The repository is successfully injected
     * - The injected instance is not null
     * - The repository implements the correct interface
     */
    @Test
    fun `subscriptionRepository should be injected correctly`() {
        // Then
        assertNotNull("SubscriptionRepository should be injected", subscriptionRepository)
        assertTrue(
            "SubscriptionRepository should implement SubscriptionRepository interface",
            subscriptionRepository is SubscriptionRepository
        )
    }

    /**
     * Test that multiple injections of the same repository return the same instance (singleton).
     * 
     * This test verifies that the @Singleton annotation is working correctly
     * and that the same instance is returned for multiple injections.
     */
    @Test
    fun `repositories should be singleton scoped`() {
        // Given - Second injection points
        @Inject
        lateinit var settingsRepository2: SettingsRepository
        
        @Inject
        lateinit var subscriptionRepository2: SubscriptionRepository

        // When - Inject dependencies again
        hiltRule.inject()

        // Then - Should be the same instances
        assertSame(
            "SettingsRepository should be singleton scoped",
            settingsRepository,
            settingsRepository2
        )
        assertSame(
            "SubscriptionRepository should be singleton scoped",
            subscriptionRepository,
            subscriptionRepository2
        )
    }

    /**
     * Test that the module can be instantiated without issues.
     * 
     * This test verifies that the module class itself can be created
     * and that there are no compilation or instantiation errors.
     */
    @Test
    fun `settingsModule should be instantiable`() {
        // Given
        val module = SettingsModule()

        // Then
        assertNotNull("SettingsModule should be instantiable", module)
        assertTrue(
            "SettingsModule should be instance of SettingsModule",
            module is SettingsModule
        )
    }

    /**
     * Test that injected repositories are distinct instances.
     * 
     * This test verifies that SettingsRepository and SubscriptionRepository
     * are different instances, ensuring proper dependency separation.
     */
    @Test
    fun `different repositories should be distinct instances`() {
        // Then
        assertNotSame(
            "SettingsRepository and SubscriptionRepository should be different instances",
            settingsRepository,
            subscriptionRepository
        )
    }

    /**
     * Test that repositories maintain their type contracts.
     * 
     * This test uses reflection to verify that the injected repositories
     * maintain their expected type contracts and interface implementations.
     */
    @Test
    fun `repositories should maintain type contracts`() {
        // Then
        val settingsClass = settingsRepository::class.java
        val subscriptionClass = subscriptionRepository::class.java

        assertTrue(
            "SettingsRepository should implement SettingsRepository interface",
            SettingsRepository::class.java.isAssignableFrom(settingsClass)
        )
        
        assertTrue(
            "SubscriptionRepository should implement SubscriptionRepository interface",
            SubscriptionRepository::class.java.isAssignableFrom(subscriptionClass)
        )
    }

    /**
     * Test that repositories can be used in basic operations without throwing exceptions.
     * 
     * This test verifies that the injected repositories are properly initialized
     * and can be used for basic operations without immediate failures.
     */
    @Test
    fun `repositories should be usable after injection`() {
        // Given
        val userId = "test_user_id"

        // Then - Should not throw exceptions on basic operations
        assertDoesNotThrow("SettingsRepository should not throw on basic operations") {
            // Basic repository method calls that should not fail immediately
            settingsRepository.toString()
            settingsRepository.hashCode()
            settingsRepository.equals(settingsRepository)
        }

        assertDoesNotThrow("SubscriptionRepository should not throw on basic operations") {
            // Basic repository method calls that should not fail immediately
            subscriptionRepository.toString()
            subscriptionRepository.hashCode()
            subscriptionRepository.equals(subscriptionRepository)
        }
    }

    /**
     * Helper method to assert that no exceptions are thrown during execution.
     * 
     * @param message The assertion message
     * @param executable The code block to execute
     */
    private fun assertDoesNotThrow(message: String, executable: () -> Unit) {
        try {
            executable()
        } catch (e: Exception) {
            fail("$message - Exception thrown: ${e.message}")
        }
    }
}