package com.example.liftrix.ui.coach

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import com.example.liftrix.ui.chat.ChatbotScreen

/**
 * AI Coach screen that directly embeds the AI chatbot interface.
 * 
 * The Coach tab IS the AI chat experience - providing immediate access to
 * personalized workout guidance and fitness advice powered by advanced AI.
 * 
 * This direct integration removes unnecessary navigation steps and makes
 * the AI coach the primary focus of this tab, aligning with the concept
 * that the Coach IS the AI, not a gateway to it.
 */
@Composable
fun CoachScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    // The Coach tab IS the AI chatbot - show it directly
    ChatbotScreen(
        conversationId = null, // Start a new conversation
        initialWorkoutContext = null, // No specific workout context from tab
        onNavigateBack = { 
            // Since this is a main tab, there's nowhere to navigate back to
            // The user can switch tabs using the bottom navigation
        },
        showTopBar = false
    )
} 
