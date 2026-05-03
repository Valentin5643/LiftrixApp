package com.example.liftrix.ui.accessibility

import android.content.Context
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetData

object AccessibilityUtils {
    fun announceForAccessibility(
        text: String,
        delayMs: Long = 100L
    ) {
        // Core UI keeps this as a no-op utility; platform-specific announcements
        // are provided by app-owned accessibility services.
    }

    fun announceToScreenReader(
        context: Context,
        text: String
    ) {
        // See announceForAccessibility.
    }

    fun Modifier.dragDropGridAccessibilitySemantics(
        totalWidgets: Int,
        isDragging: Boolean,
        draggedWidgetName: String?
    ): Modifier = semantics {
        contentDescription = buildString {
            append("Analytics widget grid with $totalWidgets widgets")
            if (isDragging && draggedWidgetName != null) {
                append(", dragging $draggedWidgetName")
            }
        }
    }

    fun Modifier.widgetAccessibilitySemantics(
        widget: AnalyticsWidget,
        widgetData: WidgetData,
        position: Int,
        totalWidgets: Int,
        isEnabled: Boolean,
        isLoading: Boolean,
        onMoveUp: (() -> Unit)?,
        onMoveDown: (() -> Unit)?,
        onRefresh: (() -> Unit)?
    ): Modifier = semantics {
        contentDescription = "${widget.displayName}, position ${position + 1} of $totalWidgets" +
            if (isLoading) ", loading" else ""
        if (!isEnabled) disabled()
        customActions = buildList {
            onMoveUp?.let { action ->
                add(CustomAccessibilityAction("Move up") { action(); true })
            }
            onMoveDown?.let { action ->
                add(CustomAccessibilityAction("Move down") { action(); true })
            }
            onRefresh?.let { action ->
                add(CustomAccessibilityAction("Refresh") { action(); true })
            }
        }
    }

    fun Modifier.widgetToggleAccessibilitySemantics(
        widget: AnalyticsWidget,
        isEnabled: Boolean,
        isLoading: Boolean = false,
        canReorder: Boolean = true,
        onToggle: (Boolean) -> Unit,
        onReorder: (() -> Unit)? = null
    ): Modifier = semantics(mergeDescendants = true) {
        contentDescription = buildString {
            append("${widget.displayName} widget toggle")
            append(". Currently ${if (isEnabled) "enabled" else "disabled"}")
            if (isLoading) append(". Loading")
            append(". ${widget.description}")
            append(". Complexity: ${widget.complexity.name.lowercase()}")
            append(". Updates every ${widget.complexity.defaultRefreshIntervalMinutes} minutes")
            if (canReorder) append(". Can be reordered")
        }
        role = Role.Switch
        stateDescription = when {
            isLoading -> "Loading toggle state"
            isEnabled -> "Enabled. Tap to disable"
            else -> "Disabled. Tap to enable"
        }
        toggleableState = ToggleableState(isEnabled)
        customActions = buildList {
            if (!isLoading) {
                add(CustomAccessibilityAction(if (isEnabled) "Disable" else "Enable") {
                    onToggle(!isEnabled)
                    true
                })
            }
            if (canReorder && onReorder != null) {
                add(CustomAccessibilityAction("Reorder widget") {
                    onReorder()
                    true
                })
            }
        }
    }
}

object TalkBackAnnouncements {
    fun widgetMoved(widgetName: String, position: Int): String =
        "$widgetName moved to position ${position + 1}"
}
