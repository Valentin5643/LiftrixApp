package com.example.liftrix.ui.accessibility

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.example.liftrix.domain.model.analytics.AnalyticsWidget
import com.example.liftrix.domain.model.analytics.WidgetData

object AccessibilityUtils {
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
}

object TalkBackAnnouncements {
    fun widgetMoved(widgetName: String, position: Int): String =
        "$widgetName moved to position ${position + 1}"
}
