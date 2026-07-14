package com.example.liftrix.ui.chat.workoutbuilder

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ai.*
import com.example.liftrix.ui.chat.workoutbuilder.components.WorkoutPreferenceSummary
import org.junit.Rule
import org.junit.Test

class AIWorkoutBuilderScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reviewExplainsOfflineGenerationState() {
        val preferences = WorkoutGenerationPreferences(
            WorkoutProgramGoal.GENERAL_FITNESS,
            WorkoutProgramLevel.BEGINNER,
            setOf(Equipment.BODYWEIGHT_ONLY),
            listOf(WorkoutTrainingDay.MONDAY),
            30
        )
        compose.setContent { WorkoutPreferenceSummary(preferences, false, {}, {}) }
        compose.onNodeWithText("Review your training brief").assertIsDisplayed()
        compose.onNodeWithText("Connect to the internet to generate this plan.").assertIsDisplayed()
    }
}
