package com.example.liftrix.ui.progress

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardConfiguration
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import com.example.liftrix.service.WidgetResolver
import com.example.liftrix.ui.progress.components.AnalyticsWidgetManager
import com.example.liftrix.ui.progress.components.DashboardLayoutMode as ComponentLayoutMode
import kotlinx.datetime.Clock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive integration test for the complete widget system.
 * 
 * Tests all critical integration points and validates end-to-end functionality
 * across the widget resolution, layout mode routing, data flow, and migration systems.
 * 
 * This test ensures that all the individual fixes work together seamlessly:
 * - Core Widget Resolution System fixes
 * - Layout Mode Routing fixes  
 * - Widget Data Flow fixes
 * - Widget Migration fixes
 */
class WidgetSystemIntegrationTest {
    
    private lateinit var widgetResolver: WidgetResolver
    private lateinit var widgetManager: AnalyticsWidgetManager
    
    @Before
    fun setUp() {
        widgetResolver = WidgetResolver()
        widgetManager = AnalyticsWidgetManager(widgetResolver)
    }
    
    @Test
    fun `complete flow: Beginner user gets 4 widgets through AnalyticsWidgetManager`() {
        // Given
        val userLevel = UserLevel.BEGINNER
        val layoutMode = DashboardLayoutMode.SECTIONS
        
        // When
        val configuration = widgetManager.getConfigurationForLevel(userLevel, layoutMode)
        val widgets = widgetManager.getWidgetsForConfiguration(configuration, null, layoutMode)
        
        // Then
        assertEquals("Beginner configuration should have max 4 widgets", 4, configuration.maxWidgets)
        assertEquals("Should resolve exactly 4 widgets", 4, widgets.size)
        assertEquals("Configuration should be Beginner", DashboardConfiguration.Beginner, configuration)
    }
    
    @Test
    fun `complete flow: Intermediate user gets 7 widgets through AnalyticsWidgetManager`() {
        // Given
        val userLevel = UserLevel.INTERMEDIATE
        val layoutMode = DashboardLayoutMode.SECTIONS
        
        // When
        val configuration = widgetManager.getConfigurationForLevel(userLevel, layoutMode)
        val widgets = widgetManager.getWidgetsForConfiguration(configuration, null, layoutMode)
        
        // Then
        assertEquals("Intermediate configuration should have max 7 widgets", 7, configuration.maxWidgets)
        assertEquals("Should resolve exactly 7 widgets", 7, widgets.size)
        assertEquals("Configuration should be Intermediate", DashboardConfiguration.Intermediate, configuration)
    }
    
    @Test
    fun `complete flow: Advanced user gets 10 widgets through AnalyticsWidgetManager`() {
        // Given
        val userLevel = UserLevel.ADVANCED
        val layoutMode = DashboardLayoutMode.SECTIONS
        
        // When
        val configuration = widgetManager.getConfigurationForLevel(userLevel, layoutMode)
        val widgets = widgetManager.getWidgetsForConfiguration(configuration, null, layoutMode)
        
        // Then
        assertEquals("Advanced configuration should have max 10 widgets", 10, configuration.maxWidgets)
        assertEquals("Should resolve exactly 10 widgets", 10, widgets.size)
        assertEquals("Configuration should be Advanced", DashboardConfiguration.Advanced, configuration)
    }
    
    @Test
    fun `complete flow: CUSTOM layout mode respects user preferences`() {
        // Given
        val userLevel = UserLevel.INTERMEDIATE
        val layoutMode = DashboardLayoutMode.CUSTOM
        val customWidgets = setOf("workout_frequency", "total_volume", "calories_burned", "progress_chart", "volume_chart")
        val preferences = WidgetPreferences(
            userId = "test-user",
            visibleWidgets = customWidgets,
            widgetOrder = customWidgets.toList(),
            dashboardLayout = layoutMode,
            userLevel = userLevel,
            lastModified = Clock.System.now()
        )
        
        // When
        val configuration = widgetManager.getConfigurationForLevel(userLevel, layoutMode)
        val widgets = widgetManager.getWidgetsForConfiguration(configuration, preferences, layoutMode)
        
        // Then
        assertEquals("Configuration should be Custom", DashboardConfiguration.Custom, configuration)
        assertEquals("Should respect user preferences", customWidgets.size, widgets.size)
        assertTrue("Widgets should match user selection", 
            widgets.all { widget -> customWidgets.contains(widget.id) })
    }
    
    @Test
    fun `applyPreferencesToConfiguration uses WidgetResolver correctly`() {
        // Given
        val baseConfiguration = DashboardConfiguration.Intermediate
        val preferences = WidgetPreferences(
            userId = "test-user",
            visibleWidgets = setOf("workout_frequency", "total_volume", "calories_burned"),
            widgetOrder = listOf("workout_frequency", "total_volume", "calories_burned"),
            dashboardLayout = DashboardLayoutMode.SECTIONS,
            userLevel = UserLevel.INTERMEDIATE,
            lastModified = Clock.System.now()
        )
        
        // When
        val resolvedWidgets = widgetManager.applyPreferencesToConfiguration(baseConfiguration, preferences)
        
        // Then
        assertEquals("Should resolve widgets through WidgetResolver", 3, resolvedWidgets.size)
        assertTrue("Should contain user's selected widgets", 
            resolvedWidgets.all { widget -> preferences.visibleWidgets.contains(widget.id) })
    }
    
    @Test
    fun `createDefaultPreferences creates valid preferences through WidgetResolver`() {
        // Given
        val userId = "test-user"
        val configuration = DashboardConfiguration.Advanced
        
        // When
        val preferences = widgetManager.createDefaultPreferences(userId, configuration)
        
        // Then
        assertEquals("User ID should match", userId, preferences.userId)
        assertEquals("User level should match configuration", UserLevel.ADVANCED, preferences.userLevel)
        assertEquals("Should have 10 widgets for advanced", 10, preferences.visibleWidgets.size)
        assertTrue("Widget order should contain all visible widgets", 
            preferences.widgetOrder.containsAll(preferences.visibleWidgets))
    }
    
    @Test
    fun `DashboardConfiguration fromUserLevelAndLayout works correctly`() {
        // Test standard modes
        assertEquals(DashboardConfiguration.Beginner, 
            DashboardConfiguration.fromUserLevelAndLayout(UserLevel.BEGINNER, DashboardLayoutMode.SECTIONS))
        assertEquals(DashboardConfiguration.Intermediate, 
            DashboardConfiguration.fromUserLevelAndLayout(UserLevel.INTERMEDIATE, DashboardLayoutMode.GRID))
        assertEquals(DashboardConfiguration.Advanced, 
            DashboardConfiguration.fromUserLevelAndLayout(UserLevel.ADVANCED, DashboardLayoutMode.LIST))
        
        // Test CUSTOM mode
        assertEquals(DashboardConfiguration.Custom, 
            DashboardConfiguration.fromUserLevelAndLayout(UserLevel.BEGINNER, DashboardLayoutMode.CUSTOM))
        assertEquals(DashboardConfiguration.Custom, 
            DashboardConfiguration.fromUserLevelAndLayout(UserLevel.INTERMEDIATE, DashboardLayoutMode.CUSTOM))
        assertEquals(DashboardConfiguration.Custom, 
            DashboardConfiguration.fromUserLevelAndLayout(UserLevel.ADVANCED, DashboardLayoutMode.CUSTOM))
    }
    
    @Test
    fun `widget system maintains consistency across levels`() {
        // Given
        val allLevels = UserLevel.values()
        val layoutMode = DashboardLayoutMode.SECTIONS
        
        // When & Then
        allLevels.forEach { userLevel ->
            val configuration = widgetManager.getConfigurationForLevel(userLevel, layoutMode)
            val widgets = widgetManager.getWidgetsForConfiguration(configuration, null, layoutMode)
            val expectedCount = widgetResolver.getMaxWidgetCount(userLevel)
            
            assertEquals("Widget count should match level for $userLevel", 
                expectedCount, widgets.size)
            assertEquals("Configuration max should match resolved count for $userLevel", 
                configuration.maxWidgets, widgets.size)
            assertTrue("All widgets should be appropriate for $userLevel", 
                widgets.all { widgetResolver.isWidgetAppropriate(it, userLevel) })
        }
    }
    
    @Test
    fun `widget preferences validation works through WidgetManager`() {
        // Given
        val validPreferences = WidgetPreferences(
            userId = "test-user",
            visibleWidgets = setOf("workout_frequency", "total_volume"),
            widgetOrder = listOf("workout_frequency", "total_volume"),
            dashboardLayout = DashboardLayoutMode.SECTIONS,
            userLevel = UserLevel.BEGINNER,
            lastModified = Clock.System.now()
        )
        
        val invalidPreferences = WidgetPreferences(
            userId = "test-user",
            visibleWidgets = setOf("invalid_widget"),
            widgetOrder = listOf("invalid_widget"),
            dashboardLayout = DashboardLayoutMode.SECTIONS,
            userLevel = UserLevel.BEGINNER,
            lastModified = Clock.System.now()
        )
        
        // When
        val validValidation = widgetManager.validatePreferences(validPreferences)
        val invalidValidation = widgetManager.validatePreferences(invalidPreferences)
        
        // Then
        assertTrue("Valid preferences should pass validation", validValidation.isValid)
        assertFalse("Invalid preferences should fail validation", invalidValidation.isValid)
        assertTrue("Invalid validation should have issues", invalidValidation.issues.isNotEmpty())
    }
    
    /**
     * CRITICAL TEST: Validates widget counts for all user levels.
     * 
     * This is the primary bug that was fixed - ensuring each user level
     * gets the correct number of widgets instead of defaulting to 4.
     */
    @Test
    fun `test user level widget counts - core integration fix`() {
        // Test Beginner level - should get 4 widgets
        val beginnerWidgets = widgetResolver.resolveStandardWidgets(UserLevel.BEGINNER)
        assertEquals("Beginner users should get exactly 4 widgets", 4, beginnerWidgets.size)
        
        // Test Intermediate level - should get 7 widgets (was incorrectly 4 before fix)
        val intermediateWidgets = widgetResolver.resolveStandardWidgets(UserLevel.INTERMEDIATE)
        assertEquals("Intermediate users should get exactly 7 widgets", 7, intermediateWidgets.size)
        
        // Test Advanced level - should get 10 widgets (was incorrectly 4 before fix)
        val advancedWidgets = widgetResolver.resolveStandardWidgets(UserLevel.ADVANCED)
        assertEquals("Advanced users should get exactly 10 widgets", 10, advancedWidgets.size)
    }

    /**
     * CRITICAL TEST: Validates CUSTOM layout mode works with user preferences.
     * 
     * This tests the layout mode routing fix where CUSTOM mode was being ignored.
     */
    @Test
    fun `test CUSTOM layout mode integration - layout routing fix`() {
        val testUserId = "test-user-123"
        
        // Create preferences with specific widgets for CUSTOM mode
        val customPreferences = WidgetPreferences(
            userId = testUserId,
            visibleWidgets = setOf(
                AnalyticsWidget.WorkoutFrequency.id,
                AnalyticsWidget.TotalVolume.id,
                AnalyticsWidget.StrengthProgress.id,
                AnalyticsWidget.PersonalRecords.id,
                AnalyticsWidget.VolumeChart.id
            ),
            widgetOrder = listOf(
                AnalyticsWidget.WorkoutFrequency.id,
                AnalyticsWidget.TotalVolume.id,
                AnalyticsWidget.StrengthProgress.id,
                AnalyticsWidget.PersonalRecords.id,
                AnalyticsWidget.VolumeChart.id
            ),
            dashboardLayout = DashboardLayoutMode.CUSTOM,
            userLevel = UserLevel.INTERMEDIATE,
            collapsedSections = emptySet(),
            widgetSizes = emptyMap(),
            enableAutoRefresh = true,
            refreshIntervalMinutes = 5,
            lastModified = Clock.System.now()
        )
        
        // Test that CUSTOM mode uses user preferences instead of level defaults
        val customWidgets = widgetResolver.resolveWidgets(
            userLevel = UserLevel.INTERMEDIATE,
            layoutMode = DashboardLayoutMode.CUSTOM,
            preferences = customPreferences
        )
        
        assertEquals("CUSTOM mode should respect user widget selection", 5, customWidgets.size)
        assertTrue("CUSTOM mode should include user-selected widgets", 
            customWidgets.any { it.id == AnalyticsWidget.WorkoutFrequency.id })
        assertTrue("CUSTOM mode should include user-selected widgets", 
            customWidgets.any { it.id == AnalyticsWidget.VolumeChart.id })
        
        // Test fallback behavior when no preferences provided
        val fallbackWidgets = widgetResolver.resolveWidgets(
            userLevel = UserLevel.INTERMEDIATE,
            layoutMode = DashboardLayoutMode.CUSTOM,
            preferences = null
        )
        
        assertEquals("CUSTOM mode should fallback to standard widgets when no preferences", 
            7, fallbackWidgets.size)
    }

    /**
     * CRITICAL TEST: Widget name migration from legacy formats.
     * 
     * This tests the migration fix that handles legacy camelCase and incorrect widget names.
     */
    @Test
    fun `test widget name migration - migration system fix`() {
        val testUserId = "test-user-migration"
        
        // Create preferences with legacy widget names (the format that was causing failures)
        val legacyPreferences = WidgetPreferences(
            userId = testUserId,
            visibleWidgets = setOf(
                // Legacy camelCase names that were causing resolution failures
                "workoutFrequency",  // Should map to AnalyticsWidget.WorkoutFrequency.id
                "totalVolume",       // Should map to AnalyticsWidget.TotalVolume.id
                "WorkoutFrequency",  // Should map to AnalyticsWidget.WorkoutFrequency.id (PascalCase)
                "TotalVolume",       // Should map to AnalyticsWidget.TotalVolume.id (PascalCase)
                "Workout Frequency", // Should map to AnalyticsWidget.WorkoutFrequency.id (display name)
                "progressChart"      // Should map to AnalyticsWidget.ProgressChart.id
            ),
            widgetOrder = emptyList(),
            dashboardLayout = DashboardLayoutMode.CUSTOM,
            userLevel = UserLevel.ADVANCED,
            collapsedSections = emptySet(),
            widgetSizes = emptyMap(),
            enableAutoRefresh = true,
            refreshIntervalMinutes = 5,
            lastModified = Clock.System.now()
        )
        
        // Test that migration correctly resolves legacy widget names
        val migratedWidgets = widgetResolver.resolveWidgetsFromPreferences(
            preferences = legacyPreferences,
            userLevel = UserLevel.ADVANCED
        )
        
        assertTrue("Migration should resolve legacy widget names", migratedWidgets.isNotEmpty())
        assertTrue("Migration should include WorkoutFrequency", 
            migratedWidgets.any { it.id == AnalyticsWidget.WorkoutFrequency.id })
        assertTrue("Migration should include TotalVolume", 
            migratedWidgets.any { it.id == AnalyticsWidget.TotalVolume.id })
        assertTrue("Migration should include ProgressChart", 
            migratedWidgets.any { it.id == AnalyticsWidget.ProgressChart.id })
        
        // Test that migration preserves valid widget IDs
        val validPreferences = WidgetPreferences(
            userId = testUserId,
            visibleWidgets = setOf(
                AnalyticsWidget.WorkoutFrequency.id,
                AnalyticsWidget.TotalVolume.id
            ),
            widgetOrder = emptyList(),
            dashboardLayout = DashboardLayoutMode.CUSTOM,
            userLevel = UserLevel.BEGINNER,
            collapsedSections = emptySet(),
            widgetSizes = emptyMap(),
            enableAutoRefresh = true,
            refreshIntervalMinutes = 5,
            lastModified = Clock.System.now()
        )
        
        val validWidgets = widgetResolver.resolveWidgetsFromPreferences(
            preferences = validPreferences,
            userLevel = UserLevel.BEGINNER
        )
        
        assertEquals("Valid widget IDs should be preserved", 2, validWidgets.size)
    }

    /**
     * FINAL INTEGRATION TEST: Complete end-to-end scenario.
     * 
     * This test simulates a complete user journey through the widget system
     * and validates that all components work together seamlessly.
     */
    @Test
    fun `test complete end-to-end widget system integration`() {
        val testUserId = "test-user-e2e"
        
        // 1. User starts as beginner with default preferences
        val beginnerDefaults = widgetResolver.createDefaultPreferences(
            userId = testUserId,
            userLevel = UserLevel.BEGINNER,
            layoutMode = DashboardLayoutMode.SECTIONS
        )
        
        val beginnerWidgets = widgetResolver.resolveWidgets(
            userLevel = UserLevel.BEGINNER,
            layoutMode = DashboardLayoutMode.SECTIONS,
            preferences = beginnerDefaults
        )
        
        assertEquals("E2E: Beginner should start with 4 widgets", 4, beginnerWidgets.size)
        
        // 2. User upgrades to intermediate and switches to custom layout
        val intermediateCustom = beginnerDefaults.copy(
            userLevel = UserLevel.INTERMEDIATE,
            dashboardLayout = DashboardLayoutMode.CUSTOM,
            visibleWidgets = setOf(
                AnalyticsWidget.WorkoutFrequency.id,
                AnalyticsWidget.TotalVolume.id,
                AnalyticsWidget.StrengthProgress.id,
                AnalyticsWidget.PersonalRecords.id,
                AnalyticsWidget.VolumeChart.id,
                AnalyticsWidget.DurationChart.id,
                AnalyticsWidget.ProgressChart.id
            )
        )
        
        val intermediateWidgets = widgetResolver.resolveWidgets(
            userLevel = UserLevel.INTERMEDIATE,
            layoutMode = DashboardLayoutMode.CUSTOM,
            preferences = intermediateCustom
        )
        
        assertEquals("E2E: Intermediate custom should have 7 widgets", 7, intermediateWidgets.size)
        
        // 3. User migrates legacy preferences with old widget names
        val legacyMigration = intermediateCustom.copy(
            visibleWidgets = setOf(
                "workoutFrequency",  // Legacy name
                "TotalVolume",       // PascalCase legacy name
                "Strength Progress", // Display name
                AnalyticsWidget.PersonalRecords.id, // Valid current name
                "volumeChart",       // Legacy camelCase
                "durationChart",     // Legacy camelCase
                "invalid_widget"     // Should be filtered out
            )
        )
        
        val migratedPrefs = widgetResolver.migratePreferences(legacyMigration)
        val migratedWidgets = widgetResolver.resolveWidgetsFromPreferences(
            preferences = migratedPrefs,
            userLevel = UserLevel.INTERMEDIATE
        )
        
        assertTrue("E2E: Migration should preserve valid widgets", migratedWidgets.size >= 5)
        assertTrue("E2E: Migration should filter out invalid widgets", migratedWidgets.size <= 7)
        
        // 4. User switches between different layout modes
        val gridModeWidgets = widgetResolver.resolveWidgets(
            userLevel = UserLevel.INTERMEDIATE,
            layoutMode = DashboardLayoutMode.GRID,
            preferences = migratedPrefs
        )
        
        val sectionModeWidgets = widgetResolver.resolveWidgets(
            userLevel = UserLevel.INTERMEDIATE,
            layoutMode = DashboardLayoutMode.SECTIONS,
            preferences = migratedPrefs
        )
        
        // Non-custom modes should use standard resolution
        assertEquals("E2E: GRID mode should use standard resolution", 7, gridModeWidgets.size)
        assertEquals("E2E: SECTIONS mode should use standard resolution", 7, sectionModeWidgets.size)
        
        // 5. Final validation - all critical integration points work
        assertTrue("E2E: Widget migration works", migratedWidgets.isNotEmpty())
        assertTrue("E2E: Layout mode routing works", gridModeWidgets.size == sectionModeWidgets.size)
        assertTrue("E2E: User level progression works", 
            beginnerWidgets.size < intermediateWidgets.size)
        assertTrue("E2E: Custom layout mode works", 
            intermediateWidgets.any { it.id == AnalyticsWidget.WorkoutFrequency.id })
        
        println("✅ Complete widget system integration test PASSED!")
        println("   - Widget Resolution: ✅")
        println("   - Layout Mode Routing: ✅") 
        println("   - Data Flow: ✅")
        println("   - Migration System: ✅")
        println("   - End-to-End Flow: ✅")
    }

    @Test
    fun `end-to-end scenario: user level progression`() {
        val userId = "test-user"
        
        // Scenario: User starts as beginner, progresses to intermediate, then advanced
        
        // Phase 1: Beginner
        val beginnerConfig = DashboardConfiguration.Beginner
        val beginnerPrefs = widgetManager.createDefaultPreferences(userId, beginnerConfig)
        val beginnerWidgets = widgetManager.getWidgetsForConfiguration(beginnerConfig)
        
        assertEquals("Beginner should get 4 widgets", 4, beginnerWidgets.size)
        assertEquals("Preferences should match", 4, beginnerPrefs.visibleWidgets.size)
        
        // Phase 2: Upgrade to Intermediate
        val intermediateConfig = DashboardConfiguration.Intermediate
        val upgradedPrefs = widgetManager.applyConfigurationToPreferences(beginnerPrefs, intermediateConfig)
        val intermediateWidgets = widgetManager.applyPreferencesToConfiguration(intermediateConfig, upgradedPrefs)
        
        assertEquals("Intermediate should get 7 widgets", 7, intermediateWidgets.size)
        assertEquals("Upgraded preferences should have 7 widgets", 7, upgradedPrefs.visibleWidgets.size)
        assertEquals("User level should be updated", UserLevel.INTERMEDIATE, upgradedPrefs.userLevel)
        
        // Phase 3: Upgrade to Advanced
        val advancedConfig = DashboardConfiguration.Advanced
        val finalPrefs = widgetManager.applyConfigurationToPreferences(upgradedPrefs, advancedConfig)
        val advancedWidgets = widgetManager.applyPreferencesToConfiguration(advancedConfig, finalPrefs)
        
        assertEquals("Advanced should get 10 widgets", 10, advancedWidgets.size)
        assertEquals("Final preferences should have 10 widgets", 10, finalPrefs.visibleWidgets.size)
        assertEquals("User level should be advanced", UserLevel.ADVANCED, finalPrefs.userLevel)
    }
}