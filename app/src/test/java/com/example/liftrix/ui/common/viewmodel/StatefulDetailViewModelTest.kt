package com.example.liftrix.ui.common.viewmodel

import androidx.lifecycle.SavedStateHandle
import com.example.liftrix.domain.usecase.common.ErrorHandler
import com.example.liftrix.ui.common.event.ViewModelEvent
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for StatefulDetailViewModel state persistence functionality
 */
class StatefulDetailViewModelTest {

    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var errorHandler: ErrorHandler
    private lateinit var testViewModel: TestStatefulDetailViewModel

    @Before
    fun setUp() {
        savedStateHandle = SavedStateHandle()
        errorHandler = mockk(relaxed = true)
        testViewModel = TestStatefulDetailViewModel(savedStateHandle, errorHandler)
    }

    @Test
    fun `savedStateFlow persists value correctly`() = runTest {
        // Given - a state flow with initial value
        val testKey = "test_key"
        val initialValue = "initial"
        val newValue = "updated"
        
        val stateFlow = testViewModel.createTestStateFlow(testKey, initialValue)
        
        // When - updating the saved state
        testViewModel.updateTestState(testKey, newValue)
        
        // Then - the state flow should reflect the new value
        assertEquals(newValue, stateFlow.value)
        assertEquals(newValue, savedStateHandle.get<String>(testKey))
    }

    @Test
    fun `state persists through SavedStateHandle recreation`() = runTest {
        // Given - a ViewModel with persisted state
        val testKey = "persist_test"
        val testValue = "persisted_value"
        
        testViewModel.updateTestState(testKey, testValue)
        
        // When - creating a new ViewModel instance with the same SavedStateHandle
        val newViewModel = TestStatefulDetailViewModel(savedStateHandle, errorHandler)
        val restoredStateFlow = newViewModel.createTestStateFlow(testKey, "default")
        
        // Then - the state should be restored
        assertEquals(testValue, restoredStateFlow.value)
    }

    @Test
    fun `clearAllSavedState removes all persisted data`() = runTest {
        // Given - ViewModel with multiple persisted states
        testViewModel.updateTestState("key1", "value1")
        testViewModel.updateTestState("key2", "value2")
        testViewModel.updateTestState("key3", "value3")
        
        // Verify data is present
        assertEquals("value1", savedStateHandle.get<String>("key1"))
        assertEquals("value2", savedStateHandle.get<String>("key2"))
        assertEquals("value3", savedStateHandle.get<String>("key3"))
        
        // When - clearing all saved state
        testViewModel.clearAllSavedStatePublic()
        
        // Then - all data should be cleared
        assertEquals(null, savedStateHandle.get<String>("key1"))
        assertEquals(null, savedStateHandle.get<String>("key2"))
        assertEquals(null, savedStateHandle.get<String>("key3"))
    }

    @Test
    fun `onUserSignOut clears all state`() = runTest {
        // Given - ViewModel with persisted state
        testViewModel.updateTestState("user_key", "user_data")
        assertEquals("user_data", savedStateHandle.get<String>("user_key"))
        
        // When - user signs out
        testViewModel.onUserSignOut()
        
        // Then - all state should be cleared
        assertEquals(null, savedStateHandle.get<String>("user_key"))
    }

    @Test
    fun `getViewModelId returns correct identifier`() {
        // When - getting ViewModel ID
        val viewModelId = testViewModel.getViewModelId()
        
        // Then - it should return the class name
        assertEquals("TestStatefulDetailViewModel", viewModelId)
    }

    /**
     * Test implementation of StatefulDetailViewModel for testing purposes
     */
    private class TestStatefulDetailViewModel(
        savedStateHandle: SavedStateHandle,
        errorHandler: ErrorHandler
    ) : StatefulDetailViewModel<String, TestEvent>(savedStateHandle, errorHandler) {

        override val _uiState = MutableStateFlow("initial")

        override fun handleEvent(event: TestEvent) {
            // No-op for testing
        }

        fun createTestStateFlow(key: String, initialValue: String) = savedStateFlow(
            key = key,
            initialValue = initialValue
        )

        fun updateTestState(key: String, value: String) {
            updateSavedState(key, value)
        }

        fun clearAllSavedStatePublic() {
            clearAllSavedState()
        }
    }

    /**
     * Test event for testing purposes
     */
    sealed class TestEvent : ViewModelEvent {
        object TestAction : TestEvent()
    }
}