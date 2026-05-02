package com.example.liftrix.ui.anomaly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.AnomalyValue
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.UserAnomalyAction
import com.example.liftrix.domain.model.WorkoutAnomaly
import com.example.liftrix.domain.repository.AuthRepository
import com.example.liftrix.domain.progress.ProgressAnomalyPort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for managing workout anomaly detection and user interactions
 */
@HiltViewModel
class WorkoutAnomalyViewModel @Inject constructor(
    private val detectAnomaliesUseCase: ProgressAnomalyPort,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _currentAnomaly = MutableStateFlow<WorkoutAnomaly?>(null)
    val currentAnomaly: StateFlow<WorkoutAnomaly?> = _currentAnomaly.asStateFlow()

    private val _showAnomalyDialog = MutableStateFlow(false)
    val showAnomalyDialog: StateFlow<Boolean> = _showAnomalyDialog.asStateFlow()

    private val _anomalyCheckInProgress = MutableStateFlow(false)
    val anomalyCheckInProgress: StateFlow<Boolean> = _anomalyCheckInProgress.asStateFlow()

    /**
     * Checks for weight anomalies during workout logging
     */
    fun checkWeightAnomaly(
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentWeight: Double,
        previousWeight: Double? = null
    ) {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser() ?: return@launch
            
            _anomalyCheckInProgress.value = true
            
            detectAnomaliesUseCase.detectWeightAnomaly(
                userId = currentUser.uid,
                sessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                currentWeight = currentWeight,
                previousWeight = previousWeight
            ).onSuccess { anomaly ->
                if (anomaly?.isHighConfidence() == true) {
                    _currentAnomaly.value = anomaly
                    _showAnomalyDialog.value = true
                } else {
                    // Low confidence or no anomaly - allow the value through
                    anomaly?.let { 
                        recordAnomalyForLearning(it, UserAnomalyAction.DISMISSED, null)
                    }
                }
            }.onFailure { error ->
                Timber.w("Failed to detect weight anomaly: ${error.message}")
                // Don't block user input on detection failure
            }
            
            _anomalyCheckInProgress.value = false
        }
    }

    /**
     * Checks for reps anomalies during workout logging
     */
    fun checkRepsAnomaly(
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentReps: Int,
        previousReps: Int? = null
    ) {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser() ?: return@launch
            
            _anomalyCheckInProgress.value = true
            
            detectAnomaliesUseCase.detectRepsAnomaly(
                userId = currentUser.uid,
                sessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                currentReps = currentReps,
                previousReps = previousReps
            ).onSuccess { anomaly ->
                if (anomaly?.isHighConfidence() == true) {
                    _currentAnomaly.value = anomaly
                    _showAnomalyDialog.value = true
                } else {
                    anomaly?.let { 
                        recordAnomalyForLearning(it, UserAnomalyAction.DISMISSED, null)
                    }
                }
            }.onFailure { error ->
                Timber.w("Failed to detect reps anomaly: ${error.message}")
            }
            
            _anomalyCheckInProgress.value = false
        }
    }

    /**
     * Checks for duration anomalies during workout logging
     */
    fun checkDurationAnomaly(
        sessionId: String,
        exerciseId: ExerciseId,
        exerciseName: String,
        currentDuration: Long,
        previousDuration: Long? = null
    ) {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser() ?: return@launch
            
            _anomalyCheckInProgress.value = true
            
            detectAnomaliesUseCase.detectDurationAnomaly(
                userId = currentUser.uid,
                sessionId = sessionId,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                currentDuration = currentDuration,
                previousDuration = previousDuration
            ).onSuccess { anomaly ->
                if (anomaly?.isHighConfidence() == true) {
                    _currentAnomaly.value = anomaly
                    _showAnomalyDialog.value = true
                } else {
                    anomaly?.let { 
                        recordAnomalyForLearning(it, UserAnomalyAction.DISMISSED, null)
                    }
                }
            }.onFailure { error ->
                Timber.w("Failed to detect duration anomaly: ${error.message}")
            }
            
            _anomalyCheckInProgress.value = false
        }
    }

    /**
     * Resolves an anomaly with user action
     */
    fun resolveAnomaly(
        userAction: UserAnomalyAction,
        correctedValue: AnomalyValue?,
        onAnomalyResolved: (UserAnomalyAction, AnomalyValue?) -> Unit
    ) {
        viewModelScope.launch {
            val anomaly = _currentAnomaly.value ?: return@launch
            
            detectAnomaliesUseCase.resolveAnomaly(
                anomalyId = anomaly.id,
                userAction = userAction,
                correctedValue = correctedValue
            ).onSuccess { resolvedAnomaly ->
                Timber.d("Anomaly resolved: ${resolvedAnomaly.id} with action: $userAction")
                
                // Update exercise history with the final value
                val finalValue = correctedValue ?: anomaly.currentValue
                updateExerciseHistory(anomaly.exerciseId, finalValue)
                
                // Call the callback to update the UI with the resolved value
                onAnomalyResolved(userAction, correctedValue)
                
                // Clear the anomaly state
                dismissAnomalyDialog()
                
            }.onFailure { error ->
                Timber.e("Failed to resolve anomaly: ${error.message}")
                // Still dismiss the dialog and allow the action
                onAnomalyResolved(userAction, correctedValue)
                dismissAnomalyDialog()
            }
        }
    }

    /**
     * Dismisses the current anomaly dialog
     */
    fun dismissAnomalyDialog() {
        _showAnomalyDialog.value = false
        _currentAnomaly.value = null
    }

    /**
     * Updates exercise history with performance data for learning
     */
    fun updateExerciseHistory(
        exerciseId: ExerciseId,
        weight: Double? = null,
        reps: Int? = null,
        duration: Long? = null
    ) {
        viewModelScope.launch {
            val currentUser = authRepository.getCurrentUser() ?: return@launch
            
            detectAnomaliesUseCase.updateExerciseHistory(
                userId = currentUser.uid,
                exerciseId = exerciseId,
                weight = weight,
                reps = reps,
                duration = duration
            ).onFailure { error ->
                Timber.w("Failed to update exercise history: ${error.message}")
            }
        }
    }

    /**
     * Records an anomaly for learning purposes (low confidence anomalies)
     */
    private fun recordAnomalyForLearning(
        anomaly: WorkoutAnomaly,
        userAction: UserAnomalyAction,
        correctedValue: AnomalyValue?
    ) {
        viewModelScope.launch {
            detectAnomaliesUseCase.resolveAnomaly(
                anomalyId = anomaly.id,
                userAction = userAction,
                correctedValue = correctedValue
            ).onFailure { error ->
                Timber.w("Failed to record anomaly for learning: ${error.message}")
            }
        }
    }

    /**
     * Helper function to update exercise history based on anomaly value type
     */
    private fun updateExerciseHistory(exerciseId: ExerciseId, value: AnomalyValue) {
        when (value) {
            is AnomalyValue.WeightValue -> updateExerciseHistory(exerciseId, weight = value.value)
            is AnomalyValue.RepsValue -> updateExerciseHistory(exerciseId, reps = value.value)
            is AnomalyValue.DurationValue -> updateExerciseHistory(exerciseId, duration = value.seconds)
        }
    }
}
