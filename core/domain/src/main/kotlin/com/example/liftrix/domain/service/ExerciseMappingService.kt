package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult

interface ExerciseMappingService {
    
    /**
     * Maps an external exercise name to a Liftrix exercise ID
     * @param externalName The exercise name from the imported file
     * @param context Additional context like category, equipment, or source app
     * @return LiftrixResult with the mapped exercise ID or null if no match found
     */
    suspend fun mapExerciseName(
        externalName: String,
        context: ExerciseMappingContext = ExerciseMappingContext()
    ): LiftrixResult<String?>
    
    /**
     * Maps multiple exercise names in batch for better performance
     * @param exerciseNames List of external exercise names to map
     * @return LiftrixResult with map of external name to Liftrix exercise ID
     */
    suspend fun mapExerciseNamesBatch(
        exerciseNames: List<String>,
        context: ExerciseMappingContext = ExerciseMappingContext()
    ): LiftrixResult<Map<String, String?>>
    
    /**
     * Gets suggested mappings for an exercise name with confidence scores
     * @param externalName The exercise name to find suggestions for
     * @param maxSuggestions Maximum number of suggestions to return
     * @return LiftrixResult with list of exercise suggestions with confidence scores
     */
    suspend fun getSuggestedMappings(
        externalName: String,
        maxSuggestions: Int = 5
    ): LiftrixResult<List<ExerciseSuggestion>>
}

data class ExerciseMappingContext(
    val category: String? = null,
    val equipment: String? = null,
    val sourceApp: String? = null,
    val movementPattern: String? = null
)

data class ExerciseSuggestion(
    val exerciseId: String,
    val exerciseName: String,
    val confidence: Double, // 0.0 to 1.0
    val matchType: MatchType
)

enum class MatchType {
    EXACT,
    NORMALIZED,
    FUZZY,
    SEMANTIC,
    CATEGORY_BASED
}