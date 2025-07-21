package com.example.liftrix.ui.progress.components

/**
 * Layout modes for the responsive dashboard system.
 * 
 * Defines different organizational patterns for displaying analytics widgets
 * across various screen sizes and user preferences. Each mode is optimized
 * for specific use cases and device configurations.
 */
enum class DashboardLayoutMode {
    /**
     * Grid layout with fixed columns.
     * Responsive: 1 column (compact), 2 columns (medium), 3 columns (expanded)
     * Best for: Overview dashboards with equal-importance widgets
     */
    GRID,
    
    /**
     * Sections layout with categorized groupings.
     * Organizes widgets by category (METRICS, CHARTS, PROGRESS, ANALYTICS)
     * with collapsible sections and priority-based ordering.
     * Best for: Comprehensive analytics with logical grouping
     */
    SECTIONS,
    
    /**
     * List layout with single column.
     * Full-width widgets in vertical list arrangement.
     * Best for: Mobile-first design and detail-focused views
     */
    LIST,
    
    /**
     * Custom layout with user-defined arrangements.
     * Supports drag-and-drop positioning and custom sizing.
     * Best for: Power users and personalized dashboards
     */
    CUSTOM;
    
    /**
     * Display name for UI presentation
     */
    val displayName: String
        get() = when (this) {
            GRID -> "Grid Layout"
            SECTIONS -> "Sections Layout"
            LIST -> "List Layout"
            CUSTOM -> "Custom Layout"
        }
    
    /**
     * Description for each layout mode
     */
    val description: String
        get() = when (this) {
            GRID -> "Responsive grid with automatic column adjustment"
            SECTIONS -> "Organized sections grouped by widget category"
            LIST -> "Single column list for focused viewing"
            CUSTOM -> "Customizable layout with drag-and-drop positioning"
        }
    
    /**
     * Indicates if this layout mode supports responsive column changes
     */
    val isResponsive: Boolean
        get() = when (this) {
            GRID, SECTIONS -> true
            LIST, CUSTOM -> false
        }
    
    /**
     * Maximum recommended widgets for this layout mode
     */
    val maxRecommendedWidgets: Int
        get() = when (this) {
            GRID -> 12
            SECTIONS -> 25
            LIST -> 8
            CUSTOM -> 20
        }
    
    companion object {
        /**
         * Default layout mode for new dashboards
         */
        val DEFAULT = SECTIONS
        
        /**
         * Recommended layout mode based on widget count
         */
        fun getRecommendedMode(widgetCount: Int): DashboardLayoutMode {
            return when {
                widgetCount <= 6 -> GRID
                widgetCount <= 12 -> SECTIONS
                widgetCount <= 25 -> SECTIONS
                else -> CUSTOM
            }
        }
        
        /**
         * Get layout mode optimized for screen width and user preferences
         * 
         * @param screenWidthDp Screen width in density-independent pixels
         * @param widgetCount Number of widgets to display
         * @param userPreference User's preferred layout mode (takes priority over optimization)
         * @return Optimal layout mode considering user preference and device constraints
         */
        fun getOptimalMode(
            screenWidthDp: Int, 
            widgetCount: Int, 
            userPreference: DashboardLayoutMode? = null
        ): DashboardLayoutMode {
            // CRITICAL FIX: Always honor user preference, especially CUSTOM mode
            userPreference?.let { preference ->
                return preference
            }
            
            // Only apply automatic optimization when no user preference is set
            return when {
                screenWidthDp < 400 -> LIST // Phone portrait
                screenWidthDp < 600 && widgetCount <= 8 -> GRID // Phone landscape
                widgetCount <= 12 -> GRID // Tablet with moderate widgets
                else -> SECTIONS // Tablet with many widgets
            }
        }
    }
}