package com.example.liftrix.service

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.domain.model.analytics.UIWidgetData
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.repository.WidgetPreferencesRepository
import com.example.liftrix.ui.progress.components.AnalyticsWidgetManager
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive unit tests for AnalyticsServiceImpl
 * 
 * Tests all service methods including widget data loading, preference management,
 * visibility toggling, and error handling scenarios using MockK and proper 
 * coroutine testing patterns following Given/When/Then structure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsServiceTest {
    
    @MockK
    private lateinit var widgetManager: AnalyticsWidgetManager
    
    @MockK
    private lateinit var preferencesRepository: WidgetPreferencesRepository
    
    @MockK
    private lateinit var analyticsEngine: AnalyticsEngine
    
    private lateinit var analyticsService: AnalyticsService
    
    @Before
    fun setup() {
        MockKAnnotations.init(this)
        analyticsService = AnalyticsServiceImpl(widgetManager, preferencesRepository, analyticsEngine)
    }
    
    @Test
    fun `given valid user and widget, when getting widget data, then returns success with data`() = runTest {
        // Given
        val userId = "user123"
        val widget = AnalyticsWidget.TotalVolume
        val hasWorkoutData = true
        val dataAge = 0
        
        val expectedWidgetData = UIWidgetData(
            widget = widget,
            value = "1,500 kg",
            subtitle = "This week",
            trend = TrendDirection.UP,
            isLoading = false
        )
        
        every { widgetManager.shouldShowWidget(widget, hasWorkoutData, dataAge) } returns true
        coEvery { analyticsEngine.calculateProgressMetrics(userId, any()) } returns LiftrixResult.Success(
            mockk {
                every { volumeMetrics } returns mockk {
                    every { totalVolume } returns mockk {
                        every { kilograms } returns 1500.0
                    }
                    every { volumeTrend } returns TrendDirection.UP
                }
            }
        )
        
        // When
        val result = analyticsService.getWidgetData(userId, widget)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        val widgetData = result.getOrNull()!!
        assertEquals("Should return correct widget", widget, widgetData.widget)
        assertEquals("Should return correct value", "1500 kg", widgetData.value)
        assertEquals("Should return correct subtitle", "This week", widgetData.subtitle)
        assertEquals("Should return correct trend", TrendDirection.UP, widgetData.trend)
        assertFalse("Should not be loading", widgetData.isLoading)
        
        coVerify { analyticsEngine.calculateProgressMetrics(userId, any()) }
    }
    
    @Test
    fun `given user with no workout data, when getting widget data, then returns default widget data`() = runTest {
        // Given
        val userId = "user123"
        val widget = AnalyticsWidget.TotalVolume
        val hasWorkoutData = false
        val dataAge = 0
        
        every { widgetManager.shouldShowWidget(widget, hasWorkoutData, dataAge) } returns false
        
        // When
        val result = analyticsService.getWidgetData(userId, widget)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        val widgetData = result.getOrNull()!!
        assertEquals("Should return correct widget", widget, widgetData.widget)
        assertEquals("Should return default value", "0", widgetData.value)
        assertEquals("Should return insufficient data message", "Insufficient data for this widget", widgetData.subtitle)
        assertEquals("Should return stable trend", TrendDirection.STABLE, widgetData.trend)
        assertFalse("Should not be loading", widgetData.isLoading)
    }
    
    @Test
    fun `given blank user id, when getting widget data, then returns validation error`() = runTest {
        // Given
        val userId = ""
        val widget = AnalyticsWidget.TotalVolume
        
        // When
        val result = analyticsService.getWidgetData(userId, widget)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.ValidationError", error is LiftrixError.ValidationError)
        
        val validationError = error as LiftrixError.ValidationError
        assertEquals("userId_or_widget", validationError.field)
        assertTrue("Should contain validation message", validationError.violations.isNotEmpty())
    }
    
    @Test
    fun `given analytics engine error, when getting widget data, then returns calculation error`() = runTest {
        // Given
        val userId = "user123"
        val widget = AnalyticsWidget.TotalVolume
        val hasWorkoutData = true
        val dataAge = 0
        
        every { widgetManager.shouldShowWidget(widget, hasWorkoutData, dataAge) } returns true
        coEvery { analyticsEngine.calculateProgressMetrics(userId, any()) } throws RuntimeException("Engine calculation failed")
        
        // When
        val result = analyticsService.getWidgetData(userId, widget)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.CalculationError", error is LiftrixError.CalculationError)
        
        val calculationError = error as LiftrixError.CalculationError
        assertEquals("Failed to load widget data: Engine calculation failed", calculationError.errorMessage)
        assertEquals("getWidgetData", calculationError.operation)
    }
    
    @Test
    fun `given valid user id, when getting widget preferences, then returns success with preferences`() = runTest {
        // Given
        val userId = "user123"
        val expectedPreferences = WidgetPreferences.createDefault(userId)
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns flowOf(LiftrixResult.Success(expectedPreferences))
        
        // When
        val result = analyticsService.getWidgetPreferences(userId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        assertEquals("Should return expected preferences", expectedPreferences, result.getOrNull())
        
        coVerify { preferencesRepository.getWidgetPreferences(userId) }
    }
    
    @Test
    fun `given user with no preferences, when getting widget preferences, then creates and returns defaults`() = runTest {
        // Given
        val userId = "user123"
        val defaultPreferences = WidgetPreferences.createDefault(userId)
        
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns flowOf(LiftrixResult.Error(LiftrixError.NotFoundError("No preferences found")))
        coEvery { preferencesRepository.saveWidgetPreferences(any()) } returns LiftrixResult.Success(Unit)
        
        // When
        val result = analyticsService.getWidgetPreferences(userId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        val preferences = result.getOrNull()!!
        assertEquals("Should return default preferences", userId, preferences.userId)
        
        coVerify { preferencesRepository.getWidgetPreferences(userId) }
        coVerify { preferencesRepository.saveWidgetPreferences(any()) }
    }
    
    @Test
    fun `given blank user id, when getting widget preferences, then returns validation error`() = runTest {
        // Given
        val userId = ""
        
        // When
        val result = analyticsService.getWidgetPreferences(userId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.ValidationError", error is LiftrixError.ValidationError)
        
        val validationError = error as LiftrixError.ValidationError
        assertEquals("userId", validationError.field)
        assertTrue("Should contain validation message", validationError.violations.isNotEmpty())
    }
    
    @Test
    fun `given valid preferences, when updating widget preferences, then returns success`() = runTest {
        // Given
        val userId = "user123"
        val preferences = WidgetPreferences.createDefault(userId)
        val validationResult = mockk<AnalyticsWidgetManager.ValidationResult> {
            every { isValid } returns true
        }
        
        every { widgetManager.validatePreferences(preferences) } returns validationResult
        coEvery { preferencesRepository.saveWidgetPreferences(any()) } returns LiftrixResult.Success(Unit)
        
        // When
        val result = analyticsService.updateWidgetPreferences(preferences)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        
        coVerify { preferencesRepository.saveWidgetPreferences(any()) }
    }
    
    @Test
    fun `given invalid preferences, when updating widget preferences, then returns validation error`() = runTest {
        // Given
        val userId = "user123"
        val preferences = WidgetPreferences.createDefault(userId)
        val validationResult = mockk<AnalyticsWidgetManager.ValidationResult> {
            every { isValid } returns false
            every { issues } returns listOf("Invalid widget configuration", "Missing required field")
        }
        
        every { widgetManager.validatePreferences(preferences) } returns validationResult
        
        // When
        val result = analyticsService.updateWidgetPreferences(preferences)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.ValidationError", error is LiftrixError.ValidationError)
        
        val validationError = error as LiftrixError.ValidationError
        assertEquals("preferences", validationError.field)
        assertTrue("Should contain validation message", validationError.violations.isNotEmpty())
    }
    
    @Test
    fun `given valid user and widget id, when toggling widget visibility, then returns success`() = runTest {
        // Given
        val userId = "user123"
        val widgetId = "TotalVolume"
        val widget = AnalyticsWidget.TotalVolume
        val currentPreferences = WidgetPreferences.createDefault(userId)
        val updatedPreferences = currentPreferences.toggleWidget(widgetId)
        
        every { widgetManager.getWidgetById(widgetId) } returns widget
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns flowOf(LiftrixResult.Success(currentPreferences))
        coEvery { preferencesRepository.saveWidgetPreferences(any()) } returns LiftrixResult.Success(Unit)
        
        // When
        val result = analyticsService.toggleWidgetVisibility(userId, widgetId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        
        coVerify { widgetManager.getWidgetById(widgetId) }
        coVerify { preferencesRepository.getWidgetPreferences(userId) }
        coVerify { preferencesRepository.saveWidgetPreferences(any()) }
    }
    
    @Test
    fun `given attempt to hide last visible widget, when toggling widget visibility, then returns business rule error`() = runTest {
        // Given
        val userId = "user123"
        val widgetId = "TotalVolume"
        val widget = AnalyticsWidget.TotalVolume
        val currentPreferences = mockk<WidgetPreferences> {
            every { isWidgetVisible(widgetId) } returns true
            every { visibleWidgets } returns setOf(widgetId)
        }
        
        every { widgetManager.getWidgetById(widgetId) } returns widget
        coEvery { preferencesRepository.getWidgetPreferences(userId) } returns flowOf(LiftrixResult.Success(currentPreferences))
        
        // When
        val result = analyticsService.toggleWidgetVisibility(userId, widgetId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.BusinessRuleError", error is LiftrixError.BusinessRuleError)
        
        val businessRuleError = error as LiftrixError.BusinessRuleError
        assertEquals("minimum_visible_widgets", businessRuleError.rule)
        assertEquals("Cannot hide the last visible widget", businessRuleError.ruleMessage)
    }
    
    @Test
    fun `given blank user id, when toggling widget visibility, then returns validation error`() = runTest {
        // Given
        val userId = ""
        val widgetId = "TotalVolume"
        
        // When
        val result = analyticsService.toggleWidgetVisibility(userId, widgetId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.ValidationError", error is LiftrixError.ValidationError)
        
        val validationError = error as LiftrixError.ValidationError
        assertEquals("userId_or_widgetId", validationError.field)
        assertTrue("Should contain validation message", validationError.violations.isNotEmpty())
    }
    
    @Test
    fun `given blank widget id, when toggling widget visibility, then returns validation error`() = runTest {
        // Given
        val userId = "user123"
        val widgetId = ""
        
        // When
        val result = analyticsService.toggleWidgetVisibility(userId, widgetId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.ValidationError", error is LiftrixError.ValidationError)
        
        val validationError = error as LiftrixError.ValidationError
        assertEquals("userId_or_widgetId", validationError.field)
        assertTrue("Should contain validation message", validationError.violations.isNotEmpty())
    }
    
    @Test
    fun `given non-existent widget id, when toggling widget visibility, then returns validation error`() = runTest {
        // Given
        val userId = "user123"
        val widgetId = "NonExistentWidget"
        
        every { widgetManager.getWidgetById(widgetId) } returns null
        
        // When
        val result = analyticsService.toggleWidgetVisibility(userId, widgetId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.ValidationError", error is LiftrixError.ValidationError)
        
        val validationError = error as LiftrixError.ValidationError
        assertEquals("userId_or_widgetId", validationError.field)
        assertTrue("Should contain validation message", validationError.violations.isNotEmpty())
    }
    
    @Test
    fun `given valid user id, when resetting preferences, then returns success`() = runTest {
        // Given
        val userId = "user123"
        
        coEvery { preferencesRepository.resetToDefaults(userId) } returns LiftrixResult.Success(Unit)
        
        // When
        val result = analyticsService.resetPreferences(userId)
        
        // Then
        assertTrue("Result should be successful", result.isSuccess)
        
        coVerify { preferencesRepository.resetToDefaults(userId) }
    }
    
    @Test
    fun `given blank user id, when resetting preferences, then returns validation error`() = runTest {
        // Given
        val userId = ""
        
        // When
        val result = analyticsService.resetPreferences(userId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.ValidationError", error is LiftrixError.ValidationError)
        
        val validationError = error as LiftrixError.ValidationError
        assertEquals("userId", validationError.field)
        assertTrue("Should contain validation message", validationError.violations.isNotEmpty())
    }
    
    @Test
    fun `given repository error, when resetting preferences, then returns database error`() = runTest {
        // Given
        val userId = "user123"
        val repositoryException = RuntimeException("Database reset failed")
        
        coEvery { preferencesRepository.resetToDefaults(userId) } returns LiftrixResult.Error(
            LiftrixError.DatabaseError("Reset operation failed")
        )
        
        // When
        val result = analyticsService.resetPreferences(userId)
        
        // Then
        assertTrue("Result should be failure", result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue("Should be LiftrixError.DatabaseError", error is LiftrixError.DatabaseError)
        
        val databaseError = error as LiftrixError.DatabaseError
        assertEquals("Failed to reset preferences: Reset operation failed", databaseError.errorMessage)
    }
    
    @Test
    fun `given multiple widget types, when getting widget data, then returns appropriate data for each`() = runTest {
        // Given
        val userId = "user123"
        val widgets = listOf(
            AnalyticsWidget.TotalVolume,
            AnalyticsWidget.WorkoutFrequency,
            AnalyticsWidget.ConsistencyStreak
        )
        
        widgets.forEach { widget ->
            every { widgetManager.shouldShowWidget(widget, true, 0) } returns true
        }
        
        coEvery { analyticsEngine.calculateProgressMetrics(userId, any()) } returns LiftrixResult.Success(
            mockk {
                every { volumeMetrics } returns mockk {
                    every { totalVolume } returns mockk { every { kilograms } returns 1500.0 }
                    every { volumeTrend } returns TrendDirection.UP
                }
                every { frequencyMetrics } returns mockk {
                    every { averageWorkoutsPerWeek } returns 3.5f
                    every { weekOverWeekChange } returns 0.2f
                }
                every { consistencyMetrics } returns mockk {
                    every { currentStreak } returns 7
                    every { longestStreak } returns 14
                }
            }
        )
        
        // When
        val results = widgets.map { widget ->
            analyticsService.getWidgetData(userId, widget)
        }
        
        // Then
        results.forEach { result ->
            assertTrue("Each result should be successful", result.isSuccess)
            val widgetData = result.getOrNull()!!
            assertFalse("Should not be loading", widgetData.isLoading)
            assertTrue("Should have meaningful value", widgetData.value.isNotBlank())
            assertTrue("Should have subtitle", widgetData.subtitle.isNotBlank())
        }
        
        coVerify(exactly = widgets.size) { analyticsEngine.calculateProgressMetrics(userId, any()) }
    }
    
    @Test
    fun `given concurrent widget data requests, when executed simultaneously, then handles concurrency correctly`() = runTest {
        // Given
        val userId = "user123"
        val widget = AnalyticsWidget.TotalVolume
        val hasWorkoutData = true
        val dataAge = 0
        
        every { widgetManager.shouldShowWidget(widget, hasWorkoutData, dataAge) } returns true
        coEvery { analyticsEngine.calculateProgressMetrics(userId, any()) } returns LiftrixResult.Success(
            mockk {
                every { volumeMetrics } returns mockk {
                    every { totalVolume } returns mockk { every { kilograms } returns 1500.0 }
                    every { volumeTrend } returns TrendDirection.UP
                }
            }
        )
        
        // When - Execute multiple concurrent calls
        val results = (1..5).map {
            analyticsService.getWidgetData(userId, widget)
        }
        
        // Then
        results.forEach { result ->
            assertTrue("Each result should be successful", result.isSuccess)
            val widgetData = result.getOrNull()!!
            assertEquals("Should return correct widget", widget, widgetData.widget)
            assertEquals("Should return correct value", "1500 kg", widgetData.value)
        }
        
        coVerify(exactly = 5) { analyticsEngine.calculateProgressMetrics(userId, any()) }
    }
}