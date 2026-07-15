package com.example.liftrix.feature.chat.navigation

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

fun NavGraphBuilder.chatGraph(
    navController: NavHostController,
    isAiAccessEnabled: Boolean
) {
    composable<LiftrixRoute.Coach> {
        AiAccessGate(isAiAccessEnabled, navController) {
            CoachRoute(navController = navController)
        }
    }

    composable<LiftrixRoute.AIChatbot> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.AIChatbot>()
        AiAccessGate(isAiAccessEnabled, navController) {
            ChatbotRoute(
                conversationId = route.conversationId,
                initialWorkoutContext = route.workoutContext,
                onNavigateBack = { navController.popBackStackSafely() },
                onCreateWorkoutPlan = { conversationId, seedPrompt ->
                    navController.navigate(LiftrixRoute.AIWorkoutBuilder(conversationId, seedPrompt))
                }
            )
        }
    }

    composable<LiftrixRoute.AIWorkoutBuilder> { backStackEntry ->
        val route = backStackEntry.toRoute<LiftrixRoute.AIWorkoutBuilder>()
        AiAccessGate(isAiAccessEnabled, navController) {
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
    }

    composable<LiftrixRoute.AIChatSettings> {
        AiAccessGate(isAiAccessEnabled, navController) {
            AIChatSettingsRoute(
                onNavigateBack = { navController.popBackStackSafely() }
            )
        }
    }
}

@Composable
private fun AiAccessGate(
    isAiAccessEnabled: Boolean,
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    if (isAiAccessEnabled) {
        content()
    } else {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "AI access is currently unavailable.",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                modifier = Modifier.padding(top = 16.dp),
                onClick = {
                    if (!navController.popBackStackSafely()) {
                        navController.navigate(LiftrixRoute.Home)
                    }
                }
            ) {
                Text("Go back")
            }
        }
    }
}

private fun NavController.popBackStackSafely(): Boolean {
    return if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        false
    }
}
