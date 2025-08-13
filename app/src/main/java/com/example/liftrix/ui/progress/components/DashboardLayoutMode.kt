package com.example.liftrix.ui.progress.components

import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.DashboardLayoutMode as DomainDashboardLayoutMode

/**
 * UI layer enum for dashboard layout modes in the Progress Dashboard.
 * 
 * This enum provides UI-specific presentation logic and adaptive layout selection
 * based on screen size and widget count. It maps to domain layer DashboardLayoutMode
 * while adding UI-specific optimization logic.
 */
enum class DashboardLayoutMode(
    val displayName: String,
    val description: String
) {
    /**
     * Default/automatic layout - determines optimal mode based on content and screen
     */
    DEFAULT(
        displayName = "Auto",
        description = "Automatically selects optimal layout"
    ),
    
    /**
     * Grid layout with responsive columns
     */
    GRID(
        displayName = "Grid",
        description = "Grid layout with responsive columns"
    ),
    
    /**
     * Sectioned layout with collapsible categories
     */
    SECTIONS(
        displayName = "Sections",
        description = "Organized sections with categories"
    ),
    
    /**
     * List layout for vertical scrolling
     */
    LIST(
        displayName = "List",
        description = "Vertical list layout"
    ),
    
    /**
     * Custom layout with drag-and-drop
     */
    CUSTOM(
        displayName = "Custom",
        description = "Fully customizable layout"
    );
    
    companion object {
        /**
         * Gets the optimal layout mode based on screen size and widget count.
         * 
         * @param screenWidthDp Screen width in dp
         * @param widgetCount Number of widgets to display
         * @param userPreference User's preferred layout mode (nullable)
         * @return Optimal DashboardLayoutMode
         */
        fun getOptimalMode(
            screenWidthDp: Int,
            widgetCount: Int,
            userPreference: DashboardLayoutMode? = null
        ): DashboardLayoutMode {
            // Honor user preference if explicitly set
            userPreference?.let { preference ->
                if (preference != DEFAULT) return preference
            }
            
            return when {
                // Mobile: Use SECTIONS for best organization
                screenWidthDp < 600 -> SECTIONS
                
                // Small tablet: GRID works well
                screenWidthDp < 768 -> GRID
                
                // Large tablet/desktop: Choose based on widget count
                widgetCount <= 4 -> GRID
                widgetCount <= 8 -> SECTIONS
                else -> GRID // Grid handles many widgets better
            }
        }
        
        /**
         * Gets optimal layout mode specifically for widget collections.
         * 
         * @param widgets List of widgets to display
         * @param screenWidthDp Screen width in dp
         * @param userPreference User's preferred layout mode (nullable)
         * @return Optimal DashboardLayoutMode for widgets
         */
        fun getOptimalModeForWidgets(
            widgets: List<AnalyticsWidget>,
            screenWidthDp: Int,
            userPreference: DashboardLayoutMode? = null
        ): DashboardLayoutMode {
            // Honor user preference if explicitly set
            userPreference?.let { preference ->
                if (preference != DEFAULT) return preference
            }
            
            val widgetCount = widgets.size
            
            // Check if widgets have different priority levels for section organization
            val hasVariedPriorities = widgets.map { it.priority }.distinct().size > 1
            
            return when {
                // No widgets: default to GRID
                widgetCount == 0 -> GRID
                
                // Mobile: Always use SECTIONS for better organization
                screenWidthDp < 600 -> SECTIONS
                
                // Tablet: Use SECTIONS if widgets have varied priorities
                screenWidthDp < 768 && hasVariedPriorities -> SECTIONS
                screenWidthDp < 768 -> GRID
                
                // Desktop: Optimize based on content complexity
                hasVariedPriorities && widgetCount > 6 -> SECTIONS
                widgetCount <= 4 -> GRID
                else -> GRID
            }
        }
        
        /**
         * Maps domain DashboardLayoutMode to UI DashboardLayoutMode
         */
        fun fromDomain(domainMode: DomainDashboardLayoutMode?): DashboardLayoutMode? {
            return when (domainMode) {
                DomainDashboardLayoutMode.AUTO -> DEFAULT
                DomainDashboardLayoutMode.CUSTOM -> CUSTOM
                DomainDashboardLayoutMode.COMPACT -> SECTIONS
                DomainDashboardLayoutMode.EXPANDED -> GRID
                DomainDashboardLayoutMode.GRID -> GRID
                DomainDashboardLayoutMode.LIST -> LIST
                DomainDashboardLayoutMode.SECTIONS -> SECTIONS
                DomainDashboardLayoutMode.DEFAULT -> DEFAULT
                null -> null
            }
        }
        
        /**
         * Maps UI DashboardLayoutMode to domain DashboardLayoutMode
         */
        fun toDomain(uiMode: DashboardLayoutMode): DomainDashboardLayoutMode {
            return when (uiMode) {
                DEFAULT -> DomainDashboardLayoutMode.AUTO
                GRID -> DomainDashboardLayoutMode.EXPANDED
                SECTIONS -> DomainDashboardLayoutMode.COMPACT
                LIST -> DomainDashboardLayoutMode.COMPACT
                CUSTOM -> DomainDashboardLayoutMode.CUSTOM
            }
        }
    }
}