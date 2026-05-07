package com.example.liftrix.domain.usecase.exercise

import com.example.liftrix.domain.model.common.LiftrixResult
import javax.inject.Inject

class SearchExercisesUseCase @Inject constructor(
    private val exerciseQueryUseCase: ExerciseQueryUseCase
) {
    suspend operator fun invoke(request: SearchExercisesRequest): LiftrixResult<SearchExercisesResult> =
        exerciseQueryUseCase.searchExercises(request)
}
