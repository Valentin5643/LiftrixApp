package com.example.liftrix.ui.common.viewmodel

import app.cash.turbine.test
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.event.ViewModelEvent
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Test utilities for ViewModels following the MVI pattern.
 * 
 * Provides standardized testing patterns for:
 * - StateFlow behavior verification
 * - MVI pattern compliance checking
 * - Error handling integration testing
 * - State transition validation
 * - Use case mocking utilities
 * 
 * Usage:
 * ```kotlin
 * @Test
 * fun `test MVI pattern compliance`() = runTest {
 *     viewModel.uiState.test {
 *         // Initial state
 *         val initialState = awaitItem()
 *         assertIs<UiState.Loading>(initialState)
 *         
 *         // Trigger event
 *         viewModel.onEvent(TestEvent.LoadData)
 *         
 *         // Verify state transition
 *         val finalState = awaitItem()
 *         assertIs<UiState.Success>(finalState)
 *     }
 * }
 * ```
 */
object ViewModelTestUtils {
    
    /**
     * Verifies that a state transition follows expected MVI patterns.
     * 
     * @param fromState Expected initial state
     * @param toState Expected final state
     * @param stateFlow StateFlow to observe
     * @param eventTrigger Action that triggers the state change
     */
    suspend fun <T> verifyStateTransition(
        fromState: UiState<T>,
        toState: UiState<T>,
        stateFlow: StateFlow<UiState<T>>,
        eventTrigger: suspend () -> Unit
    ) {
        stateFlow.test {
            // Verify initial state
            val initial = awaitItem()
            assertEquals(fromState, initial)
            
            // Trigger event
            eventTrigger()
            
            // Verify final state
            val final = awaitItem()
            assertEquals(toState, final)
        }
    }
    
    /**
     * Verifies that a ViewModel follows MVI pattern correctly.
     * 
     * Checks:
     * - StateFlow is properly exposed
     * - onEvent method handles events correctly
     * - State updates are atomic and predictable
     * 
     * @param viewModel ViewModel instance to test
     * @param event Event to send to the ViewModel
     * @param expectedStateType Expected state type after event processing
     */
    suspend inline fun <reified S : UiState<*>, E : ViewModelEvent> verifyMVIPattern(
        viewModel: BaseViewModel<S, E>,
        event: E,
        expectedStateType: Class<out S>
    ) {
        viewModel.uiState.test {
            // Skip initial state if needed
            skipItems(1)
            
            // Send event
            viewModel.onEvent(event)
            
            // Verify state type
            val resultState = awaitItem()
            assertTrue(expectedStateType.isInstance(resultState))
        }
    }
    
    /**
     * Asserts that error handling follows the expected pattern.
     * 
     * @param error Expected LiftrixError
     * @param expectedState Expected error state
     */
    fun <T> assertErrorHandling(
        error: LiftrixError,
        expectedState: UiState.Error<T>
    ) {
        assertEquals(error, expectedState.error)
        assertTrue(expectedState is UiState.Error)
    }
    
    /**
     * Creates a mock use case that returns a specific LiftrixResult.
     * 
     * @param result Result to return from the use case
     * @return Mocked use case function
     */
    inline fun <reified T> mockUseCase(result: LiftrixResult<T>): suspend () -> LiftrixResult<T> {
        val mockUseCase: suspend () -> LiftrixResult<T> = mockk()
        coEvery { mockUseCase() } returns result
        return mockUseCase
    }
    
    /**
     * Creates a mock use case that returns a successful result.
     * 
     * @param data Data to return in success result
     * @return Mocked use case function
     */
    inline fun <reified T> mockSuccessUseCase(data: T): suspend () -> LiftrixResult<T> {
        return mockUseCase(Result.success(data))
    }
    
    /**
     * Creates a mock use case that returns a failure result.
     * 
     * @param error Error to return in failure result
     * @return Mocked use case function
     */
    inline fun <reified T> mockFailureUseCase(error: Throwable): suspend () -> LiftrixResult<T> {
        return mockUseCase(Result.failure(error))
    }
    
    /**
     * Creates a mock use case that returns a LiftrixError.
     * 
     * @param liftrixError LiftrixError to return
     * @return Mocked use case function
     */
    inline fun <reified T> mockErrorUseCase(liftrixError: LiftrixError): suspend () -> LiftrixResult<T> {
        return mockUseCase(Result.failure(Exception(liftrixError.message)))
    }
    
    /**
     * Verifies that a StateFlow properly handles loading states.
     * 
     * @param stateFlow StateFlow to test
     * @param loadingTrigger Action that should trigger loading state
     */
    suspend fun <T> verifyLoadingState(
        stateFlow: StateFlow<UiState<T>>,
        loadingTrigger: suspend () -> Unit
    ) {
        stateFlow.test {
            // Trigger loading
            loadingTrigger()
            
            // Verify loading state appears
            val loadingState = awaitItem()
            assertIs<UiState.Loading>(loadingState)
        }
    }
    
    /**
     * Verifies that a StateFlow properly handles success states.
     * 
     * @param stateFlow StateFlow to test
     * @param expectedData Expected data in success state
     * @param successTrigger Action that should trigger success state
     */
    suspend fun <T> verifySuccessState(
        stateFlow: StateFlow<UiState<T>>,
        expectedData: T,
        successTrigger: suspend () -> Unit
    ) {
        stateFlow.test {
            // Trigger success
            successTrigger()
            
            // Verify success state
            val successState = awaitItem()
            assertIs<UiState.Success<T>>(successState)
            assertEquals(expectedData, successState.data)
        }
    }
    
    /**
     * Verifies that a StateFlow properly handles error states.
     * 
     * @param stateFlow StateFlow to test
     * @param expectedError Expected LiftrixError
     * @param errorTrigger Action that should trigger error state
     */
    suspend fun <T> verifyErrorState(
        stateFlow: StateFlow<UiState<T>>,
        expectedError: LiftrixError,
        errorTrigger: suspend () -> Unit
    ) {
        stateFlow.test {
            // Trigger error
            errorTrigger()
            
            // Verify error state
            val errorState = awaitItem()
            assertIs<UiState.Error<T>>(errorState)
            assertEquals(expectedError.message, errorState.error.message)
        }
    }
    
    /**
     * Creates a test observer for StateFlow testing with Turbine.
     * 
     * @param stateFlow StateFlow to observe
     * @return Test observer that can be used to await state changes
     */
    fun <T> StateFlow<T>.testObserver() = this
    
    /**
     * Asserts that a state is of the expected type and has expected properties.
     * 
     * @param state State to check
     * @param expectedType Expected state type
     * @param assertion Additional assertions to perform
     */
    inline fun <reified T : UiState<*>> assertStateType(
        state: UiState<*>,
        expectedType: Class<T>,
        assertion: (T) -> Unit = {}
    ) {
        assertIs<T>(state)
        assertion(state)
    }
    
    /**
     * Verifies that event processing maintains UI consistency.
     * 
     * @param viewModel ViewModel to test
     * @param events Sequence of events to send
     * @param stateValidator Function to validate each state transition
     */
    suspend fun <S : UiState<*>, E : ViewModelEvent> verifyEventSequence(
        viewModel: BaseViewModel<S, E>,
        events: List<E>,
        stateValidator: suspend (S) -> Unit
    ) {
        viewModel.uiState.test {
            events.forEach { event ->
                viewModel.onEvent(event)
                val state = awaitItem()
                stateValidator(state)
            }
        }
    }
    
    /**
     * Verifies that ViewModel state updates use standardized patterns.
     * 
     * @param stateFlow StateFlow to observe
     * @param updateAction Action that triggers state update
     * @param stateCount Expected number of state emissions
     */
    suspend fun <T> verifyStateUpdatePattern(
        stateFlow: StateFlow<UiState<T>>,
        updateAction: suspend () -> Unit,
        stateCount: Int = 2
    ) {
        stateFlow.test {
            updateAction()
            
            // Verify expected number of state emissions
            repeat(stateCount) {
                awaitItem()
            }
        }
    }
    
    /**
     * Creates standardized test data for common UI states.
     */
    object TestData {
        
        fun createLoadingState(): UiState.Loading = UiState.Loading
        
        fun <T> createSuccessState(data: T): UiState.Success<T> = 
            UiState.Success(data)
        
        fun <T> createErrorState(message: String): UiState.Error<T> = 
            UiState.Error(LiftrixError.ValidationError(field = "test_field", violations = listOf(message)))
        
        fun createNetworkError(): LiftrixError = 
            LiftrixError.NetworkError(Exception("Network connection failed"))
        
        fun createValidationError(field: String, violations: List<String>): LiftrixError = 
            LiftrixError.ValidationError(field = field, violations = violations)
        
        fun createAuthError(): LiftrixError = 
            LiftrixError.AuthenticationError(errorMessage = "Authentication required")
    }
}

/**
 * Extension functions for easier testing of MVI patterns.
 */

/**
 * Tests StateFlow with proper cleanup and error handling.
 */
suspend fun <T> StateFlow<T>.testWithCleanup(
    testBlock: suspend app.cash.turbine.ReceiveTurbine<T>.() -> Unit
) = runTest {
    this@testWithCleanup.test(testBlock)
}

/**
 * Asserts that a StateFlow emits the expected states in order.
 */
suspend fun <T> StateFlow<T>.assertEmissions(
    vararg expectedStates: T
) = test {
    expectedStates.forEach { expectedState ->
        val actualState = awaitItem()
        assertEquals(expectedState, actualState)
    }
}

/**
 * Verifies that error recovery works correctly.
 */
suspend fun <T> verifyErrorRecovery(
    stateFlow: StateFlow<UiState<T>>,
    errorTrigger: suspend () -> Unit,
    recoveryTrigger: suspend () -> Unit,
    expectedRecoveryData: T
) {
    stateFlow.test {
        // Trigger error
        errorTrigger()
        val errorState = awaitItem()
        assertIs<UiState.Error<T>>(errorState)
        
        // Trigger recovery
        recoveryTrigger()
        val recoveryState = awaitItem()
        assertIs<UiState.Success<T>>(recoveryState)
        assertEquals(expectedRecoveryData, recoveryState.data)
    }
}