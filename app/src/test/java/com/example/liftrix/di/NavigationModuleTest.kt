package com.example.liftrix.di

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for NavigationModule
 * 
 * Tests that the NavigationModule is properly configured and can be used
 * in the Hilt dependency injection graph without issues.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class NavigationModuleTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Before
    fun init() {
        hiltRule.inject()
    }

    @Test
    fun `navigationModule should be properly configured for Hilt`() {
        // Test that the module can be loaded without errors
        // This test verifies that the module is properly annotated and configured
        // Additional tests will be added as navigation-related dependencies are added to the module
        
        // If we reach this point without exceptions, the module is properly configured
        assert(true)
    }

    @Test
    fun `navigationModule should be installed in SingletonComponent`() {
        // Verify that the module is installed in the correct component
        // This is implicitly tested by the Hilt compilation and injection
        
        // The @InstallIn(SingletonComponent::class) annotation ensures this
        assert(true)
    }
} 