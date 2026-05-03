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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * Modal dialog warning users about personalization benefits when skipping onboarding steps.
 * Displays clear benefits explanation and prominent action buttons.
 */
@Composable
fun SkipWarningDialog(
    onDismiss: () -> Unit,
    onSkipAnyway: () -> Unit,
    onContinueSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warning",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(24.dp)
                )
                
                Text(
                    text = "Skip Setup?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            WarningContent()
        },
        confirmButton = {
            Button(
                onClick = onContinueSetup,
                modifier = Modifier.semantics {
                    contentDescription = "Continue with onboarding setup"
                }
            ) {
                Text(
                    text = "Continue Setup",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onSkipAnyway,
                modifier = Modifier.semantics {
                    contentDescription = "Skip onboarding anyway"
                }
            ) {
                Text(
                    text = "Skip Anyway",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        modifier = modifier
    )
}

/**
 * Warning content explaining the benefits of completing onboarding.
 */
@Composable
private fun WarningContent() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "You'll miss out on a personalized experience tailored to your fitness goals and available equipment.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
        
        BenefitsReminderCard()
        
        Text(
            text = "It only takes a minute to set up your profile and unlock these benefits.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Card displaying key benefits of completing onboarding.
 */
@Composable
private fun BenefitsReminderCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "What you'll get with setup:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            
            BenefitItem(
                icon = Icons.Default.FitnessCenter,
                title = "Better Workouts",
                description = "Exercises matched to your equipment and goals"
            )
            
            BenefitItem(
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                title = "Progress Tracking",
                description = "Track your achievements and see improvements"
            )
            
            BenefitItem(
                icon = Icons.Default.Person,
                title = "Personalized Plans",
                description = "Custom training plans based on your preferences"
            )
            
            BenefitItem(
                icon = Icons.Default.CheckCircle,
                title = "Goal Achievement",
                description = "Structured path to reach your fitness objectives"
            )
        }
    }
}

/**
 * Individual benefit item with icon, title, and description.
 */
@Composable
private fun BenefitItem(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "$title: $description"
            },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
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
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Preview for SkipWarningDialog.
 */
@Preview(showBackground = true)
@Composable
private fun SkipWarningDialogPreview() {
    MaterialTheme {
        SkipWarningDialog(
            onDismiss = { },
            onSkipAnyway = { },
            onContinueSetup = { }
        )
    }
}

/**
 * Preview for warning content only.
 */
@Preview(showBackground = true, name = "Warning Content")
@Composable
private fun WarningContentPreview() {
    MaterialTheme {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            WarningContent()
        }
    }
} 
