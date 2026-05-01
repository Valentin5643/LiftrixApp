package com.example.liftrix.domain.model.analytics

/**
 * Dashboard layout modes for customizable analytics display.
 * 
 * Defines 4 core ways users can organize and view their analytics widgets
 * on the dashboard, from automatic adaptive layouts to fully custom arrangements.
 */
enum class DashboardLayoutMode(
    val displayName: String,
    val description: String
) {
    /**
     * Automatic adaptive layout based on user level and screen size
     * Intelligently selects and arranges widgets based on context
     */
    AUTO(
        displayName = "Automatic",
        description = "Smart adaptive layout based on your screen and experience"
    ),
    
    /**
     * Fully customizable layout with drag-and-drop
     * Users have complete control over widget selection and positioning
     */
    CUSTOM(
        displayName = "Custom",
        description = "Full control with drag-and-drop arrangement"
    ),
    
    /**
     * Responsive grid layout with equal-sized widgets
     * Adapts column count based on screen size for optimal viewing
     */
    GRID(
        displayName = "Grid",
        description = "Responsive grid layout with smart column adaptation"
    ),
    
    /**
     * Organized sections with collapsible categories
     * Groups widgets by category with expand/collapse functionality
     */
    SECTIONS(
        displayName = "Sections",
        description = "Organized categories with collapsible sections"
    );
    
    /**
     * Gets the maximum number of widgets for this layout mode
     */
    fun getMaxWidgets(): Int = when (this) {
        AUTO -> 8        // Adaptive based on screen/level
        CUSTOM -> 12     // User-controlled maximum
        GRID -> 10       // Grid layout standard
        SECTIONS -> 10   // Section-based layout with categories
    }
    
    /**
     * Gets the default number of columns for grid layout
     * Note: This adapts based on screen size in actual implementation
     */
    fun getColumns(): Int = when (this) {
        AUTO -> 2        // Adapts: 1-3 based on screen
        CUSTOM -> 2      // User-configurable
        GRID -> 2        // Adapts: 2-4 based on screen  
        SECTIONS -> 1    // Single column with expandable sections
    }
    
    /**
     * Checks if this layout supports widget reordering
     */
    fun supportsReordering(): Boolean = when (this) {
        AUTO -> false      // Automatic arrangement
        CUSTOM -> true     // Full drag-and-drop support
        GRID -> true       // Grid supports reordering
        SECTIONS -> true   // Sections support category reordering
    }
    
    /**
     * Checks if this layout supports widget visibility toggling
     */
    fun supportsVisibilityToggle(): Boolean = when (this) {
        AUTO -> false      // Automatic selection
        CUSTOM -> true     // Full user control
        GRID -> true       // Can hide/show widgets
        SECTIONS -> true   // Can collapse/expand sections
    }
    
    companion object {
        /**
         * Gets the recommended layout mode for user level
         */
        fun getRecommendedForLevel(userLevel: UserLevel): DashboardLayoutMode = when (userLevel) {
            UserLevel.BEGINNER -> AUTO      // Automatic adaptive for beginners
            UserLevel.INTERMEDIATE -> GRID  // Grid layout for intermediate
            UserLevel.ADVANCED -> CUSTOM    // Full customization for advanced
        }
        
        /**
         * Gets layout mode by name (case-insensitive)
         * Includes legacy name mappings for backward compatibility
         */
        fun fromName(name: String): DashboardLayoutMode? = when (name.lowercase()) {
            "auto", "automatic", "default" -> AUTO
            "custom" -> CUSTOM
            "grid", "expanded" -> GRID  // Map expanded to grid
            "sections", "compact", "list" -> SECTIONS  // Map compact/list to sections
            else -> null
        }
        
        /**
         * Maps legacy layout modes to new consolidated modes
         * Used for migration and backward compatibility
         */
        fun migrateLegacyMode(legacyName: String): DashboardLayoutMode = when (legacyName.lowercase()) {
            "default" -> AUTO
            "compact" -> AUTO  // Compact becomes AUTO (adaptive for small screens)
            "expanded" -> GRID  // Expanded becomes GRID
            "list" -> SECTIONS  // List becomes SECTIONS (single column)
            else -> fromName(legacyName) ?: AUTO
        }
    }
}