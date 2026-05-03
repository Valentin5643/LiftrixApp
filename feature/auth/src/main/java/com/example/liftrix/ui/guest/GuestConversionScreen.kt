package com.example.liftrix.ui.guest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.liftrix.domain.model.GuestSession

/**
 * Guest-to-registered conversion screen that highlights the benefits of creating an account
 * and provides a clear path to upgrade from guest mode.
 */
@Composable
fun GuestConversionScreen(
    source: String,
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onMaybeLater: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuestSessionViewModel = hiltViewModel()
) {
    val guestSessionState by viewModel.guestSessionState.collectAsState()
    
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header based on source
            ConversionHeader(source = source)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Current progress summary (if applicable)
            val currentState = guestSessionState
            if (currentState is GuestSessionState.Active) {
                CurrentProgressCard(guestSession = currentState.guestSession)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Benefits of upgrading
            UpgradeAdvantagesCard()
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Action buttons
            ConversionActions(
                onCreateAccount = onCreateAccount,
                onSignIn = onSignIn,
                onMaybeLater = onMaybeLater
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Additional messaging based on source
            SourceSpecificMessage(source = source)
        }
    }
}

/**
 * Header section that adapts based on the conversion source
 */
@Composable
private fun ConversionHeader(
    source: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val (title, subtitle, icon) = when (source) {
            "limit_reached" -> Triple(
                "You've Reached Your Limit!",
                "Create an account to continue your fitness journey",
                Icons.Default.Star
            )
            "nudge" -> Triple(
                "Ready to Level Up?",
                "Unlock the full Liftrix experience",
                Icons.Default.TrendingUp
            )
            "manual" -> Triple(
                "Upgrade Your Experience",
                "Get the most out of Liftrix with a free account",
                Icons.Default.PersonAdd
            )
            else -> Triple(
                "Create Your Account",
                "Don't lose your progress - save it forever",
                Icons.Default.PersonAdd
            )
        }
        
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Card showing current guest progress that would be preserved
 */
@Composable
private fun CurrentProgressCard(
    guestSession: com.example.liftrix.domain.model.GuestSession,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "Progress history",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Your Progress So Far",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "• ${guestSession.workoutCount} workouts completed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            if (guestSession.significantInteractionCount > 0) {
                Text(
                    text = "• ${guestSession.significantInteractionCount} app interactions",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            val sessionDuration = guestSession.getSessionStats().sessionDurationMinutes
            if (sessionDuration > 5) {
                Text(
                    text = "• ${sessionDuration} minutes using Liftrix",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Don't lose this progress! Create an account to save everything.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Card highlighting the advantages of upgrading
 */
@Composable
private fun UpgradeAdvantagesCard(
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = "What You'll Get",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            AdvantageItem(
                icon = Icons.Default.Star,
                title = "Unlimited Workouts",
                description = "No more limits - log as many workouts as you want"
            )
            
            AdvantageItem(
                icon = Icons.Default.TrendingUp,
                title = "Advanced Analytics",
                description = "Track progress, see trends, and optimize your training"
            )
            
            AdvantageItem(
                icon = Icons.Default.CloudUpload,
                title = "Data Backup",
                description = "Your workouts are automatically saved and synced"
            )
            
            AdvantageItem(
                icon = Icons.Default.History,
                title = "Complete History",
                description = "Access all your workouts and templates anytime"
            )
            
            AdvantageItem(
                icon = Icons.Default.CheckCircle,
                title = "Free Forever",
                description = "No cost, no commitments - just great features"
            )
        }
    }
}

/**
 * Individual advantage item
 */
@Composable
private fun AdvantageItem(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * Action buttons for conversion
 */
@Composable
private fun ConversionActions(
    onCreateAccount: () -> Unit,
    onSignIn: () -> Unit,
    onMaybeLater: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onCreateAccount,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Create account",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Create Free Account",
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        OutlinedButton(
            onClick = onSignIn,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "I Already Have an Account",
                fontWeight = FontWeight.Medium
            )
        }
        
        OutlinedButton(
            onClick = onMaybeLater,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Maybe Later",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Additional messaging based on conversion source
 */
@Composable
private fun SourceSpecificMessage(
    source: String,
    modifier: Modifier = Modifier
) {
    val message = when (source) {
        "limit_reached" -> "You've used all 3 guest workouts. Creating an account takes less than 30 seconds and gives you unlimited access."
        "nudge" -> "You're already enjoying Liftrix! Why not make it official and unlock everything?"
        "manual" -> "Ready to take your fitness journey to the next level?"
        else -> "Join thousands of users who track their progress with Liftrix."
    }
    
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth()
    )
}
