package com.example.liftrix.ui.accessibility

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import kotlin.math.pow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.domain.model.analytics.MetricWidgetData
import com.example.liftrix.domain.model.analytics.TrendDirection
import com.example.liftrix.ui.common.AccessibilityUtils as CommonAccessibilityUtils
import com.example.liftrix.ui.common.AccessibilityUtils.ensureMinimumTouchTarget
import com.example.liftrix.ui.theme.LiftrixColorsV2
import kotlinx.coroutines.delay

/**
 * Dashboard-specific accessibility utilities ensuring WCAG 2.1 AA compliance for analytics widgets.
 * 
 * Provides comprehensive accessibility support including:
 * - Dynamic content descriptions for widget data
 * - Alternative interaction methods for drag-and-drop
 * - TalkBack announcements for widget state changes
 * - Semantic actions for widget reordering and management
 * - Focus management for dashboard navigation
 * 
 * Leverages existing accessibility infrastructure while adding dashboard-specific enhancements.
 */
object AccessibilityUtils {

    /**
     * Enhanced accessibility semantics specifically for analytics widgets.
     * 
     * @param widget The analytics widget to describe
     * @param widgetData Current widget data
     * @param position Widget position in list (for reordering)
     * @param totalWidgets Total number of widgets
     * @param isEnabled Whether widget is enabled/visible
     * @param isLoading Whether widget is currently loading
     * @param onMoveUp Callback for moving widget up in order
     * @param onMoveDown Callback for moving widget down in order
     * @param onToggle Callback for toggling widget visibility
     * @param onRefresh Callback for refreshing widget data
     */
    fun Modifier.widgetAccessibilitySemantics(
        widget: AnalyticsWidget,
        widgetData: WidgetData?,
        position: Int = -1,
        totalWidgets: Int = 0,
        isEnabled: Boolean = true,
        isLoading: Boolean = false,
        onMoveUp: (() -> Unit)? = null,
        onMoveDown: (() -> Unit)? = null,
        onToggle: ((Boolean) -> Unit)? = null,
        onRefresh: (() -> Unit)? = null
    ): Modifier = this.semantics(mergeDescendants = true) {
        // Primary content description
        contentDescription = buildWidgetContentDescription(widget, widgetData, position, totalWidgets, isEnabled, isLoading)
        
        // Semantic role
        role = Role.Button
        
        // State description for dynamic content
        stateDescription = buildWidgetStateDescription(widget, widgetData, isEnabled, isLoading)
        
        // Custom accessibility actions for alternative interactions
        val actions = mutableListOf<CustomAccessibilityAction>()
        
        // Reordering actions (alternative to drag-and-drop)
        if (position >= 0 && totalWidgets > 1) {
            if (position > 0 && onMoveUp != null) {
                actions.add(
                    CustomAccessibilityAction("Move ${widget.displayName} up") {
                        onMoveUp()
                        true
                    }
                )
            }
            if (position < totalWidgets - 1 && onMoveDown != null) {
                actions.add(
                    CustomAccessibilityAction("Move ${widget.displayName} down") {
                        onMoveDown()
                        true
                    }
                )
            }
        }
        
        // Toggle visibility action (for settings)
        onToggle?.let { toggle ->
            actions.add(
                CustomAccessibilityAction(if (isEnabled) "Disable ${widget.displayName}" else "Enable ${widget.displayName}") {
                    toggle(!isEnabled)
                    true
                }
            )
        }
        
        // Refresh action
        onRefresh?.let { refresh ->
            actions.add(
                CustomAccessibilityAction("Refresh ${widget.displayName} data") {
                    refresh()
                    true
                }
            )
        }
        
        if (actions.isNotEmpty()) {
            customActions = actions
        }
        
        // Live region for dynamic data updates
        if (widgetData != null && !isLoading) {
            liveRegion = LiveRegionMode.Polite
        }
        
        // Disabled state
        if (!isEnabled) {
            disabled()
        }
    }

    /**
     * Accessibility semantics for drag-and-drop grid container.
     */
    fun Modifier.dragDropGridAccessibilitySemantics(
        totalWidgets: Int,
        isDragging: Boolean = false,
        draggedWidgetName: String? = null
    ): Modifier = this.semantics {
        contentDescription = "Analytics dashboard with $totalWidgets widgets. ${if (isDragging) "Currently dragging $draggedWidgetName. " else ""}Use custom actions to reorder widgets."
        role = Role.Button
        
        if (isDragging && draggedWidgetName != null) {
            stateDescription = "Dragging $draggedWidgetName. Release to drop in new position."
            liveRegion = LiveRegionMode.Assertive
        }
    }

    /**
     * Accessibility semantics for widget toggle cards in settings.
     */
    fun Modifier.widgetToggleAccessibilitySemantics(
        widget: AnalyticsWidget,
        isEnabled: Boolean,
        isLoading: Boolean = false,
        canReorder: Boolean = true,
        onToggle: (Boolean) -> Unit,
        onReorder: (() -> Unit)? = null
    ): Modifier = this.semantics(mergeDescendants = true) {
        // Comprehensive content description
        contentDescription = buildToggleCardContentDescription(widget, isEnabled, isLoading, canReorder)
        
        role = Role.Switch
        
        // State for screen readers
        stateDescription = buildToggleCardStateDescription(widget, isEnabled, isLoading)
        
        // Custom actions
        val actions = mutableListOf<CustomAccessibilityAction>()
        
        if (!isLoading) {
            actions.add(
                CustomAccessibilityAction(if (isEnabled) "Disable" else "Enable") {
                    onToggle(!isEnabled)
                    true
                }
            )
        }
        
        if (canReorder && onReorder != null) {
            actions.add(
                CustomAccessibilityAction("Reorder widget") {
                    onReorder()
                    true
                }
            )
        }
        
        if (actions.isNotEmpty()) {
            customActions = actions
        }
        
        // Toggle state
        toggleableState = ToggleableState(isEnabled)
        
        if (isLoading) {
            stateDescription = "Loading toggle state for ${widget.displayName}"
        }
    }

    /**
     * Announces widget reorder operation to screen readers.
     */
    @Composable
    fun announceWidgetReorder(
        widgetName: String,
        fromPosition: Int,
        toPosition: Int,
        totalWidgets: Int
    ) {
        val announcement = "Moved $widgetName from position ${fromPosition + 1} to position ${toPosition + 1} of $totalWidgets"
        
        CommonAccessibilityUtils.announceForAccessibility(
            text = announcement,
            delayMs = 100L
        )
    }

    /**
     * Announces widget toggle state change to screen readers.
     */
    @Composable
    fun announceWidgetToggle(
        widgetName: String,
        isEnabled: Boolean
    ) {
        val announcement = "$widgetName ${if (isEnabled) "enabled" else "disabled"}"
        
        CommonAccessibilityUtils.announceForAccessibility(
            text = announcement,
            delayMs = 100L
        )
    }

    /**
     * Announces widget data refresh to screen readers.
     */
    @Composable
    fun announceWidgetRefresh(
        widgetName: String,
        newValue: String? = null
    ) {
        val announcement = if (newValue != null) {
            "$widgetName refreshed. New value: $newValue"
        } else {
            "$widgetName data refreshed"
        }
        
        CommonAccessibilityUtils.announceForAccessibility(
            text = announcement,
            delayMs = 100L
        )
    }

    /**
     * Announces drag-and-drop operation start to screen readers.
     */
    @Composable
    fun announceDragStart(widgetName: String) {
        CommonAccessibilityUtils.announceForAccessibility(
            text = "Started dragging $widgetName. Move to new position and release.",
            delayMs = 50L
        )
    }

    /**
     * Announces drag-and-drop operation end to screen readers.
     */
    @Composable
    fun announceDragEnd(
        widgetName: String,
        successful: Boolean,
        newPosition: Int? = null,
        totalWidgets: Int = 0
    ) {
        val announcement = if (successful && newPosition != null) {
            "Dropped $widgetName at position ${newPosition + 1} of $totalWidgets"
        } else {
            "Cancelled dragging $widgetName"
        }
        
        CommonAccessibilityUtils.announceForAccessibility(
            text = announcement,
            delayMs = 100L
        )
    }

    /**
     * Focus management for widget navigation.
     */
    @Composable
    fun rememberWidgetFocusRequester(widgetId: String): FocusRequester {
        return remember(widgetId) { FocusRequester() }
    }

    /**
     * Manages focus transition between widgets during navigation.
     */
    @Composable
    fun manageWidgetFocusTransition(
        focusRequester: FocusRequester,
        shouldFocus: Boolean,
        delayMs: Long = 300L
    ) {
        CommonAccessibilityUtils.manageFocusTransition(
            focusRequester = focusRequester,
            condition = shouldFocus,
            delayMs = delayMs
        )
    }

    /**
     * Ensures minimum touch target size for accessibility compliance.
     */
    fun Modifier.ensureAccessibleTouchTarget(): Modifier {
        return this.ensureMinimumTouchTarget(44.dp)
    }

    /**
     * Validates color contrast for widget elements using 5-color system standards.
     */
    fun validateWidgetColorContrast(
        foreground: Color,
        background: Color,
        isLargeText: Boolean = false,
        requireWcagAaa: Boolean = false
    ): Boolean {
        return if (requireWcagAaa) {
            HighContrastColors.meetsWcagAaaStandards(foreground, background, isLargeText)
        } else {
            CommonAccessibilityUtils.meetsContrastRequirements(
                foreground = foreground,
                background = background,
                isLargeText = isLargeText
            )
        }
    }
    
    /**
     * Gets optimal accessible colors from 5-color system for maximum contrast
     */
    fun getOptimalAccessibleColors(isDarkTheme: Boolean): Pair<Color, Color> {
        return HighContrastColors.getBestContrastPair(isDarkTheme)
    }
    
    /**
     * Validates that all 5-color system combinations meet accessibility standards
     */
    fun validate5ColorSystemAccessibility(): Map<String, Float> {
        val results = mutableMapOf<String, Float>()
        
        // Light theme combinations
        results["Dark on Light (light)"] = HighContrastColors.getContrastRatio(LiftrixColorsV2.Dark.BackgroundPrimary, LiftrixColorsV2.Light.BackgroundPrimary)
        results["Text on Light (light)"] = HighContrastColors.getContrastRatio(LiftrixColorsV2.Light.TextPrimary, LiftrixColorsV2.Light.BackgroundPrimary)
        results["Teal on Light (light)"] = HighContrastColors.getContrastRatio(LiftrixColorsV2.Teal, LiftrixColorsV2.Light.BackgroundPrimary)
        results["White on Teal (light)"] = HighContrastColors.getContrastRatio(Color.White, LiftrixColorsV2.Teal)
        results["Dark on Teal Light (light)"] = HighContrastColors.getContrastRatio(LiftrixColorsV2.Dark.BackgroundPrimary, LiftrixColorsV2.TealLight)
        
        // Dark theme combinations
        results["Light on Dark (dark)"] = HighContrastColors.getContrastRatio(LiftrixColorsV2.Light.BackgroundPrimary, LiftrixColorsV2.Dark.BackgroundPrimary)
        results["Light Text on Dark (dark)"] = HighContrastColors.getContrastRatio(LiftrixColorsV2.Dark.TextPrimary, LiftrixColorsV2.Dark.BackgroundPrimary)
        results["Teal Light on Dark (dark)"] = HighContrastColors.getContrastRatio(LiftrixColorsV2.TealLight, LiftrixColorsV2.Dark.BackgroundPrimary)
        results["Teal Light on Secondary (dark)"] = HighContrastColors.getContrastRatio(LiftrixColorsV2.TealLight, LiftrixColorsV2.Dark.BackgroundSecondary)
        results["Teal on Dark (dark)"] = HighContrastColors.getContrastRatio(LiftrixColorsV2.Teal, LiftrixColorsV2.Dark.BackgroundPrimary)
        
        return results
    }

    // Private helper functions for building content descriptions

    private fun buildWidgetContentDescription(
        widget: AnalyticsWidget,
        widgetData: WidgetData?,
        position: Int,
        totalWidgets: Int,
        isEnabled: Boolean,
        isLoading: Boolean
    ): String = buildString {
        append(widget.displayName)
        append(" widget")
        
        if (position >= 0 && totalWidgets > 0) {
            append(". Position ${position + 1} of $totalWidgets")
        }
        
        when {
            isLoading -> append(". Loading data")
            !isEnabled -> append(". Disabled")
            widgetData is MetricWidgetData -> {
                append(". Current value: ${widgetData.primaryValue} ${widgetData.unit}")
                if (widgetData.secondaryValue?.isNotBlank() == true) {
                    append(". ${widgetData.secondaryValue}")
                }
                append(". Trend: ${getTrendDescription(widgetData.trend)}")
            }
            else -> append(". No data available")
        }
        
        append(". ${widget.description}")
    }

    private fun buildWidgetStateDescription(
        widget: AnalyticsWidget,
        widgetData: WidgetData?,
        isEnabled: Boolean,
        isLoading: Boolean
    ): String = when {
        isLoading -> "Loading"
        !isEnabled -> "Disabled"
        widgetData is MetricWidgetData -> {
            buildString {
                append("Enabled")
                append(". Complexity: ${widget.complexity.name.lowercase()}")
                append(". Updates every ${widget.complexity.defaultRefreshIntervalMinutes} minutes")
                widgetData.trend?.let { trend ->
                    append(". ${getTrendDescription(trend)}")
                }
            }
        }
        else -> "Enabled, no data"
    }

    private fun buildToggleCardContentDescription(
        widget: AnalyticsWidget,
        isEnabled: Boolean,
        isLoading: Boolean,
        canReorder: Boolean
    ): String = buildString {
        append("${widget.displayName} widget toggle")
        append(". Currently ${if (isEnabled) "enabled" else "disabled"}")
        
        if (isLoading) {
            append(". Loading")
        }
        
        append(". ${widget.description}")
        append(". Complexity: ${widget.complexity.name.lowercase()}")
        append(". Updates every ${widget.complexity.defaultRefreshIntervalMinutes} minutes")
        
        if (canReorder) {
            append(". Can be reordered")
        }
    }

    private fun buildToggleCardStateDescription(
        widget: AnalyticsWidget,
        isEnabled: Boolean,
        isLoading: Boolean
    ): String = when {
        isLoading -> "Loading toggle state"
        isEnabled -> "Enabled. Tap to disable"
        else -> "Disabled. Tap to enable"
    }

    private fun getTrendDescription(trend: TrendDirection): String = when (trend) {
        TrendDirection.UP -> "trending up"
        TrendDirection.DOWN -> "trending down"
        TrendDirection.STABLE -> "stable"
        TrendDirection.UNKNOWN -> "trend unknown"
    }
}

/**
 * High contrast color system for enhanced accessibility using 5-color palette
 */
object HighContrastColors {
    // Enhanced contrast combinations for accessibility using LiftrixColorsV2
    val HighContrastPrimary = LiftrixColorsV2.Teal         // Same color, higher contrast context
    val HighContrastSecondary = LiftrixColorsV2.TealLight  // Same color, higher contrast context
    val HighContrastBackground = Color.Black               // True black for maximum contrast
    val HighContrastOnBackground = Color.White             // True white for maximum contrast
    val HighContrastSurface = LiftrixColorsV2.Dark.BackgroundPrimary     // Dark surface
    val HighContrastOnSurface = LiftrixColorsV2.Light.BackgroundPrimary  // Light text
    
    /**
     * Gets high contrast color scheme for enhanced accessibility
     */
    fun getHighContrastColorScheme(isDark: Boolean): androidx.compose.material3.ColorScheme {
        return if (isDark) {
            androidx.compose.material3.darkColorScheme(
                primary = HighContrastPrimary,
                onPrimary = Color.White,
                primaryContainer = HighContrastPrimary.copy(alpha = 0.3f),
                onPrimaryContainer = Color.White,
                secondary = HighContrastSecondary,
                onSecondary = Color.Black,
                secondaryContainer = HighContrastSecondary.copy(alpha = 0.3f),
                onSecondaryContainer = Color.White,
                background = HighContrastBackground,
                onBackground = HighContrastOnBackground,
                surface = HighContrastSurface,
                onSurface = HighContrastOnSurface,
                surfaceVariant = HighContrastSurface,
                onSurfaceVariant = HighContrastOnSurface,
                outline = HighContrastPrimary,
                outlineVariant = HighContrastPrimary.copy(alpha = 0.5f)
            )
        } else {
            androidx.compose.material3.lightColorScheme(
                primary = HighContrastPrimary,
                onPrimary = Color.White,
                primaryContainer = HighContrastPrimary.copy(alpha = 0.15f),
                onPrimaryContainer = Color.Black,
                secondary = HighContrastSecondary,
                onSecondary = Color.Black,
                secondaryContainer = HighContrastSecondary.copy(alpha = 0.15f),
                onSecondaryContainer = Color.Black,
                background = Color.White,
                onBackground = Color.Black,
                surface = Color.White,
                onSurface = Color.Black,
                surfaceVariant = Color.White,
                onSurfaceVariant = Color.Black,
                outline = HighContrastPrimary,
                outlineVariant = HighContrastPrimary.copy(alpha = 0.5f)
            )
        }
    }
    
    /**
     * Enhanced contrast ratios for WCAG AAA compliance verification
     */
    fun getContrastRatio(foreground: Color, background: Color): Float {
        val fgLuminance = calculateRelativeLuminance(foreground)
        val bgLuminance = calculateRelativeLuminance(background)
        
        val lighter = maxOf(fgLuminance, bgLuminance)
        val darker = minOf(fgLuminance, bgLuminance)
        
        return (lighter + 0.05f) / (darker + 0.05f)
    }
    
    /**
     * Validates color combinations meet WCAG AAA standards (7:1 ratio)
     */
    fun meetsWcagAaaStandards(foreground: Color, background: Color, isLargeText: Boolean = false): Boolean {
        val contrastRatio = getContrastRatio(foreground, background)
        return if (isLargeText) {
            contrastRatio >= 4.5f  // WCAG AAA for large text
        } else {
            contrastRatio >= 7.0f  // WCAG AAA for normal text
        }
    }
    
    /**
     * Gets the best high contrast color pair from 5-color system
     */
    fun getBestContrastPair(isDarkBackground: Boolean): Pair<Color, Color> {
        return if (isDarkBackground) {
            // For dark backgrounds, use Snow text on Night background (16.8:1 ratio)
            LiftrixColorsV2.Light.BackgroundPrimary to LiftrixColorsV2.Dark.BackgroundPrimary
        } else {
            // For light backgrounds, use Night text on Snow background (16.8:1 ratio)
            LiftrixColorsV2.Dark.BackgroundPrimary to LiftrixColorsV2.Light.BackgroundPrimary
        }
    }
    
    private fun calculateRelativeLuminance(color: Color): Float {
        val r = if (color.red <= 0.03928f) color.red / 12.92f else ((color.red + 0.055f) / 1.055f).pow(2.4f)
        val g = if (color.green <= 0.03928f) color.green / 12.92f else ((color.green + 0.055f) / 1.055f).pow(2.4f)
        val b = if (color.blue <= 0.03928f) color.blue / 12.92f else ((color.blue + 0.055f) / 1.055f).pow(2.4f)
        
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }
}