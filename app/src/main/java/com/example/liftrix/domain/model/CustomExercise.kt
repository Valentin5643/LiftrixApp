package com.example.liftrix.domain.model

import java.time.Instant

/**
 * Domain model representing a user-created custom exercise with validation and business rules
 */
data class CustomExercise(
    val id: CustomExerciseId,
    val userId: String,
    val name: String,
    val description: String? = null,
    val exerciseType: ExerciseType,
    val primaryMuscle: ExerciseCategory,
    val secondaryMuscles: Set<ExerciseCategory> = emptySet(),
    val equipment: Equipment,
    val difficulty: Int? = null,
    val instructions: List<String> = emptyList(),
    val mainImageUrl: String? = null,
    val additionalImageUrls: List<String> = emptyList(),
    val videoUrl: String? = null,
    val tags: List<String> = emptyList(),
    val categories: List<ExerciseCategory> = emptyList(),
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(name.isNotBlank()) { "Exercise name cannot be blank" }
        require(name.length <= MAX_NAME_LENGTH) { 
            "Exercise name cannot exceed $MAX_NAME_LENGTH characters: ${name.length}" 
        }
        
        difficulty?.let { diff ->
            require(diff in MIN_DIFFICULTY..MAX_DIFFICULTY) { 
                "Difficulty must be between $MIN_DIFFICULTY and $MAX_DIFFICULTY: $diff" 
            }
        }
        
        notes?.let { note ->
            require(note.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${note.length}" 
            }
        }
        
        description?.let { desc ->
            require(desc.length <= MAX_DESCRIPTION_LENGTH) {
                "Description cannot exceed $MAX_DESCRIPTION_LENGTH characters: ${desc.length}"
            }
        }
        
        require(instructions.size <= MAX_INSTRUCTIONS) {
            "Cannot have more than $MAX_INSTRUCTIONS instructions: ${instructions.size}"
        }
        
        instructions.forEach { instruction ->
            require(instruction.length <= MAX_INSTRUCTION_LENGTH) {
                "Each instruction cannot exceed $MAX_INSTRUCTION_LENGTH characters: ${instruction.length}"
            }
        }
        
        require(additionalImageUrls.size <= MAX_ADDITIONAL_IMAGES) {
            "Cannot have more than $MAX_ADDITIONAL_IMAGES additional images: ${additionalImageUrls.size}"
        }
        
        require(tags.size <= MAX_TAGS) {
            "Cannot have more than $MAX_TAGS tags: ${tags.size}"
        }
        
        tags.forEach { tag ->
            require(tag.length <= MAX_TAG_LENGTH) {
                "Each tag cannot exceed $MAX_TAG_LENGTH characters: ${tag.length}"
            }
        }
        
        require(categories.size <= MAX_CATEGORIES) {
            "Cannot have more than $MAX_CATEGORIES categories: ${categories.size}"
        }
        
        require(!secondaryMuscles.contains(primaryMuscle)) {
            "Primary muscle group cannot be in secondary muscles: $primaryMuscle"
        }
        
        require(secondaryMuscles.size <= MAX_SECONDARY_MUSCLES) {
            "Cannot have more than $MAX_SECONDARY_MUSCLES secondary muscles: ${secondaryMuscles.size}"
        }
    }
    
    companion object {
        const val MAX_NAME_LENGTH: Int = 100
        const val MAX_DESCRIPTION_LENGTH: Int = 300
        const val MAX_NOTES_LENGTH: Int = 500
        const val MAX_INSTRUCTION_LENGTH: Int = 200
        const val MAX_INSTRUCTIONS: Int = 10
        const val MIN_DIFFICULTY: Int = 1
        const val MAX_DIFFICULTY: Int = 10
        const val MAX_SECONDARY_MUSCLES: Int = 3
        const val MAX_ADDITIONAL_IMAGES: Int = 5
        const val MAX_TAGS: Int = 10
        const val MAX_TAG_LENGTH: Int = 30
        const val MAX_CATEGORIES: Int = 5
        
        /**
         * Creates a new CustomExercise with validation
         */
        fun create(
            userId: String,
            name: String,
            description: String? = null,
            exerciseType: ExerciseType,
            primaryMuscle: ExerciseCategory,
            equipment: Equipment,
            secondaryMuscles: Set<ExerciseCategory> = emptySet(),
            difficulty: Int? = null,
            instructions: List<String> = emptyList(),
            mainImageUrl: String? = null,
            additionalImageUrls: List<String> = emptyList(),
            videoUrl: String? = null,
            tags: List<String> = emptyList(),
            categories: List<ExerciseCategory> = emptyList(),
            notes: String? = null
        ): CustomExercise {
            val now = Instant.now()
            return CustomExercise(
                id = CustomExerciseId.generate(),
                userId = userId,
                name = name.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                exerciseType = exerciseType,
                primaryMuscle = primaryMuscle,
                secondaryMuscles = secondaryMuscles,
                equipment = equipment,
                difficulty = difficulty,
                instructions = instructions.map { it.trim() }.filter { it.isNotBlank() },
                mainImageUrl = mainImageUrl,
                additionalImageUrls = additionalImageUrls,
                videoUrl = videoUrl?.trim()?.takeIf { it.isNotBlank() },
                tags = tags.map { it.trim() }.filter { it.isNotBlank() },
                categories = categories,
                notes = notes?.trim()?.takeIf { it.isNotBlank() },
                createdAt = now,
                updatedAt = now
            )
        }
    }
    
    /**
     * Updates the exercise name with validation
     */
    fun updateName(newName: String): CustomExercise {
        require(newName.isNotBlank()) { "Exercise name cannot be blank" }
        require(newName.trim().length <= MAX_NAME_LENGTH) { 
            "Exercise name cannot exceed $MAX_NAME_LENGTH characters" 
        }
        
        return copy(
            name = newName.trim(),
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Adds a secondary muscle group with validation
     */
    fun addSecondaryMuscle(muscle: ExerciseCategory): CustomExercise {
        require(muscle != primaryMuscle) { 
            "Cannot add primary muscle as secondary: $muscle" 
        }
        require(!secondaryMuscles.contains(muscle)) { 
            "Secondary muscle already exists: $muscle" 
        }
        require(secondaryMuscles.size < MAX_SECONDARY_MUSCLES) { 
            "Cannot add more than $MAX_SECONDARY_MUSCLES secondary muscles" 
        }
        
        return copy(
            secondaryMuscles = secondaryMuscles + muscle,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Removes a secondary muscle group
     */
    fun removeSecondaryMuscle(muscle: ExerciseCategory): CustomExercise {
        require(secondaryMuscles.contains(muscle)) { 
            "Secondary muscle does not exist: $muscle" 
        }
        
        return copy(
            secondaryMuscles = secondaryMuscles - muscle,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the difficulty level with validation
     */
    fun updateDifficulty(newDifficulty: Int?): CustomExercise {
        newDifficulty?.let { diff ->
            require(diff in MIN_DIFFICULTY..MAX_DIFFICULTY) { 
                "Difficulty must be between $MIN_DIFFICULTY and $MAX_DIFFICULTY: $diff" 
            }
        }
        
        return copy(
            difficulty = newDifficulty,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the notes with validation
     */
    fun updateNotes(newNotes: String?): CustomExercise {
        val trimmedNotes = newNotes?.trim()?.takeIf { it.isNotBlank() }
        
        trimmedNotes?.let { notes ->
            require(notes.length <= MAX_NOTES_LENGTH) { 
                "Notes cannot exceed $MAX_NOTES_LENGTH characters: ${notes.length}" 
            }
        }
        
        return copy(
            notes = trimmedNotes,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the equipment type
     */
    fun updateEquipment(newEquipment: Equipment): CustomExercise {
        return copy(
            equipment = newEquipment,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Gets all muscle groups (primary + secondary)
     */
    fun getAllMuscles(): Set<ExerciseCategory> = setOf(primaryMuscle) + secondaryMuscles
    
    /**
     * Checks if this exercise targets a specific muscle group
     */
    fun targetsMuscle(muscle: ExerciseCategory): Boolean = 
        primaryMuscle == muscle || secondaryMuscles.contains(muscle)
    
    /**
     * Checks if this exercise is suitable for specific equipment
     */
    fun isCompatibleWith(userEquipment: Set<Equipment>): Boolean = 
        userEquipment.contains(equipment)
    
    /**
     * Updates the description with validation
     */
    fun updateDescription(newDescription: String?): CustomExercise {
        val trimmedDescription = newDescription?.trim()?.takeIf { it.isNotBlank() }
        
        trimmedDescription?.let { desc ->
            require(desc.length <= MAX_DESCRIPTION_LENGTH) {
                "Description cannot exceed $MAX_DESCRIPTION_LENGTH characters: ${desc.length}"
            }
        }
        
        return copy(
            description = trimmedDescription,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the exercise type
     */
    fun updateExerciseType(newExerciseType: ExerciseType): CustomExercise {
        return copy(
            exerciseType = newExerciseType,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Adds an instruction with validation
     */
    fun addInstruction(instruction: String): CustomExercise {
        val trimmedInstruction = instruction.trim()
        require(trimmedInstruction.isNotBlank()) { "Instruction cannot be blank" }
        require(trimmedInstruction.length <= MAX_INSTRUCTION_LENGTH) {
            "Instruction cannot exceed $MAX_INSTRUCTION_LENGTH characters: ${trimmedInstruction.length}"
        }
        require(instructions.size < MAX_INSTRUCTIONS) {
            "Cannot add more than $MAX_INSTRUCTIONS instructions"
        }
        
        return copy(
            instructions = instructions + trimmedInstruction,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Removes an instruction by index
     */
    fun removeInstruction(index: Int): CustomExercise {
        require(index in instructions.indices) { "Invalid instruction index: $index" }
        
        return copy(
            instructions = instructions.filterIndexed { i, _ -> i != index },
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the main image URL
     */
    fun updateMainImageUrl(imageUrl: String?): CustomExercise {
        return copy(
            mainImageUrl = imageUrl,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Adds an additional image URL with validation
     */
    fun addAdditionalImage(imageUrl: String): CustomExercise {
        require(imageUrl.isNotBlank()) { "Image URL cannot be blank" }
        require(additionalImageUrls.size < MAX_ADDITIONAL_IMAGES) {
            "Cannot add more than $MAX_ADDITIONAL_IMAGES additional images"
        }
        require(!additionalImageUrls.contains(imageUrl)) {
            "Image URL already exists: $imageUrl"
        }
        
        return copy(
            additionalImageUrls = additionalImageUrls + imageUrl,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Removes an additional image URL
     */
    fun removeAdditionalImage(imageUrl: String): CustomExercise {
        require(additionalImageUrls.contains(imageUrl)) {
            "Image URL does not exist: $imageUrl"
        }
        
        return copy(
            additionalImageUrls = additionalImageUrls - imageUrl,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Updates the video URL with validation
     */
    fun updateVideoUrl(newVideoUrl: String?): CustomExercise {
        val trimmedUrl = newVideoUrl?.trim()?.takeIf { it.isNotBlank() }
        
        return copy(
            videoUrl = trimmedUrl,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Adds a tag with validation
     */
    fun addTag(tag: String): CustomExercise {
        val trimmedTag = tag.trim()
        require(trimmedTag.isNotBlank()) { "Tag cannot be blank" }
        require(trimmedTag.length <= MAX_TAG_LENGTH) {
            "Tag cannot exceed $MAX_TAG_LENGTH characters: ${trimmedTag.length}"
        }
        require(tags.size < MAX_TAGS) {
            "Cannot add more than $MAX_TAGS tags"
        }
        require(!tags.contains(trimmedTag)) {
            "Tag already exists: $trimmedTag"
        }
        
        return copy(
            tags = tags + trimmedTag,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Removes a tag
     */
    fun removeTag(tag: String): CustomExercise {
        require(tags.contains(tag)) { "Tag does not exist: $tag" }
        
        return copy(
            tags = tags - tag,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Adds a category with validation
     */
    fun addCategory(category: ExerciseCategory): CustomExercise {
        require(categories.size < MAX_CATEGORIES) {
            "Cannot add more than $MAX_CATEGORIES categories"
        }
        require(!categories.contains(category)) {
            "Category already exists: $category"
        }
        
        return copy(
            categories = categories + category,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Removes a category
     */
    fun removeCategory(category: ExerciseCategory): CustomExercise {
        require(categories.contains(category)) { "Category does not exist: $category" }
        
        return copy(
            categories = categories - category,
            updatedAt = Instant.now()
        )
    }
    
    /**
     * Checks if this exercise matches a search term
     */
    fun matchesSearch(searchTerm: String): Boolean {
        val term = searchTerm.lowercase()
        return name.lowercase().contains(term) ||
                description?.lowercase()?.contains(term) == true ||
                tags.any { it.lowercase().contains(term) } ||
                primaryMuscle.name.lowercase().contains(term) ||
                secondaryMuscles.any { it.name.lowercase().contains(term) } ||
                equipment.name.lowercase().contains(term)
    }
    
    /**
     * Checks if this exercise has any images
     */
    fun hasImages(): Boolean = mainImageUrl != null || additionalImageUrls.isNotEmpty()
    
    /**
     * Gets all image URLs (main + additional)
     */
    fun getAllImageUrls(): List<String> {
        val allImages = mutableListOf<String>()
        mainImageUrl?.let { allImages.add(it) }
        allImages.addAll(additionalImageUrls)
        return allImages
    }
    
    /**
     * Checks if this exercise matches a specific exercise type
     */
    fun isOfType(type: ExerciseType): Boolean = exerciseType == type
    
    /**
     * Checks if this exercise has a specific tag
     */
    fun hasTag(tag: String): Boolean = tags.contains(tag)
    
    /**
     * Checks if this exercise is in a specific category
     */
    fun isInCategory(category: ExerciseCategory): Boolean = categories.contains(category)
} 