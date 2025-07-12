package com.example.liftrix.ui.components.layouts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive container that adapts layout based on screen size
 * 
 * Provides different layouts for compact, medium, and expanded screen sizes
 * following Material Design 3 breakpoints.
 */
@Composable
fun ResponsiveContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = LayoutConstants.modalMaxWidth,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    content: @Composable BoxScope.(screenSize: ScreenSize) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        val screenSize = remember(maxWidth) {
            when {
                maxWidth < 600.dp -> ScreenSize.Compact
                maxWidth < 905.dp -> ScreenSize.Medium
                else -> ScreenSize.Expanded
            }
        }
        
        Box(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            content(screenSize)
        }
    }
}

/**
 * Asymmetrical layout container for dashboard-style compositions
 * 
 * Provides different layouts based on screen size with focus on
 * creating personal performance dashboard feeling.
 */
@Composable
fun AsymmetricalLayout(
    modifier: Modifier = Modifier,
    primaryContent: @Composable BoxScope.() -> Unit,
    secondaryContent: @Composable BoxScope.() -> Unit,
    tertiaryContent: (@Composable BoxScope.() -> Unit)? = null
) {
    BoxWithConstraints(modifier = modifier) {
        val screenSize = remember(maxWidth) {
            when {
                maxWidth < 600.dp -> ScreenSize.Compact
                maxWidth < 905.dp -> ScreenSize.Medium
                else -> ScreenSize.Expanded
            }
        }
        
        when (screenSize) {
            ScreenSize.Compact -> {
                // Vertical stacking for compact screens
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = GridSystem.spacing3)
                    ) {
                        primaryContent()
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = GridSystem.spacing3)
                    ) {
                        secondaryContent()
                    }
                    
                    tertiaryContent?.let { content ->
                        Box(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            content()
                        }
                    }
                }
            }
            
            ScreenSize.Medium -> {
                // Two-column layout for medium screens
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.5f)
                            .padding(end = GridSystem.spacing2)
                    ) {
                        primaryContent()
                    }
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = GridSystem.spacing2)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = GridSystem.spacing3)
                        ) {
                            secondaryContent()
                        }
                        
                        tertiaryContent?.let { content ->
                            Box(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                content()
                            }
                        }
                    }
                }
            }
            
            ScreenSize.Expanded -> {
                // Three-column layout for expanded screens
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .weight(2f)
                            .padding(end = GridSystem.spacing3)
                    ) {
                        primaryContent()
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .padding(horizontal = GridSystem.spacing2)
                    ) {
                        secondaryContent()
                    }
                    
                    tertiaryContent?.let { content ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = GridSystem.spacing3)
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Grid container that adapts column count based on screen size
 */
@Composable
fun AdaptiveGrid(
    modifier: Modifier = Modifier,
    compactColumns: Int = 1,
    mediumColumns: Int = 2,
    expandedColumns: Int = 3,
    content: @Composable ColumnScope.(columns: Int, screenSize: ScreenSize) -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        val screenSize = remember(maxWidth) {
            when {
                maxWidth < 600.dp -> ScreenSize.Compact
                maxWidth < 905.dp -> ScreenSize.Medium
                else -> ScreenSize.Expanded
            }
        }
        
        val columns = when (screenSize) {
            ScreenSize.Compact -> compactColumns
            ScreenSize.Medium -> mediumColumns
            ScreenSize.Expanded -> expandedColumns
        }
        
        Column(modifier = Modifier.fillMaxWidth()) {
            content(columns, screenSize)
        }
    }
}

/**
 * Responsive padding that adjusts based on screen size
 */
@Composable
fun Modifier.responsivePadding(
    compact: Dp = GridSystem.spacing3,
    medium: Dp = GridSystem.spacing4,
    expanded: Dp = GridSystem.spacing5
): Modifier {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidthDp = with(density) {
        configuration.screenWidthDp.dp
    }
    
    val padding = when {
        screenWidthDp < 600.dp -> compact
        screenWidthDp < 905.dp -> medium
        else -> expanded
    }
    
    return this.padding(padding)
}

/**
 * Responsive width that constrains content based on screen size
 */
@Composable
fun Modifier.responsiveWidth(
    compactFraction: Float = 1f,
    mediumMaxWidth: Dp = 600.dp,
    expandedMaxWidth: Dp = 800.dp
): Modifier {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidthDp = with(density) {
        configuration.screenWidthDp.dp
    }
    
    return when {
        screenWidthDp < 600.dp -> this.fillMaxWidth(compactFraction)
        screenWidthDp < 905.dp -> this.widthIn(max = mediumMaxWidth)
        else -> this.widthIn(max = expandedMaxWidth)
    }
}

/**
 * Screen size classification for responsive design
 */
enum class ScreenSize {
    Compact,    // 0-599dp (phones in portrait)
    Medium,     // 600-904dp (tablets, phones in landscape)
    Expanded    // 905+dp (large tablets, foldables)
}

/**
 * Utility function to get current screen size
 */
@Composable
fun rememberScreenSize(): ScreenSize {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    val screenWidthDp = with(density) {
        configuration.screenWidthDp.dp
    }
    
    return remember(screenWidthDp) {
        when {
            screenWidthDp < 600.dp -> ScreenSize.Compact
            screenWidthDp < 905.dp -> ScreenSize.Medium
            else -> ScreenSize.Expanded
        }
    }
} 