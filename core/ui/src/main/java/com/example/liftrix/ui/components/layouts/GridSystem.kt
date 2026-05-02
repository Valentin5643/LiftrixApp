package com.example.liftrix.ui.components.layouts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 8pt Grid System Spacing Values
 * Provides consistent spacing throughout the app following Material Design principles
 */
object SpacingValues {
    // Base 8pt grid spacing
    val grid4pt = 4.dp
    val grid8pt = 8.dp
    val grid12pt = 12.dp
    val grid16pt = 16.dp
    val grid24pt = 24.dp
    val grid32pt = 32.dp
    val grid40pt = 40.dp
    val grid48pt = 48.dp
    val grid56pt = 56.dp
    val grid64pt = 64.dp
    
    // Semantic spacing aliases
    val xxs = grid4pt
    val xs = grid8pt
    val s = grid12pt
    val m = grid16pt
    val l = grid24pt
    val xl = grid32pt
    val xxl = grid40pt
    
    // Content padding defaults
    val contentPadding = grid16pt
    val compactPadding = grid12pt
    val spaciousPadding = grid24pt
}

/**
 * LiftrixGrid System - 8pt grid implementation for consistent spacing
 * 
 * Provides standardized spacing values based on 8pt grid system
 * ensuring consistent spacing and alignment throughout the app.
 */
object GridSystem {
    // Base spacing unit (8dp)
    private val baseUnit = 8.dp
    
    // Spacing scale based on 8pt grid
    val spacing0 = 0.dp
    val spacing1 = 4.dp    // 0.5x base unit
    val spacing2 = 8.dp    // 1x base unit
    val spacing3 = 16.dp   // 2x base unit
    val spacing4 = 24.dp   // 3x base unit
    val spacing5 = 32.dp   // 4x base unit
    val spacing6 = 48.dp   // 6x base unit
    val spacing7 = 64.dp   // 8x base unit
    val spacing8 = 80.dp   // 10x base unit
    val spacing9 = 96.dp   // 12x base unit
    
    // Semantic spacing names for common use cases
    val paddingSmall = spacing2      // 8dp
    val paddingMedium = spacing3     // 16dp
    val paddingLarge = spacing4      // 24dp
    val paddingXLarge = spacing5     // 32dp
    
    val marginSmall = spacing2       // 8dp
    val marginMedium = spacing3      // 16dp
    val marginLarge = spacing4       // 24dp
    val marginXLarge = spacing5      // 32dp
    
    val gapSmall = spacing2          // 8dp
    val gapMedium = spacing3         // 16dp
    val gapLarge = spacing4          // 24dp
    
    // Component-specific spacing
    val cardPadding = spacing3       // 16dp
    val cardMargin = spacing3        // 16dp
    val cardGap = spacing3           // 16dp
    
    val buttonPadding = spacing3     // 16dp
    val buttonMargin = spacing2      // 8dp
    val buttonGap = spacing2         // 8dp
    
    val screenPadding = spacing3     // 16dp
    val screenMargin = spacing3      // 16dp
    
    // Touch target sizing (minimum 48dp per Material Design)
    val touchTargetMinimum = 48.dp
    val touchTargetRecommended = 56.dp
    
    // Icon sizing based on Material Design
    val iconSmall = 16.dp
    val iconMedium = 24.dp
    val iconLarge = 32.dp
    val iconXLarge = 48.dp
    
    // Corner radius values
    val cornerRadiusSmall = 4.dp
    val cornerRadiusMedium = 8.dp
    val cornerRadiusLarge = 16.dp
    val cornerRadiusXLarge = 24.dp  // Used for LiftrixCard
    
    // Elevation values
    val elevationNone = 0.dp
    val elevationSmall = 2.dp
    val elevationMedium = 4.dp
    val elevationLarge = 8.dp
    val elevationXLarge = 16.dp
    
    /**
     * Calculates spacing based on multiplier of base unit
     */
    fun spacing(multiplier: Float): Dp = (baseUnit.value * multiplier).dp
    
    /**
     * Validates if a dimension follows the 8pt grid system
     */
    fun isGridAligned(dimension: Dp): Boolean {
        return dimension.value % 4 == 0f
    }
}

/**
 * Breakpoint definitions for responsive design
 */
object Breakpoints {
    // Material Design 3 breakpoints
    val compact = 0.dp..599.dp      // Phones in portrait
    val medium = 600.dp..904.dp     // Tablets, phones in landscape
    val expanded = 905.dp..1239.dp  // Large tablets, foldables
    val large = 1240.dp..1439.dp    // Desktop, large screens
    val extraLarge = 1440.dp..Float.MAX_VALUE.dp  // Ultra-wide screens
    
    // Convenience functions for breakpoint checking
    fun isCompact(width: Dp): Boolean = width in compact
    fun isMedium(width: Dp): Boolean = width in medium
    fun isExpanded(width: Dp): Boolean = width in expanded
    fun isLarge(width: Dp): Boolean = width in large
    fun isExtraLarge(width: Dp): Boolean = width in extraLarge
}

/**
 * Layout constants for consistent component sizing
 */
object LayoutConstants {
    // App bar heights
    val appBarHeight = 64.dp
    val appBarHeightSmall = 56.dp
    
    // Bottom navigation
    val bottomNavigationHeight = 80.dp
    
    // Card dimensions
    val cardMinHeight = 120.dp
    val cardMaxWidth = 400.dp
    
    // List item heights
    val listItemHeightSmall = 56.dp
    val listItemHeightMedium = 72.dp
    val listItemHeightLarge = 88.dp
    
    // FAB dimensions
    val fabSize = 56.dp
    val fabSizeSmall = 40.dp
    val fabSizeLarge = 80.dp
    
    // Modal dimensions
    val modalMaxWidth = 560.dp
    val modalMaxHeight = 0.9f // 90% of screen height
    
    // Image dimensions
    val avatarSizeSmall = 32.dp
    val avatarSizeMedium = 48.dp
    val avatarSizeLarge = 64.dp
    val avatarSizeXLarge = 96.dp
}

/**
 * Grid container that enforces 8pt grid spacing with flexible column configuration
 * 
 * @param columns Number of columns in the grid
 * @param spacing Spacing between grid items (default: 8pt grid base)
 * @param contentPadding Padding around the entire grid
 * @param modifier Modifier for styling the grid container
 * @param content Grid content lambda with LazyGridScope
 */
@Composable
fun GridContainer(
    columns: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = SpacingValues.grid8pt,
    contentPadding: PaddingValues = PaddingValues(SpacingValues.grid16pt),
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/**
 * Adaptive grid container that automatically adjusts columns based on available width
 * Each column has a minimum width ensuring responsive behavior
 * 
 * @param minColumnWidth Minimum width for each column (default: 160dp for cards)
 * @param spacing Spacing between grid items (default: 8pt grid base)
 * @param contentPadding Padding around the entire grid
 * @param modifier Modifier for styling the grid container
 * @param content Grid content lambda with LazyGridScope
 */
@Composable
fun AdaptiveGridContainer(
    minColumnWidth: Dp = 160.dp,
    modifier: Modifier = Modifier,
    spacing: Dp = SpacingValues.grid8pt,
    contentPadding: PaddingValues = PaddingValues(SpacingValues.grid16pt),
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minColumnWidth),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/**
 * Vertical list container with consistent 8pt grid spacing
 * 
 * @param spacing Vertical spacing between items (default: 8pt grid base)
 * @param contentPadding Padding around the entire list
 * @param modifier Modifier for styling the list container
 * @param content List content lambda with LazyListScope
 */
@Composable
fun VerticalListContainer(
    modifier: Modifier = Modifier,
    spacing: Dp = SpacingValues.grid8pt,
    contentPadding: PaddingValues = PaddingValues(SpacingValues.grid16pt),
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/**
 * Horizontal list container with consistent 8pt grid spacing
 * 
 * @param spacing Horizontal spacing between items (default: 8pt grid base)
 * @param contentPadding Padding around the entire list
 * @param modifier Modifier for styling the list container
 * @param content List content lambda with LazyListScope
 */
@Composable
fun HorizontalListContainer(
    modifier: Modifier = Modifier,
    spacing: Dp = SpacingValues.grid8pt,
    contentPadding: PaddingValues = PaddingValues(SpacingValues.grid16pt),
    content: LazyListScope.() -> Unit
) {
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/**
 * Standard column layout with 8pt grid spacing
 * For non-scrollable content arrangements
 * 
 * @param spacing Vertical spacing between items (default: 8pt grid base)
 * @param modifier Modifier for styling the column
 * @param content Column content lambda
 */
@Composable
fun GridColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = SpacingValues.grid8pt,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/**
 * Standard row layout with 8pt grid spacing
 * For non-scrollable content arrangements
 * 
 * @param spacing Horizontal spacing between items (default: 8pt grid base)
 * @param modifier Modifier for styling the row
 * @param content Row content lambda
 */
@Composable
fun GridRow(
    modifier: Modifier = Modifier,
    spacing: Dp = SpacingValues.grid8pt,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/**
 * Content section with consistent spacing and optional title
 * Provides standardized content organization
 * 
 * @param title Optional section title
 * @param spacing Vertical spacing between items (default: 8pt grid base)
 * @param contentPadding Padding around the section content
 * @param modifier Modifier for styling the section
 * @param content Section content lambda
 */
@Composable
fun ContentSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    spacing: Dp = SpacingValues.grid8pt,
    contentPadding: PaddingValues = PaddingValues(SpacingValues.grid16pt),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        title?.let {
            // Title handling would be added here with proper typography
            // For now, focusing on layout structure
        }
        content()
    }
} 