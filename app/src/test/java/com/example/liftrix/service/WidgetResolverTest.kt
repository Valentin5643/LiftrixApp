package com.example.liftrix.service

import com.example.liftrix.domain.model.analytics.DashboardLayoutMode
import com.example.liftrix.domain.model.analytics.UserLevel
import com.example.liftrix.domain.model.analytics.WidgetPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for WidgetResolver to verify dynamic widget resolution
 * based on user level and layout mode.
 * 
 * These tests ensure that the widget system correctly delivers:
 * - Beginner: 4 widgets
 * - Intermediate: 7 widgets  
 * - Advanced: 10 widgets
 * - CUSTOM layout mode: User preferences
 */
class WidgetResolverTest {
    
    private lateinit var widgetResolver: WidgetResolver
    
    @Before
    fun setUp() {
        widgetResolver = WidgetResolver()
    }
    
    @Test
    fun `resolveWidgets returns 4 widgets for BEGINNER level`() {
        // Given
        val userLevel = UserLevel.BEGINNER
        val layoutMode = DashboardLayoutMode.SECTIONS
        
        // When
        val widgets = widgetResolver.resolveWidgets(userLevel, layoutMode)
        
        // Then
        assertEquals("Beginner should get exactly 4 widgets", 4, widgets.size)
        assertTrue("All widgets should be appropriate for beginners", 
            widgets.all { widgetResolver.isWidgetAppropriate(it, userLevel) })
    }
    
    @Test
    fun `resolveWidgets returns 7 widgets for INTERMEDIATE level`() {
        // Given
        val userLevel = UserLevel.INTERMEDIATE
        val layoutMode = DashboardLayoutMode.SECTIONS
        
        // When
        val widgets = widgetResolver.resolveWidgets(userLevel, layoutMode)
        
        // Then
        assertEquals("Intermediate should get exactly 7 widgets", 7, widgets.size)
        assertTrue("All widgets should be appropriate for intermediate users", 
            widgets.all { widgetResolver.isWidgetAppropriate(it, userLevel) })
    }
    
    @Test
    fun `resolveWidgets returns 10 widgets for ADVANCED level`() {
        // Given
        val userLevel = UserLevel.ADVANCED
        val layoutMode = DashboardLayoutMode.SECTIONS
        
        // When
        val widgets = widgetResolver.resolveWidgets(userLevel, layoutMode)
        
        // Then
        assertEquals("Advanced should get exactly 10 widgets", 10, widgets.size)
        assertTrue("All widgets should be appropriate for advanced users", 
            widgets.all { widgetResolver.isWidgetAppropriate(it, userLevel) })
    }
    
    @Test
    fun `resolveWidgets respects CUSTOM layout mode with preferences`() {
        // Given
        val userLevel = UserLevel.INTERMEDIATE
        val layoutMode = DashboardLayoutMode.CUSTOM
        val customWidgets = setOf("workout_frequency", "total_volume", "calories_burned")
        val preferences = WidgetPreferences(
            userId = "test-user",
            visibleWidgets = customWidgets,
            widgetOrder = customWidgets.toList(),
            dashboardLayout = layoutMode,
            userLevel = userLevel
        )
        
        // When
        val widgets = widgetResolver.resolveWidgets(userLevel, layoutMode, preferences)
        
        // Then
        assertEquals("Custom mode should respect user preferences", customWidgets.size, widgets.size)
        assertTrue("Widgets should match user selection", 
            widgets.all { widget -> customWidgets.contains(widget.id) })
    }
    
    @Test
    fun `resolveWidgets falls back to standard for CUSTOM mode without preferences`() {
        // Given
        val userLevel = UserLevel.INTERMEDIATE
        val layoutMode = DashboardLayoutMode.CUSTOM
        
        // When
        val widgets = widgetResolver.resolveWidgets(userLevel, layoutMode, null)
        
        // Then
        assertEquals("Should fall back to standard intermediate widgets", 7, widgets.size)
    }
    
    @Test
    fun `getMaxWidgetCount returns correct limits for each level`() {
        assertEquals(4, widgetResolver.getMaxWidgetCount(UserLevel.BEGINNER))
        assertEquals(7, widgetResolver.getMaxWidgetCount(UserLevel.INTERMEDIATE))
        assertEquals(10, widgetResolver.getMaxWidgetCount(UserLevel.ADVANCED))
    }
    
    @Test
    fun `createDefaultPreferences creates valid preferences for each level`() {
        val userId = "test-user"
        
        // Test each user level
        UserLevel.values().forEach { userLevel ->
            val preferences = widgetResolver.createDefaultPreferences(userId, userLevel)
            
            assertEquals("User ID should match", userId, preferences.userId)
            assertEquals("User level should match", userLevel, preferences.userLevel)
            assertTrue("Should have visible widgets", preferences.visibleWidgets.isNotEmpty())
            assertTrue("Widget order should match visible widgets", 
                preferences.widgetOrder.containsAll(preferences.visibleWidgets))
            
            val expectedCount = widgetResolver.getMaxWidgetCount(userLevel)
            assertEquals("Widget count should match level expectations", 
                expectedCount, preferences.visibleWidgets.size)
        }
    }
    
    @Test
    fun `migratePreferences removes invalid widgets`() {
        // Given
        val invalidPreferences = WidgetPreferences(
            userId = "test-user",
            visibleWidgets = setOf("invalid_widget", "workout_frequency", "another_invalid"),
            widgetOrder = listOf("invalid_widget", "workout_frequency", "another_invalid"),
            userLevel = UserLevel.INTERMEDIATE
        )
        
        // When
        val migratedPreferences = widgetResolver.migratePreferences(invalidPreferences)
        
        // Then
        assertTrue("Should remove invalid widgets", 
            migratedPreferences.visibleWidgets.none { it.contains("invalid") })
        assertTrue("Should preserve valid widgets", 
            migratedPreferences.visibleWidgets.contains("workout_frequency"))
        assertEquals("Should preserve user level", 
            UserLevel.INTERMEDIATE, migratedPreferences.userLevel)
    }
    
    @Test
    fun `migratePreferences falls back to defaults when no valid widgets remain`() {
        // Given
        val invalidPreferences = WidgetPreferences(
            userId = "test-user",
            visibleWidgets = setOf("invalid_widget_1", "invalid_widget_2"),
            widgetOrder = listOf("invalid_widget_1", "invalid_widget_2"),
            userLevel = UserLevel.BEGINNER
        )
        
        // When
        val migratedPreferences = widgetResolver.migratePreferences(invalidPreferences)
        
        // Then
        assertEquals("Should have default widget count for beginner", 
            4, migratedPreferences.visibleWidgets.size)
        assertTrue("Should have valid widgets", 
            migratedPreferences.visibleWidgets.all { widgetId ->
                widgetResolver.getAvailableWidgets(UserLevel.BEGINNER).any { it.id == widgetId }
            })
    }
    
    @Test
    fun `widget progression ensures more complex widgets for higher levels`() {
        // Given
        val beginnerWidgets = widgetResolver.resolveWidgets(UserLevel.BEGINNER)
        val intermediateWidgets = widgetResolver.resolveWidgets(UserLevel.INTERMEDIATE)
        val advancedWidgets = widgetResolver.resolveWidgets(UserLevel.ADVANCED)
        
        // Then
        assertTrue("Intermediate should have more widgets than beginner", 
            intermediateWidgets.size > beginnerWidgets.size)
        assertTrue("Advanced should have more widgets than intermediate", 
            advancedWidgets.size > intermediateWidgets.size)
        
        // Check that beginner widgets are mostly included in intermediate
        val beginnerIds = beginnerWidgets.map { it.id }.toSet()
        val intermediateIds = intermediateWidgets.map { it.id }.toSet()
        assertTrue("Most beginner widgets should be in intermediate", 
            beginnerIds.intersect(intermediateIds).size >= beginnerIds.size * 0.5)
    }
}