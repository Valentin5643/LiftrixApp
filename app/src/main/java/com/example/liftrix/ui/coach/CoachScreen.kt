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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.liftrix.ui.components.cards.LiftrixCard
import com.example.liftrix.ui.components.layouts.GridSystem

/**
 * AI Coach screen with enhanced modern feature preview styling.
 * 
 * This screen provides a professional presentation of upcoming AI coaching
 * features using the enhanced LiftrixCard system with 8pt grid spacing,
 * modern styling, and improved accessibility.
 * 
 * Features:
 * - Enhanced LiftrixCard components with 2xl radius (24dp)
 * - 8pt grid system for consistent spacing
 * - Modern feature preview cards with professional athletic styling
 * - Improved accessibility with proper content descriptions
 * - Brand color integration for engaging preview content
 * - Scrollable layout optimized for various screen sizes
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
                .padding(GridSystem.screenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GridSystem.spacing4)
        ) {
            // Header Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing3)
            ) {
                Icon(
                    imageVector = Icons.Filled.Psychology,
                    contentDescription = "AI Coach Icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(GridSystem.iconXLarge + GridSystem.spacing3)
                )
                
                Text(
                    text = "AI Coach",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
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
            
            // Enhanced Coming Soon Badge
            LiftrixCard(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = GridSystem.elevationMedium),
                contentDescription = "AI Coach features coming soon notification",
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Coming Soon",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(GridSystem.spacing1))
                    
                    Text(
                        text = "Revolutionary AI coaching features in development",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // Features Section Header
            Text(
                text = "Upcoming Features",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            // Enhanced Feature Cards
            AIFeaturePreviewCard(
                icon = Icons.Filled.Lightbulb,
                title = "Smart Workout Recommendations",
                description = "AI-powered workout suggestions tailored to your fitness level, goals, and available equipment. Get personalized routines that adapt to your progress.",
                contentDescription = "Smart workout recommendations feature preview"
            )
            
            AIFeaturePreviewCard(
                icon = Icons.Filled.Analytics,
                title = "Form Analysis & Feedback",
                description = "Advanced computer vision technology to analyze your exercise form in real-time and provide instant feedback to prevent injuries and optimize performance.",
                contentDescription = "Form analysis and feedback feature preview"
            )
            
            AIFeaturePreviewCard(
                icon = Icons.Filled.TrendingUp,
                title = "Progress Intelligence",
                description = "Deep insights into your fitness journey with predictive analytics, plateau detection, and intelligent progression recommendations.",
                contentDescription = "Progress intelligence feature preview"
            )
            
            AIFeaturePreviewCard(
                icon = Icons.Filled.Schedule,
                title = "Adaptive Scheduling",
                description = "Smart workout scheduling that considers your lifestyle, recovery needs, and optimal training times based on your performance patterns.",
                contentDescription = "Adaptive scheduling feature preview"
            )
            
            AIFeaturePreviewCard(
                icon = Icons.Filled.FitnessCenter,
                title = "Personalized Coaching",
                description = "24/7 AI coach that understands your preferences, motivates you during workouts, and provides expert guidance whenever you need it.",
                contentDescription = "Personalized coaching feature preview"
            )
            
            AIFeaturePreviewCard(
                icon = Icons.Filled.Psychology,
                title = "Behavioral Insights",
                description = "Advanced analysis of your workout habits and motivation patterns to help build sustainable fitness routines and overcome barriers.",
                contentDescription = "Behavioral insights feature preview"
            )
            
            // Enhanced Footer
            Spacer(modifier = Modifier.height(GridSystem.spacing3))
            
            LiftrixCard(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = GridSystem.elevationSmall),
                contentDescription = "AI coaching benefits summary",
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "These AI-powered features will revolutionize your fitness journey with personalized, intelligent guidance every step of the way.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Enhanced AI feature preview card component with modern styling and accessibility.
 * 
 * @param icon The Material icon representing the feature
 * @param title The feature title
 * @param description Detailed description of the feature
 * @param contentDescription Accessibility description for screen readers
 * @param modifier Modifier for styling customization
 */
@Composable
private fun AIFeaturePreviewCard(
    icon: ImageVector,
    title: String,
    description: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    LiftrixCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = GridSystem.elevationLarge),
        contentDescription = contentDescription
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            // Feature Icon with enhanced styling
            Surface(
                shape = RoundedCornerShape(GridSystem.cornerRadiusMedium),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(GridSystem.iconLarge + GridSystem.spacing2)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(GridSystem.iconLarge)
                        .padding(GridSystem.spacing1)
                )
            }
            
            Spacer(modifier = Modifier.width(GridSystem.spacing3))
            
            // Feature Content
            Column(
                verticalArrangement = Arrangement.spacedBy(GridSystem.spacing2),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                )
                
                // Coming Soon indicator
                Text(
                    text = "Coming Soon",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = GridSystem.spacing1)
                )
            }
        }
    }
} 