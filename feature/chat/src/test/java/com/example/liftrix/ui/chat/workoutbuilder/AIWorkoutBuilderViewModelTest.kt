package com.example.liftrix.ui.chat.workoutbuilder

import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ai.*
import org.junit.Assert.assertTrue
import org.junit.Test

class AIWorkoutBuilderViewModelTest {
    @Test fun reviewGateRejectsMissingEquipmentAndDays() {
        val draft = WorkoutGenerationPreferences(
            goal = WorkoutProgramGoal.STRENGTH,
            level = WorkoutProgramLevel.INTERMEDIATE,
            availableEquipment = emptySet<Equipment>(),
            trainingDays = emptyList(),
            sessionDurationMinutes = 45
        )
        assertTrue(draft.validationErrors().size >= 2)
    }

    @Test fun validReviewedDraftPassesGate() {
        val draft = WorkoutGenerationPreferences(
            goal = WorkoutProgramGoal.GENERAL_FITNESS,
            level = WorkoutProgramLevel.BEGINNER,
            availableEquipment = setOf(Equipment.BODYWEIGHT_ONLY),
            trainingDays = listOf(WorkoutTrainingDay.MONDAY),
            sessionDurationMinutes = 30
        )
        assertTrue(draft.validationErrors().isEmpty())
    }
}
