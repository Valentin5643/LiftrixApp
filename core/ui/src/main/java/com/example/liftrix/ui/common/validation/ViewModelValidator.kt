package com.example.liftrix.ui.common.validation

import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

object ViewModelValidator {
    fun <T> validateStateFlow(
        stateFlow: StateFlow<T>?,
        viewModelName: String
    ): Boolean = try {
        if (stateFlow == null) {
            Timber.e("$viewModelName: StateFlow is null - check ViewModel initialization")
            false
        } else {
            stateFlow.value
            Timber.d("$viewModelName: StateFlow validation passed")
            true
        }
    } catch (e: Exception) {
        Timber.e(e, "$viewModelName: StateFlow validation failed")
        false
    }

    fun validateViewModels(
        vararg validationPairs: Pair<String, StateFlow<*>?>
    ): ValidationResult {
        val failedViewModels = validationPairs
            .filterNot { (name, stateFlow) -> validateStateFlow(stateFlow, name) }
            .map { it.first }
        val totalValidated = validationPairs.size
        return ValidationResult(
            isValid = failedViewModels.isEmpty(),
            message = if (failedViewModels.isEmpty()) {
                "All $totalValidated ViewModels validated successfully"
            } else {
                "Failed ViewModels: ${failedViewModels.joinToString(", ")}"
            },
            failedViewModels = failedViewModels,
            totalValidated = totalValidated
        )
    }

    data class ValidationResult(
        val isValid: Boolean,
        val message: String,
        val failedViewModels: List<String>,
        val totalValidated: Int
    ) {
        fun logResult() {
            if (isValid) Timber.i("ViewModel Validation: $message")
            else Timber.e("ViewModel Validation Failed: $message")
        }
    }
}
