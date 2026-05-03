package com.example.liftrix.domain.model

/**
 * Domain model representing an exercise from the exercise library
 */
data class ExerciseLibrary(
    val id: String,
    val name: String,
    val primaryMuscleGroup: ExerciseCategory,
    val equipment: Equipment,
    val secondaryMuscleGroups: List<ExerciseCategory>,
    val movementPattern: String,
    val difficultyLevel: Int,
    val instructions: String?,
    val isCompound: Boolean,
    val searchableTerms: List<String>
) {
    init {
        require(id.isNotBlank()) { "Exercise ID cannot be blank" }
        require(name.isNotBlank()) { "Exercise name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Exercise name cannot exceed $MAX_NAME_LENGTH characters: ${name.length}" 
        }
        require(movementPattern.isNotBlank()) { "Movement pattern cannot be blank" }
        require(difficultyLevel in MIN_DIFFICULTY..MAX_DIFFICULTY) { 
            "Difficulty level must be between $MIN_DIFFICULTY and $MAX_DIFFICULTY: $difficultyLevel" 
        }
        require(!secondaryMuscleGroups.contains(primaryMuscleGroup)) { 
            "Primary muscle group cannot be included in secondary muscle groups" 
        }
        instructions?.let { inst ->
            require(inst.length <= MAX_INSTRUCTIONS_LENGTH) { 
                "Instructions cannot exceed $MAX_INSTRUCTIONS_LENGTH characters: ${inst.length}" 
            }
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        const val MAX_INSTRUCTIONS_LENGTH: Int = 1000
        const val MIN_DIFFICULTY: Int = 1
        const val MAX_DIFFICULTY: Int = 10
    }
    
    /**
     * Checks if this exercise matches the search query using fuzzy matching
     */
    fun matchesQuery(query: String): Boolean {
        val normalizedQuery = query.lowercase().trim()
        if (normalizedQuery.isBlank()) return true
        
        return name.lowercase().contains(normalizedQuery) ||
               searchableTerms.any { term -> term.lowercase().contains(normalizedQuery) } ||
               movementPattern.lowercase().contains(normalizedQuery)
    }
    
    /**
     * Calculates fuzzy match score (0.0 to 1.0, higher is better)
     */
    fun calculateMatchScore(query: String): Double {
        val normalizedQuery = query.lowercase().trim()
        if (normalizedQuery.isBlank()) return 0.0
        
        val normalizedName = name.lowercase()
        
        // Exact match gets highest score
        if (normalizedName == normalizedQuery) return 1.0
        
        // Starts with query gets high score
        if (normalizedName.startsWith(normalizedQuery)) return 0.9
        
        // Contains query gets medium score
        if (normalizedName.contains(normalizedQuery)) return 0.7
        
        // Searchable terms match gets lower score
        val termMatch = searchableTerms.any { term -> term.lowercase().contains(normalizedQuery) }
        if (termMatch) return 0.5
        
        // Movement pattern match gets lowest score
        if (movementPattern.lowercase().contains(normalizedQuery)) return 0.3
        
        return 0.0
    }
}
