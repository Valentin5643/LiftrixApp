package com.example.liftrix.ui.coach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * AI Coach screen placeholder with comprehensive feature descriptions.
 * 
 * This placeholder screen provides a professional presentation of upcoming AI coaching
 * features for the Liftrix app. It showcases the planned AI-powered functionality
 * including personalized recommendations, form analysis, progress insights, and
 * intelligent coaching sessions.
 * 
 * Features:
 * - Professional Material3 design with feature cards
 * - Comprehensive AI coaching feature descriptions
 * - Accessible design with proper content descriptions
 * - Scrollable layout for extensive feature information
 * - Consistent with app's design language and patterns
 * 
 * This screen serves as both a placeholder and a preview of the AI coaching
 * capabilities that will be implemented in future development phases.
 */
@Composable
fun CoachScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = "AI Coach",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp)
                )
                
                Text(
                    text = "AI Coach",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = "Your personal AI fitness coach powered by advanced machine learning",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            
            // Coming Soon Badge
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Coming Soon",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
            
            // Features Section
            Text(
                text = "Upcoming Features",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            // Feature Cards
            CoachFeatureCard(
                icon = Icons.Filled.Lightbulb,
                title = "Smart Workout Recommendations",
                description = "AI-powered workout suggestions tailored to your fitness level, goals, and available equipment. Get personalized routines that adapt to your progress."
            )
            
            CoachFeatureCard(
                icon = Icons.Filled.Analytics,
                title = "Form Analysis & Feedback",
                description = "Advanced computer vision technology to analyze your exercise form in real-time and provide instant feedback to prevent injuries and optimize performance."
            )
            
            CoachFeatureCard(
                icon = Icons.Filled.TrendingUp,
                title = "Progress Intelligence",
                description = "Deep insights into your fitness journey with predictive analytics, plateau detection, and intelligent progression recommendations."
            )
            
            CoachFeatureCard(
                icon = Icons.Filled.Schedule,
                title = "Adaptive Scheduling",
                description = "Smart workout scheduling that considers your lifestyle, recovery needs, and optimal training times based on your performance patterns."
            )
            
            CoachFeatureCard(
                icon = Icons.Filled.FitnessCenter,
                title = "Personalized Coaching",
                description = "24/7 AI coach that understands your preferences, motivates you during workouts, and provides expert guidance whenever you need it."
            )
            
            CoachFeatureCard(
                icon = Icons.Filled.Psychology,
                title = "Behavioral Insights",
                description = "Advanced analysis of your workout habits and motivation patterns to help build sustainable fitness routines and overcome barriers."
            )
            
            // Footer
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "These AI-powered features will revolutionize your fitness journey with personalized, intelligent guidance every step of the way.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

/**
 * Feature card component for displaying AI coaching capabilities.
 * 
 * @param icon The Material icon representing the feature
 * @param title The feature title
 * @param description Detailed description of the feature
 * @param modifier Modifier for styling customization
 */
@Composable
private fun CoachFeatureCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
} 