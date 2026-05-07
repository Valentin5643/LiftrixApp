package com.example.liftrix.domain.usecase.workout

import com.example.liftrix.domain.model.Workout
import com.example.liftrix.domain.model.common.LiftrixResult
import javax.inject.Inject

class CreateWorkoutUseCase @Inject constructor(
    private val workoutCommandUseCase: WorkoutCommandUseCase
) {
    suspend operator fun invoke(workout: Workout): LiftrixResult<Workout> =
        LiftrixResult.success(workout)
}
