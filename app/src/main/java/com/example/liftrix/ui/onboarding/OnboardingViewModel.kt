package com.example.liftrix.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.usecase.GetProfileUseCase
import com.example.liftrix.domain.usecase.SaveProfileUseCase
import com.example.liftrix.domain.usecase.ValidateProfileInputUseCase
import com.example.liftrix.domain.usecase.ValidationResult
import com.example.liftrix.ui.onboarding.model.OnboardingStep
import com.example.liftrix.ui.onboarding.model.UserProfileData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for onboarding flow with MVI pattern implementation.
 * Manages state, validation, and persistence of user profile data during onboarding.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val saveProfileUseCase: SaveProfileUseCase,
    private val validateProfileInputUseCase: ValidateProfileInputUseCase,
    private val getProfileUseCase: GetProfileUseCase,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow<OnboardingState>(OnboardingState.Loading)
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    // Navigation state for synchronization
    private val _currentNavigationStep = MutableStateFlow<OnboardingStep?>(null)
    val currentNavigationStep: StateFlow<OnboardingStep?> = _currentNavigationStep.asStateFlow()

    /**
     * Initialize onboarding for a user with state restoration support.
     * @param userId The user ID to create onboarding profile for
     * @param resumeFromSavedState Whether to attempt state restoration
     */
    fun initializeOnboarding(userId: String, resumeFromSavedState: Boolean = true) {
        viewModelScope.launch {
            try {
                _state.value = OnboardingState.Loading
                
                // Try to restore state first
                if (resumeFromSavedState && restoreStateFromHandle(userId)) {
                    Timber.d("Onboarding state restored for user: $userId")
                    return@launch
                }
                
                // Check for existing profile
                val hasExistingProfile = getProfileUseCase.hasProfile(userId)
                if (hasExistingProfile) {
                    loadExistingProfile()
                    return@launch
                }
                
                // Create new onboarding session
                val profileData = UserProfileData.createInitial(userId)
                val initialStep = OnboardingStep.INTRO
                
                _state.value = OnboardingState.StepActive(
                    step = initialStep,
                    profileData = profileData,
                    isValidForNavigation = true
                )
                _currentNavigationStep.value = initialStep
                
                // Save initial state
                saveStateToHandle()
                
                Timber.d("Onboarding initialized for user: $userId")
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize onboarding for user: $userId")
                _state.value = OnboardingState.Error(e, canRetry = true)
            }
        }
    }

    /**
     * Handle onboarding events from UI.
     */
    fun handleEvent(event: OnboardingEvent) {
        viewModelScope.launch {
            try {
                when (event) {
                    is OnboardingEvent.NavigateNext -> navigateToNext()
                    is OnboardingEvent.NavigateBack -> navigateBack()
                    is OnboardingEvent.SkipOnboarding -> skipOnboarding()
                    is OnboardingEvent.ConfirmSkip -> confirmSkip()
                    is OnboardingEvent.CancelSkip -> cancelSkip()
                    
                    is OnboardingEvent.UpdateAge -> updateAge(event.age)
                    is OnboardingEvent.UpdateWeight -> updateWeight(event.weight)
                    is OnboardingEvent.UpdateWeightUnit -> updateWeightUnit(event.unit)
                    is OnboardingEvent.ToggleEquipment -> toggleEquipment(event.equipment)
                    is OnboardingEvent.UpdateOtherEquipment -> updateOtherEquipment(event.description)
                    is OnboardingEvent.ToggleGoal -> toggleGoal(event.goal)
                    is OnboardingEvent.UpdateGoalPriority -> updateGoalPriority(event.goal, event.priority)
                    
                    is OnboardingEvent.SaveProfile -> saveProfile()
                    is OnboardingEvent.LoadExistingProfile -> loadExistingProfile()
                    is OnboardingEvent.RetryOperation -> retryLastOperation()
                    is OnboardingEvent.ClearError -> clearError()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error handling onboarding event: $event")
                updateStateWithError(e)
            }
        }
    }

    private fun navigateToNext() {
        val currentState = _state.value
        if (currentState !is OnboardingState.StepActive) return

        val currentStep = currentState.step
        val nextStep = currentStep.getNextStep()

        if (nextStep != null) {
            val isValid = validateCurrentStep(currentState.profileData, currentStep)
            
            if (isValid) {
                _state.value = currentState.copy(
                    step = nextStep,
                    isValidForNavigation = validateCurrentStep(currentState.profileData, nextStep),
                    errorMessage = null
                )
                _currentNavigationStep.value = nextStep
                saveStateToHandle()
                Timber.d("Navigated to step: $nextStep")
            } else {
                _state.value = currentState.copy(
                    errorMessage = getValidationErrorMessage(currentState.profileData, currentStep)
                )
            }
        } else {
            viewModelScope.launch {
                saveProfile()
            }
        }
    }

    private fun navigateBack() {
        val currentState = _state.value
        if (currentState !is OnboardingState.StepActive) return

        val currentStep = currentState.step
        val previousStep = currentStep.getPreviousStep()

        if (previousStep != null) {
            _state.value = currentState.copy(
                step = previousStep,
                isValidForNavigation = validateCurrentStep(currentState.profileData, previousStep),
                errorMessage = null
            )
            _currentNavigationStep.value = previousStep
            saveStateToHandle()
            Timber.d("Navigated back to step: $previousStep")
        }
    }

    private fun skipOnboarding() {
        _state.value = OnboardingState.Completed(
            UserProfileData.createInitial("").toDomainModel()
        )
    }

    private fun confirmSkip() {
        skipOnboarding()
    }

    private fun cancelSkip() {
        // Return to current step
    }

    private fun updateAge(age: String) {
        updateProfileData { profileData ->
            val validationResult = validateProfileInputUseCase.validateAge(age)
            val errorMessage = when (validationResult) {
                is ValidationResult.Valid -> null
                is ValidationResult.Invalid -> validationResult.message
                is ValidationResult.Loading -> null // Handle loading state if needed
            }

            profileData.copy(
                ageInput = age,
                ageError = errorMessage
            )
        }
    }

    private fun updateWeight(weight: String) {
        updateProfileData { profileData ->
            val unitString = when (profileData.weightUnit) {
                WeightUnit.KILOGRAMS -> "kg"
                WeightUnit.POUNDS -> "lbs"
            }
            
            val validationResult = if (weight.isBlank()) {
                ValidationResult.Valid // Weight is optional
            } else {
                validateProfileInputUseCase.validateWeight(weight, unitString)
            }
            
            val errorMessage = when (validationResult) {
                is ValidationResult.Valid -> null
                is ValidationResult.Invalid -> validationResult.message
                is ValidationResult.Loading -> null
            }

            profileData.copy(
                weightInput = weight,
                weightError = errorMessage
            )
        }
    }

    private fun updateWeightUnit(unit: WeightUnit) {
        updateProfileData { profileData ->
            profileData.copy(weightUnit = unit)
        }
    }

    private fun toggleEquipment(equipment: Equipment) {
        updateProfileData { profileData ->
            val newSelection = if (equipment in profileData.selectedEquipment) {
                profileData.selectedEquipment - equipment
            } else {
                profileData.selectedEquipment + equipment
            }
            
            // Validate equipment selection
            val validationResult = validateProfileInputUseCase.validateEquipmentSelection(newSelection.toList())
            val errorMessage = when (validationResult) {
                is ValidationResult.Valid -> null
                is ValidationResult.Invalid -> validationResult.message
                is ValidationResult.Loading -> null
            }
            
            profileData.copy(
                selectedEquipment = newSelection,
                // Reset other equipment error when main selection changes
                otherEquipmentError = if (errorMessage != null) null else profileData.otherEquipmentError
            )
        }
    }

    private fun updateOtherEquipment(description: String) {
        updateProfileData { profileData ->
            val validationResult = if (description.isBlank()) {
                ValidationResult.Valid // Optional field
            } else {
                validateProfileInputUseCase.validateOtherEquipment(description)
            }
            
            val errorMessage = when (validationResult) {
                is ValidationResult.Valid -> null
                is ValidationResult.Invalid -> validationResult.message
                is ValidationResult.Loading -> null
            }

            profileData.copy(
                otherEquipmentInput = description,
                otherEquipmentError = errorMessage
            )
        }
    }

    private fun toggleGoal(goal: FitnessGoal) {
        updateProfileData { profileData ->
            val newSelection = if (goal in profileData.selectedGoals) {
                profileData.selectedGoals - goal
            } else {
                profileData.selectedGoals + goal
            }
            
            // Validate goals selection
            val validationResult = validateProfileInputUseCase.validateGoalSelection(newSelection.toList())
            val errorMessage = when (validationResult) {
                is ValidationResult.Valid -> null
                is ValidationResult.Invalid -> validationResult.message
                is ValidationResult.Loading -> null
            }
            
            profileData.copy(
                selectedGoals = newSelection,
                goalsError = errorMessage
            )
        }
    }

    private fun updateGoalPriority(goal: FitnessGoal, priority: Int) {
        updateProfileData { profileData ->
            val newPriorities = profileData.goalsPriority.toMutableMap()
            newPriorities[goal] = priority

            // Validate goal priority mapping
            val validationResult = validateProfileInputUseCase.validateGoalPriorityMapping(
                profileData.selectedGoals.toList(),
                newPriorities
            )
            val errorMessage = when (validationResult) {
                is ValidationResult.Valid -> null
                is ValidationResult.Invalid -> validationResult.message
                is ValidationResult.Loading -> null
            }

            profileData.copy(
                goalsPriority = newPriorities,
                goalsError = errorMessage
            )
        }
    }

    private suspend fun saveProfile() {
        val currentState = _state.value
        if (currentState !is OnboardingState.StepActive) return

        try {
            _state.value = OnboardingState.Loading
            
            val result = validateAndSaveProfile()

            if (result.isSuccess) {
                val profileData = currentState.profileData
                val domainProfile = profileData.toDomainModel()
                _state.value = OnboardingState.Completed(domainProfile)
                Timber.d("Profile saved successfully for user: ${profileData.userId}")
            } else {
                val exception = result.exceptionOrNull() ?: Exception("Unknown error occurred")
                _state.value = OnboardingState.Error(exception, canRetry = true)
                Timber.e(exception, "Failed to save profile")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception while saving profile")
            _state.value = OnboardingState.Error(e, canRetry = true)
        }
    }

    private fun loadExistingProfile() {
        viewModelScope.launch {
            try {
                _state.value = OnboardingState.Loading
                
                val currentState = _state.value
                if (currentState !is OnboardingState.StepActive) {
                    Timber.e("Cannot load profile: invalid current state")
                    return@launch
                }
                
                val userId = currentState.profileData.userId
                if (userId.isBlank()) {
                    Timber.e("Cannot load profile: missing user ID")
                    _state.value = OnboardingState.Error(
                        IllegalStateException("User ID is required to load profile"),
                        canRetry = false
                    )
                    return@launch
                }
                
                // Check if profile exists
                val hasProfile = getProfileUseCase.hasProfile(userId)
                if (!hasProfile) {
                    Timber.d("No existing profile found for user: $userId")
                    // Return to current onboarding step
                    initializeOnboarding(userId)
                    return@launch
                }
                
                // Load profile data
                getProfileUseCase(userId).collect { profile ->
                    if (profile != null) {
                        val profileData = UserProfileData.fromDomainModel(profile)
                        
                        // Check if profile is complete
                        if (profile.completedAt != null) {
                            _state.value = OnboardingState.Completed(profile)
                            Timber.d("Complete profile loaded for user: $userId")
                        } else {
                            // Resume onboarding at appropriate step
                            val resumeStep = determineResumeStep(profileData)
                            _state.value = OnboardingState.StepActive(
                                step = resumeStep,
                                profileData = profileData,
                                isValidForNavigation = validateCurrentStep(profileData, resumeStep)
                            )
                            Timber.d("Resuming onboarding at step $resumeStep for user: $userId")
                        }
                    } else {
                        // Profile was deleted or not found
                        initializeOnboarding(userId)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load existing profile")
                _state.value = OnboardingState.Error(e, canRetry = true)
            }
        }
    }

    private fun retryLastOperation() {
        val currentState = _state.value
        if (currentState is OnboardingState.Error) {
            viewModelScope.launch {
                saveProfile()
            }
        }
    }

    private fun clearError() {
        val currentState = _state.value
        if (currentState is OnboardingState.Error) {
            _state.value = OnboardingState.Loading
        }
    }

    private fun updateProfileData(update: (UserProfileData) -> UserProfileData) {
        val currentState = _state.value
        if (currentState is OnboardingState.StepActive) {
            val updatedProfileData = update(currentState.profileData)
            val isValid = validateCurrentStep(updatedProfileData, currentState.step)
            
            _state.value = currentState.copy(
                profileData = updatedProfileData,
                isValidForNavigation = isValid
            )
            
            // Save state after data updates for persistence
            saveStateToHandle()
        }
    }

    private fun validateCurrentStep(profileData: UserProfileData, step: OnboardingStep): Boolean {
        return when (step) {
            OnboardingStep.INTRO -> true
            OnboardingStep.AGE -> profileData.isAgeStepValid()
            OnboardingStep.WEIGHT -> profileData.isWeightStepValid()
            OnboardingStep.EQUIPMENT -> profileData.isEquipmentStepValid()
            OnboardingStep.GOALS -> profileData.isGoalsStepValid()
            OnboardingStep.COMPLETION -> profileData.isCompleteForSaving()
        }
    }

    private fun getValidationErrorMessage(profileData: UserProfileData, step: OnboardingStep): String {
        return when (step) {
            OnboardingStep.INTRO -> "Welcome screen completed"
            OnboardingStep.AGE -> {
                profileData.ageError ?: "Please enter a valid age between ${UserProfile.MIN_AGE} and ${UserProfile.MAX_AGE}"
            }
            OnboardingStep.WEIGHT -> {
                profileData.weightError ?: if (profileData.weightInput.isBlank() && !profileData.preferNotToSayWeight) {
                    "Please enter your weight or select 'Prefer not to say'"
                } else {
                    "Please enter a valid weight"
                }
            }
            OnboardingStep.EQUIPMENT -> {
                if (profileData.selectedEquipment.isEmpty()) {
                    "Please select at least one equipment type"
                } else if (profileData.otherEquipmentError != null) {
                    profileData.otherEquipmentError!!
                } else {
                    "Please complete equipment selection"
                }
            }
            OnboardingStep.GOALS -> {
                profileData.goalsError ?: if (profileData.selectedGoals.isEmpty()) {
                    "Please select at least one fitness goal"
                } else {
                    "Please complete goals selection"
                }
            }
            OnboardingStep.COMPLETION -> {
                when {
                    !profileData.isAgeStepValid() -> "Age information is required"
                    !profileData.isEquipmentStepValid() -> "Equipment selection is required" 
                    !profileData.isGoalsStepValid() -> "Fitness goals selection is required"
                    else -> "Profile is ready to save"
                }
            }
        }
    }

    private fun updateStateWithError(exception: Throwable) {
        _state.value = OnboardingState.Error(exception, canRetry = true)
    }

    /**
     * Determines the appropriate step to resume onboarding based on completed data.
     */
    private fun determineResumeStep(profileData: UserProfileData): OnboardingStep {
        return when {
            !profileData.isAgeStepValid() -> OnboardingStep.AGE
            !profileData.isWeightStepValid() -> OnboardingStep.WEIGHT
            !profileData.isEquipmentStepValid() -> OnboardingStep.EQUIPMENT
            !profileData.isGoalsStepValid() -> OnboardingStep.GOALS
            else -> OnboardingStep.COMPLETION
        }
    }

    /**
     * Validates and saves profile using comprehensive validation.
     */
    private suspend fun validateAndSaveProfile(): Result<Unit> {
        val currentState = _state.value
        if (currentState !is OnboardingState.StepActive) {
            return Result.failure(IllegalStateException("Invalid state for profile saving"))
        }

        val profileData = currentState.profileData
        
        // Validate profile completion
        val completionValidation = validateProfileInputUseCase.validateProfileCompletion(
            age = profileData.getValidatedAge(),
            weight = profileData.getValidatedWeight(),
            equipment = profileData.selectedEquipment.toList(),
            goals = profileData.selectedGoals.toList()
        )
        
        if (completionValidation is ValidationResult.Invalid) {
            return Result.failure(IllegalArgumentException(completionValidation.message))
        }

        // Convert to domain model and save
        val domainProfile = profileData.toDomainModel()
        return saveProfileUseCase(domainProfile)
    }

    /**
     * Handles validation result and updates UI state accordingly.
     */
    private fun handleValidationResult(result: ValidationResult, fieldName: String) {
        when (result) {
            is ValidationResult.Valid -> {
                Timber.d("Validation passed for field: $fieldName")
            }
            is ValidationResult.Invalid -> {
                Timber.w("Validation failed for field $fieldName: ${result.message}")
            }
            is ValidationResult.Loading -> {
                Timber.d("Validation in progress for field: $fieldName")
            }
        }
    }

    /**
     * Navigation synchronization for UI
     */
    fun onNavigationStepChanged(step: OnboardingStep) {
        val currentState = _state.value
        if (currentState is OnboardingState.StepActive && currentState.step != step) {
            // Sync ViewModel state with navigation
            _state.value = currentState.copy(step = step)
            _currentNavigationStep.value = step
            saveStateToHandle()
            Timber.d("Navigation synced to step: $step")
        }
    }

    /**
     * Handle back navigation from UI with data preservation
     */
    fun handleBackNavigation(): Boolean {
        val currentState = _state.value
        if (currentState is OnboardingState.StepActive) {
            val currentStep = currentState.step
            if (currentStep.getPreviousStep() != null) {
                navigateBack()
                return true // Navigation handled by ViewModel
            }
        }
        return false // Let system handle back navigation
    }

    /**
     * Save current state to SavedStateHandle for persistence
     */
    private fun saveStateToHandle() {
        val currentState = _state.value
        try {
            when (currentState) {
                is OnboardingState.StepActive -> {
                    savedStateHandle[KEY_CURRENT_STEP] = currentState.step.name
                    savedStateHandle[KEY_USER_ID] = currentState.profileData.userId
                    savedStateHandle[KEY_AGE_INPUT] = currentState.profileData.ageInput
                    savedStateHandle[KEY_WEIGHT_INPUT] = currentState.profileData.weightInput
                    savedStateHandle[KEY_WEIGHT_UNIT] = currentState.profileData.weightUnit.name
                    savedStateHandle[KEY_OTHER_EQUIPMENT] = currentState.profileData.otherEquipmentInput
                    savedStateHandle[KEY_PREFER_NOT_SAY_WEIGHT] = currentState.profileData.preferNotToSayWeight
                    // Note: Collections are more complex - would need serialization for full state
                    Timber.d("State saved to handle")
                }
                else -> {
                    // Clear saved state for non-active states
                    clearSavedState()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to save state to handle")
        }
    }

    /**
     * Restore state from SavedStateHandle after configuration change
     */
    private fun restoreStateFromHandle(userId: String): Boolean {
        return try {
            val savedStepName = savedStateHandle.get<String>(KEY_CURRENT_STEP)
            val savedUserId = savedStateHandle.get<String>(KEY_USER_ID)
            
            if (savedStepName != null && savedUserId == userId) {
                val step = OnboardingStep.valueOf(savedStepName)
                val ageInput = savedStateHandle.get<String>(KEY_AGE_INPUT) ?: ""
                val weightInput = savedStateHandle.get<String>(KEY_WEIGHT_INPUT) ?: ""
                val weightUnitName = savedStateHandle.get<String>(KEY_WEIGHT_UNIT) ?: WeightUnit.KILOGRAMS.name
                val otherEquipment = savedStateHandle.get<String>(KEY_OTHER_EQUIPMENT) ?: ""
                val preferNotSayWeight = savedStateHandle.get<Boolean>(KEY_PREFER_NOT_SAY_WEIGHT) ?: false
                
                val weightUnit = WeightUnit.valueOf(weightUnitName)
                
                val profileData = UserProfileData.createInitial(userId).copy(
                    ageInput = ageInput,
                    weightInput = weightInput,
                    weightUnit = weightUnit,
                    otherEquipmentInput = otherEquipment,
                    preferNotToSayWeight = preferNotSayWeight
                )
                
                _state.value = OnboardingState.StepActive(
                    step = step,
                    profileData = profileData,
                    isValidForNavigation = validateCurrentStep(profileData, step)
                )
                _currentNavigationStep.value = step
                
                Timber.d("State restored from handle: step=$step, userId=$userId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to restore state from handle")
            false
        }
    }

    /**
     * Clear saved state from handle
     */
    private fun clearSavedState() {
        try {
            savedStateHandle.remove<String>(KEY_CURRENT_STEP)
            savedStateHandle.remove<String>(KEY_USER_ID)
            savedStateHandle.remove<String>(KEY_AGE_INPUT)
            savedStateHandle.remove<String>(KEY_WEIGHT_INPUT)
            savedStateHandle.remove<String>(KEY_WEIGHT_UNIT)
            savedStateHandle.remove<String>(KEY_OTHER_EQUIPMENT)
            savedStateHandle.remove<Boolean>(KEY_PREFER_NOT_SAY_WEIGHT)
            Timber.d("Saved state cleared")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear saved state")
        }
    }

    companion object {
        private const val KEY_CURRENT_STEP = "onboarding_current_step"
        private const val KEY_USER_ID = "onboarding_user_id"
        private const val KEY_AGE_INPUT = "onboarding_age_input"
        private const val KEY_WEIGHT_INPUT = "onboarding_weight_input"
        private const val KEY_WEIGHT_UNIT = "onboarding_weight_unit"
        private const val KEY_OTHER_EQUIPMENT = "onboarding_other_equipment"
        private const val KEY_PREFER_NOT_SAY_WEIGHT = "onboarding_prefer_not_say_weight"
    }
} 