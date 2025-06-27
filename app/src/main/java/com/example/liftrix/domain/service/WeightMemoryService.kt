package com.example.liftrix.domain.service

interface WeightMemoryService {
    
    /**
     * Retrieves the last used weight for a specific exercise by a user
     * @param userId The ID of the user
     * @param exerciseId The ID of the exercise
     * @return Result containing the last used weight or null if no history exists
     */
    suspend fun getLastUsedWeight(userId: String, exerciseId: String): Result<Float?>
    
    /**
     * Updates the weight used for an exercise by a user
     * @param userId The ID of the user
     * @param exerciseId The ID of the exercise
     * @param weight The weight used
     * @param reps Number of reps performed (optional)
     * @param sets Number of sets performed (optional)
     * @param workoutId Associated workout ID (optional)
     * @return Result indicating success or failure
     */
    suspend fun updateExerciseWeight(
        userId: String, 
        exerciseId: String, 
        weight: Float,
        reps: Int = 0,
        sets: Int = 0,
        workoutId: String? = null
    ): Result<Unit>
    
    /**
     * Gets the recent exercises used by a user
     * @param userId The ID of the user
     * @param limit Maximum number of exercises to return
     * @return Result containing list of recent exercise IDs
     */
    suspend fun getRecentExercises(userId: String, limit: Int = 10): Result<List<String>>
    
    /**
     * Gets the average weight used for an exercise in the last 30 days
     * @param userId The ID of the user
     * @param exerciseId The ID of the exercise
     * @return Result containing the average weight or null if no recent history
     */
    suspend fun getAverageWeightLast30Days(userId: String, exerciseId: String): Result<Float?>
    
    /**
     * Gets the usage count for a specific exercise by a user
     * @param userId The ID of the user
     * @param exerciseId The ID of the exercise
     * @return Result containing the usage count
     */
    suspend fun getExerciseUsageCount(userId: String, exerciseId: String): Result<Int>
} 