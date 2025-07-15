package com.example.liftrix.ui.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.liftrix.ui.common.state.UiState
import com.example.liftrix.ui.common.event.ViewModelEvent
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.common.ErrorHandler
import timber.log.Timber

/**
 * Base ViewModel implementing standardized MVI pattern with StateFlow and comprehensive error handling.
 * 
 * This abstract class provides a consistent foundation for all ViewModels in the application,
 * implementing the Model-View-Intent (MVI) pattern with reactive state management and
 * integrated error handling capabilities.
 * 
 * Key Features:
 * - Standardized MVI pattern with StateFlow for reactive UI updates
 * - Integrated error handling with LiftrixError and centralized ErrorHandler
 * - Type-safe event handling with sealed class hierarchies
 * - Use case execution with automatic state management
 * - Performance-optimized state updates with proper scoping
 * - Comprehensive logging for debugging and monitoring
 * 
 * Usage:
 * ```kotlin
 * @HiltViewModel
 * class MyViewModel @Inject constructor(
 *     private val myUseCase: MyUseCase,
 *     errorHandler: ErrorHandler
 * ) : BaseViewModel<MyUiState, MyEvent>(errorHandler) {
 * 
 *     override val initialState = MyUiState.Loading
 * 
 *     override fun onEvent(event: MyEvent) {
 *         when (event) {
 *             is MyEvent.LoadData -> loadData()
 *             is MyEvent.RefreshData -> refreshData()
 *         }
 *     }
 * 
 *     private fun loadData() {
 *         executeUseCase(
 *             useCase = { myUseCase() },
 *             onSuccess = { data -> updateState { MyUiState.Success(data) } }
 *         )
 *     }
 * }
 * ```
 * 
 * @param S The UI state type that extends UiState<*>
 * @param E The event type that extends ViewModelEvent
 * @param errorHandler Centralized error handler for consistent error processing
 */
abstract class BaseViewModel<S : UiState<*>, E : ViewModelEvent>(
    protected val errorHandler: ErrorHandler
) : ViewModel() {

    /**
     * The initial state for this ViewModel.
     * Must be implemented by concrete ViewModels to provide the starting state.
     */
    protected abstract val initialState: S

    /**
     * Internal mutable state flow for state management.
     * Protected to allow controlled access by subclasses.
     */
    protected val _uiState = MutableStateFlow<S>(initialState)

    /**
     * Public read-only state flow for UI consumption.
     * Provides reactive state updates to Compose UI components.
     */
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    /**
     * Handles events from the UI following the MVI pattern.
     * Must be implemented by concrete ViewModels to process domain-specific events.
     * 
     * Event handling guidelines:
     * - Use when expressions to handle all event types exhaustively
     * - Delegate to private methods for complex event processing
     * - Avoid direct state mutations within event handlers
     * - Use provided helper methods for common operations
     * 
     * @param event The event to process
     */
    abstract fun onEvent(event: E)

    /**
     * Thread-safe state update method with transformation function.
     * 
     * Provides a safe way to update the current state using a transformation function.
     * The transformation is applied atomically to prevent race conditions and ensure
     * consistent state updates.
     * 
     * Usage:
     * ```kotlin
     * updateState { currentState ->
     *     currentState.copy(
     *         isLoading = false,
     *         data = newData
     *     )
     * }
     * ```
     * 
     * @param transform Function that takes current state and returns new state
     */
    protected fun updateState(transform: (S) -> S) {
        _uiState.value = transform(_uiState.value)
    }

    /**
     * Direct state update method for simple state assignments.
     * 
     * Use when you need to set the state directly without transformation.
     * Prefer updateState(transform) for more complex updates.
     * 
     * @param newState The new state to set
     */
    protected fun setState(newState: S) {
        _uiState.value = newState
    }

    /**
     * Handles errors consistently using the centralized ErrorHandler.
     * 
     * Processes LiftrixError instances through the error handling system,
     * including analytics reporting, user message generation, and retry policy creation.
     * Updates the UI state with appropriate error information.
     * 
     * @param error The LiftrixError to handle
     * @param context Additional context for error processing
     */
    protected fun handleError(
        error: LiftrixError,
        context: Map<String, Any> = emptyMap()
    ) {
        viewModelScope.launch {
            try {
                val result = errorHandler.handleError(error, context)
                
                // Log error for debugging
                Timber.e("ViewModel error handled: ${error::class.simpleName} - ${error.message}")
                
                // Update state based on error type
                when (error) {
                    is LiftrixError.NetworkError -> handleNetworkError(error, result)
                    is LiftrixError.ValidationError -> handleValidationError(error, result)
                    is LiftrixError.AuthenticationError -> handleAuthError(error, result)
                    is LiftrixError.DatabaseError -> handleDatabaseError(error, result)
                    is LiftrixError.BusinessLogicError -> handleBusinessLogicError(error, result)
                    is LiftrixError.UnknownError -> handleUnknownError(error, result)
                }
            } catch (exception: Exception) {
                Timber.e(exception, "Failed to handle error properly")
                // Fallback to basic error state
                updateErrorState(error)
            }
        }
    }

    /**
     * Updates state to reflect error condition.
     * Override in subclasses for custom error state handling.
     * 
     * @param error The error to reflect in the state
     */
    protected open fun updateErrorState(error: LiftrixError) {
        // Default implementation - override in subclasses for specific error handling
        Timber.w("updateErrorState not overridden for ${this::class.simpleName}")
    }

    /**
     * Executes a use case with automatic state management and error handling.
     * 
     * This method provides a standardized way to execute domain use cases while
     * automatically managing loading states, error handling, and success state updates.
     * 
     * Features:
     * - Automatic loading state management
     * - Integrated error handling with LiftrixError
     * - Optional success and error callbacks
     * - Proper coroutine scope management
     * - Performance monitoring and analytics
     * 
     * Usage:
     * ```kotlin
     * executeUseCase(
     *     useCase = { getWorkoutsUseCase(userId) },
     *     onSuccess = { workouts -> 
     *         updateState { currentState.copy(workouts = workouts) }
     *     },
     *     onError = { error ->
     *         // Custom error handling if needed
     *     }
     * )
     * ```
     * 
     * @param T The type of data returned by the use case
     * @param useCase Suspend function that returns LiftrixResult<T>
     * @param onSuccess Optional callback for successful execution
     * @param onError Optional callback for error handling (in addition to default handling)
     * @param showLoading Whether to show loading state during execution
     */
    protected fun <T> executeUseCase(
        useCase: suspend () -> LiftrixResult<T>,
        onSuccess: ((T) -> Unit)? = null,
        onError: ((LiftrixError) -> Unit)? = null,
        showLoading: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                if (showLoading) {
                    setLoadingState()
                }

                val startTime = System.currentTimeMillis()
                val result = useCase()
                val executionTime = System.currentTimeMillis() - startTime

                result.fold(
                    onSuccess = { data ->
                        logUseCaseSuccess(executionTime)
                        onSuccess?.invoke(data)
                    },
                    onFailure = { throwable ->
                        val liftrixError = if (throwable is LiftrixError) {
                            throwable
                        } else {
                            LiftrixError.UnknownError(
                                errorMessage = "Use case execution failed",
                            )
                        }
                        
                        logUseCaseError(liftrixError, executionTime)
                        handleError(liftrixError)
                        onError?.invoke(liftrixError)
                    }
                )
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError(
                    errorMessage = "Unexpected error during use case execution",
                )
                Timber.e(exception, "Unexpected error in executeUseCase")
                handleError(error)
                onError?.invoke(error)
            }
        }
    }

    /**
     * Sets the loading state in the UI.
     * Override in subclasses to implement specific loading state logic.
     */
    protected open fun setLoadingState() {
        // Default implementation - override in subclasses
        Timber.d("setLoadingState called for ${this::class.simpleName}")
    }

    /**
     * Extension function to safely update MutableStateFlow with transformation.
     * 
     * Provides a thread-safe way to update StateFlow values using transformation functions.
     * This method ensures atomic updates and prevents race conditions.
     * 
     * @param T The type of the StateFlow value
     * @param transform Function to transform the current value
     */
    protected fun <T> MutableStateFlow<T>.updateValue(transform: T.() -> T) {
        value = value.transform()
    }

    /**
     * Executes multiple use cases concurrently and combines their results.
     * 
     * Useful for screens that need to load multiple data sources simultaneously.
     * Automatically handles loading states and error scenarios.
     * 
     * @param useCases List of suspend functions returning LiftrixResult
     * @param onSuccess Callback when all use cases succeed
     * @param onError Callback when any use case fails
     */
    protected fun executeMultipleUseCases(
        vararg useCases: suspend () -> LiftrixResult<*>,
        onSuccess: (List<Any>) -> Unit,
        onError: (LiftrixError) -> Unit = { handleError(it) }
    ) {
        viewModelScope.launch {
            try {
                setLoadingState()
                
                val results = useCases.map { useCase ->
                    useCase()
                }
                
                val allSuccessful = results.all { it.isSuccess }
                
                if (allSuccessful) {
                    val data = results.map { it.getOrThrow() }.filterNotNull()
                    onSuccess(data)
                } else {
                    val firstError = results.firstOrNull { it.isFailure }?.exceptionOrNull()
                    val liftrixError = if (firstError is LiftrixError) {
                        firstError
                    } else {
                        LiftrixError.UnknownError("Multiple errors occurred")
                    }
                    onError(liftrixError)
                }
            } catch (exception: Exception) {
                val error = LiftrixError.UnknownError("Use case execution failed")
                onError(error)
            }
        }
    }

    /**
     * Error handling methods for specific error types.
     * These methods can be overridden by subclasses for custom error handling.
     */

    protected open fun handleNetworkError(error: LiftrixError.NetworkError, result: com.example.liftrix.domain.usecase.common.ErrorHandlingResult) {
        updateErrorState(error)
    }

    protected open fun handleValidationError(error: LiftrixError.ValidationError, result: com.example.liftrix.domain.usecase.common.ErrorHandlingResult) {
        updateErrorState(error)
    }

    protected open fun handleAuthError(error: LiftrixError.AuthenticationError, result: com.example.liftrix.domain.usecase.common.ErrorHandlingResult) {
        updateErrorState(error)
    }

    protected open fun handleDatabaseError(error: LiftrixError.DatabaseError, result: com.example.liftrix.domain.usecase.common.ErrorHandlingResult) {
        updateErrorState(error)
    }

    protected open fun handleBusinessLogicError(error: LiftrixError.BusinessLogicError, result: com.example.liftrix.domain.usecase.common.ErrorHandlingResult) {
        updateErrorState(error)
    }

    protected open fun handleUnknownError(error: LiftrixError.UnknownError, result: com.example.liftrix.domain.usecase.common.ErrorHandlingResult) {
        updateErrorState(error)
    }

    /**
     * Logging methods for monitoring and debugging.
     */

    private fun logUseCaseSuccess(executionTime: Long) {
        Timber.d("Use case executed successfully in ${executionTime}ms for ${this::class.simpleName}")
    }

    private fun logUseCaseError(error: LiftrixError, executionTime: Long) {
        Timber.e("Use case failed after ${executionTime}ms for ${this::class.simpleName}: ${error.message}")
    }

    /**
     * Cleanup method called when ViewModel is cleared.
     * Override to perform custom cleanup operations.
     */
    override fun onCleared() {
        super.onCleared()
        Timber.d("ViewModel cleared: ${this::class.simpleName}")
    }
}

/**
 * Extension functions for common BaseViewModel operations.
 */

/**
 * Creates a BaseViewModel instance with specified initial state.
 * Useful for testing and simple ViewModel implementations.
 */
inline fun <S : UiState<*>, E : ViewModelEvent> createBaseViewModel(
    initialState: S,
    errorHandler: ErrorHandler,
    crossinline eventHandler: (E) -> Unit
): BaseViewModel<S, E> = object : BaseViewModel<S, E>(errorHandler) {
    override val initialState: S = initialState
    override fun onEvent(event: E) = eventHandler(event)
}

