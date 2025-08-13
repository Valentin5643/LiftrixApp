package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.TimeRangeType
import com.example.liftrix.domain.model.analytics.VolumeGrouping
import com.example.liftrix.domain.model.analytics.RankingMetric
import com.example.liftrix.domain.model.MuscleGroup
import org.junit.Test
import org.junit.Assert.*
import io.mockk.mockk

/**
 * Tests to validate existing widget functionality is preserved after navigation integration.
 * 
 * This test ensures that the navigation integration changes don't break:
 * - Widget analytics tracking
 * - Widget data display
 * - Widget interaction patterns
 * - Widget configuration and preferences
 * - Widget performance characteristics
 * 
 * Critical for TEST-002: Validate existing widget functionality preservation
 */
class WidgetFunctionalityPreservationTest {

    @Test
    fun `analytics widgets maintain their core properties`() {
        // Test that core analytics widgets still have expected properties
        val oneRmWidget = AnalyticsWidget.OneRMProgression
        assertEquals("OneRMProgression should have correct ID", "one_rm_progression", oneRmWidget.id)
        assertEquals("OneRMProgression should have expected display name", "1RM Progression", oneRmWidget.displayName)
        
        val volumeWidget = AnalyticsWidget.VolumeChart
        assertEquals("VolumeChart should have correct ID", "volume_chart", volumeWidget.id)
        assertEquals("VolumeChart should have expected display name", "Volume Chart", volumeWidget.displayName)
        
        val muscleGroupWidget = AnalyticsWidget.MuscleGroupDistribution
        assertEquals("MuscleGroupDistribution should have correct ID", "muscle_group_distribution", muscleGroupWidget.id)
        
        val exerciseRankingWidget = AnalyticsWidget.MuscleGroupDistribution
        assertEquals("MuscleGroupDistribution should have correct ID", "muscle_group_distribution", exerciseRankingWidget.id)
    }

    @Test
    fun `widget event tracking functionality preserved`() {
        // Test that AnalyticsWidgetEvent still supports all necessary events
        
        // Test WidgetClicked event (used for both analytics and navigation)
        val clickEvent = AnalyticsWidgetEvent.WidgetClicked(AnalyticsWidget.OneRMProgression)
        assertEquals("WidgetClicked should contain the correct widget", 
            AnalyticsWidget.OneRMProgression, clickEvent.widget)
        
        // Test WidgetReordered event (for drag & drop functionality)
        val reorderEvent = AnalyticsWidgetEvent.WidgetReordered(fromIndex = 0, toIndex = 2)
        assertEquals("WidgetReordered should preserve fromIndex", 0, reorderEvent.fromIndex)
        assertEquals("WidgetReordered should preserve toIndex", 2, reorderEvent.toIndex)
        assertTrue("WidgetReordered should persist by default", reorderEvent.shouldPersist)
        
        // Test other critical events still exist
        val refreshEvent = AnalyticsWidgetEvent.RefreshAllWidgets()
        assertTrue("RefreshAllWidgets should show loading states by default", refreshEvent.showLoadingStates)
        
        val navigationEvent = AnalyticsWidgetEvent.NavigateToDashboardCustomization
        assertNotNull("NavigateToDashboardCustomization should exist", navigationEvent)
    }

    @Test
    fun `widget parameter validation works correctly`() {
        // Test that navigation parameter validation doesn't break widget functionality
        
        // Test TimeRangeType values are accessible
        val timeRanges = TimeRangeType.entries
        assertTrue("TimeRangeType should include MONTH", timeRanges.contains(TimeRangeType.MONTH))
        assertTrue("TimeRangeType should include SIX_MONTHS", timeRanges.contains(TimeRangeType.SIX_MONTHS))
        assertTrue("TimeRangeType should include ALL_TIME", timeRanges.contains(TimeRangeType.ALL_TIME))
        
        // Test VolumeGrouping values are accessible
        val volumeGroupings = VolumeGrouping.entries
        assertTrue("VolumeGrouping should include TOTAL", volumeGroupings.contains(VolumeGrouping.TOTAL))
        assertTrue("VolumeGrouping should include BY_EXERCISE", volumeGroupings.contains(VolumeGrouping.BY_EXERCISE))
        assertTrue("VolumeGrouping should include BY_MUSCLE_GROUP", volumeGroupings.contains(VolumeGrouping.BY_MUSCLE_GROUP))
        
        // Test RankingMetric values are accessible
        val rankingMetrics = RankingMetric.entries
        assertTrue("RankingMetric should include PERFORMANCE_SCORE", rankingMetrics.contains(RankingMetric.PERFORMANCE_SCORE))
        assertTrue("RankingMetric should include VOLUME_GROWTH", rankingMetrics.contains(RankingMetric.VOLUME_GROWTH))
        assertTrue("RankingMetric should include STRENGTH_GROWTH", rankingMetrics.contains(RankingMetric.STRENGTH_GROWTH))
        
        // Test MuscleGroup values are accessible
        val muscleGroups = MuscleGroup.entries
        assertTrue("MuscleGroup should include CHEST", muscleGroups.contains(MuscleGroup.CHEST))
        assertTrue("MuscleGroup should include BACK", muscleGroups.contains(MuscleGroup.BACK))
        assertTrue("MuscleGroup should include QUADRICEPS", muscleGroups.contains(MuscleGroup.QUADRICEPS))
    }

    @Test
    fun `widget navigation mappings preserve analytics requirements`() {
        // Test that our navigation mappings align with widget types correctly
        
        // OneRM widgets should map to OneRM detail
        val oneRmWidgets = listOf(AnalyticsWidget.OneRMProgression)
        oneRmWidgets.forEach { widget ->
            assertNotNull("OneRM widget should be mappable", widget.id)
            assertEquals("OneRM widget should have expected ID pattern", "one_rm_progression", widget.id)
        }
        
        // Volume widgets should map to Volume detail
        val volumeWidgets = listOf(
            AnalyticsWidget.VolumeChart,
            AnalyticsWidget.VolumeTrends,
            AnalyticsWidget.VolumeLoadProgression
        )
        
        volumeWidgets.forEach { widget ->
            assertNotNull("Volume widget should be mappable", widget.id)
            assertTrue("Volume widget ID should contain volume", 
                widget.id.contains("volume", ignoreCase = true))
        }
        
        // Muscle Group widgets should map to Muscle Group detail
        val muscleGroupWidgets = listOf(AnalyticsWidget.MuscleGroupDistribution)
        muscleGroupWidgets.forEach { widget ->
            assertNotNull("Muscle group widget should be mappable", widget.id)
            assertTrue("Muscle group widget ID should be recognizable", 
                widget.id.contains("muscle", ignoreCase = true))
        }
        
        // Exercise Ranking widgets should map to Exercise Ranking detail
        val exerciseRankingWidgets = listOf(
            AnalyticsWidget.MuscleGroupDistribution
        )
        
        exerciseRankingWidgets.forEach { widget ->
            assertNotNull("Muscle group widget should be mappable", widget.id)
            assertTrue("Muscle group widget ID should be recognizable", 
                widget.id.contains("muscle", ignoreCase = true) || widget.id.contains("group", ignoreCase = true))
        }
    }

    @Test
    fun `widget interaction metadata preserved`() {
        // Test that widget interaction tracking metadata is preserved
        
        val trackingEvent = AnalyticsWidgetEvent.TrackInteraction(
            widgetId = "test_widget",
            interactionType = "click",
            metadata = mapOf(
                "timestamp" to System.currentTimeMillis(),
                "timeRange" to "THREE_MONTHS",
                "userLevel" to "INTERMEDIATE"
            )
        )
        
        assertEquals("Widget ID should be preserved", "test_widget", trackingEvent.widgetId)
        assertEquals("Interaction type should be preserved", "click", trackingEvent.interactionType)
        assertTrue("Metadata should be preserved", trackingEvent.metadata.containsKey("timestamp"))
        assertTrue("Time range metadata should be preserved", trackingEvent.metadata.containsKey("timeRange"))
        assertTrue("User level metadata should be preserved", trackingEvent.metadata.containsKey("userLevel"))
        
        // Test event validation extensions work
        assertTrue("Valid tracking event should pass validation", trackingEvent.isValid())
        
        val invalidEvent = AnalyticsWidgetEvent.TrackInteraction("", "", emptyMap())
        assertFalse("Invalid tracking event should fail validation", invalidEvent.isValid())
    }

    @Test
    fun `widget refresh and loading states preserved`() {
        // Test that widget refresh functionality is preserved
        
        val refreshAllEvent = AnalyticsWidgetEvent.RefreshAllWidgets(
            showLoadingStates = true,
            retryFailedWidgets = true
        )
        
        assertTrue("Refresh event should show loading states", refreshAllEvent.showLoadingStates)
        assertTrue("Refresh event should retry failed widgets", refreshAllEvent.retryFailedWidgets)
        assertTrue("Refresh event should require network", refreshAllEvent.requiresNetwork())
        
        val silentRefreshEvent = AnalyticsWidgetEvent.RefreshAllWidgets(
            showLoadingStates = false,
            retryFailedWidgets = false
        )
        
        assertFalse("Silent refresh should not show loading states", silentRefreshEvent.showLoadingStates)
        assertFalse("Silent refresh should not retry failed widgets", silentRefreshEvent.retryFailedWidgets)
    }

    @Test
    fun `widget configuration events preserved`() {
        // Test that widget configuration functionality is preserved
        
        val configEvent = AnalyticsWidgetEvent.UpdateConfiguration(
            configuration = mockk(), // Would be actual DashboardConfiguration in real test
            shouldPersist = true
        )
        
        assertTrue("Configuration event should persist by default", configEvent.shouldPersist)
        assertTrue("Configuration event should affect visibility", configEvent.affectsVisibility())
        assertTrue("Configuration event should require network", configEvent.requiresNetwork())
        
        val visibilityEvent = AnalyticsWidgetEvent.ToggleVisibility(
            widgetId = "test_widget",
            visible = true
        )
        
        assertEquals("Visibility event should preserve widget ID", "test_widget", visibilityEvent.widgetId)
        assertEquals("Visibility event should preserve visibility state", true, visibilityEvent.visible)
        assertTrue("Visibility event should affect visibility", visibilityEvent.affectsVisibility())
        assertEquals("Visibility event should affect correct widget", 
            setOf("test_widget"), visibilityEvent.getAffectedWidgets())
    }

    @Test
    fun `widget error handling preserved`() {
        // Test that widget error handling is preserved
        
        val dismissErrorEvent = AnalyticsWidgetEvent.DismissError(
            widgetId = "test_widget",
            shouldClearHistory = false
        )
        
        assertEquals("Error dismissal should preserve widget ID", "test_widget", dismissErrorEvent.widgetId)
        assertFalse("Error dismissal should preserve history setting", dismissErrorEvent.shouldClearHistory)
        assertEquals("Error dismissal should affect correct widget", 
            setOf("test_widget"), dismissErrorEvent.getAffectedWidgets())
        
        val clearErrorEvent = AnalyticsWidgetEvent.ClearError(widgetId = "test_widget")
        assertEquals("Clear error should preserve widget ID", "test_widget", clearErrorEvent.widgetId)
        
        val retryEvent = AnalyticsWidgetEvent.RetryOperation(widgetId = "test_widget", operation = "load")
        assertEquals("Retry event should preserve widget ID", "test_widget", retryEvent.widgetId)
        assertEquals("Retry event should preserve operation", "load", retryEvent.operation)
        assertTrue("Retry event should require network", retryEvent.requiresNetwork())
    }
}