package com.example.liftrix.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Navigation graph for the Coach tab.
 * 
 * Defines the navigation structure for the AI Coach section of the app, including
 * the main coach screen and any nested destinations within the coaching flow.
 * 
 * Features:
 * - Independent navigation stack for AI coaching functionality
 * - Deep linking support for coach-related routes
 * - Placeholder implementation ready for AI coaching features
 * - Support for personalized recommendations and coaching sessions
 * 
 * @param onNavigateToAuth Callback to navigate to authentication flow
 */
fun NavGraphBuilder.coachGraph(
    onNavigateToAuth: () -> Unit
) {
    navigation(
        startDestination = CoachRoutes.COACH_MAIN,
        route = "coach"
    ) {
        composable(CoachRoutes.COACH_MAIN) {
            CoachScreenPlaceholder()
        }
        
        composable(CoachRoutes.WORKOUT_RECOMMENDATIONS) {
            // Future: AI-powered workout recommendations
            CoachScreenPlaceholder(
                title = "Workout Recommendations",
                subtitle = "AI-powered workout suggestions based on your progress and goals"
            )
        }
        
        composable(CoachRoutes.FORM_ANALYSIS) {
            // Future: Exercise form analysis and feedback
            CoachScreenPlaceholder(
                title = "Form Analysis",
                subtitle = "AI-powered exercise form analysis and improvement tips"
            )
        }
        
        composable(CoachRoutes.GOAL_SETTING) {
            // Future: Intelligent goal setting and tracking
            CoachScreenPlaceholder(
                title = "Goal Setting",
                subtitle = "Set and track your fitness goals with AI guidance"
            )
        }
        
        composable(CoachRoutes.COACHING_SESSIONS) {
            // Future: Interactive coaching sessions
            CoachScreenPlaceholder(
                title = "Coaching Sessions",
                subtitle = "Interactive AI coaching sessions for personalized guidance"
            )
        }
    }
}

/**
 * Route definitions for the Coach navigation graph.
 */
object CoachRoutes {
    const val COACH_MAIN = "coach/main"
    const val WORKOUT_RECOMMENDATIONS = "coach/recommendations"
    const val FORM_ANALYSIS = "coach/form_analysis"
    const val GOAL_SETTING = "coach/goal_setting"
    const val COACHING_SESSIONS = "coach/sessions"
    const val NUTRITION_ADVICE = "coach/nutrition"
}

/**
 * Placeholder for AI Coach main screen with feature preview.
 * This will be replaced by actual AI coaching implementation in future tasks.
 */
@Composable
private fun CoachScreenPlaceholder(
    title: String = "AI Coach",
    subtitle: String = "Personalized coaching and recommendations coming soon",
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lightbulb,
                contentDescription = "AI coach preview",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
            )
            
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Preview of upcoming AI features
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FeaturePreviewItem(
                    icon = Icons.Filled.Lightbulb,
                    title = "Smart Recommendations",
                    description = "AI-powered workout and exercise suggestions"
                )
                
                FeaturePreviewItem(
                    icon = Icons.Filled.TrendingUp,
                    title = "Progress Analysis",
                    description = "Intelligent insights into your fitness journey"
                )
                
                FeaturePreviewItem(
                    icon = Icons.Filled.Lightbulb,
                    title = "Personal Coaching", 
                    description = "Guidance and motivation features"
                )
            }
        }
    }
}

/**
 * Preview item for AI coaching features.
 */
@Composable
private fun FeaturePreviewItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.secondary
        )
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
} 
