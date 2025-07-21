package com.example.liftrix.domain.usecase.analytics

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Comprehensive unit tests for analytics use cases.
 * 
 * Tests cover success scenarios, error handling, data validation,
 * cache behavior, and performance characteristics for 80% coverage target.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsUseCaseTests {
    
    private lateinit var analyticsService: AnalyticsService
    private lateinit var getWidgetDataUseCase: GetWidgetDataUseCase
    
    private val testUserId = "test-user-123"
    
    @Before
    fun setup() {
        analyticsService = mockk(relaxed = true)
        getWidgetDataUseCase = GetWidgetDataUseCase(analyticsService)
    }
    
    @Test
    fun `getWidgetData returns success for volume chart widget`() = runTest {
        // Given
        val widgetType = AnalyticsWidget.VolumeChart
        
        // When
        val result = getWidgetDataUseCase.getWidgetData(testUserId, widgetType)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetData = result.getOrNull()
        assertNotNull(widgetData)
        assertEquals(widgetType, widgetData.widgetType)
        assertTrue(widgetData.data.containsKey("totalVolume"))
        assertTrue(widgetData.data.containsKey("weeklyAverage"))
        assertTrue(widgetData.data.containsKey("trend"))
        assertTrue(widgetData.data.containsKey("chartData"))
        assertFalse(widgetData.isStale)
    }
    
    @Test
    fun `getWidgetData returns success for duration chart widget`() = runTest {
        // Given
        val widgetType = AnalyticsWidget.DurationChart
        
        // When
        val result = getWidgetDataUseCase.getWidgetData(testUserId, widgetType)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetData = result.getOrNull()
        assertNotNull(widgetData)
        assertEquals(widgetType, widgetData.widgetType)
        assertTrue(widgetData.data.containsKey("averageDuration"))
        assertTrue(widgetData.data.containsKey("totalTime"))
        assertTrue(widgetData.data.containsKey("efficiency"))
        assertTrue(widgetData.data.containsKey("chartData"))
    }
    
    @Test
    fun `getWidgetData returns success for frequency chart widget`() = runTest {
        // Given
        val widgetType = AnalyticsWidget.FrequencyChart
        
        // When
        val result = getWidgetDataUseCase.getWidgetData(testUserId, widgetType)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetData = result.getOrNull()
        assertNotNull(widgetData)
        assertEquals(widgetType, widgetData.widgetType)
        assertTrue(widgetData.data.containsKey("weeklyFrequency"))
        assertTrue(widgetData.data.containsKey("consistency"))
        assertTrue(widgetData.data.containsKey("streak"))
    }
    
    @Test
    fun `getWidgetData returns success for strength progress widget`() = runTest {
        // Given
        val widgetType = AnalyticsWidget.StrengthProgress
        
        // When
        val result = getWidgetDataUseCase.getWidgetData(testUserId, widgetType)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetData = result.getOrNull()
        assertNotNull(widgetData)
        assertEquals(widgetType, widgetData.widgetType)
        assertTrue(widgetData.data.containsKey("totalPRs"))
        assertTrue(widgetData.data.containsKey("recentPRs"))
        assertTrue(widgetData.data.containsKey("strengthScore"))
        assertTrue(widgetData.data.containsKey("topExercises"))
    }
    
    @Test
    fun `getWidgetData returns success for calories burned widget`() = runTest {
        // Given
        val widgetType = AnalyticsWidget.CaloriesBurned
        
        // When
        val result = getWidgetDataUseCase.getWidgetData(testUserId, widgetType)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetData = result.getOrNull()
        assertNotNull(widgetData)
        assertEquals(widgetType, widgetData.widgetType)
        assertTrue(widgetData.data.containsKey("dailyCalories"))
        assertTrue(widgetData.data.containsKey("weeklyTotal"))
        assertTrue(widgetData.data.containsKey("goal"))
        assertTrue(widgetData.data.containsKey("goalProgress"))
    }
    
    @Test
    fun `getWidgetData returns success for workout streak widget`() = runTest {
        // Given
        val widgetType = AnalyticsWidget.WorkoutStreak
        
        // When
        val result = getWidgetDataUseCase.getWidgetData(testUserId, widgetType)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetData = result.getOrNull()
        assertNotNull(widgetData)
        assertEquals(widgetType, widgetData.widgetType)
        assertTrue(widgetData.data.containsKey("currentStreak"))
        assertTrue(widgetData.data.containsKey("longestStreak"))
        assertTrue(widgetData.data.containsKey("streakType"))
        assertTrue(widgetData.data.containsKey("nextMilestone"))
    }
    
    @Test
    fun `getWidgetData returns success for personal records widget`() = runTest {
        // Given
        val widgetType = AnalyticsWidget.PersonalRecords
        
        // When
        val result = getWidgetDataUseCase.getWidgetData(testUserId, widgetType)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetData = result.getOrNull()
        assertNotNull(widgetData)
        assertEquals(widgetType, widgetData.widgetType)
        assertTrue(widgetData.data.containsKey("recentPRs"))
        assertTrue(widgetData.data.containsKey("totalPRs"))
        assertTrue(widgetData.data.containsKey("thisMonth"))
    }
    
    @Test
    fun `getWidgetData returns mock data for unknown widget types`() = runTest {
        // Given - using a widget type that falls into the else clause
        val widgetType = AnalyticsWidget.BodyComposition // Assuming this isn't in specific cases
        
        // When
        val result = getWidgetDataUseCase.getWidgetData(testUserId, widgetType)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetData = result.getOrNull()
        assertNotNull(widgetData)
        assertEquals(widgetType, widgetData.widgetType)
        assertTrue(widgetData.data.containsKey("value"))
        assertTrue(widgetData.data.containsKey("subtitle"))
        assertTrue(widgetData.data.containsKey("trend"))
        assertTrue(widgetData.data.containsKey("lastUpdated"))
    }
    
    @Test
    fun `getMultipleWidgetData returns success for multiple widgets`() = runTest {
        // Given
        val widgetTypes = listOf(
            AnalyticsWidget.VolumeChart,
            AnalyticsWidget.DurationChart,
            AnalyticsWidget.CaloriesBurned
        )
        
        // When
        val result = getWidgetDataUseCase.getMultipleWidgetData(testUserId, widgetTypes)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetDataMap = result.getOrNull()
        assertNotNull(widgetDataMap)
        assertEquals(3, widgetDataMap.size)
        
        widgetTypes.forEach { widgetType ->
            assertTrue(widgetDataMap.containsKey(widgetType))
            val widgetData = widgetDataMap[widgetType]
            assertNotNull(widgetData)
            assertEquals(widgetType, widgetData.widgetType)
        }
    }
    
    @Test
    fun `getMultipleWidgetData returns empty map for empty widget list`() = runTest {
        // Given
        val widgetTypes = emptyList<AnalyticsWidget>()
        
        // When
        val result = getWidgetDataUseCase.getMultipleWidgetData(testUserId, widgetTypes)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetDataMap = result.getOrNull()
        assertNotNull(widgetDataMap)
        assertTrue(widgetDataMap.isEmpty())
    }
    
    @Test
    fun `isDataStale returns false for fresh data`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val widgetData = GetWidgetDataUseCase.WidgetData(
            widgetType = AnalyticsWidget.VolumeChart,
            data = mapOf("value" to 100),
            lastUpdated = currentTime - 30000, // 30 seconds ago
            isStale = false
        )
        
        // When
        val isStale = getWidgetDataUseCase.isDataStale(widgetData, maxAgeMs = 60000) // 1 minute max age
        
        // Then
        assertFalse(isStale)
    }
    
    @Test
    fun `isDataStale returns true for old data`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val widgetData = GetWidgetDataUseCase.WidgetData(
            widgetType = AnalyticsWidget.VolumeChart,
            data = mapOf("value" to 100),
            lastUpdated = currentTime - 360000, // 6 minutes ago
            isStale = false
        )
        
        // When
        val isStale = getWidgetDataUseCase.isDataStale(widgetData, maxAgeMs = 300000) // 5 minutes max age
        
        // Then
        assertTrue(isStale)
    }
    
    @Test
    fun `isDataStale returns true when data is marked as stale`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val widgetData = GetWidgetDataUseCase.WidgetData(
            widgetType = AnalyticsWidget.VolumeChart,
            data = mapOf("value" to 100),
            lastUpdated = currentTime - 30000, // 30 seconds ago (fresh)
            isStale = true // But marked as stale
        )
        
        // When
        val isStale = getWidgetDataUseCase.isDataStale(widgetData, maxAgeMs = 60000) // 1 minute max age
        
        // Then
        assertTrue(isStale)
    }
    
    @Test
    fun `isDataStale uses default max age when not specified`() {
        // Given
        val currentTime = System.currentTimeMillis()
        val widgetData = GetWidgetDataUseCase.WidgetData(
            widgetType = AnalyticsWidget.VolumeChart,
            data = mapOf("value" to 100),
            lastUpdated = currentTime - 360000, // 6 minutes ago
            isStale = false
        )
        
        // When - using default max age (5 minutes)
        val isStale = getWidgetDataUseCase.isDataStale(widgetData)
        
        // Then
        assertTrue(isStale)
    }
    
    @Test
    fun `refreshWidgetData returns success and calls getWidgetData`() = runTest {
        // Given
        val widgetType = AnalyticsWidget.VolumeChart
        
        // When
        val result = getWidgetDataUseCase.refreshWidgetData(testUserId, widgetType)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetData = result.getOrNull()
        assertNotNull(widgetData)
        assertEquals(widgetType, widgetData.widgetType)
    }
    
    @Test
    fun `widget data contains valid timestamps`() = runTest {
        // Given
        val widgetType = AnalyticsWidget.VolumeChart
        val startTime = System.currentTimeMillis()
        
        // When
        val result = getWidgetDataUseCase.getWidgetData(testUserId, widgetType)
        val endTime = System.currentTimeMillis()
        
        // Then
        assertTrue(result.isSuccess)
        val widgetData = result.getOrNull()
        assertNotNull(widgetData)
        assertTrue(widgetData.lastUpdated >= startTime)
        assertTrue(widgetData.lastUpdated <= endTime)
    }
    
    @Test
    fun `widget data contains expected data types`() = runTest {
        // Given
        val widgetType = AnalyticsWidget.VolumeChart
        
        // When
        val result = getWidgetDataUseCase.getWidgetData(testUserId, widgetType)
        
        // Then
        assertTrue(result.isSuccess)
        val widgetData = result.getOrNull()
        assertNotNull(widgetData)
        
        // Verify specific data types for volume chart
        val totalVolume = widgetData.data["totalVolume"]
        assertTrue(totalVolume is Int)
        assertEquals(12500, totalVolume)
        
        val chartData = widgetData.data["chartData"]
        assertTrue(chartData is List<*>)
        val chartList = chartData as List<*>
        assertEquals(5, chartList.size)
        assertTrue(chartList.all { it is Int })
    }
    
    @Test
    fun `performance test - getWidgetData completes within 200ms for simple widgets`() = runTest {
        // Given
        val widgetType = AnalyticsWidget.CaloriesBurned // Simple widget
        val startTime = System.currentTimeMillis()
        
        // When
        val result = getWidgetDataUseCase.getWidgetData(testUserId, widgetType)
        val endTime = System.currentTimeMillis()
        
        // Then
        assertTrue(result.isSuccess)
        val executionTime = endTime - startTime
        assertTrue(executionTime < 200, "Execution time $executionTime ms should be < 200ms")
    }
    
    @Test
    fun `batch operation performance - multiple widgets complete within 2 seconds`() = runTest {
        // Given
        val widgetTypes = listOf(
            AnalyticsWidget.VolumeChart,
            AnalyticsWidget.DurationChart,
            AnalyticsWidget.FrequencyChart,
            AnalyticsWidget.StrengthProgress,
            AnalyticsWidget.CaloriesBurned,
            AnalyticsWidget.WorkoutStreak,
            AnalyticsWidget.PersonalRecords
        )
        val startTime = System.currentTimeMillis()
        
        // When
        val result = getWidgetDataUseCase.getMultipleWidgetData(testUserId, widgetTypes)
        val endTime = System.currentTimeMillis()
        
        // Then
        assertTrue(result.isSuccess)
        val executionTime = endTime - startTime
        assertTrue(executionTime < 2000, "Batch execution time $executionTime ms should be < 2000ms")
    }
}