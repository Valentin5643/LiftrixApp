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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.FitnessGoal
import com.example.liftrix.ui.onboarding.OnboardingState
import com.example.liftrix.ui.onboarding.OnboardingViewModel
import com.example.liftrix.ui.onboarding.WeightUnit
import com.example.liftrix.ui.onboarding.model.OnboardingStep
import com.example.liftrix.ui.onboarding.model.UserProfileData
import com.example.liftrix.ui.theme.LiftrixTheme

/**
 * Completion screen showing success feedback and profile summary.
 * Displays collected user data and provides options to create account or edit profile.
 */
@Composable
fun CompletionScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val profileData = (state as? OnboardingState.StepActive)?.profileData
    
    OnboardingScreenTemplate(
        title = OnboardingStep.COMPLETION.title,
        subtitle = OnboardingStep.COMPLETION.description,
        currentStep = OnboardingStep.COMPLETION.stepNumber + 1,
        totalSteps = OnboardingStep.getContentSteps().size + 1,
        onBack = onNavigateBack,
        onSkip = null, // No skip option on completion screen
        onContinue = onComplete,
        continueText = "Create Account",
        canContinue = true,
        isLoading = false,
        modifier = modifier
    ) {
        if (profileData != null) {
            CompletionContent(
                profileData = profileData,
                onEditProfile = onNavigateBack
            )
        }
    }
}

/**
 * Main content area of the completion screen with success message and profile summary.
 */
@Composable
private fun CompletionContent(
    profileData: UserProfileData,
    onEditProfile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SuccessHeader()
        ProfileSummaryCard(
            profileData = profileData,
            onEditProfile = onEditProfile
        )
        CelebrationMessage()
    }
}

/**
 * Success header with celebration icon and congratulatory message.
 */
@Composable
private fun SuccessHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Success",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(48.dp)
                        .padding(12.dp)
                )
            }
            
            Text(
                text = "🎉 Profile Complete!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "Your personalized fitness journey starts now. We've created a custom plan based on your preferences.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Profile summary card displaying collected user information.
 */
@Composable
private fun ProfileSummaryCard(
    profileData: UserProfileData,
    onEditProfile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Profile Summary",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                
                TextButton(
                    onClick = onEditProfile,
                    modifier = Modifier.semantics {
                        contentDescription = "Edit profile information"
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ProfileSection(
                    icon = Icons.Default.Person,
                    title = "Age",
                    value = profileData.getValidatedAge()?.toString() ?: "Not specified"
                )
                
                WeightProfileSection(profileData = profileData)
                
                EquipmentProfileSection(profileData = profileData)
                
                GoalsProfileSection(profileData = profileData)
            }
        }
    }
}

/**
 * Individual profile section displaying an icon, title, and value.
 */
@Composable
private fun ProfileSection(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$title: $value"
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
            
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Weight profile section with unit conversion display.
 */
@Composable
private fun WeightProfileSection(profileData: UserProfileData) {
    val weight = profileData.getValidatedWeight()
    val weightDisplay = if (weight != null) {
        val primaryValue = "${String.format("%.1f", weight.kilograms)} kg"
        val conversion = profileData.getWeightConversion()
        if (conversion != null) "$primaryValue ($conversion)" else primaryValue
    } else {
        "Not specified"
    }
    
    ProfileSection(
        icon = Icons.Default.MonitorWeight,
        title = "Weight",
        value = weightDisplay
    )
}

/**
 * Equipment profile section displaying selected equipment.
 */
@Composable
private fun EquipmentProfileSection(profileData: UserProfileData) {
    val equipmentList = profileData.selectedEquipment.toList()
    val otherEquipment = profileData.getValidatedOtherEquipment()
    
    val equipmentDisplay = buildString {
        equipmentList.forEachIndexed { index, equipment ->
            append(equipment.displayName)
            if (index < equipmentList.size - 1) append(", ")
        }
        otherEquipment?.let { other ->
            if (equipmentList.isNotEmpty()) append(", ")
            append(other)
        }
    }.takeIf { it.isNotBlank() } ?: "None specified"
    
    ProfileSection(
        icon = Icons.Default.FitnessCenter,
        title = "Available Equipment",
        value = equipmentDisplay
    )
}

/**
 * Goals profile section displaying prioritized fitness goals.
 */
@Composable
private fun GoalsProfileSection(profileData: UserProfileData) {
    val goals = profileData.selectedGoals.toList()
    val goalsPriority = profileData.goalsPriority
    
    val goalsDisplay = if (goals.isNotEmpty()) {
        // Sort goals by priority if priority map exists
        val sortedGoals = if (goalsPriority.isNotEmpty()) {
            goals.sortedBy { goalsPriority[it] ?: Int.MAX_VALUE }
        } else {
            goals
        }
        
        sortedGoals.mapIndexed { index, goal ->
            if (goalsPriority.isNotEmpty()) {
                "${index + 1}. ${goal.displayName}"
            } else {
                goal.displayName
            }
        }.joinToString(separator = "\n")
    } else {
        "None specified"
    }
    
    ProfileSection(
        icon = Icons.Default.TrendingUp,
        title = "Fitness Goals",
        value = goalsDisplay
    )
}

/**
 * Celebration message with next steps.
 */
@Composable
private fun CelebrationMessage() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "🚀",
                style = MaterialTheme.typography.headlineMedium
            )
            
            Text(
                text = "Ready to Get Started?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold
            )
            
            Text(
                text = "Create your account to save your progress, access personalized workouts, and start your fitness journey.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Preview for CompletionScreen with sample data.
 */
@Preview(showBackground = true)
@Composable
private fun CompletionScreenPreview() {
    LiftrixTheme {
        val sampleProfileData = UserProfileData(
            userId = "preview-user",
            ageInput = "25",
            weightInput = "70.0",
            weightUnit = WeightUnit.KILOGRAMS,
            selectedEquipment = setOf(Equipment.DUMBBELLS, Equipment.RESISTANCE_BANDS),
            otherEquipmentInput = "Pull-up bar",
            selectedGoals = setOf(FitnessGoal.BUILD_MUSCLE, FitnessGoal.LOSE_WEIGHT),
            goalsPriority = mapOf(
                FitnessGoal.BUILD_MUSCLE to 1,
                FitnessGoal.LOSE_WEIGHT to 2
            )
        )
        
        CompletionContent(
            profileData = sampleProfileData,
            onEditProfile = { }
        )
    }
}