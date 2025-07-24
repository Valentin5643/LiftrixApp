package com.example.liftrix.ui.onboarding.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import com.example.liftrix.ui.workout.components.UnifiedWorkoutCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.onboarding.model.OnboardingStep
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Introduction screen for onboarding flow.
 * Displays welcome message, app benefits, and personalization preview.
 */
@Composable
fun IntroScreen(
    onStart: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    OnboardingScreenTemplate(
        title = OnboardingStep.INTRO.title,
        subtitle = OnboardingStep.INTRO.description,
        currentStep = OnboardingStep.INTRO.stepNumber + 1,
        totalSteps = OnboardingStep.getContentSteps().size + 1,
        onBack = null, // No back navigation from intro
        onSkip = onSkip,
        onContinue = onStart,
        continueText = "Get Started",
        canContinue = true,
        isLoading = false,
        modifier = modifier
    ) {
        IntroContent()
    }
}

/**
 * Main content area of the intro screen with benefits and personalization preview.
 */
@Composable
private fun IntroContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        WelcomeMessage()
        BenefitsSection()
        PersonalizationPreview()
    }
}

/**
 * Welcome message explaining the onboarding purpose.
 */
@Composable
private fun WelcomeMessage() {
    UnifiedWorkoutCard(
        title = "Personalized Workouts Await",
        subtitle = "Your fitness journey starts here",
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🎯",
                style = MaterialTheme.typography.displaySmall
            )
            
            Text(
                text = "Answer a few quick questions to get workout recommendations tailored to your goals, equipment, and fitness level.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Section highlighting key benefits of completing onboarding.
 */
@Composable
private fun BenefitsSection() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "What you'll get:",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium
        )
        
        BenefitItem(
            icon = Icons.Default.FitnessCenter,
            title = "Smart Workout Plans",
            description = "Exercises matched to your available equipment and experience level"
        )
        
        BenefitItem(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            title = "Progress Tracking",
            description = "Monitor your strength gains and achieve your fitness goals faster"
        )
        
        BenefitItem(
            icon = Icons.Default.Person,
            title = "Personalized Experience",
            description = "Recommendations that adapt to your preferences and progress"
        )
    }
}

/**
 * Individual benefit item with icon, title, and description.
 */
@Composable
private fun BenefitItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$title: $description"
            },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Preview of personalization features to engage users.
 */
@Composable
private fun PersonalizationPreview() {
    UnifiedWorkoutCard(
        title = "Quick Setup",
        subtitle = "Takes less than 2 minutes",
        leadingIcon = Icons.AutoMirrored.Filled.TrendingUp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "We'll ask about your age, equipment, and fitness goals to create the perfect workout plan for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IntroScreenPreview() {
    LiftrixTheme {
        IntroScreen(
            onStart = {},
            onSkip = {}
        )
    }
}

@Preview(showBackground = true, name = "Dark Theme")
@Composable
private fun IntroScreenDarkPreview() {
    LiftrixTheme(darkTheme = true) {
        IntroScreen(
            onStart = {},
            onSkip = {}
        )
    }
} 