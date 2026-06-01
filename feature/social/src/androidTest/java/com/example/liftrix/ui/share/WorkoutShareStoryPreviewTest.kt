package com.example.liftrix.ui.share

import android.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WorkoutShareStoryPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun storyPreviewComposes() {
        composeRule.setContent {
            WorkoutShareStoryPreview(
                stats = stats(),
                template = WorkoutShareTemplateCatalog.defaultTemplate
            )
        }

        composeRule.waitForIdle()
    }

    @Test
    fun exporterCreatesStorySizedNonblankBitmap() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val exporter = WorkoutShareImageExporter(context)
        val bitmap = exporter.renderStoryBitmap(stats(), WorkoutShareTemplateCatalog.defaultTemplate)

        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
        assertTrue(bitmap.getPixel(540, 1700) != Color.TRANSPARENT)

        bitmap.recycle()
    }

    private fun stats(): WorkoutShareStoryStats = WorkoutShareStoryStats(
        workoutName = "Push Day",
        displayDate = "May 31, 2026",
        totalVolume = "10,000 kg",
        exerciseCount = "6",
        duration = "58m",
        prCount = 2,
        prSummary = "2 personal records",
        prLabels = listOf("Bench Press: 125 kg", "Squat: 12 reps")
    )
}
