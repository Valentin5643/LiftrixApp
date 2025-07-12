package com.example.liftrix.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.liftrix.domain.model.WorkoutId
import io.mockk.mockk
import io.mockk.verify
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import timber.log.Timber

/**
 * Comprehensive test suite for performance optimizations
 * Tests stable key generation, composition tracking, and memory optimization helpers
 * 
 * Follows existing test patterns from the codebase using JUnit and Compose testing
 */
class PerformanceOptimizationsTest {
    
    @get:Rule
    val composeTestRule = createComposeRule()
    
    @Test
    fun `rememberStableKey generates consistent keys for same objects`() {
        var key1: String? = null
        var key2: String? = null
        
        composeTestRule.setContent {
            val testObject = "test_string"
            key1 = PerformanceOptimizations.rememberStableKey(testObject)
            key2 = PerformanceOptimizations.rememberStableKey(testObject)
        }
        
        assertNotNull(key1)
        assertNotNull(key2)
        assertEquals(key1, key2)
    }
    
    @Test
    fun `rememberStableKey handles different data types correctly`() {
        var stringKey: String? = null
        var numberKey: String? = null
        var booleanKey: String? = null
        
        composeTestRule.setContent {
            stringKey = PerformanceOptimizations.rememberStableKey("test")
            numberKey = PerformanceOptimizations.rememberStableKey(123)
            booleanKey = PerformanceOptimizations.rememberStableKey(true)
        }
        
        assertEquals("test", stringKey)
        assertEquals("123", numberKey)
        assertEquals("true", booleanKey)
    }
    
    @Test
    fun `rememberStableKey extracts identifier from complex objects`() {
        var workoutIdKey: String? = null
        
        composeTestRule.setContent {
            val workoutId = WorkoutId("workout_123")
            workoutIdKey = PerformanceOptimizations.rememberStableKey(workoutId)
        }
        
        assertNotNull(workoutIdKey)
        assertTrue(workoutIdKey!!.contains("WorkoutId"))
    }
    
    @Test
    fun `trackComposition executes block and returns result`() {
        var result: String? = null
        
        composeTestRule.setContent {
            result = PerformanceMetrics.trackComposition("TestComponent") {
                "test_result"
            }
        }
        
        assertEquals("test_result", result)
    }
    
    @Test
    fun `trackComposition handles recomposition correctly`() {
        var recompositionCount = 0
        
        composeTestRule.setContent {
            var state by remember { mutableStateOf(0) }
            
            PerformanceMetrics.trackComposition("RecompositionTest") {
                recompositionCount++
                state // Access state to trigger recomposition tracking
            }
            
            // Trigger recomposition
            if (recompositionCount == 1) {
                state = 1
            }
        }
        
        // Allow composition to complete
        composeTestRule.waitForIdle()
        
        // Should have recomposed at least once
        assertTrue(recompositionCount >= 1)
    }
    
    @Test
    fun `setTracingEnabled controls tracing behavior`() {
        // Test enabling tracing
        PerformanceMetrics.setTracingEnabled(true)
        
        var executedWithTracingEnabled = false
        composeTestRule.setContent {
            PerformanceMetrics.trackComposition("TracingEnabledTest") {
                executedWithTracingEnabled = true
            }
        }
        
        assertTrue(executedWithTracingEnabled)
        
        // Test disabling tracing
        PerformanceMetrics.setTracingEnabled(false)
        
        var executedWithTracingDisabled = false
        composeTestRule.setContent {
            PerformanceMetrics.trackComposition("TracingDisabledTest") {
                executedWithTracingDisabled = true
            }
        }
        
        assertTrue(executedWithTracingDisabled)
        
        // Reset to enabled for other tests
        PerformanceMetrics.setTracingEnabled(true)
    }
    
    @Test
    fun `rememberStableCallback prevents lambda recreation`() {
        var callback1: (() -> Unit)? = null
        var callback2: (() -> Unit)? = null
        
        composeTestRule.setContent {
            val originalCallback = { /* test callback */ }
            callback1 = MemoryOptimizations.rememberStableCallback(originalCallback)
            callback2 = MemoryOptimizations.rememberStableCallback(originalCallback)
        }
        
        assertNotNull(callback1)
        assertNotNull(callback2)
        // Should be the same reference due to remember()
        assertEquals(callback1, callback2)
    }
    
    @Test
    fun `rememberStableCallbacks creates stable callback wrapper`() {
        var callbacks: MemoryOptimizations.StableCallbacks<String>? = null
        var callbackInvoked = false
        
        composeTestRule.setContent {
            callbacks = MemoryOptimizations.rememberStableCallbacks<String>(
                onItemClick = { callbackInvoked = true },
                onLoadMore = { },
                onRefresh = { }
            )
        }
        
        assertNotNull(callbacks)
        
        // Test callback execution
        callbacks!!.onItemClick("test")
        assertTrue(callbackInvoked)
    }
    
    @Test
    fun `rememberStableUUID generates stable UUID per composition`() {
        var uuid1: String? = null
        var uuid2: String? = null
        
        composeTestRule.setContent {
            uuid1 = rememberStableUUID()
            uuid2 = rememberStableUUID()
        }
        
        assertNotNull(uuid1)
        assertNotNull(uuid2)
        // Should be the same UUID due to remember() behavior in same composition
        assertEquals(uuid1, uuid2)
        
        // Verify UUID format
        assertTrue(uuid1!!.contains("-"))
        assertEquals(36, uuid1!!.length) // Standard UUID length
    }
    
    @Test
    fun `rememberWorkoutKey handles different workout ID types`() {
        var workoutKey1: String? = null
        var workoutKey2: String? = null
        var workoutKey3: String? = null
        
        composeTestRule.setContent {
            workoutKey1 = rememberWorkoutKey("workout_123")
            workoutKey2 = rememberWorkoutKey(null, "fallback")
            workoutKey3 = rememberWorkoutKey(null)
        }
        
        assertEquals("workout_workout_123", workoutKey1)
        assertEquals("workout_fallback", workoutKey2)
        assertNotNull(workoutKey3)
        assertTrue(workoutKey3!!.startsWith("workout_"))
    }
    
    @Test
    fun `StableCallbacks data class maintains stability`() {
        val onItemClick: (String) -> Unit = { }
        val onLoadMore: () -> Unit = { }
        val onRefresh: () -> Unit = { }
        
        val callbacks1 = MemoryOptimizations.StableCallbacks(
            onItemClick = onItemClick,
            onLoadMore = onLoadMore,
            onRefresh = onRefresh
        )
        
        val callbacks2 = MemoryOptimizations.StableCallbacks(
            onItemClick = onItemClick,
            onLoadMore = onLoadMore,
            onRefresh = onRefresh
        )
        
        // Data classes with same parameters should be equal
        assertEquals(callbacks1, callbacks2)
    }
    
    @Test
    fun `rememberStableKey handles objects without identifiers gracefully`() {
        var objectKey: String? = null
        
        composeTestRule.setContent {
            val testObject = object {
                val someProperty = "test"
            }
            objectKey = PerformanceOptimizations.rememberStableKey(testObject)
        }
        
        assertNotNull(objectKey)
        // Should contain class information
        assertTrue(objectKey!!.contains("PerformanceOptimizationsTest"))
    }
    
    @Test
    fun `logCompositionInfo works with different parameters`() {
        // Test that logging doesn't throw exceptions
        PerformanceMetrics.logCompositionInfo("TestComponent", 1)
        PerformanceMetrics.logCompositionInfo("TestComponent", 0)
        PerformanceMetrics.logCompositionInfo("TestComponent", 100)
        
        // Should complete without exceptions
        assertTrue(true)
    }
    
    @Test
    fun `rememberEfficientKey generates stable keys for large lists`() {
        var keys: List<String>? = null
        
        composeTestRule.setContent {
            val items = (1..100).map { "item_$it" }
            keys = items.map { item ->
                rememberEfficientKey(item, maxCacheSize = 50)
            }
        }
        
        assertNotNull(keys)
        assertEquals(100, keys!!.size)
        
        // All keys should be unique
        val uniqueKeys = keys!!.toSet()
        assertEquals(100, uniqueKeys.size)
        
        // Keys should follow expected pattern
        assertTrue(keys!!.all { it.startsWith("item_") })
    }
} 