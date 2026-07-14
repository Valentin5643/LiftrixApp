package com.example.liftrix.domain.usecase.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ModifyWorkoutProgramUseCaseTest {
    @Test fun scopedOperationsRetainExplicitTargets() {
        val replace = WorkoutModificationScope.ReplaceExercise(1, "exercise-7")
        val day = WorkoutModificationScope.RegenerateDay(2)
        assertEquals(1, replace.dayIndex)
        assertEquals("exercise-7", replace.exerciseId)
        assertEquals(2, day.dayIndex)
    }
}
