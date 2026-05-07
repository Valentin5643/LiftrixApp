package com.example.liftrix.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Responsive utility functions for the Progress Tab UI Redesign.
 * 
 * Implements responsive breakpoints:
 * - Mobile: <600dp (2 columns)
 * - Tablet: 600-767dp (3 columns) 
 * - Desktop: ≥768dp (4 columns)
 * 
 * Provides reusable breakpoint detection, spacing, and layout utilities
 * for consistent responsive behavior across the app.
 */
object ResponsiveUtils {
    
    /**
     * Responsive breakpoint values
     */
    object Breakpoints {
        val MOBILE_MAX = 599.dp
        val TABLET_MIN = 600.dp
        val TABLET_MAX = 767.dp
        val DESKTOP_MIN = 768.dp
    }
    
    /**
     * Responsive spacing values
     */
    object Spacing {
        val CARD_SPACING = 12.dp
        val SCREEN_PADDING = 16.dp
        val INTERNAL_PADDING = 20.dp
        val DESKTOP_CARD_SPACING = 16.dp
    }
    
    /**
     * Responsive layout rules
     */
    data class LayoutRules(
        val mobileColumns: Int = 2,        // <600dp
        val tabletColumns: Int = 3,        // 600-767dp
        val desktopColumns: Int = 4,       // 768dp+
        val cardSpacing: Dp = Spacing.CARD_SPACING,
        val screenPadding: Dp = Spacing.SCREEN_PADDING,
        val internalPadding: Dp = Spacing.INTERNAL_PADDING,
        val minCardHeight: Dp = 120.dp,
        val maxCardHeight: Dp = 400.dp
    )
    
    /**
     * Device size classification based on current screen width
     */
    enum class DeviceSize {
        MOBILE,    // <600dp
        TABLET,    // 600-767dp  
        DESKTOP    // ≥768dp
    }
    
    /**
     * Determines device size based on screen width
     */
    fun getDeviceSize(screenWidthDp: Dp): DeviceSize {
        return when {
            screenWidthDp < Breakpoints.TABLET_MIN -> DeviceSize.MOBILE
            screenWidthDp < Breakpoints.DESKTOP_MIN -> DeviceSize.TABLET
            else -> DeviceSize.DESKTOP
        }
    }
    
    /**
     * Calculates optimal column count based on device size
     */
    fun calculateColumns(deviceSize: DeviceSize): Int {
        return when (deviceSize) {
            DeviceSize.MOBILE -> LayoutRules().mobileColumns
            DeviceSize.TABLET -> LayoutRules().tabletColumns
            DeviceSize.DESKTOP -> LayoutRules().desktopColumns
        }
    }
    
    /**
     * Calculates card spacing based on device size
     */
    fun calculateCardSpacing(deviceSize: DeviceSize): Dp {
        return when (deviceSize) {
            DeviceSize.MOBILE, DeviceSize.TABLET -> Spacing.CARD_SPACING
            DeviceSize.DESKTOP -> Spacing.DESKTOP_CARD_SPACING
        }
    }
    
    /**
     * Gets screen padding (always 16dp)
     */
    fun getScreenPadding(): Dp = Spacing.SCREEN_PADDING
    
    /**
     * Gets internal card padding (always 20dp)
     */
    fun getInternalPadding(): Dp = Spacing.INTERNAL_PADDING
    
    /**
     * Checks if current screen width is mobile
     */
    fun isMobile(screenWidthDp: Dp): Boolean = 
        screenWidthDp < Breakpoints.TABLET_MIN
    
    /**
     * Checks if current screen width is tablet
     */
    fun isTablet(screenWidthDp: Dp): Boolean = 
        screenWidthDp >= Breakpoints.TABLET_MIN && screenWidthDp < Breakpoints.DESKTOP_MIN
    
    /**
     * Checks if current screen width is desktop
     */
    fun isDesktop(screenWidthDp: Dp): Boolean = 
        screenWidthDp >= Breakpoints.DESKTOP_MIN
}

/**
 * Composable that provides current responsive layout information
 */
data class ResponsiveLayoutInfo(
    val deviceSize: ResponsiveUtils.DeviceSize,
    val columns: Int,
    val cardSpacing: Dp,
    val screenPadding: Dp,
    val internalPadding: Dp,
    val screenWidthDp: Dp
)

/**
 * Remembers responsive layout information for the current screen
 */
@Composable
fun rememberResponsiveLayoutInfo(): ResponsiveLayoutInfo {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    
    return remember(screenWidthDp) {
        val deviceSize = ResponsiveUtils.getDeviceSize(screenWidthDp)
        ResponsiveLayoutInfo(
            deviceSize = deviceSize,
            columns = ResponsiveUtils.calculateColumns(deviceSize),
            cardSpacing = ResponsiveUtils.calculateCardSpacing(deviceSize),
            screenPadding = ResponsiveUtils.getScreenPadding(),
            internalPadding = ResponsiveUtils.getInternalPadding(),
            screenWidthDp = screenWidthDp
        )
    }
}

/**
 * Extension functions for WindowSizeClass to use new responsive system
 */
fun WindowSizeClass.getResponsiveColumns(): Int {
    val deviceSize = ResponsiveUtils.getDeviceSize(this.widthDp)
    return ResponsiveUtils.calculateColumns(deviceSize)
}

fun WindowSizeClass.getResponsiveCardSpacing(): Dp {
    val deviceSize = ResponsiveUtils.getDeviceSize(this.widthDp)
    return ResponsiveUtils.calculateCardSpacing(deviceSize)
}

fun WindowSizeClass.isProgressTabMobile(): Boolean =
    ResponsiveUtils.isMobile(this.widthDp)

fun WindowSizeClass.isProgressTabTablet(): Boolean =
    ResponsiveUtils.isTablet(this.widthDp)

fun WindowSizeClass.isProgressTabDesktop(): Boolean =
    ResponsiveUtils.isDesktop(this.widthDp)