package com.example.liftrix.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiftrixMacrobenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun authToHomeStartup() = measureStartup {
        waitForAnyText("Home", "Workout", "Progress", "Coach", "Sign In", "Sign in")
    }

    @Test
    fun homeFeed() = measureWarmFlow {
        waitForAnyText("Home", "Workout", "Progress", "Coach")
        clickText("Home")
        device.scrollFeed()
    }

    @Test
    fun workoutStart() = measureWarmFlow {
        waitForAnyText("Home", "Workout", "Progress", "Coach")
        clickText("Workout")
        waitForAnyText("Workout", "Workouts", "Start")
        clickFirstAvailable("Start Workout", "Quick Start", "Start Empty Workout", "New Workout")
        waitForAnyText("Active Workout", "Add Exercise", "Workout")
    }

    @Test
    fun activeWorkoutResume() = measureWarmFlow {
        waitForAnyText("Home", "Workout", "Progress", "Coach")
        clickText("Workout")
        waitForAnyText("Active Workout", "Continue Workout", "Resume Workout", "Workout")
        clickFirstAvailable("Continue Workout", "Resume Workout", "Active Workout")
        waitForAnyText("Active Workout", "Add Exercise", "Workout")
    }

    private fun measureStartup(block: MacrobenchmarkScope.() -> Unit) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric(), FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.COLD,
            iterations = 5,
            setupBlock = {
                pressHome()
            },
            measureBlock = {
                startActivityAndWait()
                block()
            }
        )
    }

    private fun measureWarmFlow(block: MacrobenchmarkScope.() -> Unit) {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
            startupMode = StartupMode.WARM,
            iterations = 5,
            setupBlock = {
                pressHome()
                startActivityAndWait()
            },
            measureBlock = block
        )
    }

    private fun MacrobenchmarkScope.clickFirstAvailable(vararg labels: String): Boolean {
        labels.forEach { label ->
            if (clickText(label)) return true
        }
        return false
    }

    private fun MacrobenchmarkScope.clickText(label: String): Boolean {
        val exact = device.findObject(By.text(label))
        if (exact != null) {
            exact.click()
            device.waitForIdle()
            return true
        }

        val partial = device.findObject(By.textContains(label))
        if (partial != null) {
            partial.click()
            device.waitForIdle()
            return true
        }

        return false
    }

    private fun MacrobenchmarkScope.waitForAnyText(
        vararg labels: String,
        timeoutMs: Long = 10_000L
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (labels.any { label ->
                    device.hasObject(By.text(label)) || device.hasObject(By.textContains(label))
                }
            ) {
                return true
            }
            SystemClock.sleep(250L)
        }
        return false
    }

    private fun UiDevice.scrollFeed() {
        val centerX = displayWidth / 2
        val startY = (displayHeight * 0.78f).toInt()
        val endY = (displayHeight * 0.24f).toInt()
        repeat(2) {
            swipe(centerX, startY, centerX, endY, 24)
            waitForIdle()
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.liftrix.app"
    }
}
