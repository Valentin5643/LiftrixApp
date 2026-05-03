package com.example.liftrix.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

/**
 * Room entity for storing MET (Metabolic Equivalent of Task) values for exercise types
 * 
 * Contains research-backed MET coefficients for accurate calorie calculations:
 * - Exercise-specific MET values from fitness industry standards
 * - Intensity multipliers for different exercise variations
 * - Category-based grouping for efficient lookups
 * 
 * MET Value Standards:
 * - 1.0 MET = Resting metabolic rate
 * - 3.0-4.0 MET = Light activity (walking, light resistance)
 * - 5.0-7.0 MET = Moderate activity (moderate resistance, cycling)
 * - 8.0+ MET = Vigorous activity (running, heavy resistance)
 * 
 * Data Sources:
 * - American College of Sports Medicine (ACSM) guidelines
 * - Compendium of Physical Activities research
 * - Exercise physiology standards for resistance training
 */
@Entity(
    tableName = "met_data",
    indices = [
        Index(value = ["exercise_type"]),
        Index(value = ["exercise_category"]),
        Index(value = ["exercise_type", "exercise_category"])
    ]
)
data class MetDataEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "exercise_type")
    val exerciseType: String, // e.g., "bench_press", "deadlift", "squats"
    
    @ColumnInfo(name = "exercise_category")
    val exerciseCategory: String, // e.g., "CHEST", "BACK", "LEGS"
    
    @ColumnInfo(name = "met_coefficient")
    val metCoefficient: Float, // Base MET value for the exercise
    
    @ColumnInfo(name = "intensity_multiplier")
    val intensityMultiplier: Float, // Multiplier for different intensities (0.8-1.5)
    
    @ColumnInfo(name = "equipment_type")
    val equipmentType: String?, // e.g., "barbell", "dumbbell", "bodyweight"
    
    @ColumnInfo(name = "description")
    val description: String, // Human-readable description
    
    @ColumnInfo(name = "research_source")
    val researchSource: String, // Citation or reference source
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        // MET value ranges for validation
        const val MIN_MET_VALUE = 1.0f
        const val MAX_MET_VALUE = 15.0f
        
        // Intensity multiplier ranges
        const val MIN_INTENSITY_MULTIPLIER = 0.5f
        const val MAX_INTENSITY_MULTIPLIER = 2.0f
        
        // Common exercise types for standardization
        const val EXERCISE_TYPE_BENCH_PRESS = "bench_press"
        const val EXERCISE_TYPE_DEADLIFT = "deadlift"
        const val EXERCISE_TYPE_SQUATS = "squats"
        const val EXERCISE_TYPE_PULL_UPS = "pull_ups"
        const val EXERCISE_TYPE_PUSH_UPS = "push_ups"
        const val EXERCISE_TYPE_OVERHEAD_PRESS = "overhead_press"
        const val EXERCISE_TYPE_ROWS = "rows"
        const val EXERCISE_TYPE_BICEP_CURLS = "bicep_curls"
        const val EXERCISE_TYPE_TRICEP_EXTENSIONS = "tricep_extensions"
        const val EXERCISE_TYPE_LATERAL_RAISES = "lateral_raises"
        const val EXERCISE_TYPE_LUNGES = "lunges"
        const val EXERCISE_TYPE_LEG_PRESS = "leg_press"
        const val EXERCISE_TYPE_CALF_RAISES = "calf_raises"
        const val EXERCISE_TYPE_PLANKS = "planks"
        const val EXERCISE_TYPE_CARDIO_LIGHT = "cardio_light"
        const val EXERCISE_TYPE_CARDIO_MODERATE = "cardio_moderate"
        const val EXERCISE_TYPE_CARDIO_VIGOROUS = "cardio_vigorous"
        
        // Equipment types
        const val EQUIPMENT_BARBELL = "barbell"
        const val EQUIPMENT_DUMBBELL = "dumbbell"
        const val EQUIPMENT_BODYWEIGHT = "bodyweight"
        const val EQUIPMENT_MACHINE = "machine"
        const val EQUIPMENT_CABLE = "cable"
        const val EQUIPMENT_KETTLEBELL = "kettlebell"
        
        // Research sources
        const val SOURCE_ACSM = "ACSM Guidelines for Exercise Testing and Prescription"
        const val SOURCE_COMPENDIUM = "Compendium of Physical Activities"
        const val SOURCE_EXERCISE_PHYSIOLOGY = "Exercise Physiology Research"
    }
    
    init {
        require(id.isNotBlank()) { "MetData ID cannot be blank" }
        require(exerciseType.isNotBlank()) { "Exercise type cannot be blank" }
        require(exerciseCategory.isNotBlank()) { "Exercise category cannot be blank" }
        require(metCoefficient >= MIN_MET_VALUE && metCoefficient <= MAX_MET_VALUE) {
            "MET coefficient must be between $MIN_MET_VALUE and $MAX_MET_VALUE: $metCoefficient"
        }
        require(intensityMultiplier >= MIN_INTENSITY_MULTIPLIER && intensityMultiplier <= MAX_INTENSITY_MULTIPLIER) {
            "Intensity multiplier must be between $MIN_INTENSITY_MULTIPLIER and $MAX_INTENSITY_MULTIPLIER: $intensityMultiplier"
        }
        require(description.isNotBlank()) { "Description cannot be blank" }
        require(researchSource.isNotBlank()) { "Research source cannot be blank" }
    }
    
    /**
     * Calculates effective MET value with intensity adjustment
     */
    fun getEffectiveMET(intensityFactor: Float = 1.0f): Float {
        val adjustedMultiplier = intensityMultiplier * intensityFactor
        return (metCoefficient * adjustedMultiplier).coerceIn(MIN_MET_VALUE, MAX_MET_VALUE)
    }
    
    /**
     * Checks if this MET data applies to a specific exercise category
     */
    fun appliesToCategory(category: String): Boolean {
        return exerciseCategory.equals(category, ignoreCase = true)
    }
    
    /**
     * Checks if this MET data applies to a specific equipment type
     */
    fun appliesToEquipment(equipment: String?): Boolean {
        return equipmentType == null || equipmentType.equals(equipment, ignoreCase = true)
    }
}