package com.example.liftrix.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import timber.log.Timber

/**
 * Generic loading extension functions for ViewModels to reduce boilerplate code.
 * 
 * These extensions provide a consistent pattern for:
 * - Authentication checking
 * - Loading state management
 * - Error handling with logging
 * - Flow collection with exception handling
 */

/**
 * Generic data loading function for ViewModels with authentication and error handling.
 * 
 * @param dataLoader Suspend function that returns a Flow of data for the given userId
 * @param updateLoading Function to update the loading state in UI state
 * @param updateData Function to update the data in UI state when successful
 * @param updateError Function to update the error state in UI state
 * @param dataType Description of the data being loaded (for logging and error messages)
 */
inline fun <T, S> ViewModel.loadDataWithAuth(
    authRepository: AuthRepository,
    uiState: MutableStateFlow<S>,
    crossinline dataLoader: suspend (String, LocalDate, LocalDate) -> Flow<T>,
    crossinline updateLoading: S.(Boolean) -> S,
    crossinline updateData: S.(T) -> S,
    crossinline updateError: S.(String?) -> S,
    dataType: String,
    startDate: LocalDate,
    endDate: LocalDate
) {
    viewModelScope.launch {
        try {
            // Check authentication
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                uiState.value = uiState.value.updateLoading(false).updateError("User not authenticated")
                return@launch
            }

            // Set loading state
            uiState.value = uiState.value.updateLoading(true)

            // Load data with error handling
            dataLoader(userId, startDate, endDate)
                .catch { exception ->
                    Timber.e(exception, "Failed to load $dataType for user: $userId")
                    uiState.value = uiState.value
                        .updateLoading(false)
                        .updateError("Failed to load $dataType: ${exception.message}")
                }
                .collect { data ->
                    uiState.value = uiState.value
                        .updateData(data)
                        .updateLoading(false)
                        .updateError(null)
                }
        } catch (exception: Exception) {
            Timber.e(exception, "Unexpected error loading $dataType")
            uiState.value = uiState.value
                .updateLoading(false)
                .updateError("Unexpected error: ${exception.message}")
        }
    }
}

/**
 * Simplified loading function for cases without date range parameters.
 */
inline fun <T, S> ViewModel.loadDataWithAuth(
    authRepository: AuthRepository,
    uiState: MutableStateFlow<S>,
    crossinline dataLoader: suspend (String) -> Flow<T>,
    crossinline updateLoading: S.(Boolean) -> S,
    crossinline updateData: S.(T) -> S,
    crossinline updateError: S.(String?) -> S,
    dataType: String
) {
    viewModelScope.launch {
        try {
            val userId = authRepository.getCurrentUserId()
            if (userId == null) {
                uiState.value = uiState.value.updateLoading(false).updateError("User not authenticated")
                return@launch
            }

            uiState.value = uiState.value.updateLoading(true)

            dataLoader(userId)
                .catch { exception ->
                    Timber.e(exception, "Failed to load $dataType for user: $userId")
                    uiState.value = uiState.value
                        .updateLoading(false)
                        .updateError("Failed to load $dataType: ${exception.message}")
                }
                .collect { data ->
                    uiState.value = uiState.value
                        .updateData(data)
                        .updateLoading(false)
                        .updateError(null)
                }
        } catch (exception: Exception) {
            Timber.e(exception, "Unexpected error loading $dataType")
            uiState.value = uiState.value
                .updateLoading(false)
                .updateError("Unexpected error: ${exception.message}")
        }
    }
}

/**
 * Extension for MutableStateFlow to update state functionally.
 */
inline fun <T> MutableStateFlow<T>.updateState(crossinline transform: T.() -> T) {
    value = value.transform()
}

/**
 * Chain multiple loading state updates for cleaner fluent syntax.
 */
inline fun <T> T.applyStateUpdates(crossinline updates: T.() -> T): T = updates()