package com.example.liftrix.ui.progress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyticsWidgetViewModelTest {

    @Test
    fun `unavailable widget is distinct from data and error`() {
        val state = AnalyticsWidgetState().withWidgetUnavailable("strength_progress")

        assertTrue("strength_progress" in state.unavailableWidgets)
        assertFalse(state.widgetData.containsKey("strength_progress"))
        assertFalse(state.widgetErrors.containsKey("strength_progress"))
    }
}
