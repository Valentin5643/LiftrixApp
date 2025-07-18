package com.example.liftrix.ui.progress.components

/**
 * UI layer enum for widget layout modes in the Progress Dashboard.
 * 
 * This enum provides UI-specific layout options that are mapped to domain layer
 * DashboardLayoutMode values. It serves as the boundary between UI concerns and
 * domain logic, allowing for UI-specific optimizations and terminology.
 * 
 * Layout Modes:
 * - GRID: Traditional grid layout with equal-sized widgets
 * - STAGGERED: Staggered grid layout with varying widget heights
 * - LIST: Vertical list layout optimized for scrolling
 * - SECTIONS: Organized sections with collapsible categories
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun LayoutModeSelector(
 *     currentMode: WidgetLayoutMode,
 *     onModeChanged: (WidgetLayoutMode) -> Unit
 * ) {
 *     WidgetLayoutMode.values().forEach { mode ->
 *         RadioButton(
 *             selected = currentMode == mode,
 *             onClick = { onModeChanged(mode) }
 *         )
 *         Text(text = mode.displayName)
 *     }
 * }
 * ```
 */
enum class WidgetLayoutMode(
    val displayName: String,
    val description: String,
    val supportsCollapsibleSections: Boolean = false
) {
    /**
     * Traditional grid layout with equal-sized widgets.
     * Best for users who prefer consistent visual hierarchy.
     */
    GRID(
        displayName = "Grid",
        description = "Traditional grid layout with equal-sized widgets",
        supportsCollapsibleSections = false
    ),
    
    /**
     * Staggered grid layout with varying widget heights.
     * Allows for more dynamic content display and better space utilization.
     */
    STAGGERED(
        displayName = "Staggered",
        description = "Staggered grid layout with varying widget heights",
        supportsCollapsibleSections = false
    ),
    
    /**
     * Vertical list layout optimized for scrolling.
     * Ideal for mobile devices and users who prefer linear information flow.
     */
    LIST(
        displayName = "List",
        description = "Vertical list layout optimized for scrolling",
        supportsCollapsibleSections = false
    ),
    
    /**
     * Organized sections with collapsible categories.
     * Provides the most organizational flexibility with grouping capabilities.
     */
    SECTIONS(
        displayName = "Sections",
        description = "Organized sections with collapsible categories",
        supportsCollapsibleSections = true
    );
    
    /**
     * Checks if this layout mode supports widget reordering.
     * 
     * @return true if widgets can be reordered in this layout mode
     */
    fun supportsReordering(): Boolean = when (this) {
        GRID, STAGGERED -> true
        LIST, SECTIONS -> true
    }
    
    /**
     * Checks if this layout mode supports different widget sizes.
     * 
     * @return true if widgets can have different sizes in this layout mode
     */
    fun supportsVariableSizes(): Boolean = when (this) {
        GRID -> false
        STAGGERED -> true
        LIST -> false
        SECTIONS -> true
    }
    
    /**
     * Gets the recommended number of columns for this layout mode.
     * 
     * @param isTablet whether the device is a tablet
     * @return recommended number of columns
     */
    fun getRecommendedColumns(isTablet: Boolean): Int = when (this) {
        GRID -> if (isTablet) 3 else 2
        STAGGERED -> if (isTablet) 3 else 2
        LIST -> 1
        SECTIONS -> 1
    }
    
    companion object {
        /**
         * Gets the default layout mode for new users.
         * 
         * @return default WidgetLayoutMode
         */
        fun getDefault(): WidgetLayoutMode = SECTIONS
        
        /**
         * Gets layout modes available for the specified user level.
         * 
         * @param userLevel the user's experience level
         * @return list of available layout modes
         */
        fun getAvailableForUserLevel(userLevel: com.example.liftrix.domain.model.analytics.UserLevel): List<WidgetLayoutMode> = when (userLevel) {
            com.example.liftrix.domain.model.analytics.UserLevel.BEGINNER -> listOf(SECTIONS, LIST)
            com.example.liftrix.domain.model.analytics.UserLevel.INTERMEDIATE -> listOf(SECTIONS, LIST, GRID)
            com.example.liftrix.domain.model.analytics.UserLevel.ADVANCED -> values().toList()
        }
    }
}