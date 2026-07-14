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
import com.example.liftrix.ui.chat.workoutbuilder.AIWorkoutBuilderScreen

@Composable
fun CoachRoute(navController: NavController) {
    CoachScreen(navController = navController)
}

@Composable
fun ChatbotRoute(
    conversationId: String?,
    initialWorkoutContext: String?,
    onNavigateBack: () -> Unit,
    onCreateWorkoutPlan: (String?, String?) -> Unit
) {
    ChatbotScreen(
        conversationId = conversationId,
        initialWorkoutContext = initialWorkoutContext,
        onNavigateBack = onNavigateBack,
        onCreateWorkoutPlan = onCreateWorkoutPlan
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
            onNavigateBack = { navController.popBackStackSafely() },
            onCreateWorkoutPlan = { conversationId, seedPrompt ->
                navController.navigate(LiftrixRoute.AIWorkoutBuilder(conversationId, seedPrompt))
            }
        )
    }

    composable<LiftrixRoute.AIWorkoutBuilder> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.AIWorkoutBuilder>()
        AIWorkoutBuilderScreen(
            onNavigateBack = { navController.popBackStackSafely() },
            onReturnToConversation = {
                navController.navigate(LiftrixRoute.AIChatbot(route.conversationId))
            },
            onStartWorkout = { templateId ->
                navController.navigate(LiftrixRoute.ActiveWorkout(templateId, false))
            },
            onEditWorkout = { templateId -> navController.navigate(LiftrixRoute.EditWorkout(templateId)) }
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
