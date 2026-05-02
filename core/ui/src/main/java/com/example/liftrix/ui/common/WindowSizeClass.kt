package com.example.liftrix.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.components.layouts.Breakpoints

enum class WindowWidthSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

enum class WindowHeightSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED
}

data class WindowSizeClass(
    val widthSizeClass: WindowWidthSizeClass,
    val heightSizeClass: WindowHeightSizeClass,
    val widthDp: Dp,
    val heightDp: Dp
) {
    fun calculateOptimalColumns(maxColumns: Int = 3): Int = when (widthSizeClass) {
        WindowWidthSizeClass.COMPACT -> 1
        WindowWidthSizeClass.MEDIUM -> minOf(2, maxColumns)
        WindowWidthSizeClass.EXPANDED -> minOf(3, maxColumns)
    }

    val shouldUseCompactSpacing: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.COMPACT

    val shouldShowCollapsibleSections: Boolean
        get() = widthSizeClass != WindowWidthSizeClass.COMPACT

    val recommendedMinWidgetWidth: Dp
        get() = when (widthSizeClass) {
            WindowWidthSizeClass.COMPACT -> 280.dp
            WindowWidthSizeClass.MEDIUM -> 320.dp
            WindowWidthSizeClass.EXPANDED -> 360.dp
        }

    val supportsDragAndDrop: Boolean
        get() = widthDp.value >= 400 || heightSizeClass == WindowHeightSizeClass.EXPANDED

    fun getResponsiveSpacing(
        compactSpacing: Dp = 8.dp,
        mediumSpacing: Dp = 12.dp,
        expandedSpacing: Dp = 16.dp
    ): Dp = when (widthSizeClass) {
        WindowWidthSizeClass.COMPACT -> compactSpacing
        WindowWidthSizeClass.MEDIUM -> mediumSpacing
        WindowWidthSizeClass.EXPANDED -> expandedSpacing
    }

    fun getResponsivePadding(
        compactPadding: Dp = 16.dp,
        mediumPadding: Dp = 24.dp,
        expandedPadding: Dp = 32.dp
    ): Dp = when (widthSizeClass) {
        WindowWidthSizeClass.COMPACT -> compactPadding
        WindowWidthSizeClass.MEDIUM -> mediumPadding
        WindowWidthSizeClass.EXPANDED -> expandedPadding
    }
}

@Composable
fun rememberWindowSizeClass(): WindowSizeClass {
    val configuration = LocalConfiguration.current
    return remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        val width = configuration.screenWidthDp.dp
        val height = configuration.screenHeightDp.dp
        WindowSizeClass(
            widthSizeClass = when {
                Breakpoints.isCompact(width) -> WindowWidthSizeClass.COMPACT
                Breakpoints.isMedium(width) -> WindowWidthSizeClass.MEDIUM
                else -> WindowWidthSizeClass.EXPANDED
            },
            heightSizeClass = when {
                height < 480.dp -> WindowHeightSizeClass.COMPACT
                height < 900.dp -> WindowHeightSizeClass.MEDIUM
                else -> WindowHeightSizeClass.EXPANDED
            },
            widthDp = width,
            heightDp = height
        )
    }
}
