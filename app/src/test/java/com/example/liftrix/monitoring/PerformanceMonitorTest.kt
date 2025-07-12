package com.example.liftrix.monitoring

import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

class PerformanceMonitorTest {

    private lateinit var mockFirebasePerformance: FirebasePerformance
    private lateinit var mockTrace: Trace
    private lateinit var performanceMonitor: PerformanceMonitor

    @Before
    fun setUp() {
        mockFirebasePerformance = mockk()
        mockTrace = mockk(relaxed = true)
        performanceMonitor = PerformanceMonitor(mockFirebasePerformance)
    }

    @Test
    fun `trackScreenTransition should create trace with correct attributes`() = runTest {
        // Arrange
        val fromScreen = "HomeScreen"
        val toScreen = "WorkoutScreen"
        every { mockFirebasePerformance.newTrace("screen_transition") } returns mockTrace

        // Act
        val result = performanceMonitor.trackScreenTransition(fromScreen, toScreen)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        
        verify(exactly = 1) {
            mockFirebasePerformance.newTrace("screen_transition")
            mockTrace.putAttribute("from_screen", fromScreen)
            mockTrace.putAttribute("to_screen", toScreen)
            mockTrace.start()
            mockTrace.stop()
        }
    }

    @Test
    fun `trackScreenTransition should handle exceptions gracefully`() = runTest {
        // Arrange
        val fromScreen = "HomeScreen"
        val toScreen = "WorkoutScreen"
        val exception = RuntimeException("Firebase error")
        every { mockFirebasePerformance.newTrace("screen_transition") } throws exception

        // Act
        val result = performanceMonitor.trackScreenTransition(fromScreen, toScreen)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertEquals("Expected same exception", exception, result.exceptionOrNull())
    }

    @Test
    fun `trackAnimationPerformance should create trace with duration metric`() = runTest {
        // Arrange
        val animationType = "fade_transition"
        val duration = 150L
        every { mockFirebasePerformance.newTrace("animation_performance") } returns mockTrace

        // Act
        val result = performanceMonitor.trackAnimationPerformance(animationType, duration)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        
        verify(exactly = 1) {
            mockFirebasePerformance.newTrace("animation_performance")
            mockTrace.putAttribute("animation_type", animationType)
            mockTrace.putMetric("duration_ms", duration)
            mockTrace.start()
            mockTrace.stop()
        }
    }

    @Test
    fun `trackAnimationPerformance should handle exceptions gracefully`() = runTest {
        // Arrange
        val animationType = "slide_transition"
        val duration = 200L
        val exception = RuntimeException("Performance monitoring error")
        every { mockFirebasePerformance.newTrace("animation_performance") } throws exception

        // Act
        val result = performanceMonitor.trackAnimationPerformance(animationType, duration)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertEquals("Expected same exception", exception, result.exceptionOrNull())
    }

    @Test
    fun `trackUserInteraction should create trace with interaction details`() = runTest {
        // Arrange
        val interactionType = "tap"
        val componentType = "button"
        val responseTime = 50L
        every { mockFirebasePerformance.newTrace("user_interaction") } returns mockTrace

        // Act
        val result = performanceMonitor.trackUserInteraction(interactionType, componentType, responseTime)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        
        verify(exactly = 1) {
            mockFirebasePerformance.newTrace("user_interaction")
            mockTrace.putAttribute("interaction_type", interactionType)
            mockTrace.putAttribute("component_type", componentType)
            mockTrace.putMetric("response_time_ms", responseTime)
            mockTrace.start()
            mockTrace.stop()
        }
    }

    @Test
    fun `trackUserInteraction should handle exceptions gracefully`() = runTest {
        // Arrange
        val interactionType = "swipe"
        val componentType = "card"
        val responseTime = 75L
        val exception = RuntimeException("Interaction tracking error")
        every { mockFirebasePerformance.newTrace("user_interaction") } throws exception

        // Act
        val result = performanceMonitor.trackUserInteraction(interactionType, componentType, responseTime)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertEquals("Expected same exception", exception, result.exceptionOrNull())
    }

    @Test
    fun `startCustomTrace should return trace when successful`() {
        // Arrange
        val traceName = "custom_workflow"
        every { mockFirebasePerformance.newTrace(traceName) } returns mockTrace

        // Act
        val result = performanceMonitor.startCustomTrace(traceName)

        // Assert
        assertNotNull("Expected non-null trace", result)
        assertEquals("Expected same trace object", mockTrace, result)
        
        verify(exactly = 1) {
            mockFirebasePerformance.newTrace(traceName)
            mockTrace.start()
        }
    }

    @Test
    fun `startCustomTrace should return null when exception occurs`() {
        // Arrange
        val traceName = "failing_trace"
        val exception = RuntimeException("Trace creation failed")
        every { mockFirebasePerformance.newTrace(traceName) } throws exception

        // Act
        val result = performanceMonitor.startCustomTrace(traceName)

        // Assert
        assertNull("Expected null result", result)
    }

    @Test
    fun `stopCustomTrace should add attributes and metrics before stopping`() {
        // Arrange
        val attributes = mapOf("screen" to "home", "user_type" to "premium")
        val metrics = mapOf("item_count" to 10L, "load_time" to 500L)

        // Act
        val result = performanceMonitor.stopCustomTrace(mockTrace, attributes, metrics)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        
        verify(exactly = 1) {
            mockTrace.putAttribute("screen", "home")
            mockTrace.putAttribute("user_type", "premium")
            mockTrace.putMetric("item_count", 10L)
            mockTrace.putMetric("load_time", 500L)
            mockTrace.stop()
        }
    }

    @Test
    fun `stopCustomTrace should handle exceptions gracefully`() {
        // Arrange
        val exception = RuntimeException("Stop trace failed")
        every { mockTrace.stop() } throws exception

        // Act
        val result = performanceMonitor.stopCustomTrace(mockTrace)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertEquals("Expected same exception", exception, result.exceptionOrNull())
    }

    @Test
    fun `trackAppStartup should create startup trace with correct attributes`() = runTest {
        // Arrange
        val startupTime = 1200L
        val coldStart = true
        every { mockFirebasePerformance.newTrace("app_startup") } returns mockTrace

        // Act
        val result = performanceMonitor.trackAppStartup(startupTime, coldStart)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        
        verify(exactly = 1) {
            mockFirebasePerformance.newTrace("app_startup")
            mockTrace.putAttribute("startup_type", "cold")
            mockTrace.putMetric("startup_time_ms", startupTime)
            mockTrace.start()
            mockTrace.stop()
        }
    }

    @Test
    fun `trackAppStartup should use warm start type for non-cold starts`() = runTest {
        // Arrange
        val startupTime = 600L
        val coldStart = false
        every { mockFirebasePerformance.newTrace("app_startup") } returns mockTrace

        // Act
        val result = performanceMonitor.trackAppStartup(startupTime, coldStart)

        // Assert
        assertTrue("Expected successful result", result.isSuccess)
        
        verify(exactly = 1) {
            mockTrace.putAttribute("startup_type", "warm")
        }
    }

    @Test
    fun `trackAppStartup should handle exceptions gracefully`() = runTest {
        // Arrange
        val startupTime = 800L
        val coldStart = true
        val exception = RuntimeException("Startup tracking failed")
        every { mockFirebasePerformance.newTrace("app_startup") } throws exception

        // Act
        val result = performanceMonitor.trackAppStartup(startupTime, coldStart)

        // Assert
        assertTrue("Expected failure result", result.isFailure)
        assertEquals("Expected same exception", exception, result.exceptionOrNull())
    }
} 