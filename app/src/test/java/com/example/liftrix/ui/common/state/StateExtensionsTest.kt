package com.example.liftrix.ui.common.state

import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalCoroutinesApi::class)
class StateExtensionsTest {

    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        testScope = TestScope()
    }

    @Test
    fun `asUiState should transform successful LiftrixResult to Success state`() = testScope.runTest {
        val data = "test data"
        val flow = flowOf(Result.success(data))
        
        val uiStateFlow = flow.asUiState(testScope)
        
        assertEquals(UiState.Loading, uiStateFlow.value)
        
        advanceUntilIdle()
        
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Success)
        assertEquals(data, (finalState as UiState.Success).data)
    }

    @Test
    fun `asUiState should transform failed LiftrixResult to Error state`() = testScope.runTest {
        val error = LiftrixError.NetworkError(errorMessage = "Test error")
        val flow = flowOf(Result.failure<String>(error))
        
        val uiStateFlow = flow.asUiState(testScope)
        
        advanceUntilIdle()
        
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Error)
        assertEquals(error, (finalState as UiState.Error).error)
    }

    @Test
    fun `asUiState should map Throwable to LiftrixError when custom mapper provided`() = testScope.runTest {
        val originalError = RuntimeException("Original error")
        val mappedError = LiftrixError.DatabaseError(errorMessage = "Mapped error")
        val flow = flowOf(Result.failure<String>(originalError))
        
        val uiStateFlow = flow.asUiState(
            scope = testScope,
            mapError = { mappedError }
        )
        
        advanceUntilIdle()
        
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Error)
        assertEquals(mappedError, (finalState as UiState.Error).error)
    }

    @Test
    fun `asUiState should handle flow exceptions with catch`() = testScope.runTest {
        val exception = RuntimeException("Flow exception")
        val flow = flow<LiftrixResult<String>> { 
            throw exception 
        }
        
        val uiStateFlow = flow.asUiState(testScope)
        
        advanceUntilIdle()
        
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Error)
        assertTrue((finalState as UiState.Error).error is LiftrixError.UnknownError)
    }

    @Test
    fun `asUiStateWithEmpty should convert empty list to Empty state`() = testScope.runTest {
        val emptyList = emptyList<String>()
        val flow = flowOf(emptyList)
        
        val uiStateFlow = flow.asUiStateWithEmpty(
            scope = testScope,
            emptyMessage = "No items",
            emptyAction = "Add Item"
        )
        
        advanceUntilIdle()
        
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Empty)
        assertEquals("No items", (finalState as UiState.Empty).message)
        assertEquals("Add Item", finalState.actionText)
        assertTrue(finalState.showAction)
    }

    @Test
    fun `asUiStateWithEmpty should convert non-empty list to Success state`() = testScope.runTest {
        val data = listOf("item1", "item2")
        val flow = flowOf(data)
        
        val uiStateFlow = flow.asUiStateWithEmpty(testScope)
        
        advanceUntilIdle()
        
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Success)
        assertEquals(data, (finalState as UiState.Success).data)
    }

    @Test
    fun `asUiStateWithEmpty should use custom empty check`() = testScope.runTest {
        val data = "empty"
        val flow = flowOf(data)
        
        val uiStateFlow = flow.asUiStateWithEmpty(
            scope = testScope,
            isEmptyCheck = { it == "empty" },
            emptyMessage = "Custom empty"
        )
        
        advanceUntilIdle()
        
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Empty)
        assertEquals("Custom empty", (finalState as UiState.Empty).message)
    }

    @Test
    fun `combineWith should combine two Success states correctly`() = testScope.runTest {
        val data1 = "first"
        val data2 = "second"
        val flow1 = MutableStateFlow(UiState.Success(data1))
        val flow2 = MutableStateFlow(UiState.Success(data2))
        
        val combinedFlow = flow1.combineWith(flow2)
        
        val result = combinedFlow.value
        assertTrue(result is UiState.Success)
        assertEquals(Pair(data1, data2), (result as UiState.Success).data)
    }

    @Test
    fun `combineWith should return Loading when either state is loading`() = testScope.runTest {
        val flow1 = MutableStateFlow(UiState.Loading)
        val flow2 = MutableStateFlow(UiState.Success("data"))
        
        val combinedFlow = flow1.combineWith(flow2)
        
        val result = combinedFlow.value
        assertTrue(result is UiState.Loading)
    }

    @Test
    fun `combineWith should return Error when either state has error`() = testScope.runTest {
        val error = LiftrixError.NetworkError()
        val flow1 = MutableStateFlow(UiState.Error<String>(error))
        val flow2 = MutableStateFlow(UiState.Success("data"))
        
        val combinedFlow = flow1.combineWith(flow2)
        
        val result = combinedFlow.value
        assertTrue(result is UiState.Error)
        assertEquals(error, (result as UiState.Error).error)
    }

    @Test
    fun `combineWith should return Empty when either state is empty`() = testScope.runTest {
        val flow1 = MutableStateFlow(UiState.Empty())
        val flow2 = MutableStateFlow(UiState.Success("data"))
        
        val combinedFlow = flow1.combineWith(flow2)
        
        val result = combinedFlow.value
        assertTrue(result is UiState.Empty)
    }

    @Test
    fun `filterData should preserve Success state when predicate matches`() = testScope.runTest {
        val data = listOf("item1", "item2", "item3")
        val flow = MutableStateFlow(UiState.Success(data))
        
        val filteredFlow = flow.filterData(
            predicate = { it.size >= 3 },
            emptyMessage = "Not enough items"
        )
        
        val result = filteredFlow.value
        assertTrue(result is UiState.Success)
        assertEquals(data, (result as UiState.Success).data)
    }

    @Test
    fun `filterData should convert to Empty when predicate fails`() = testScope.runTest {
        val data = listOf("item1")
        val flow = MutableStateFlow(UiState.Success(data))
        
        val filteredFlow = flow.filterData(
            predicate = { it.size >= 3 },
            emptyMessage = "Not enough items"
        )
        
        val result = filteredFlow.value
        assertTrue(result is UiState.Empty)
        assertEquals("Not enough items", (result as UiState.Empty).message)
    }

    @Test
    fun `filterData should preserve non-Success states`() = testScope.runTest {
        val error = LiftrixError.NetworkError()
        val flow = MutableStateFlow(UiState.Error<List<String>>(error))
        
        val filteredFlow = flow.filterData { true }
        
        val result = filteredFlow.value
        assertTrue(result is UiState.Error)
        assertEquals(error, (result as UiState.Error).error)
    }

    @Test
    fun `updateState should update MutableStateFlow correctly`() {
        val initialData = "initial"
        val flow = MutableStateFlow(UiState.Success(initialData))
        
        flow.updateState { 
            UiState.Success("updated")
        }
        
        val result = flow.value
        assertTrue(result is UiState.Success)
        assertEquals("updated", (result as UiState.Success).data)
    }

    @Test
    fun `executeWithUiState should set loading state before execution`() = testScope.runTest {
        val viewModel = TestViewModel()
        val uiStateFlow = MutableStateFlow<UiState<String>>(UiState.Empty())
        
        viewModel.executeWithUiState(
            uiStateFlow = uiStateFlow,
            useCase = { 
                // Simulate delay to verify loading state
                kotlinx.coroutines.delay(100)
                Result.success("data")
            }
        )
        
        // Should be loading immediately
        assertTrue(uiStateFlow.value is UiState.Loading)
        
        advanceUntilIdle()
        
        // Should be success after completion
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Success)
        assertEquals("data", (finalState as UiState.Success).data)
    }

    @Test
    fun `executeWithUiState should preserve data during refresh`() = testScope.runTest {
        val viewModel = TestViewModel()
        val initialData = "initial data"
        val uiStateFlow = MutableStateFlow(UiState.Success(initialData))
        
        viewModel.executeWithUiState(
            uiStateFlow = uiStateFlow,
            useCase = { 
                kotlinx.coroutines.delay(100)
                Result.success("new data")
            },
            preserveData = true
        )
        
        // Should be refreshing with preserved data
        val loadingState = uiStateFlow.value
        assertTrue(loadingState is UiState.Success)
        assertTrue((loadingState as UiState.Success).isRefreshing)
        assertEquals(initialData, loadingState.data)
        
        advanceUntilIdle()
        
        // Should be success with new data
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Success)
        assertFalse((finalState as UiState.Success).isRefreshing)
        assertEquals("new data", finalState.data)
    }

    @Test
    fun `executeWithUiState should handle errors correctly`() = testScope.runTest {
        val viewModel = TestViewModel()
        val error = LiftrixError.NetworkError(errorMessage = "Test error")
        val uiStateFlow = MutableStateFlow<UiState<String>>(UiState.Loading)
        
        viewModel.executeWithUiState(
            uiStateFlow = uiStateFlow,
            useCase = { Result.failure(error) }
        )
        
        advanceUntilIdle()
        
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Error)
        assertEquals(error, (finalState as UiState.Error).error)
    }

    @Test
    fun `executeWithUiState should call success callback`() = testScope.runTest {
        val viewModel = TestViewModel()
        val uiStateFlow = MutableStateFlow<UiState<String>>(UiState.Loading)
        var callbackCalled = false
        var callbackData: String? = null
        
        viewModel.executeWithUiState(
            uiStateFlow = uiStateFlow,
            useCase = { Result.success("test data") },
            onSuccess = { data ->
                callbackCalled = true
                callbackData = data
            }
        )
        
        advanceUntilIdle()
        
        assertTrue(callbackCalled)
        assertEquals("test data", callbackData)
    }

    @Test
    fun `executeWithUiState should call error callback`() = testScope.runTest {
        val viewModel = TestViewModel()
        val error = LiftrixError.ValidationError(field = "field", violations = listOf("error"))
        val uiStateFlow = MutableStateFlow<UiState<String>>(UiState.Loading)
        var callbackCalled = false
        var callbackError: LiftrixError? = null
        
        viewModel.executeWithUiState(
            uiStateFlow = uiStateFlow,
            useCase = { Result.failure(error) },
            onError = { err ->
                callbackCalled = true
                callbackError = err
            }
        )
        
        advanceUntilIdle()
        
        assertTrue(callbackCalled)
        assertEquals(error, callbackError)
    }

    @Test
    fun `executeListWithUiState should convert empty list to Empty state`() = testScope.runTest {
        val viewModel = TestViewModel()
        val uiStateFlow = MutableStateFlow<UiState<List<String>>>(UiState.Loading)
        
        viewModel.executeListWithUiState(
            uiStateFlow = uiStateFlow,
            useCase = { Result.success(emptyList()) },
            emptyMessage = "No items found",
            emptyAction = "Add Item"
        )
        
        advanceUntilIdle()
        
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Empty)
        assertEquals("No items found", (finalState as UiState.Empty).message)
        assertEquals("Add Item", finalState.actionText)
        assertTrue(finalState.showAction)
    }

    @Test
    fun `executeListWithUiState should preserve Success for non-empty list`() = testScope.runTest {
        val viewModel = TestViewModel()
        val data = listOf("item1", "item2")
        val uiStateFlow = MutableStateFlow<UiState<List<String>>>(UiState.Loading)
        
        viewModel.executeListWithUiState(
            uiStateFlow = uiStateFlow,
            useCase = { Result.success(data) }
        )
        
        advanceUntilIdle()
        
        val finalState = uiStateFlow.value
        assertTrue(finalState is UiState.Success)
        assertEquals(data, (finalState as UiState.Success).data)
    }

    @Test
    fun `SearchState should have correct computed properties`() {
        val emptySearchState = SearchState<String>()
        assertFalse(emptySearchState.hasQuery)
        assertFalse(emptySearchState.hasResults)
        assertFalse(emptySearchState.canShowSuggestions)
        
        val searchStateWithQuery = SearchState(
            query = "test query",
            results = UiState.Success(listOf("result1", "result2"))
        )
        assertTrue(searchStateWithQuery.hasQuery)
        assertTrue(searchStateWithQuery.hasResults)
        assertFalse(searchStateWithQuery.canShowSuggestions)
        
        val searchStateWithSuggestions = SearchState(
            suggestions = listOf("suggestion1", "suggestion2")
        )
        assertFalse(searchStateWithSuggestions.hasQuery)
        assertFalse(searchStateWithSuggestions.isSearching)
        assertTrue(searchStateWithSuggestions.canShowSuggestions)
    }

    @Test
    fun `PaginatedState should have correct computed properties`() {
        val initialState = PaginatedState<String>()
        assertFalse(initialState.canLoadMore) // No data yet
        assertEquals(0, initialState.totalItems)
        
        val stateWithData = PaginatedState(
            items = UiState.Success(listOf("item1", "item2")),
            hasMorePages = true,
            isLoadingMore = false
        )
        assertTrue(stateWithData.canLoadMore)
        assertEquals(2, stateWithData.totalItems)
        
        val stateLoadingMore = stateWithData.copy(isLoadingMore = true)
        assertFalse(stateLoadingMore.canLoadMore) // Can't load more while already loading
        
        val stateNoMorePages = stateWithData.copy(hasMorePages = false)
        assertFalse(stateNoMorePages.canLoadMore) // Can't load more when no more pages
    }

    // Test ViewModel class for testing ViewModel extensions
    private class TestViewModel : ViewModel() {
        // ViewModel implementation for testing purposes
    }
}