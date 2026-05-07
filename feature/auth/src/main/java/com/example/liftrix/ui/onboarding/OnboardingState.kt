package com.example.liftrix.ui.onboarding

import com.example.liftrix.domain.model.UserProfile
import com.example.liftrix.domain.model.onboarding.WeightUnit
import com.example.liftrix.ui.onboarding.model.OnboardingStep
import com.example.liftrix.domain.model.onboarding.UserProfileData

/**
 * MVI State sealed class for onboarding flow.
 * Represents all possible states during the user onboarding process.
 */
sealed class OnboardingState {
    
    /**
     * Initial loading state when starting onboarding or loading existing profile data.
     */
    object Loading : OnboardingState()
    
    /**
     * Active step state where user is currently on a specific onboarding step.
     * @param step Current onboarding step being displayed
     * @param profileData Mutable profile data being collected
     * @param isValidForNavigation Whether current step data is valid to proceed
     * @param errorMessage Optional error message for current step
     */
    data class StepActive(
        val step: OnboardingStep,
        val profileData: UserProfileData,
        val isValidForNavigation: Boolean = false,
        val errorMessage: String? = null
    ) : OnboardingState()
    
    /**
     * Completed state when onboarding has been successfully finished.
     * @param completedProfile The final completed user profile
     */
    data class Completed(
        val completedProfile: UserProfile
    ) : OnboardingState()
    
    /**
     * Error state for unrecoverable errors during onboarding.
     * @param exception The error that occurred
     * @param canRetry Whether user can retry the operation
     */
    data class Error(
        val exception: Throwable,
        val canRetry: Boolean = true
    ) : OnboardingState()
}

/**
 * MVI Event sealed class for onboarding user interactions.
 * Represents all possible user actions and system events.
 */
sealed class OnboardingEvent {
    
    // Navigation events
    object NavigateNext : OnboardingEvent()
    object NavigateBack : OnboardingEvent()
    object SkipOnboarding : OnboardingEvent()
    object ConfirmSkip : OnboardingEvent()
    object CancelSkip : OnboardingEvent()
    
    // Profile data update events
    data class UpdateAge(val age: String) : OnboardingEvent()
    data class UpdateWeight(val weight: String) : OnboardingEvent()
    data class UpdateWeightUnit(val unit: WeightUnit) : OnboardingEvent()
    data class ToggleEquipment(val equipment: com.example.liftrix.domain.model.Equipment) : OnboardingEvent()
    data class UpdateOtherEquipment(val description: String) : OnboardingEvent()
    data class ToggleGoal(val goal: com.example.liftrix.domain.model.FitnessGoal) : OnboardingEvent()
    data class UpdateGoalPriority(val goal: com.example.liftrix.domain.model.FitnessGoal, val priority: Int) : OnboardingEvent()
    
    // System events
    object SaveProfile : OnboardingEvent()
    object LoadExistingProfile : OnboardingEvent()
    object RetryOperation : OnboardingEvent()
    object ClearError : OnboardingEvent()
}
