package com.example.liftrix.ui.profile.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.theme.LiftrixSpacing

/**
 * Profile completion indicator component
 *
 * Shows the user's profile completion percentage with visual progress bar
 * Encourages users to complete their profile for better experience
 */
@Composable
fun ProfileCompletionIndicator(
    completionPercentage: Int,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = completionPercentage / 100f,
        label = "progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.large),
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Profile Completion",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "$completionPercentage%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            )

            if (completionPercentage < 100) {
                Text(
                    text = "Complete your profile to help us personalize your experience",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    text = "Your profile is complete!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Calculate profile completion percentage based on filled fields
 */
fun calculateProfileCompletion(
    displayName: String,
    bio: String,
    age: String,
    weight: String,
    selectedGoals: Set<*>,
    selectedEquipment: Set<*>
): Int {
    var filledFields = 0
    val totalFields = 6

    if (displayName.isNotBlank()) filledFields++
    if (bio.isNotBlank() && bio.length >= 10) filledFields++
    if (age.isNotBlank()) filledFields++
    if (weight.isNotBlank()) filledFields++
    if (selectedGoals.isNotEmpty()) filledFields++
    if (selectedEquipment.isNotEmpty()) filledFields++

    return (filledFields * 100) / totalFields
}
