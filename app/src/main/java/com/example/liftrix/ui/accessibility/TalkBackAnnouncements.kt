package com.example.liftrix.ui.accessibility

import androidx.compose.runtime.Composable
import com.example.liftrix.domain.model.analytics.WidgetData
import com.example.liftrix.ui.common.AccessibilityUtils as CommonAccessibilityUtils

/**
 * TalkBack announcements for analytics widget interactions.
 * 
 * Provides comprehensive screen reader announcements for widget operations,
 * ensuring accessible feedback for all dashboard interactions.
 */
object TalkBackAnnouncements {

    /**
     * Announces when drag operation starts.
     */
    @Composable
    fun announceDragStart(
        widgetName: String,
        currentPosition: Int,
        totalWidgets: Int
    ) {
        val announcement = "Started dragging $widgetName from position ${currentPosition + 1} of $totalWidgets"
        CommonAccessibilityUtils.announceForAccessibility(announcement)
    }

    /**
     * Announces widget reorder completion.
     */
    @Composable
    fun announceWidgetReorder(
        widgetName: String,
        fromPosition: Int,
        toPosition: Int,
        totalWidgets: Int
    ) {
        val announcement = "Moved $widgetName from position ${fromPosition + 1} to position ${toPosition + 1} of $totalWidgets"
        CommonAccessibilityUtils.announceForAccessibility(announcement, delayMs = 100L)
    }

    /**
     * Announces widget data refresh.
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
        CommonAccessibilityUtils.announceForAccessibility(announcement, delayMs = 100L)
    }

    /**
     * Announces widget visibility toggle.
     */
    @Composable
    fun announceWidgetToggle(
        widgetName: String,
        isEnabled: Boolean
    ) {
        val announcement = "$widgetName ${if (isEnabled) "enabled" else "disabled"}"
        CommonAccessibilityUtils.announceForAccessibility(announcement, delayMs = 100L)
    }

    /**
     * Announces section toggle (expand/collapse).
     */
    @Composable
    fun announceSectionToggle(
        sectionName: String,
        isExpanded: Boolean,
        widgetCount: Int
    ) {
        val announcement = "$sectionName section ${if (isExpanded) "expanded" else "collapsed"}. Contains $widgetCount widgets."
        CommonAccessibilityUtils.announceForAccessibility(announcement, delayMs = 100L)
    }

    /**
     * Announces drag end with result.
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
        CommonAccessibilityUtils.announceForAccessibility(announcement, delayMs = 100L)
    }
}