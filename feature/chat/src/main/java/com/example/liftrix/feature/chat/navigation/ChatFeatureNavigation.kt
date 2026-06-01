package com.example.liftrix.feature.chat.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.example.liftrix.ui.navigation.LiftrixRoute
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
    AIChatSettingsScreen(onNavigateBack = onNavigateBack, showTopBar = false)
}

fun NavGraphBuilder.chatGraph(navController: NavHostController) {
    composable<LiftrixRoute.Coach> {
        CoachRoute(navController = navController)
    }

    composable<LiftrixRoute.AIChatbot> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.AIChatbot>()
        ChatbotRoute(
            conversationId = route.conversationId,
            initialWorkoutContext = route.workoutContext,
            onNavigateBack = { navController.popBackStackSafely() }
        )
    }

    composable<LiftrixRoute.AIChatSettings> {
        AIChatSettingsRoute(
            onNavigateBack = { navController.popBackStackSafely() }
        )
    }
}

private fun NavController.popBackStackSafely(): Boolean {
    return if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        false
    }
}
