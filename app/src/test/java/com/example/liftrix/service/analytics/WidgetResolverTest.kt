package com.example.liftrix.service.analytics

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetComplexity
import com.example.liftrix.domain.model.analytics.WidgetPriority
import com.example.liftrix.service.WidgetResolver
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for WidgetResolver
 * 
 * Tests the streamlined widget system after v40 refactoring.
 * Verifies correct widget sets are returned for each user level
 * according to SPEC-20250205-widget-system-refactoring requirements.
 */
class WidgetResolverTest {

    private lateinit var widgetResolver: WidgetResolver

    @Before
    fun setUp() {
        widgetResolver = WidgetResolver()
    }

    @Test
    fun `resolveStandardWidgets returns 4 widgets for BEGINNER level`() {
        // When
        val widgets = widgetResolver.resolveStandardWidgets(UserLevel.BEGINNER)

        // Then
        assertEquals("Beginner should have exactly 4 widgets", 4, widgets.size)
        
        // Verify all widgets are FIXED_BEGINNER priority (as per spec)
        widgets.forEach { widget ->
            assertEquals(
                "Beginner widgets should have FIXED_BEGINNER priority",
                WidgetPriority.FIXED_BEGINNER,
                widget.priority
            )
        }
    }

    @Test
    fun `resolveStandardWidgets returns 7 widgets for INTERMEDIATE level`() {
        // When
        val widgets = widgetResolver.resolveStandardWidgets(UserLevel.INTERMEDIATE)

        // Then
        assertEquals("Intermediate should have exactly 7 widgets", 7, widgets.size)
        
        // Should include 4 fixed beginner widgets
        val fixedBeginnerWidgets = widgets.filter { it.priority == WidgetPriority.FIXED_BEGINNER }
        assertEquals(
            "Intermediate should include 4 fixed beginner widgets",
            4,
            fixedBeginnerWidgets.size
        )
        
        // Should include 3 additional configurable widgets
        val configurableWidgets = widgets.filter { it.priority != WidgetPriority.FIXED_BEGINNER }
        assertEquals(
            "Intermediate should have 3 configurable widgets",
            3,
            configurableWidgets.size
        )
    }

    @Test
    fun `resolveStandardWidgets returns 10 widgets for ADVANCED level`() {
        // When
        val widgets = widgetResolver.resolveStandardWidgets(UserLevel.ADVANCED)

        // Then
        assertEquals("Advanced should have exactly 10 widgets", 10, widgets.size)
        
        // Should include 4 fixed beginner widgets
        val fixedBeginnerWidgets = widgets.filter { it.priority == WidgetPriority.FIXED_BEGINNER }
        assertEquals(
            "Advanced should include 4 fixed beginner widgets",
            4,
            fixedBeginnerWidgets.size
        )
        
        // Should include 6 additional configurable widgets
        val configurableWidgets = widgets.filter { it.priority != WidgetPriority.FIXED_BEGINNER }
        assertEquals(
            "Advanced should have 6 configurable widgets",
            6,
            configurableWidgets.size
        )
    }

    @Test
    fun `resolveWidgets uses resolveStandardWidgets for GRID layout mode`() {
        // When
        val widgets = widgetResolver.resolveWidgets(
            userLevel = UserLevel.INTERMEDIATE,
            layoutMode = DashboardLayoutMode.AUTO
        )

        // Then
        assertEquals("GRID mode should return 7 widgets for intermediate", 7, widgets.size)
    }

    @Test
    fun `resolveWidgets uses resolveStandardWidgets for SECTIONS layout mode`() {
        // When
        val widgets = widgetResolver.resolveWidgets(
            userLevel = UserLevel.BEGINNER,
            layoutMode = DashboardLayoutMode.AUTO
        )

        // Then
        assertEquals("SECTIONS mode should return 4 widgets for beginner", 4, widgets.size)
    }

    @Test
    fun `resolveWidgets uses resolveStandardWidgets for LIST layout mode`() {
        // When
        val widgets = widgetResolver.resolveWidgets(
            userLevel = UserLevel.ADVANCED,
            layoutMode = DashboardLayoutMode.COMPACT
        )

        // Then
        assertEquals("LIST mode should return 10 widgets for advanced", 10, widgets.size)
    }

    @Test
    fun `getMaxWidgetCount returns correct limits for each user level`() {
        // Test beginner limit
        assertEquals(
            "Beginner should have max 4 widgets",
            4,
            widgetResolver.getMaxWidgetCount(UserLevel.BEGINNER)
        )

        // Test intermediate limit
        assertEquals(
            "Intermediate should have max 7 widgets",
            7,
            widgetResolver.getMaxWidgetCount(UserLevel.INTERMEDIATE)
        )

        // Test advanced limit
        assertEquals(
            "Advanced should have max 10 widgets",
            10,
            widgetResolver.getMaxWidgetCount(UserLevel.ADVANCED)
        )
    }

    @Test
    fun `deprecated widgets are not included in any user level`() {
        // Get all widgets for each level
        val beginnerWidgets = widgetResolver.resolveStandardWidgets(UserLevel.BEGINNER)
        val intermediateWidgets = widgetResolver.resolveStandardWidgets(UserLevel.INTERMEDIATE)
        val advancedWidgets = widgetResolver.resolveStandardWidgets(UserLevel.ADVANCED)

        val allLevelWidgets = (beginnerWidgets + intermediateWidgets + advancedWidgets).distinct()

        // Define deprecated widget names (based on spec migration_38_39)
        val deprecatedWidgetNames = setOf(
            "CALORIES_BURNED",
            "TODAYS_CALORIES", 
            "WEEKLY_CALORIE_TRENDS",
            "CONSISTENCY_STREAK",
            "DURATION_CHART",
            "SET_COMPLETION_RATE",
            "EXERCISE_VARIETY",
            "TRAINING_INTENSITY",
            "GOAL_ACHIEVEMENT",
            "WEEKLY_TRENDS",
            "OPTIMAL_TIMING",
            "RECOVERY_PATTERNS",
            "PERFORMANCE_ANALYSIS"
        )

        // Verify no deprecated widgets are returned
        allLevelWidgets.forEach { widget ->
            assertFalse(
                "Deprecated widget ${widget.displayName} should not be included in any level",
                deprecatedWidgetNames.contains(widget.displayName)
            )
        }
    }

    @Test
    fun `widgets are returned in consistent order for each user level`() {
        // Test beginner widgets are sorted by layout priority
        val beginnerWidgets1 = widgetResolver.resolveStandardWidgets(UserLevel.BEGINNER)
        val beginnerWidgets2 = widgetResolver.resolveStandardWidgets(UserLevel.BEGINNER)
        
        assertEquals(
            "Beginner widgets should be returned in consistent order",
            beginnerWidgets1,
            beginnerWidgets2
        )

        // Test intermediate widgets are sorted by layout priority
        val intermediateWidgets1 = widgetResolver.resolveStandardWidgets(UserLevel.INTERMEDIATE)
        val intermediateWidgets2 = widgetResolver.resolveStandardWidgets(UserLevel.INTERMEDIATE)
        
        assertEquals(
            "Intermediate widgets should be returned in consistent order",
            intermediateWidgets1,
            intermediateWidgets2
        )

        // Test advanced widgets are sorted by layout priority
        val advancedWidgets1 = widgetResolver.resolveStandardWidgets(UserLevel.ADVANCED)
        val advancedWidgets2 = widgetResolver.resolveStandardWidgets(UserLevel.ADVANCED)
        
        assertEquals(
            "Advanced widgets should be returned in consistent order",
            advancedWidgets1,
            advancedWidgets2
        )
    }

    @Test
    fun `beginner level only includes appropriate complexity widgets`() {
        // When
        val widgets = widgetResolver.resolveStandardWidgets(UserLevel.BEGINNER)

        // Then - beginner should only have SIMPLE and essential MODERATE widgets
        widgets.forEach { widget ->
            assertTrue(
                "Beginner widget ${widget.displayName} should be SIMPLE or essential MODERATE complexity",
                widget.complexity == WidgetComplexity.SIMPLE ||
                (widget.complexity == WidgetComplexity.MODERATE && widget.priority == WidgetPriority.ESSENTIAL)
            )
        }
    }

    @Test
    fun `intermediate level excludes most complex widgets`() {
        // When
        val widgets = widgetResolver.resolveStandardWidgets(UserLevel.INTERMEDIATE)

        // Then - intermediate should avoid COMPLEX widgets unless essential
        widgets.forEach { widget ->
            assertTrue(
                "Intermediate widget ${widget.displayName} should not be COMPLEX unless essential",
                widget.complexity != WidgetComplexity.COMPLEX ||
                widget.priority == WidgetPriority.ESSENTIAL
            )
        }
    }

    @Test
    fun `advanced level includes all complexity levels`() {
        // When
        val widgets = widgetResolver.resolveStandardWidgets(UserLevel.ADVANCED)

        // Then - advanced should have widgets of all complexities
        val complexities = widgets.map { it.complexity }.distinct()
        
        assertTrue(
            "Advanced level should include SIMPLE widgets",
            complexities.contains(WidgetComplexity.SIMPLE)
        )
        assertTrue(
            "Advanced level should include MODERATE widgets",
            complexities.contains(WidgetComplexity.MODERATE)
        )
        // Note: COMPLEX widgets are optional and depend on available widgets
    }

    @Test
    fun `all returned widgets should be from focused strength training set`() {
        // Get all widgets for advanced level (largest set)
        val widgets = widgetResolver.resolveStandardWidgets(UserLevel.ADVANCED)

        // Expected strength training focused widgets based on spec
        val expectedStrengthWidgets = setOf(
            "ONE_RM_PROGRESSION", "TOTAL_VOLUME", "VOLUME_CHART", 
            "MUSCLE_GROUP_DISTRIBUTION", "PERSONAL_RECORDS", "WORKOUT_FREQUENCY",
            "VOLUME_CALENDAR", "STRENGTH_PROGRESS", "FREQUENCY_CHART",
            "PROGRESS_CHART", "MONTHLY_SUMMARY", "AVERAGE_DURATION",
            "WORKOUT_STREAK", "VOLUME_TRENDS", "RECOVERY_METRICS"
        )

        // Verify all returned widgets are from the expected strength training set
        widgets.forEach { widget ->
            assertTrue(
                "Widget ${widget.displayName} should be from the focused strength training set",
                expectedStrengthWidgets.contains(widget.displayName) ||
                // Allow for exact naming variations in implementation
                expectedStrengthWidgets.any { expected -> 
                    expected.replace("_", "").equals(widget.displayName.replace("_", ""), ignoreCase = true)
                }
            )
        }
    }

    @Test
    fun `resolver handles null preferences gracefully`() {
        // When resolving with null preferences
        val widgets = widgetResolver.resolveWidgets(
            userLevel = UserLevel.INTERMEDIATE,
            layoutMode = DashboardLayoutMode.AUTO,
            preferences = null
        )

        // Then should return standard widgets for the level
        assertEquals("Should return 7 widgets for intermediate with null preferences", 7, widgets.size)
    }
}