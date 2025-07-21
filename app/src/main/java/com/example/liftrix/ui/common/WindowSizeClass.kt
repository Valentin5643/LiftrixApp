package com.example.liftrix.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.components.layouts.Breakpoints

/**
 * Window size class definitions following Material Design 3 principles.
 * 
 * Provides standardized breakpoints for responsive design across different
 * device sizes and orientations. Integrates with existing GridSystem breakpoints.
 */
enum class WindowWidthSizeClass {
    /**
     * Compact width - typically phones in portrait
     * Width: 0dp to 599dp
     */
    COMPACT,
    
    /**
     * Medium width - typically tablets and phones in landscape
     * Width: 600dp to 904dp
     */
    MEDIUM,
    
    /**
     * Expanded width - typically large tablets and foldables
     * Width: 905dp and above
     */
    EXPANDED
}

enum class WindowHeightSizeClass {
    /**
     * Compact height - typically phones in landscape
     * Height: 0dp to 479dp
     */
    COMPACT,
    
    /**
     * Medium height - typically phones in portrait
     * Height: 480dp to 899dp
     */
    MEDIUM,
    
    /**
     * Expanded height - typically tablets
     * Height: 900dp and above
     */
    EXPANDED
}

/**
 * Complete window size class information for responsive layout decisions
 */
data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val widthDp: Dp,
    val heightDp: Dp
) {
    /**
     * Calculates optimal column count for dashboard layouts
     */
    fun calculateOptimalColumns(maxColumns: Int = 3): Int {
        return when (widthSizeClass) {
            WindowWidthSizeClass.COMPACT -> 1
            WindowWidthSizeClass.MEDIUM -> minOf(2, maxColumns)
            WindowWidthSizeClass.EXPANDED -> minOf(3, maxColumns)
        }
    }
    
    /**
     * Determines if the layout should use compact spacing
     */
    val shouldUseCompactSpacing: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.COMPACT
    
    /**
     * Determines if the layout should show collapsible sections
     */
    val shouldShowCollapsibleSections: Boolean
        get() = widthSizeClass != WindowWidthSizeClass.COMPACT
    
    /**
     * Gets recommended minimum widget width for this size class
     */
    val recommendedMinWidgetWidth: Dp
        get() = when (widthSizeClass) {
            WindowWidthSizeClass.COMPACT -> 280.dp
            WindowWidthSizeClass.MEDIUM -> 320.dp
            WindowWidthSizeClass.EXPANDED -> 360.dp
        }
    
    /**
     * Determines if drag-and-drop should be enabled
     */
    val supportsDragAndDrop: Boolean
        get() = widthDp.value >= 400 || heightSizeClass == WindowHeightSizeClass.EXPANDED
    
    /**
     * Determines if this is likely a foldable device in an expanded state
     */
    val isFoldableExpanded: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.EXPANDED && 
                heightSizeClass == WindowHeightSizeClass.COMPACT
    
    /**
     * Provides layout preservation key for configuration changes
     */
    val layoutPreservationKey: String
        get() = "${widthSizeClass.name}_${heightSizeClass.name}_${widthDp.value.toInt()}_${heightDp.value.toInt()}"
    
    /**
     * Determines if layout should preserve widget positions during transitions
     */
    val shouldPreserveLayout: Boolean
        get() = isFoldableExpanded || (widthSizeClass != WindowWidthSizeClass.COMPACT)
}

/**
 * Remembers the current window size class and provides responsive information
 * 
 * This composable tracks device configuration changes and provides
 * window size classification for responsive layout decisions.
 */
@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val widthDp = configuration.screenWidthDp.dp
        val heightDp = configuration.screenHeightDp.dp
        
        val widthSizeClass = when {
            Breakpoints.isCompact(widthDp) -> WindowWidthSizeClass.COMPACT
            Breakpoints.isMedium(widthDp) -> WindowWidthSizeClass.MEDIUM
            else -> WindowWidthSizeClass.EXPANDED
        }
        
        val heightSizeClass = when {
            heightDp < 480.dp -> WindowHeightSizeClass.COMPACT
            heightDp < 900.dp -> WindowHeightSizeClass.MEDIUM
            else -> WindowHeightSizeClass.EXPANDED
        }
        
        WindowSizeClass(
            widthSizeClass = widthSizeClass,
            heightSizeClass = heightSizeClass,
            widthDp = widthDp,
            heightDp = heightDp
        )
    }
}

/**
 * Extension functions for common responsive layout calculations
 */

/**
 * Calculates responsive column count based on window size and content requirements
 */
fun WindowSizeClass.calculateColumnsForContent(
    contentCount: Int,
    maxColumns: Int = 3,
    minItemsPerColumn: Int = 2
): Int {
    val optimalColumns = calculateOptimalColumns(maxColumns)
    val maxUsefulColumns = (contentCount + minItemsPerColumn - 1) / minItemsPerColumn
    return minOf(optimalColumns, maxUsefulColumns, contentCount)
}

/**
 * Determines spacing based on window size class
 */
fun WindowSizeClass.getResponsiveSpacing(
    compactSpacing: Dp = 8.dp,
    mediumSpacing: Dp = 12.dp,
    expandedSpacing: Dp = 16.dp
): Dp {
    return when (widthSizeClass) {
        WindowWidthSizeClass.COMPACT -> compactSpacing
        WindowWidthSizeClass.MEDIUM -> mediumSpacing
        WindowWidthSizeClass.EXPANDED -> expandedSpacing
    }
}

/**
 * Gets responsive padding values
 */
fun WindowSizeClass.getResponsivePadding(
    compactPadding: Dp = 16.dp,
    mediumPadding: Dp = 24.dp,
    expandedPadding: Dp = 32.dp
): Dp {
    return when (widthSizeClass) {
        WindowWidthSizeClass.COMPACT -> compactPadding
        WindowWidthSizeClass.MEDIUM -> mediumPadding
        WindowWidthSizeClass.EXPANDED -> expandedPadding
    }
}