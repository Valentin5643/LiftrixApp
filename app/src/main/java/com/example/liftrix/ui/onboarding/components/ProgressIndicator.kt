package com.example.liftrix.ui.onboarding.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.onboarding.animation.OnboardingAnimations
import com.example.liftrix.ui.onboarding.animation.OnboardingAnimationComponents
import com.example.liftrix.ui.onboarding.animation.AnimationPerformanceUtils.rememberAnimationState
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Performance-optimized progress indicator with Material Design motion and smooth 60fps animations.
 * Uses hardware acceleration and memory-efficient state management for superior performance.
 */
@Composable
fun OnboardingProgressIndicator(
    currentStep: Int,
    totalSteps: Int,
    showStepIndicators: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Optimized progress calculation with stable state
    val progress = rememberAnimationState(currentStep, totalSteps) {
        calculateProgress(currentStep, totalSteps)
    }
    
    // Performance-optimized animated progress with spring physics
    val animatedProgress = OnboardingAnimations.animateProgress(
        targetProgress = progress
    )
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Onboarding progress: step $currentStep of $totalSteps, ${(progress * 100).toInt()}% complete"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (showStepIndicators) {
            PerformanceOptimizedStepIndicators(
                currentStep = currentStep,
                totalSteps = totalSteps
            )
        }
        
        OnboardingAnimationComponents.AnimatedProgress(
            targetProgress = progress
        ) { animatedProgressValue ->
            ProgressBar(
                progress = animatedProgressValue,
                currentStep = currentStep,
                totalSteps = totalSteps
            )
        }
        
        ProgressText(
            currentStep = currentStep,
            totalSteps = totalSteps,
            progress = progress
        )
    }
}

/**
 * Animated progress bar with smooth transitions.
 */
@Composable
private fun ProgressBar(
    progress: Float,
    currentStep: Int,
    totalSteps: Int
) {
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .semantics {
                contentDescription = "Progress bar showing $currentStep of $totalSteps steps completed"
            },
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
        strokeCap = StrokeCap.Round
    )
}

/**
 * Performance-optimized step indicators with bounce animations and hardware acceleration.
 */
@Composable
private fun PerformanceOptimizedStepIndicators(
    currentStep: Int,
    totalSteps: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Step indicators: $currentStep of $totalSteps steps"
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (step in 1..totalSteps) {
            val isCompleted = step < currentStep
            val isCurrent = step == currentStep
            
            OnboardingAnimationComponents.AnimatedStepIndicator(
                isCompleted = isCompleted,
                isCurrent = isCurrent
            ) { scale ->
                StepIndicator(
                    stepNumber = step,
                    currentStep = currentStep,
                    isCompleted = isCompleted,
                    isCurrent = isCurrent,
                    scale = scale
                )
            }
            
            // Add connector line between steps (except after last step)
            if (step < totalSteps) {
                StepConnector(
                    isCompleted = isCompleted
                )
            }
        }
    }
}

/**
 * Hardware-accelerated step indicator with completion animations and optimized rendering.
 */
@Composable
private fun StepIndicator(
    stepNumber: Int,
    currentStep: Int,
    isCompleted: Boolean,
    isCurrent: Boolean,
    scale: Float = 1.0f
) {
    // Stable color calculations to prevent recomposition
    val colorScheme = MaterialTheme.colorScheme
    val colors = remember(isCompleted, isCurrent, colorScheme) {
        StepColors(
            background = when {
                isCompleted -> colorScheme.primary
                isCurrent -> colorScheme.background
                else -> colorScheme.background
            },
            border = when {
                isCompleted -> colorScheme.primary
                isCurrent -> colorScheme.primary
                else -> colorScheme.outline
            },
            content = when {
                isCompleted -> colorScheme.onPrimary
                isCurrent -> colorScheme.primary
                else -> colorScheme.onSurfaceVariant
            }
        )
    }
    
    val stepState = remember(isCompleted, isCurrent) {
        when {
            isCompleted -> "completed"
            isCurrent -> "current"
            else -> "upcoming"
        }
    }
    
    Box(
        modifier = Modifier
            .size(32.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(colors.background)
            .border(
                width = 2.dp,
                color = colors.border,
                shape = CircleShape
            )
            .semantics {
                contentDescription = "Step $stepNumber, $stepState"
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stepNumber.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = colors.content,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Stable data class for step indicator colors to prevent unnecessary recompositions.
 */
private data class StepColors(
    val background: Color,
    val border: Color,
    val content: Color
)

/**
 * Connector line between step indicators.
 */
@Composable
private fun StepConnector(
    isCompleted: Boolean
) {
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(2.dp)
            .background(
                color = if (isCompleted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                }
            )
    )
}

/**
 * Progress text showing step information and percentage.
 */
@Composable
private fun ProgressText(
    currentStep: Int,
    totalSteps: Int,
    progress: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Step $currentStep of $totalSteps",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        
        Text(
            text = "${(progress * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Calculate progress percentage based on current step and total steps.
 * Ensures accurate progress calculation with proper bounds.
 */
fun calculateProgress(currentStep: Int, totalSteps: Int): Float {
    if (totalSteps <= 0) return 0f
    return (currentStep.toFloat() / totalSteps.toFloat()).coerceIn(0f, 1f)
}

/**
 * Preview for OnboardingProgressIndicator with step indicators.
 */
@Preview(showBackground = true, name = "With Step Indicators")
@Composable
private fun OnboardingProgressIndicatorPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            OnboardingProgressIndicator(
                currentStep = 1,
                totalSteps = 5,
                showStepIndicators = true
            )
            
            OnboardingProgressIndicator(
                currentStep = 3,
                totalSteps = 5,
                showStepIndicators = true
            )
            
            OnboardingProgressIndicator(
                currentStep = 5,
                totalSteps = 5,
                showStepIndicators = true
            )
        }
    }
}

/**
 * Preview for OnboardingProgressIndicator without step indicators.
 */
@Preview(showBackground = true, name = "Without Step Indicators")
@Composable
private fun OnboardingProgressIndicatorSimplePreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OnboardingProgressIndicator(
                currentStep = 2,
                totalSteps = 4,
                showStepIndicators = false
            )
            
            OnboardingProgressIndicator(
                currentStep = 4,
                totalSteps = 4,
                showStepIndicators = false
            )
        }
    }
}

/**
 * Preview for different step counts.
 */
@Preview(showBackground = true, name = "Different Step Counts")
@Composable
private fun OnboardingProgressIndicatorVariationsPreview() {
    LiftrixTheme {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "3 Steps",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            OnboardingProgressIndicator(
                currentStep = 2,
                totalSteps = 3,
                showStepIndicators = true
            )
            
            Text(
                text = "6 Steps",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            OnboardingProgressIndicator(
                currentStep = 4,
                totalSteps = 6,
                showStepIndicators = true
            )
        }
    }
} 