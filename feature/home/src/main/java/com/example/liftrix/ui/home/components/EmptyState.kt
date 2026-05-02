package com.example.liftrix.ui.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Empty state component for new users with friendly messaging focused on social fitness.
 * 
 * Displays a welcome screen when users don't have any social activity or workouts yet, featuring:
 * - Centered layout with fitness illustration
 * - Friendly welcome message encouraging users to explore their fitness community
 * - Guidance to use other tabs for workout creation and progress tracking
 * 
 * Follows Material 3 design principles with proper accessibility support.
 * 
 * @param modifier Modifier for styling the empty state container
 */
@Composable
fun EmptyState(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .semantics {
                contentDescription = "Welcome screen showing empty social feed and workout history"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        WelcomeIllustration()
        
        Spacer(modifier = Modifier.height(32.dp))
        
        WelcomeMessage()
        
        Spacer(modifier = Modifier.height(32.dp))
        
        HelpfulGuidanceCard(
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Welcome illustration using fitness-themed Material icon
 */
@Composable
private fun WelcomeIllustration(
    modifier: Modifier = Modifier
) {
    Icon(
        imageVector = Icons.Default.FitnessCenter,
        contentDescription = "Welcome",
        modifier = modifier.size(80.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}

/**
 * Welcome message with friendly encouraging text
 */
@Composable
private fun WelcomeMessage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Your Social Feed",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "Connect with friends and see their workout activities here. Start by exploring other tabs to create workouts and track your progress.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
        )
    }
}

/**
 * Helpful guidance card directing users to other tabs
 */
@Composable
private fun HelpfulGuidanceCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .semantics {
                contentDescription = "Navigation guidance for new users"
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Get Started",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "• Use the Workout tab to create and track workouts\n• Visit Progress to see your fitness analytics\n• Add friends to see their activities here",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    MaterialTheme {
        EmptyState()
    }
} 
