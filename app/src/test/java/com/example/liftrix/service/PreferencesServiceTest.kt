package com.example.liftrix.service

import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.ui.progress.components.WidgetLayoutMode
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for PreferencesServiceImpl
 * 
 * Tests all service methods including user preference retrieval, layout mode updates,
 * user level changes, widget visibility management, and default restoration using 
 * MockK and proper coroutine testing patterns following Given/When/Then structure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PreferencesServiceTest {
    
    @MockK
    private lateinit var preferencesRepository: WidgetPreferencesRepository
    
    @MockK
    private lateinit var authRepository: AuthRepository
    
    private lateinit var testDispatcher: CoroutineDispatcher
    private lateinit var preferencesService: PreferencesService
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        testDispatcher = StandardTestDispatcher()
        preferencesService = PreferencesServiceImpl(
            preferencesRepository,
            authRepository,
            testDispatcher
        )
    }
    
    @Test
    fun `given valid user id, when getting user preferences, then returns success with preferences`() = runTest {
        // Given
        val userId = "user123"
        val expectedPreferences = WidgetPreferences.createDefault(userId)
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns 
            flowOf(LiftrixResult.Success(expectedPreferences))
        
        // When
        val result = preferencesService.getUserPreferences(userId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return expected preferences", expectedPreferences, result.getOrNull())
        
        coVerify { preferencesRepository.getWidgetPreferences(userId) }
    }
    
    @Test
    fun `given user with no existing preferences, when getting user preferences, then creates and returns defaults`() = runTest {
        // Given
        val userId = "user123"
        val notFoundError = LiftrixError.NotFoundError("No preferences found")
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns 
            flowOf(LiftrixResult.Error(notFoundError))
        coEvery { preferencesRepository.saveWidgetPreferences(any()) } returns 
            LiftrixResult.Success(Unit)
        
        // When
        val result = preferencesService.getUserPreferences(userId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        val preferences = result.getOrNull()!!
        assertEquals("Should return preferences with correct user id", userId, preferences.userId)
        
        coVerify { preferencesRepository.getWidgetPreferences(userId) }
        coVerify { preferencesRepository.saveWidgetPreferences(any()) }
    }
    
    @Test
    fun `given invalid preferences data, when getting user preferences, then returns validation error`() = runTest {
        // Given
        val userId = "user123"
        val invalidPreferences = mockk<WidgetPreferences> {
            every { validate() } throws IllegalArgumentException("Invalid widget configuration")
        }
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns 
            flowOf(LiftrixResult.Success(invalidPreferences))
        
        // When
        val result = preferencesService.getUserPreferences(userId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.ValidationError", error is LiftrixError.ValidationError)
        
        val validationError = error as LiftrixError.ValidationError
        assertEquals("preferences", validationError.field)
        assertEquals("Invalid preferences: Invalid widget configuration", validationError.errorMessage)
        assertEquals(userId, validationError.analyticsContext["userId"])
    }
    
    @Test
    fun `given repository error, when getting user preferences, then returns database error`() = runTest {
        // Given
        val userId = "user123"
        val repositoryError = RuntimeException("Database connection failed")
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } throws repositoryError
        
        // When
        val result = preferencesService.getUserPreferences(userId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.DatabaseError", error is LiftrixError.DatabaseError)
        
        val databaseError = error as LiftrixError.DatabaseError
        assertEquals("Failed to retrieve user preferences: Database connection failed", databaseError.errorMessage)
        assertEquals(userId, databaseError.analyticsContext["userId"])
    }
    
    @Test
    fun `given valid user and layout mode, when updating layout mode, then returns success`() = runTest {
        // Given
        val userId = "user123"
        val layoutMode = WidgetLayoutMode.GRID
        val currentPreferences = WidgetPreferences.createDefault(userId)
        val updatedPreferences = currentPreferences.updateLayout(DashboardLayoutMode.GRID)
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns 
            flowOf(LiftrixResult.Success(currentPreferences))
        coEvery { preferencesRepository.saveWidgetPreferences(any()) } returns 
            LiftrixResult.Success(Unit)
        
        // When
        val result = preferencesService.updateLayoutMode(userId, layoutMode)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        
        coVerify { preferencesRepository.getWidgetPreferences(userId) }
        coVerify { preferencesRepository.saveWidgetPreferences(any()) }
    }
    
    @Test
    fun `given different layout modes, when updating layout mode, then maps correctly to dashboard layout mode`() = runTest {
        // Given
        val userId = "user123"
        val currentPreferences = WidgetPreferences.createDefault(userId)
        val layoutModes = listOf(
            WidgetLayoutMode.GRID,
            WidgetLayoutMode.STAGGERED,
            WidgetLayoutMode.LIST,
            WidgetLayoutMode.SECTIONS
        )
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns 
            flowOf(LiftrixResult.Success(currentPreferences))
        coEvery { preferencesRepository.saveWidgetPreferences(any()) } returns 
            LiftrixResult.Success(Unit)
        
        // When & Then
        layoutModes.forEach { layoutMode ->
            val result = preferencesService.updateLayoutMode(userId, layoutMode)
            assertTrue("Result should be successful for $layoutMode", result.isSuccess)
        }
        
        coVerify(exactly = layoutModes.size) { preferencesRepository.getWidgetPreferences(userId) }
        coVerify(exactly = layoutModes.size) { preferencesRepository.saveWidgetPreferences(any()) }
    }
    
    @Test
    fun `given repository error, when updating layout mode, then returns business rule error`() = runTest {
        // Given
        val userId = "user123"
        val layoutMode = WidgetLayoutMode.GRID
        val repositoryError = RuntimeException("Save operation failed")
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } throws repositoryError
        
        // When
        val result = preferencesService.updateLayoutMode(userId, layoutMode)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.BusinessRuleError", error is LiftrixError.BusinessRuleError)
        
        val businessRuleError = error as LiftrixError.BusinessRuleError
        assertEquals("layout_mode_update", businessRuleError.rule)
        assertEquals(userId, businessRuleError.analyticsContext["userId"])
        assertEquals(layoutMode.name, businessRuleError.analyticsContext["layoutMode"])
    }
    
    @Test
    fun `given valid user and user level, when updating user level, then returns success`() = runTest {
        // Given
        val userId = "user123"
        val userLevel = UserLevel.INTERMEDIATE
        val currentPreferences = WidgetPreferences.createDefault(userId)
        val updatedPreferences = currentPreferences.updateUserLevel(userLevel)
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns 
            flowOf(LiftrixResult.Success(currentPreferences))
        coEvery { preferencesRepository.saveWidgetPreferences(any()) } returns 
            LiftrixResult.Success(Unit)
        
        // When
        val result = preferencesService.updateUserLevel(userId, userLevel)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        
        coVerify { preferencesRepository.getWidgetPreferences(userId) }
        coVerify { preferencesRepository.saveWidgetPreferences(any()) }
    }
    
    @Test
    fun `given different user levels, when updating user level, then handles all levels correctly`() = runTest {
        // Given
        val userId = "user123"
        val currentPreferences = WidgetPreferences.createDefault(userId)
        val userLevels = listOf(
            UserLevel.BEGINNER,
            UserLevel.INTERMEDIATE,
            UserLevel.ADVANCED
        )
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns 
            flowOf(LiftrixResult.Success(currentPreferences))
        coEvery { preferencesRepository.saveWidgetPreferences(any()) } returns 
            LiftrixResult.Success(Unit)
        
        // When & Then
        userLevels.forEach { userLevel ->
            val result = preferencesService.updateUserLevel(userId, userLevel)
            assertTrue("Result should be successful for $userLevel", result.isSuccess)
        }
        
        coVerify(exactly = userLevels.size) { preferencesRepository.getWidgetPreferences(userId) }
        coVerify(exactly = userLevels.size) { preferencesRepository.saveWidgetPreferences(any()) }
    }
    
    @Test
    fun `given repository error, when updating user level, then returns business rule error`() = runTest {
        // Given
        val userId = "user123"
        val userLevel = UserLevel.ADVANCED
        val repositoryError = RuntimeException("Update operation failed")
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } throws repositoryError
        
        // When
        val result = preferencesService.updateUserLevel(userId, userLevel)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.BusinessRuleError", error is LiftrixError.BusinessRuleError)
        
        val businessRuleError = error as LiftrixError.BusinessRuleError
        assertEquals("user_level_update", businessRuleError.rule)
        assertEquals(userId, businessRuleError.analyticsContext["userId"])
        assertEquals(userLevel.name, businessRuleError.analyticsContext["userLevel"])
    }
    
    @Test
    fun `given valid user id, when resetting to defaults, then returns success`() = runTest {
        // Given
        val userId = "user123"
        val currentPreferences = WidgetPreferences.createDefault(userId)
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns 
            flowOf(LiftrixResult.Success(currentPreferences))
        coEvery { preferencesRepository.saveWidgetPreferences(any()) } returns 
            LiftrixResult.Success(Unit)
        
        // When
        val result = preferencesService.resetToDefaults(userId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        
        coVerify { preferencesRepository.getWidgetPreferences(userId) }
        coVerify { preferencesRepository.saveWidgetPreferences(any()) }
    }
    
    @Test
    fun `given repository error, when resetting to defaults, then returns business rule error`() = runTest {
        // Given
        val userId = "user123"
        val repositoryError = RuntimeException("Reset operation failed")
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } throws repositoryError
        
        // When
        val result = preferencesService.resetToDefaults(userId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.BusinessRuleError", error is LiftrixError.BusinessRuleError)
        
        val businessRuleError = error as LiftrixError.BusinessRuleError
        assertEquals("preferences_reset", businessRuleError.rule)
        assertEquals(userId, businessRuleError.analyticsContext["userId"])
    }
    
    @Test
    fun `given valid user and widget, when updating widget visibility, then returns success`() = runTest {
        // Given
        val userId = "user123"
        val widgetName = "TotalVolume"
        val visible = true
        
        coEvery { preferencesRepository.updateWidgetVisibility(userId, widgetName, visible) } returns 
            LiftrixResult.Success(Unit)
        
        // When
        val result = preferencesService.updateWidgetVisibility(userId, widgetName, visible)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        
        coVerify { preferencesRepository.updateWidgetVisibility(userId, widgetName, visible) }
    }
    
    @Test
    fun `given repository error, when updating widget visibility, then returns business rule error`() = runTest {
        // Given
        val userId = "user123"
        val widgetName = "TotalVolume"
        val visible = false
        val repositoryError = RuntimeException("Visibility update failed")
        
        coEvery { preferencesRepository.updateWidgetVisibility(userId, widgetName, visible) } throws repositoryError
        
        // When
        val result = preferencesService.updateWidgetVisibility(userId, widgetName, visible)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.BusinessRuleError", error is LiftrixError.BusinessRuleError)
        
        val businessRuleError = error as LiftrixError.BusinessRuleError
        assertEquals("widget_visibility_update", businessRuleError.rule)
        assertEquals(userId, businessRuleError.analyticsContext["userId"])
        assertEquals(widgetName, businessRuleError.analyticsContext["widgetName"])
        assertEquals(visible.toString(), businessRuleError.analyticsContext["visible"])
    }
    
    @Test
    fun `given valid user and widget order, when updating widget order, then returns success`() = runTest {
        // Given
        val userId = "user123"
        val widgetOrder = listOf("TotalVolume", "WorkoutFrequency", "ConsistencyStreak")
        
        coEvery { preferencesRepository.updateWidgetOrder(userId, widgetOrder) } returns 
            LiftrixResult.Success(Unit)
        
        // When
        val result = preferencesService.updateWidgetOrder(userId, widgetOrder)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        
        coVerify { preferencesRepository.updateWidgetOrder(userId, widgetOrder) }
    }
    
    @Test
    fun `given repository error, when updating widget order, then returns business rule error`() = runTest {
        // Given
        val userId = "user123"
        val widgetOrder = listOf("TotalVolume", "WorkoutFrequency")
        val repositoryError = RuntimeException("Order update failed")
        
        coEvery { preferencesRepository.updateWidgetOrder(userId, widgetOrder) } throws repositoryError
        
        // When
        val result = preferencesService.updateWidgetOrder(userId, widgetOrder)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.BusinessRuleError", error is LiftrixError.BusinessRuleError)
        
        val businessRuleError = error as LiftrixError.BusinessRuleError
        assertEquals("widget_order_update", businessRuleError.rule)
        assertEquals(userId, businessRuleError.analyticsContext["userId"])
        assertEquals(widgetOrder.size.toString(), businessRuleError.analyticsContext["widgetCount"])
    }
    
    @Test
    fun `given valid auto-refresh settings, when updating auto-refresh settings, then returns success`() = runTest {
        // Given
        val userId = "user123"
        val enabled = true
        val intervalMinutes = 30
        
        coEvery { preferencesRepository.updateAutoRefreshSettings(userId, enabled, intervalMinutes) } returns 
            LiftrixResult.Success(Unit)
        
        // When
        val result = preferencesService.updateAutoRefreshSettings(userId, enabled, intervalMinutes)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        
        coVerify { preferencesRepository.updateAutoRefreshSettings(userId, enabled, intervalMinutes) }
    }
    
    @Test
    fun `given invalid interval, when updating auto-refresh settings, then returns validation error`() = runTest {
        // Given
        val userId = "user123"
        val enabled = true
        val intervalMinutes = 120 // Invalid: > 60
        
        // When
        val result = preferencesService.updateAutoRefreshSettings(userId, enabled, intervalMinutes)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.ValidationError", error is LiftrixError.ValidationError)
        
        val validationError = error as LiftrixError.ValidationError
        assertEquals("intervalMinutes", validationError.field)
        assertEquals("Invalid refresh interval: Refresh interval must be between 1 and 60 minutes", validationError.errorMessage)
        assertEquals(userId, validationError.analyticsContext["userId"])
        assertEquals(intervalMinutes.toString(), validationError.analyticsContext["intervalMinutes"])
    }
    
    @Test
    fun `given interval below minimum, when updating auto-refresh settings, then returns validation error`() = runTest {
        // Given
        val userId = "user123"
        val enabled = true
        val intervalMinutes = 0 // Invalid: < 1
        
        // When
        val result = preferencesService.updateAutoRefreshSettings(userId, enabled, intervalMinutes)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.ValidationError", error is LiftrixError.ValidationError)
        
        val validationError = error as LiftrixError.ValidationError
        assertEquals("intervalMinutes", validationError.field)
        assertTrue("Should contain validation message", validationError.errorMessage.contains("between 1 and 60"))
    }
    
    @Test
    fun `given repository error, when updating auto-refresh settings, then returns business rule error`() = runTest {
        // Given
        val userId = "user123"
        val enabled = false
        val intervalMinutes = 15
        val repositoryError = RuntimeException("Auto-refresh update failed")
        
        coEvery { preferencesRepository.updateAutoRefreshSettings(userId, enabled, intervalMinutes) } throws repositoryError
        
        // When
        val result = preferencesService.updateAutoRefreshSettings(userId, enabled, intervalMinutes)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.BusinessRuleError", error is LiftrixError.BusinessRuleError)
        
        val businessRuleError = error as LiftrixError.BusinessRuleError
        assertEquals("auto_refresh_update", businessRuleError.rule)
        assertEquals(userId, businessRuleError.analyticsContext["userId"])
        assertEquals(enabled.toString(), businessRuleError.analyticsContext["enabled"])
        assertEquals(intervalMinutes.toString(), businessRuleError.analyticsContext["intervalMinutes"])
    }
    
    @Test
    fun `given valid user and section, when toggling section, then returns success`() = runTest {
        // Given
        val userId = "user123"
        val sectionName = "analytics_section"
        
        coEvery { preferencesRepository.toggleSection(userId, sectionName) } returns 
            LiftrixResult.Success(Unit)
        
        // When
        val result = preferencesService.toggleSection(userId, sectionName)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        
        coVerify { preferencesRepository.toggleSection(userId, sectionName) }
    }
    
    @Test
    fun `given repository error, when toggling section, then returns business rule error`() = runTest {
        // Given
        val userId = "user123"
        val sectionName = "charts_section"
        val repositoryError = RuntimeException("Section toggle failed")
        
        coEvery { preferencesRepository.toggleSection(userId, sectionName) } throws repositoryError
        
        // When
        val result = preferencesService.toggleSection(userId, sectionName)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.BusinessRuleError", error is LiftrixError.BusinessRuleError)
        
        val businessRuleError = error as LiftrixError.BusinessRuleError
        assertEquals("section_toggle", businessRuleError.rule)
        assertEquals(userId, businessRuleError.analyticsContext["userId"])
        assertEquals(sectionName, businessRuleError.analyticsContext["sectionName"])
    }
    
    @Test
    fun `given multiple concurrent preference updates, when executed simultaneously, then handles concurrency correctly`() = runTest {
        // Given
        val userId = "user123"
        val currentPreferences = WidgetPreferences.createDefault(userId)
        val operations = listOf(
            { preferencesService.updateLayoutMode(userId, WidgetLayoutMode.GRID) },
            { preferencesService.updateUserLevel(userId, UserLevel.INTERMEDIATE) },
            { preferencesService.updateWidgetVisibility(userId, "TotalVolume", true) },
            { preferencesService.updateAutoRefreshSettings(userId, true, 30) },
            { preferencesService.toggleSection(userId, "analytics_section") }
        )
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns 
            flowOf(LiftrixResult.Success(currentPreferences))
        coEvery { preferencesRepository.saveWidgetPreferences(any()) } returns 
            LiftrixResult.Success(Unit)
        coEvery { preferencesRepository.updateWidgetVisibility(userId, any(), any()) } returns 
            LiftrixResult.Success(Unit)
        coEvery { preferencesRepository.updateAutoRefreshSettings(userId, any(), any()) } returns 
            LiftrixResult.Success(Unit)
        coEvery { preferencesRepository.toggleSection(userId, any()) } returns 
            LiftrixResult.Success(Unit)
        
        // When - Execute multiple concurrent operations
        val results = operations.map { operation ->
            operation()
        }
        
        // Then
        results.forEach { result ->
            assertTrue("Each result should be successful", result.isSuccess)
        }
        
        // Verify that repository methods were called
        coVerify { preferencesRepository.getWidgetPreferences(userId) }
        coVerify { preferencesRepository.saveWidgetPreferences(any()) }
        coVerify { preferencesRepository.updateWidgetVisibility(userId, any(), any()) }
        coVerify { preferencesRepository.updateAutoRefreshSettings(userId, any(), any()) }
        coVerify { preferencesRepository.toggleSection(userId, any()) }
    }
}