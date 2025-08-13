package com.example.liftrix.domain.model.analytics

/**
 * Dashboard layout modes for customizable analytics display.
 * 
 * Defines different ways users can organize and view their analytics widgets
 * on the dashboard, from automatic level-based layouts to fully custom arrangements.
 */
enum class DashboardLayoutMode(
    val displayName: String,
    val description: String
) {
    /**
     * Automatic layout based on user level
     * Widgets selected and arranged automatically based on user experience
     */
    AUTO(
        displayName = "Automatic",
        description = "Smart layout based on your experience level"
    ),
    
    /**
     * Grid layout with user-customizable widget selection
     * Users can choose which widgets to display and their positions
     */
    CUSTOM(
        displayName = "Custom",
        description = "Choose your own widgets and arrangement"
    ),
    
    /**
     * Compact layout for smaller screens or minimal view
     * Fewer widgets with optimized spacing for limited screen real estate
     */
    COMPACT(
        displayName = "Compact",
        description = "Minimal view with essential widgets only"
    ),
    
    /**
     * Expanded layout showing maximum widgets
     * All available widgets displayed for comprehensive overview
     */
    EXPANDED(
        displayName = "Expanded", 
        description = "Maximum widgets for comprehensive view"
    ),
    
    /**
     * Traditional grid layout with equal-sized widgets
     * Best for users who prefer consistent visual hierarchy
     */
    GRID(
        displayName = "Grid",
        description = "Traditional grid layout with equal-sized widgets"
    ),
    
    /**
     * Vertical list layout optimized for scrolling
     * Ideal for mobile devices and users who prefer linear information flow
     */
    LIST(
        displayName = "List",
        description = "Vertical list layout optimized for scrolling"
    ),
    
    /**
     * Organized sections with collapsible categories
     * Provides the most organizational flexibility with grouping capabilities
     */
    SECTIONS(
        displayName = "Sections",
        description = "Organized sections with collapsible categories"
    ),
    
    /**
     * Default layout mode
     * Fallback layout mode for backward compatibility
     */
    DEFAULT(
        displayName = "Default",
        description = "Default dashboard layout"
    );
    
    /**
     * Gets the maximum number of widgets for this layout mode
     */
    fun getMaxWidgets(): Int = when (this) {
        AUTO -> 7        // Based on intermediate level
        CUSTOM -> 10     // User-controlled maximum
        COMPACT -> 4     // Minimal set
        EXPANDED -> 12   // Show everything available
        GRID -> 8        // Grid layout standard
        LIST -> 6        // List layout optimized
        SECTIONS -> 10   // Section-based layout
        DEFAULT -> 7     // Default fallback
    }
    
    /**
     * Gets the number of columns for grid layout
     */
    fun getColumns(): Int = when (this) {
        AUTO -> 2
        CUSTOM -> 2
        COMPACT -> 1
        EXPANDED -> 3
        GRID -> 2
        LIST -> 1
        SECTIONS -> 1
        DEFAULT -> 2
    }
    
    /**
     * Checks if this layout supports widget reordering
     */
    fun supportsReordering(): Boolean = when (this) {
        AUTO -> false      // Fixed arrangement
        CUSTOM -> true     // Full customization
        COMPACT -> false   // Fixed minimal layout
        EXPANDED -> true   // User can organize
        GRID -> true       // Grid supports reordering
        LIST -> true       // List supports reordering
        SECTIONS -> true   // Sections support reordering
        DEFAULT -> false   // Fixed arrangement
    }
    
    /**
     * Checks if this layout supports widget visibility toggling
     */
    fun supportsVisibilityToggle(): Boolean = when (this) {
        AUTO -> false      // Automatic selection
        CUSTOM -> true     // User controls visibility
        COMPACT -> false   // Fixed minimal set
        EXPANDED -> false  // Shows all widgets
        GRID -> true       // Grid supports visibility toggle
        LIST -> true       // List supports visibility toggle
        SECTIONS -> true   // Sections support visibility toggle
        DEFAULT -> false   // Fixed set
    }
    
    companion object {
        /**
         * Gets the recommended layout mode for user level
         */
        fun getRecommendedForLevel(userLevel: UserLevel): DashboardLayoutMode = when (userLevel) {
            UserLevel.BEGINNER -> COMPACT
            UserLevel.INTERMEDIATE -> AUTO
            UserLevel.ADVANCED -> CUSTOM
        }
        
        /**
         * Gets layout mode by name (case-insensitive)
         */
        fun fromName(name: String): DashboardLayoutMode? = when (name.lowercase()) {
            "auto", "automatic" -> AUTO
            "custom" -> CUSTOM
            "compact" -> COMPACT
            "expanded" -> EXPANDED
            "grid" -> GRID
            "list" -> LIST
            "sections" -> SECTIONS
            "default" -> DEFAULT
            else -> null
        }
    }
}