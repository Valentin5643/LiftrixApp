package com.example.liftrix.domain.usecase.template

import com.example.liftrix.domain.model.WorkoutTemplate
import com.example.liftrix.domain.repository.WorkoutTemplateRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Use case for retrieving workout templates for a specific user
 */
class GetWorkoutTemplatesUseCase @Inject constructor(
    private val workoutTemplateRepository: WorkoutTemplateRepository
) {
    
    /**
     * Gets all workout templates for the specified user
     * 
     * @param userId The ID of the user whose templates to retrieve
     * @return Flow of list of workout templates
     */
    operator fun invoke(userId: String): Flow<List<WorkoutTemplate>> {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        return workoutTemplateRepository.getAllTemplatesForUser(userId)
    }
} 