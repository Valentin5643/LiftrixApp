package com.example.liftrix.domain.interactor.workout

import com.example.liftrix.domain.model.CustomExercise
import com.example.liftrix.domain.model.Equipment
import com.example.liftrix.domain.model.ExerciseDefaults
import com.example.liftrix.domain.model.ExerciseGroup
import com.example.liftrix.domain.model.ExerciseId
import com.example.liftrix.domain.model.ExerciseLibrary
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.usecase.exercise.CreateCustomExerciseInput
import com.example.liftrix.domain.usecase.exercise.CreateCustomExerciseUseCase
import com.example.liftrix.domain.usecase.exercise.ExerciseQueryUseCase
import com.example.liftrix.domain.usecase.exercise.SearchExercisesRequest
import com.example.liftrix.domain.usecase.exercise.SearchExercisesResult
import com.example.liftrix.domain.usecase.exercise.SearchableExercise
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ExerciseInteractor @Inject constructor(
    private val exerciseQueryUseCase: ExerciseQueryUseCase,
    private val createCustomExerciseUseCase: CreateCustomExerciseUseCase
) {
    suspend operator fun invoke(): LiftrixResult<List<ExerciseLibrary>> =
        exerciseQueryUseCase()

    suspend fun getExerciseById(exerciseId: String): LiftrixResult<ExerciseLibrary?> =
        exerciseQueryUseCase.getExerciseById(exerciseId)

    suspend fun getRecentExercises(userId: String, limit: Int = 10): LiftrixResult<List<ExerciseLibrary>> =
        exerciseQueryUseCase.getRecentExercises(userId, limit)

    suspend fun searchExercises(request: SearchExercisesRequest): LiftrixResult<SearchExercisesResult> =
        exerciseQueryUseCase.searchExercises(request)

    fun search(query: String, userEquipment: Set<Equipment>): Flow<List<SearchableExercise>> =
        exerciseQueryUseCase.search(query, userEquipment)

    fun searchWithVariations(query: String, userEquipment: Set<Equipment>): Flow<List<SearchableExercise>> =
        exerciseQueryUseCase.searchWithVariations(query, userEquipment)

    suspend fun getExerciseDefaults(
        exerciseId: ExerciseId,
        userId: String,
        exerciseLibrary: ExerciseLibrary
    ): Result<ExerciseDefaults> =
        exerciseQueryUseCase.getExerciseDefaults(exerciseId, userId, exerciseLibrary)

    suspend fun getVariations(
        exerciseId: String,
        userEquipment: Set<Equipment> = Equipment.entries.toSet()
    ): Flow<ExerciseGroup> =
        exerciseQueryUseCase.getVariations(exerciseId, userEquipment)

    fun getAvailableMovementPatterns(): Flow<List<String>> =
        exerciseQueryUseCase.getAvailableMovementPatterns()

    suspend fun createCustomExercise(input: CreateCustomExerciseInput): Result<CustomExercise> =
        createCustomExerciseUseCase(input)
}
