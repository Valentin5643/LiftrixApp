package com.example.liftrix.ui.common.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * Minimal modern ViewModel base class:
 * - StateFlow management
 * - No forced patterns
 * - No executeUseCase
 * - No 14-way error handling
 */
abstract class ModernBaseViewModel<S : Any>(
    initialState: S
) : ViewModel() {

    protected open val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<S> = _uiState.asStateFlow()

    protected fun updateState(transform: (S) -> S) {
        _uiState.update(transform)
    }

    protected fun setState(newState: S) {
        _uiState.value = newState
    }

    protected fun logError(error: Throwable, context: String = "") {
        Timber.e(error, "[$context] Error in ${this::class.simpleName}")
    }
}
