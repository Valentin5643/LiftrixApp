package com.example.liftrix.ui.common.state

import com.example.liftrix.domain.model.error.LiftrixError
import org.junit.Test
import org.junit.Assert.*

class UiStateTest {

    @Test
    fun `UiState Loading should represent loading state correctly`() {
        val state = UiState.Loading
        
        assertTrue(state.isLoading())
        assertFalse(state.hasData())
        assertFalse(state.isError())
        assertFalse(state.isEmpty())
        assertFalse(state.canRetry())
        assertNull(state.dataOrNull())
        assertNull(state.errorOrNull())
        assertEquals("Loading...", state.getDisplayMessage())
    }

    @Test
    fun `UiState Success should represent success state correctly`() {
        val data = "test data"
        val state = UiState.Success(data)
        
        assertFalse(state.isLoading())
        assertTrue(state.hasData())
        assertFalse(state.isError())
        assertFalse(state.isEmpty())
        assertFalse(state.canRetry())
        assertEquals(data, state.dataOrNull())
        assertNull(state.errorOrNull())
        assertNull(state.getDisplayMessage())
    }

    @Test
    fun `UiState Success with refreshing should indicate loading`() {
        val data = "test data"
        val state = UiState.Success(data, isRefreshing = true)
        
        assertTrue(state.isLoading()) // Should be true due to isRefreshing
        assertTrue(state.hasData())
        assertEquals(data, state.dataOrNull())
        assertEquals("Refreshing...", state.getDisplayMessage())
    }

    @Test
    fun `UiState Error should represent error state correctly`() {
        val error = LiftrixError.NetworkError(isRecoverable = true)
        val state = UiState.Error(error)
        
        assertFalse(state.isLoading())
        assertFalse(state.hasData())
        assertTrue(state.isError())
        assertFalse(state.isEmpty())
        assertTrue(state.canRetry()) // Because error is recoverable
        assertNull(state.dataOrNull())
        assertEquals(error, state.errorOrNull())
        assertEquals(error.message, state.getDisplayMessage())
    }

    @Test
    fun `UiState Error with previousData should have data`() {
        val error = LiftrixError.NetworkError(isRecoverable = false)
        val previousData = "cached data"
        val state = UiState.Error(error, previousData)
        
        assertFalse(state.isLoading())
        assertTrue(state.hasData()) // Should be true due to previousData
        assertTrue(state.isError())
        assertFalse(state.canRetry()) // Because error is not recoverable
        assertEquals(previousData, state.dataOrNull())
        assertEquals(error, state.errorOrNull())
    }

    @Test
    fun `UiState Empty should represent empty state correctly`() {
        val errorMessage = "No items found"
        val actionText = "Add Item"
        val state = UiState.Empty(message, actionText, showAction = true)
        
        assertFalse(state.isLoading())
        assertFalse(state.hasData())
        assertFalse(state.isError())
        assertTrue(state.isEmpty())
        assertFalse(state.canRetry())
        assertNull(state.dataOrNull())
        assertNull(state.errorOrNull())
        assertEquals(message, state.getDisplayMessage())
    }

    @Test
    fun `map should transform Success state data correctly`() {
        val originalData = 42
        val originalState = UiState.Success(originalData)
        
        val mappedState = originalState.map { it.toString() }
        
        assertTrue(mappedState is UiState.Success)
        assertEquals("42", (mappedState as UiState.Success).data)
        assertEquals(originalState.isRefreshing, mappedState.isRefreshing)
        assertEquals(originalState.timestamp, mappedState.timestamp)
    }

    @Test
    fun `map should preserve non-Success states unchanged`() {
        val error = LiftrixError.ValidationError("field", listOf("error"))
        val errorState = UiState.Error<Int>(error)
        val loadingState = UiState.Loading
        val emptyState = UiState.Empty()
        
        val mappedError = errorState.map { it.toString() }
        val mappedLoading = loadingState.map { it.toString() }
        val mappedEmpty = emptyState.map { it.toString() }
        
        assertTrue(mappedError is UiState.Error)
        assertEquals(error, (mappedError as UiState.Error).error)
        
        assertTrue(mappedLoading is UiState.Loading)
        assertTrue(mappedEmpty is UiState.Empty)
    }

    @Test
    fun `map should transform previousData in Error state`() {
        val error = LiftrixError.NetworkError()
        val previousData = 42
        val errorState = UiState.Error(error, previousData)
        
        val mappedState = errorState.map { it.toString() }
        
        assertTrue(mappedState is UiState.Error)
        assertEquals(error, (mappedState as UiState.Error).error)
        assertEquals("42", mappedState.previousData)
    }

    @Test
    fun `mapError should transform error in Error state`() {
        val originalError = LiftrixError.NetworkError(errorMessage = "Original error")
        val newError = LiftrixError.DatabaseError(errorMessage = "Transformed error")
        val errorState = UiState.Error<String>(originalError)
        
        val mappedState = errorState.mapError { newError }
        
        assertTrue(mappedState is UiState.Error)
        assertEquals(newError, (mappedState as UiState.Error).error)
    }

    @Test
    fun `mapError should preserve non-Error states unchanged`() {
        val data = "test data"
        val successState = UiState.Success(data)
        val loadingState = UiState.Loading
        val emptyState = UiState.Empty()
        
        val newError = LiftrixError.DatabaseError()
        val mappedSuccess = successState.mapError { newError }
        val mappedLoading = loadingState.mapError { newError }
        val mappedEmpty = emptyState.mapError { newError }
        
        assertEquals(successState, mappedSuccess)
        assertEquals(loadingState, mappedLoading)
        assertEquals(emptyState, mappedEmpty)
    }

    @Test
    fun `combineUiStates should return Loading when any state is loading`() {
        val loadingState = UiState.Loading
        val successState = UiState.Success("data")
        val emptyState = UiState.Empty()
        
        val combined = combineUiStates(loadingState, successState, emptyState)
        
        assertTrue(combined is UiState.Loading)
    }

    @Test
    fun `combineUiStates should return Error when any state has error`() {
        val error = LiftrixError.NetworkError()
        val errorState = UiState.Error<String>(error)
        val successState = UiState.Success("data")
        
        val combined = combineUiStates(successState, errorState)
        
        assertTrue(combined is UiState.Error)
        assertEquals(error, (combined as UiState.Error).error)
    }

    @Test
    fun `combineUiStates should return Empty when all states are empty`() {
        val emptyState1 = UiState.Empty()
        val emptyState2 = UiState.Empty()
        
        val combined = combineUiStates(emptyState1, emptyState2)
        
        assertTrue(combined is UiState.Empty)
    }

    @Test
    fun `combineUiStates should return Success when all states are successful`() {
        val successState1 = UiState.Success("data1")
        val successState2 = UiState.Success("data2")
        
        val combined = combineUiStates(successState1, successState2)
        
        assertTrue(combined is UiState.Success)
    }

    @Test
    fun `asSuccessState should create Success state correctly`() {
        val data = "test data"
        val state = data.asSuccessState()
        
        assertTrue(state is UiState.Success)
        assertEquals(data, (state as UiState.Success).data)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `asSuccessState with refreshing should set flag correctly`() {
        val data = "test data"
        val state = data.asSuccessState(isRefreshing = true)
        
        assertTrue(state is UiState.Success)
        assertTrue((state as UiState.Success).isRefreshing)
    }

    @Test
    fun `asErrorState should create Error state correctly`() {
        val error = LiftrixError.ValidationError("field", listOf("error"))
        val state = error.asErrorState<String>()
        
        assertTrue(state is UiState.Error)
        assertEquals(error, (state as UiState.Error).error)
        assertNull(state.previousData)
    }

    @Test
    fun `asErrorState with previousData should include data`() {
        val error = LiftrixError.NetworkError()
        val previousData = "cached data"
        val state = error.asErrorState(previousData)
        
        assertTrue(state is UiState.Error)
        assertEquals(error, (state as UiState.Error).error)
        assertEquals(previousData, state.previousData)
    }

    @Test
    fun `emptyUiState should create Empty state correctly`() {
        val errorMessage = "Custom empty message"
        val actionText = "Take Action"
        val state = emptyUiState(message, actionText, showAction = true)
        
        assertTrue(state is UiState.Empty)
        assertEquals(message, (state as UiState.Empty).message)
        assertEquals(actionText, state.actionText)
        assertTrue(state.showAction)
    }

    @Test
    fun `emptyUiState with defaults should use default values`() {
        val state = emptyUiState()
        
        assertTrue(state is UiState.Empty)
        assertEquals("No data available", (state as UiState.Empty).message)
        assertNull(state.actionText)
        assertFalse(state.showAction)
    }

    @Test
    fun `UiState Success timestamp should be set correctly`() {
        val beforeTimestamp = System.currentTimeMillis()
        val state = UiState.Success("data")
        val afterTimestamp = System.currentTimeMillis()
        
        assertTrue(state.timestamp >= beforeTimestamp)
        assertTrue(state.timestamp <= afterTimestamp)
    }

    @Test
    fun `UiState Success with custom timestamp should preserve timestamp`() {
        val customTimestamp = 1234567890L
        val state = UiState.Success("data", timestamp = customTimestamp)
        
        assertEquals(customTimestamp, state.timestamp)
    }
}