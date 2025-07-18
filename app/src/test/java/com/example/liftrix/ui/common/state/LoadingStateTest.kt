package com.example.liftrix.ui.common.state

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests for LoadingState class.
 * 
 * Tests cover all functionality including:
 * - Basic loading state operations
 * - Multiple operation management
 * - Query methods
 * - Extension functions
 * - Utility functions
 * - Thread safety considerations
 * - Performance characteristics
 */
class LoadingStateTest {

    private lateinit var emptyLoadingState: LoadingState
    private lateinit var singleOperationState: LoadingState
    private lateinit var multipleOperationsState: LoadingState

    @BeforeEach
    fun setup() {
        emptyLoadingState = LoadingState()
        singleOperationState = LoadingState().withOperation("test_operation")
        multipleOperationsState = LoadingState()
            .withOperation("operation1")
            .withOperation("operation2")
            .withOperation("operation3")
    }

    @Nested
    @DisplayName("Basic Loading State Operations")
    inner class BasicOperations {

        @Test
        fun `given empty loading state, when checking isLoading, then returns false`() {
            assertFalse(emptyLoadingState.isLoading())
        }

        @Test
        fun `given loading state with operations, when checking isLoading, then returns true`() {
            assertTrue(singleOperationState.isLoading())
            assertTrue(multipleOperationsState.isLoading())
        }

        @Test
        fun `given loading state, when checking specific operation, then returns correct status`() {
            assertTrue(singleOperationState.isLoading("test_operation"))
            assertFalse(singleOperationState.isLoading("nonexistent_operation"))
            
            assertTrue(multipleOperationsState.isLoading("operation1"))
            assertTrue(multipleOperationsState.isLoading("operation2"))
            assertFalse(multipleOperationsState.isLoading("nonexistent_operation"))
        }

        @Test
        fun `given loading state, when adding operation, then returns new state with operation`() {
            val newState = emptyLoadingState.withOperation("new_operation")
            
            // Original state unchanged
            assertFalse(emptyLoadingState.isLoading())
            
            // New state has operation
            assertTrue(newState.isLoading())
            assertTrue(newState.isLoading("new_operation"))
        }

        @Test
        fun `given loading state with operation, when removing operation, then returns new state without operation`() {
            val newState = singleOperationState.withoutOperation("test_operation")
            
            // Original state unchanged
            assertTrue(singleOperationState.isLoading())
            
            // New state doesn't have operation
            assertFalse(newState.isLoading())
            assertFalse(newState.isLoading("test_operation"))
        }

        @Test
        fun `given loading state, when removing nonexistent operation, then returns same state`() {
            val newState = singleOperationState.withoutOperation("nonexistent_operation")
            
            // State should be functionally equivalent but not necessarily same instance
            assertTrue(newState.isLoading())
            assertTrue(newState.isLoading("test_operation"))
            assertEquals(singleOperationState.getActiveOperations(), newState.getActiveOperations())
        }
    }

    @Nested
    @DisplayName("Multiple Operations Management")
    inner class MultipleOperations {

        @Test
        fun `given loading state, when adding multiple operations, then all operations are active`() {
            val newState = emptyLoadingState.withOperations("op1", "op2", "op3")
            
            assertTrue(newState.isLoading())
            assertTrue(newState.isLoading("op1"))
            assertTrue(newState.isLoading("op2"))
            assertTrue(newState.isLoading("op3"))
            assertEquals(3, newState.getOperationCount())
        }

        @Test
        fun `given loading state, when removing multiple operations, then all operations are removed`() {
            val newState = multipleOperationsState.withoutOperations("operation1", "operation2")
            
            assertTrue(newState.isLoading())
            assertFalse(newState.isLoading("operation1"))
            assertFalse(newState.isLoading("operation2"))
            assertTrue(newState.isLoading("operation3"))
            assertEquals(1, newState.getOperationCount())
        }

        @Test
        fun `given loading state, when adding duplicate operation, then no duplicates exist`() {
            val state1 = emptyLoadingState.withOperation("duplicate")
            val state2 = state1.withOperation("duplicate")
            
            assertEquals(1, state2.getOperationCount())
            assertTrue(state2.isLoading("duplicate"))
        }

        @Test
        fun `given loading state, when combining with another state, then all operations are present`() {
            val state1 = LoadingState().withOperations("op1", "op2")
            val state2 = LoadingState().withOperations("op3", "op4")
            val combined = state1.combine(state2)
            
            assertEquals(4, combined.getOperationCount())
            assertTrue(combined.isLoading("op1"))
            assertTrue(combined.isLoading("op2"))
            assertTrue(combined.isLoading("op3"))
            assertTrue(combined.isLoading("op4"))
        }

        @Test
        fun `given loading state, when combining with overlapping operations, then no duplicates exist`() {
            val state1 = LoadingState().withOperations("op1", "op2", "common")
            val state2 = LoadingState().withOperations("op3", "common")
            val combined = state1.combine(state2)
            
            assertEquals(4, combined.getOperationCount())
            assertTrue(combined.isLoading("op1"))
            assertTrue(combined.isLoading("op2"))
            assertTrue(combined.isLoading("op3"))
            assertTrue(combined.isLoading("common"))
        }
    }

    @Nested
    @DisplayName("Query Methods")
    inner class QueryMethods {

        @Test
        fun `given loading state, when checking isEmpty, then returns correct status`() {
            assertTrue(emptyLoadingState.isEmpty())
            assertFalse(singleOperationState.isEmpty())
            assertFalse(multipleOperationsState.isEmpty())
        }

        @Test
        fun `given loading state, when checking isNotEmpty, then returns correct status`() {
            assertFalse(emptyLoadingState.isNotEmpty())
            assertTrue(singleOperationState.isNotEmpty())
            assertTrue(multipleOperationsState.isNotEmpty())
        }

        @Test
        fun `given loading state, when getting operation count, then returns correct count`() {
            assertEquals(0, emptyLoadingState.getOperationCount())
            assertEquals(1, singleOperationState.getOperationCount())
            assertEquals(3, multipleOperationsState.getOperationCount())
        }

        @Test
        fun `given loading state, when getting active operations, then returns correct set`() {
            assertEquals(emptySet(), emptyLoadingState.getActiveOperations())
            assertEquals(setOf("test_operation"), singleOperationState.getActiveOperations())
            assertEquals(setOf("operation1", "operation2", "operation3"), multipleOperationsState.getActiveOperations())
        }

        @Test
        fun `given loading state, when clearing, then returns empty state`() {
            val clearedState = multipleOperationsState.clear()
            
            assertTrue(clearedState.isEmpty())
            assertEquals(0, clearedState.getOperationCount())
            assertEquals(emptySet(), clearedState.getActiveOperations())
        }
    }

    @Nested
    @DisplayName("Functional Operations")
    inner class FunctionalOperations {

        @Test
        fun `given loading state, when filtering operations, then returns filtered state`() {
            val filteredState = multipleOperationsState.filter { it.startsWith("operation") }
            
            assertEquals(3, filteredState.getOperationCount())
            assertTrue(filteredState.isLoading("operation1"))
            assertTrue(filteredState.isLoading("operation2"))
            assertTrue(filteredState.isLoading("operation3"))
        }

        @Test
        fun `given loading state, when filtering with no matches, then returns empty state`() {
            val filteredState = multipleOperationsState.filter { it.startsWith("nonexistent") }
            
            assertTrue(filteredState.isEmpty())
            assertEquals(0, filteredState.getOperationCount())
        }

        @Test
        fun `given loading state, when using any predicate, then returns correct result`() {
            assertTrue(multipleOperationsState.any { it == "operation1" })
            assertFalse(multipleOperationsState.any { it == "nonexistent" })
            assertFalse(emptyLoadingState.any { true })
        }

        @Test
        fun `given loading state, when using all predicate, then returns correct result`() {
            assertTrue(multipleOperationsState.all { it.startsWith("operation") })
            assertFalse(multipleOperationsState.all { it == "operation1" })
            assertTrue(emptyLoadingState.all { false }) // Vacuously true
        }

        @Test
        fun `given loading state, when using none predicate, then returns correct result`() {
            assertTrue(multipleOperationsState.none { it == "nonexistent" })
            assertFalse(multipleOperationsState.none { it == "operation1" })
            assertTrue(emptyLoadingState.none { true })
        }

        @Test
        fun `given loading state, when transforming operations, then returns transformed state`() {
            val transformedState = multipleOperationsState.transform { operations ->
                operations.map { "${it}_transformed" }.toSet()
            }
            
            assertEquals(3, transformedState.getOperationCount())
            assertTrue(transformedState.isLoading("operation1_transformed"))
            assertTrue(transformedState.isLoading("operation2_transformed"))
            assertTrue(transformedState.isLoading("operation3_transformed"))
        }
    }

    @Nested
    @DisplayName("Extension Functions")
    inner class ExtensionFunctions {

        @Test
        fun `given operation string, when converting to loading state, then returns state with operation`() {
            val loadingState = "test_operation".asLoadingState()
            
            assertTrue(loadingState.isLoading())
            assertTrue(loadingState.isLoading("test_operation"))
            assertEquals(1, loadingState.getOperationCount())
        }

        @Test
        fun `given operation collection, when converting to loading state, then returns state with all operations`() {
            val operations = listOf("op1", "op2", "op3")
            val loadingState = operations.asLoadingState()
            
            assertTrue(loadingState.isLoading())
            assertEquals(3, loadingState.getOperationCount())
            assertTrue(loadingState.isLoading("op1"))
            assertTrue(loadingState.isLoading("op2"))
            assertTrue(loadingState.isLoading("op3"))
        }

        @Test
        fun `given empty collection, when converting to loading state, then returns empty state`() {
            val loadingState = emptyList<String>().asLoadingState()
            
            assertTrue(loadingState.isEmpty())
            assertEquals(0, loadingState.getOperationCount())
        }
    }

    @Nested
    @DisplayName("Factory Functions")
    inner class FactoryFunctions {

        @Test
        fun `given single operation, when creating loading state, then returns state with operation`() {
            val loadingState = loadingStateOf("test_operation")
            
            assertTrue(loadingState.isLoading())
            assertTrue(loadingState.isLoading("test_operation"))
            assertEquals(1, loadingState.getOperationCount())
        }

        @Test
        fun `given multiple operations, when creating loading state, then returns state with all operations`() {
            val loadingState = loadingStateOf("op1", "op2", "op3")
            
            assertTrue(loadingState.isLoading())
            assertEquals(3, loadingState.getOperationCount())
            assertTrue(loadingState.isLoading("op1"))
            assertTrue(loadingState.isLoading("op2"))
            assertTrue(loadingState.isLoading("op3"))
        }

        @Test
        fun `given collection of operations, when creating loading state, then returns state with all operations`() {
            val operations = setOf("op1", "op2", "op3")
            val loadingState = loadingStateOf(operations)
            
            assertTrue(loadingState.isLoading())
            assertEquals(3, loadingState.getOperationCount())
            assertTrue(loadingState.isLoading("op1"))
            assertTrue(loadingState.isLoading("op2"))
            assertTrue(loadingState.isLoading("op3"))
        }

        @Test
        fun `when creating empty loading state, then returns empty state`() {
            val loadingState = emptyLoadingState()
            
            assertTrue(loadingState.isEmpty())
            assertEquals(0, loadingState.getOperationCount())
        }
    }

    @Nested
    @DisplayName("Utility Functions")
    inner class UtilityFunctions {

        @Test
        fun `when creating chart loading state, then returns state with chart operations`() {
            val loadingState = chartLoadingState()
            
            assertTrue(loadingState.isLoading())
            assertTrue(loadingState.isLoading(LoadingOperations.CHARTS))
            assertTrue(loadingState.isLoading(LoadingOperations.WIDGETS))
            assertTrue(loadingState.isLoading(LoadingOperations.SUMMARY))
        }

        @Test
        fun `when creating refresh loading state, then returns state with refresh operations`() {
            val loadingState = refreshLoadingState()
            
            assertTrue(loadingState.isLoading())
            assertTrue(loadingState.isLoading(LoadingOperations.REFRESH))
            assertTrue(loadingState.isLoading(LoadingOperations.SYNC))
        }

        @Test
        fun `when creating initial loading state, then returns state with initial operations`() {
            val loadingState = initialLoadingState()
            
            assertTrue(loadingState.isLoading())
            assertTrue(loadingState.isLoading(LoadingOperations.INITIAL_LOAD))
            assertTrue(loadingState.isLoading(LoadingOperations.PREFERENCES))
        }
    }

    @Nested
    @DisplayName("String Representation")
    inner class StringRepresentation {

        @Test
        fun `given empty loading state, when converting to string, then returns empty message`() {
            val stringRepresentation = emptyLoadingState.toString()
            
            assertEquals("LoadingState(no active operations)", stringRepresentation)
        }

        @Test
        fun `given loading state with operations, when converting to string, then returns operations list`() {
            val stringRepresentation = singleOperationState.toString()
            
            assertTrue(stringRepresentation.contains("test_operation"))
            assertTrue(stringRepresentation.startsWith("LoadingState(active operations:"))
        }

        @Test
        fun `given loading state with multiple operations, when converting to string, then returns sorted operations`() {
            val stringRepresentation = multipleOperationsState.toString()
            
            assertTrue(stringRepresentation.contains("operation1"))
            assertTrue(stringRepresentation.contains("operation2"))
            assertTrue(stringRepresentation.contains("operation3"))
            assertTrue(stringRepresentation.startsWith("LoadingState(active operations:"))
        }
    }

    @Nested
    @DisplayName("Immutability and Thread Safety")
    inner class ImmutabilityAndThreadSafety {

        @Test
        fun `given loading state, when performing operations, then original state is unchanged`() {
            val originalOperations = singleOperationState.getActiveOperations()
            
            // Perform various operations
            val newState1 = singleOperationState.withOperation("new_operation")
            val newState2 = singleOperationState.withoutOperation("test_operation")
            val newState3 = singleOperationState.withOperations("op1", "op2")
            val newState4 = singleOperationState.clear()
            
            // Original state should be unchanged
            assertEquals(originalOperations, singleOperationState.getActiveOperations())
            assertTrue(singleOperationState.isLoading("test_operation"))
            
            // New states should be different
            assertFalse(newState1.getActiveOperations() == originalOperations)
            assertFalse(newState2.getActiveOperations() == originalOperations)
            assertFalse(newState3.getActiveOperations() == originalOperations)
            assertFalse(newState4.getActiveOperations() == originalOperations)
        }

        @Test
        fun `given loading state, when getting active operations, then returns defensive copy`() {
            val operations1 = singleOperationState.getActiveOperations()
            val operations2 = singleOperationState.getActiveOperations()
            
            // Should be equal but not the same instance
            assertEquals(operations1, operations2)
            assertTrue(operations1 !== operations2) // Different instances
        }

        @Test
        fun `given loading state, when concurrent modifications, then state remains consistent`() {
            val initialState = LoadingState()
            
            // Simulate concurrent operations
            val results = (1..100).map { i ->
                Thread {
                    initialState.withOperation("operation_$i")
                }.apply { start() }
            }.map { thread ->
                thread.join()
                initialState.withOperation("test_concurrent")
            }
            
            // All results should be consistent
            results.forEach { state ->
                assertTrue(state.isLoading("test_concurrent"))
                assertEquals(1, state.getOperationCount())
            }
        }
    }

    @Nested
    @DisplayName("Performance Characteristics")
    inner class PerformanceCharacteristics {

        @Test
        fun `given loading state, when performing operations on large sets, then performance is acceptable`() {
            val largeOperationSet = (1..1000).map { "operation_$it" }.toSet()
            val loadingState = loadingStateOf(largeOperationSet)
            
            // Performance test - should complete quickly
            val startTime = System.currentTimeMillis()
            
            // Perform operations
            val newState = loadingState.withOperation("new_operation")
            val filteredState = newState.filter { it.startsWith("operation_") }
            val combinedState = filteredState.combine(LoadingState().withOperation("combined"))
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            // Should complete within reasonable time (100ms is generous)
            assertTrue(duration < 100, "Operations took too long: ${duration}ms")
            
            // Verify correctness
            assertEquals(1000, filteredState.getOperationCount())
            assertEquals(1001, combinedState.getOperationCount())
        }

        @Test
        fun `given loading state, when checking operations frequently, then performance is acceptable`() {
            val loadingState = loadingStateOf("test_operation")
            
            val startTime = System.currentTimeMillis()
            
            // Perform many checks
            repeat(10000) {
                loadingState.isLoading("test_operation")
                loadingState.isLoading("nonexistent_operation")
                loadingState.isLoading()
            }
            
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime
            
            // Should complete within reasonable time
            assertTrue(duration < 100, "Checks took too long: ${duration}ms")
        }
    }

    @Nested
    @DisplayName("Loading Operations Constants")
    inner class LoadingOperationsConstants {

        @Test
        fun `loading operations constants have expected values`() {
            assertEquals("charts", LoadingOperations.CHARTS)
            assertEquals("widgets", LoadingOperations.WIDGETS)
            assertEquals("summary", LoadingOperations.SUMMARY)
            assertEquals("calories", LoadingOperations.CALORIES)
            assertEquals("preferences", LoadingOperations.PREFERENCES)
            assertEquals("export", LoadingOperations.EXPORT)
            assertEquals("sync", LoadingOperations.SYNC)
            assertEquals("refresh", LoadingOperations.REFRESH)
            assertEquals("initial_load", LoadingOperations.INITIAL_LOAD)
            assertEquals("background_refresh", LoadingOperations.BACKGROUND_REFRESH)
        }
    }
}