package com.example.liftrix.ui.onboarding.model

/**
 * Enum representing the steps in the onboarding flow.
 * Defines the ordered progression and navigation structure.
 */
enum class OnboardingStep(
    val stepNumber: Int,
    val title: String,
    val description: String,
    val isOptional: Boolean = false
) {
    /**
     * Introduction/Welcome step explaining onboarding benefits.
     */
    INTRO(
        stepNumber = 0,
        title = "Welcome to Liftrix",
        description = "Let's personalize your workout experience",
        isOptional = true
    ),
    
    /**
     * Age input step for workout recommendations.
     */
    AGE(
        stepNumber = 1,
        title = "What's your age?",
        description = "This helps us recommend safe and effective workouts",
        isOptional = false
    ),
    
    /**
     * Weight input step with unit selection.
     */
    WEIGHT(
        stepNumber = 2,
        title = "What's your current weight?",
        description = "Optional: This helps us track your progress over time",
        isOptional = true
    ),
    
    /**
     * Available equipment selection step.
     */
    EQUIPMENT(
        stepNumber = 3,
        title = "What equipment do you have access to?",
        description = "Select all that apply to get personalized workout recommendations",
        isOptional = false
    ),
    
    /**
     * Fitness goals selection and prioritization step.
     */
    GOALS(
        stepNumber = 4,
        title = "What are your fitness goals?",
        description = "Choose your top priorities to customize your training plan",
        isOptional = false
    ),
    
    /**
     * Completion step with summary and next actions.
     */
    COMPLETION(
        stepNumber = 5,
        title = "You're all set!",
        description = "Your personalized profile has been created",
        isOptional = false
    );

    /**
     * Get the next step in the flow.
     * @return Next OnboardingStep or null if this is the last step
     */
    fun getNextStep(): OnboardingStep? {
        return values().find { it.stepNumber == this.stepNumber + 1 }
    }

    /**
     * Get the previous step in the flow.
     * @return Previous OnboardingStep or null if this is the first step
     */
    fun getPreviousStep(): OnboardingStep? {
        return values().find { it.stepNumber == this.stepNumber - 1 }
    }

    /**
     * Check if this is the first step in the flow.
     */
    val isFirstStep: Boolean
        get() = this == INTRO

    /**
     * Check if this is the last step in the flow.
     */
    val isLastStep: Boolean
        get() = this == COMPLETION

    /**
     * Get progress percentage for this step.
     * @param totalSteps Total number of steps (excluding intro and completion)
     */
    fun getProgressPercentage(totalSteps: Int = 3): Float {
        return when (this) {
            INTRO -> 0.0f
            COMPLETION -> 1.0f
            else -> (stepNumber - 1).toFloat() / totalSteps.toFloat()
        }
    }

    companion object {
        /**
         * Get step by step number.
         * @param stepNumber The step number to find
         * @return OnboardingStep with matching step number or null
         */
        fun fromStepNumber(stepNumber: Int): OnboardingStep? {
            return values().find { it.stepNumber == stepNumber }
        }

        /**
         * Get all content steps (excluding intro and completion).
         */
        fun getContentSteps(): List<OnboardingStep> {
            return listOf(AGE, WEIGHT, EQUIPMENT, GOALS)
        }

        /**
         * Get required steps only.
         */
        fun getRequiredSteps(): List<OnboardingStep> {
            return values().filter { !it.isOptional }
        }
    }
} 