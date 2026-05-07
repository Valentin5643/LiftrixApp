package com.example.liftrix.domain.service

import com.example.liftrix.data.local.dao.ExerciseLibraryDao
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseMappingServiceImpl @Inject constructor(
    private val exerciseLibraryDao: ExerciseLibraryDao
) : ExerciseMappingService {
    
    // Common exercise name mappings from external apps
    private val commonMappings = mapOf(
        // Strava/Running apps
        "run" to "Running",
        "cycling" to "Cycling",
        "bike" to "Cycling",
        "walk" to "Walking",
        "swim" to "Swimming",
        
        // Strength training variations
        "squat" to "Back Squat",
        "squats" to "Back Squat",
        "deadlift" to "Deadlift",
        "deadlifts" to "Deadlift",
        "bench press" to "Bench Press",
        "bench" to "Bench Press",
        "pull up" to "Pull-ups",
        "pullup" to "Pull-ups",
        "pull-up" to "Pull-ups",
        "pullups" to "Pull-ups",
        "pull-ups" to "Pull-ups",
        "chin up" to "Chin-ups",
        "chinup" to "Chin-ups",
        "chin-up" to "Chin-ups",
        "chinups" to "Chin-ups",
        "chin-ups" to "Chin-ups",
        "push up" to "Push-ups",
        "pushup" to "Push-ups",
        "push-up" to "Push-ups",
        "pushups" to "Push-ups",
        "push-ups" to "Push-ups",
        "overhead press" to "Overhead Press",
        "military press" to "Overhead Press",
        "shoulder press" to "Overhead Press",
        "bicep curl" to "Bicep Curls",
        "biceps curl" to "Bicep Curls",
        "bicep curls" to "Bicep Curls",
        "biceps curls" to "Bicep Curls",
        "curl" to "Bicep Curls",
        "curls" to "Bicep Curls"
    )
    
    override suspend fun mapExerciseName(
        externalName: String,
        context: ExerciseMappingContext
    ): LiftrixResult<String?> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "EXERCISE_MAPPING",
                errorMessage = "Failed to map exercise name",
                analyticsContext = mapOf(
                    "external_name" to externalName,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        val cleanedName = cleanExerciseName(externalName)
        
        // 1. Try exact match first
        val exactMatch = findExactMatch(cleanedName)
        if (exactMatch != null) return@liftrixCatching exactMatch
        
        // 2. Try common mappings
        val commonMapping = findCommonMapping(cleanedName)
        if (commonMapping != null) {
            val mapped = findExactMatch(commonMapping)
            if (mapped != null) return@liftrixCatching mapped
        }
        
        // 3. Try normalized match
        val normalizedMatch = findNormalizedMatch(cleanedName)
        if (normalizedMatch != null) return@liftrixCatching normalizedMatch
        
        // 4. Try fuzzy matching
        val fuzzyMatch = findFuzzyMatch(cleanedName, threshold = 0.8)
        if (fuzzyMatch != null) return@liftrixCatching fuzzyMatch
        
        // 5. Try partial matches with searchable terms
        val partialMatch = findPartialMatch(cleanedName)
        if (partialMatch != null) return@liftrixCatching partialMatch
        
        // 6. Try semantic matching based on context
        if (context.category != null || context.movementPattern != null) {
            val semanticMatch = findSemanticMatch(cleanedName, context)
            if (semanticMatch != null) return@liftrixCatching semanticMatch
        }
        
        // No match found
        null
    }
    
    override suspend fun mapExerciseNamesBatch(
        exerciseNames: List<String>,
        context: ExerciseMappingContext
    ): LiftrixResult<Map<String, String?>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "EXERCISE_MAPPING_BATCH",
                errorMessage = "Failed to map exercise names in batch",
                analyticsContext = mapOf(
                    "exercise_count" to exerciseNames.size.toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        val results = mutableMapOf<String, String?>()
        
        for (exerciseName in exerciseNames) {
            val mapped = mapExerciseName(exerciseName, context)
            results[exerciseName] = mapped.fold(
                onSuccess = { it },
                onFailure = { null }
            )
        }
        
        results
    }
    
    override suspend fun getSuggestedMappings(
        externalName: String,
        maxSuggestions: Int
    ): LiftrixResult<List<ExerciseSuggestion>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "EXERCISE_SUGGESTIONS",
                errorMessage = "Failed to get exercise suggestions",
                analyticsContext = mapOf(
                    "external_name" to externalName,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        val suggestions = mutableListOf<ExerciseSuggestion>()
        val cleanedName = cleanExerciseName(externalName)
        
        // Get all exercises for comparison
        val allExercises = exerciseLibraryDao.getAllExercises().first()
        
        // Score each exercise
        for (exercise in allExercises) {
            val similarity = calculateSimilarity(cleanedName, exercise.name)
            val matchType = determineMatchType(cleanedName, exercise.name, exercise.searchableTerms)
            
            if (similarity >= 0.3) { // Minimum threshold
                suggestions.add(
                    ExerciseSuggestion(
                        exerciseId = exercise.id,
                        exerciseName = exercise.name,
                        confidence = similarity,
                        matchType = matchType
                    )
                )
            }
        }
        
        // Sort by confidence and return top suggestions
        suggestions
            .sortedByDescending { it.confidence }
            .take(maxSuggestions)
    }
    
    private suspend fun findExactMatch(cleanedName: String): String? {
        val exercises = exerciseLibraryDao.getAllExercises().first()
        return exercises.find { exercise ->
            exercise.name.equals(cleanedName, ignoreCase = true)
        }?.id
    }
    
    private fun findCommonMapping(cleanedName: String): String? {
        return commonMappings[cleanedName.lowercase()]
    }
    
    private suspend fun findNormalizedMatch(cleanedName: String): String? {
        val normalized = normalizeExerciseName(cleanedName)
        val exercises = exerciseLibraryDao.getAllExercises().first()
        
        return exercises.find { exercise ->
            normalizeExerciseName(exercise.name) == normalized
        }?.id
    }
    
    private suspend fun findFuzzyMatch(cleanedName: String, threshold: Double): String? {
        val exercises = exerciseLibraryDao.getAllExercises().first()
        
        return exercises
            .map { exercise -> exercise to calculateSimilarity(cleanedName, exercise.name) }
            .filter { (_, similarity) -> similarity >= threshold }
            .maxByOrNull { (_, similarity) -> similarity }
            ?.first?.id
    }
    
    private suspend fun findPartialMatch(cleanedName: String): String? {
        val exercises = exerciseLibraryDao.searchExercises(cleanedName).first()
        return exercises.firstOrNull()?.id
    }
    
    private suspend fun findSemanticMatch(
        cleanedName: String,
        context: ExerciseMappingContext
    ): String? {
        // Try to find exercises in the same category or movement pattern
        val exercises = exerciseLibraryDao.getAllExercises().first()
        
        val candidateExercises = exercises.filter { exercise ->
            when {
                context.category != null -> 
                    exercise.primaryMuscleGroup.name.equals(context.category, ignoreCase = true)
                context.movementPattern != null -> 
                    exercise.movementPattern.equals(context.movementPattern, ignoreCase = true)
                else -> true
            }
        }
        
        // From candidates, find best fuzzy match
        return candidateExercises
            .map { exercise -> exercise to calculateSimilarity(cleanedName, exercise.name) }
            .filter { (_, similarity) -> similarity >= 0.6 }
            .maxByOrNull { (_, similarity) -> similarity }
            ?.first?.id
    }
    
    private fun cleanExerciseName(name: String): String {
        return name.trim()
            .replace(Regex("[^a-zA-Z0-9\\s-]"), "") // Remove special characters except hyphens
            .replace(Regex("\\s+"), " ") // Normalize whitespace
            .lowercase()
    }
    
    private fun normalizeExerciseName(name: String): String {
        return cleanExerciseName(name)
            .replace("-", " ")
            .replace("_", " ")
            .split(" ")
            .filter { it.isNotBlank() }
            .sorted() // Sort words for order-independent matching
            .joinToString(" ")
    }
    
    private fun calculateSimilarity(name1: String, name2: String): Double {
        val clean1 = cleanExerciseName(name1)
        val clean2 = cleanExerciseName(name2)
        
        // Exact match
        if (clean1 == clean2) return 1.0
        
        // Calculate Levenshtein distance
        val distance = levenshteinDistance(clean1, clean2)
        val maxLength = maxOf(clean1.length, clean2.length)
        
        if (maxLength == 0) return 1.0
        
        return 1.0 - (distance.toDouble() / maxLength)
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val len1 = s1.length
        val len2 = s2.length
        val dp = Array(len1 + 1) { IntArray(len2 + 1) }
        
        for (i in 0..len1) dp[i][0] = i
        for (j in 0..len2) dp[0][j] = j
        
        for (i in 1..len1) {
            for (j in 1..len2) {
                dp[i][j] = if (s1[i - 1] == s2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }
        
        return dp[len1][len2]
    }
    
    private fun determineMatchType(
        cleanedName: String,
        exerciseName: String,
        searchableTerms: List<String>
    ): MatchType {
        val cleanExerciseName = cleanExerciseName(exerciseName)
        
        return when {
            cleanedName == cleanExerciseName -> MatchType.EXACT
            normalizeExerciseName(cleanedName) == normalizeExerciseName(exerciseName) -> MatchType.NORMALIZED
            searchableTerms.any { it.equals(cleanedName, ignoreCase = true) } -> MatchType.SEMANTIC
            calculateSimilarity(cleanedName, exerciseName) >= 0.8 -> MatchType.FUZZY
            else -> MatchType.CATEGORY_BASED
        }
    }
}