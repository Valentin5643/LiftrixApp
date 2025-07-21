package com.example.liftrix.ui.accessibility

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
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
     * Validates color contrast for widget elements.
     */
    fun validateWidgetColorContrast(
        foreground: Color,
        background: Color,
        isLargeText: Boolean = false
    ): Boolean {
        return CommonAccessibilityUtils.meetsContrastRequirements(
            foreground = foreground,
            background = background,
            isLargeText = isLargeText
        )
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