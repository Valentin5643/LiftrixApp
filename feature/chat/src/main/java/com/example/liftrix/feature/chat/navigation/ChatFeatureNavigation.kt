package com.example.liftrix.feature.chat.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.example.liftrix.ui.chat.ChatbotScreen
import com.example.liftrix.ui.chat.settings.AIChatSettingsScreen
import com.example.liftrix.ui.coach.CoachScreen

@Composable
fun CoachRoute(navController: NavController) {
    CoachScreen(navController = navController)
}

@Composable
fun ChatbotRoute(
    conversationId: String?,
    initialWorkoutContext: String?,
    onNavigateBack: () -> Unit
) {
    ChatbotScreen(
        conversationId = conversationId,
        initialWorkoutContext = initialWorkoutContext,
        onNavigateBack = onNavigateBack
    )
}

@Composable
fun AIChatSettingsRoute(onNavigateBack: () -> Unit) {
    AIChatSettingsScreen(onNavigateBack = onNavigateBack)
}
