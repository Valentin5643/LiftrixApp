package com.example.liftrix.data.seed

import com.example.liftrix.data.local.entity.MetDataEntity
import com.example.liftrix.data.local.entity.MetDataEntity.Companion.EQUIPMENT_BARBELL
import com.example.liftrix.data.local.entity.MetDataEntity.Companion.EQUIPMENT_BODYWEIGHT
import com.example.liftrix.data.local.entity.MetDataEntity.Companion.EQUIPMENT_DUMBBELL
import com.example.liftrix.data.local.entity.MetDataEntity.Companion.EQUIPMENT_MACHINE
import com.example.liftrix.data.local.entity.MetDataEntity.Companion.EQUIPMENT_CABLE
import com.example.liftrix.data.local.entity.MetDataEntity.Companion.EQUIPMENT_KETTLEBELL
import com.example.liftrix.data.local.entity.MetDataEntity.Companion.SOURCE_ACSM
import com.example.liftrix.data.local.entity.MetDataEntity.Companion.SOURCE_COMPENDIUM
import com.example.liftrix.data.local.entity.MetDataEntity.Companion.SOURCE_EXERCISE_PHYSIOLOGY

/**
 * Seed data for MET (Metabolic Equivalent of Task) values based on fitness industry research
 * 
 * Data Sources:
 * - American College of Sports Medicine (ACSM) Guidelines
 * - Compendium of Physical Activities (2011 & 2019 updates)
 * - Exercise Physiology research from peer-reviewed sources
 * - International Association of Fitness Professionals (IAFP) standards
 * 
 * MET Value Methodology:
 * - 1 MET = 3.5 mL O2/kg/min (resting metabolic rate)
 * - Values derived from controlled laboratory studies
 * - Intensity multipliers account for load, speed, and form variations
 * - Category-specific adjustments for movement patterns
 */
object MetDataSeed {
    
    /**
     * Comprehensive MET data for resistance training and cardio exercises
     * Research-backed values with intensity multipliers for accuracy
     */
    fun getMetDataEntities(): List<MetDataEntity> = listOf(
        
        // CHEST EXERCISES
        MetDataEntity(
            id = "chest_bench_press_barbell",
            exerciseType = "bench_press",
            exerciseCategory = "CHEST",
            metCoefficient = 6.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BARBELL,
            description = "Barbell bench press - moderate to vigorous intensity",
            researchSource = SOURCE_ACSM
        ),
        
        MetDataEntity(
            id = "chest_bench_press_dumbbell",
            exerciseType = "bench_press",
            exerciseCategory = "CHEST",
            metCoefficient = 5.5f,
            intensityMultiplier = 1.1f,
            equipmentType = EQUIPMENT_DUMBBELL,
            description = "Dumbbell bench press - increased stabilization requirement",
            researchSource = SOURCE_ACSM
        ),
        
        MetDataEntity(
            id = "chest_push_ups",
            exerciseType = "push_ups",
            exerciseCategory = "CHEST",
            metCoefficient = 4.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Push-ups - bodyweight chest exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "chest_incline_press",
            exerciseType = "incline_press",
            exerciseCategory = "CHEST",
            metCoefficient = 5.8f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BARBELL,
            description = "Incline barbell press - upper chest focus",
            researchSource = SOURCE_ACSM
        ),
        
        MetDataEntity(
            id = "chest_flyes",
            exerciseType = "flyes",
            exerciseCategory = "CHEST",
            metCoefficient = 4.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_DUMBBELL,
            description = "Dumbbell flyes - isolation chest exercise",
            researchSource = SOURCE_EXERCISE_PHYSIOLOGY
        ),
        
        // BACK EXERCISES
        MetDataEntity(
            id = "back_deadlift",
            exerciseType = "deadlift",
            exerciseCategory = "BACK",
            metCoefficient = 7.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BARBELL,
            description = "Conventional deadlift - full body compound movement",
            researchSource = SOURCE_ACSM
        ),
        
        MetDataEntity(
            id = "back_pull_ups",
            exerciseType = "pull_ups",
            exerciseCategory = "BACK",
            metCoefficient = 6.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Pull-ups - bodyweight back exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "back_rows_barbell",
            exerciseType = "rows",
            exerciseCategory = "BACK",
            metCoefficient = 5.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BARBELL,
            description = "Barbell rows - horizontal pulling movement",
            researchSource = SOURCE_ACSM
        ),
        
        MetDataEntity(
            id = "back_rows_dumbbell",
            exerciseType = "rows",
            exerciseCategory = "BACK",
            metCoefficient = 5.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_DUMBBELL,
            description = "Dumbbell rows - unilateral back exercise",
            researchSource = SOURCE_ACSM
        ),
        
        MetDataEntity(
            id = "back_lat_pulldown",
            exerciseType = "lat_pulldown",
            exerciseCategory = "BACK",
            metCoefficient = 4.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_MACHINE,
            description = "Lat pulldown - machine-based back exercise",
            researchSource = SOURCE_ACSM
        ),
        
        // LEGS EXERCISES
        MetDataEntity(
            id = "legs_squats_barbell",
            exerciseType = "squats",
            exerciseCategory = "LEGS",
            metCoefficient = 7.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BARBELL,
            description = "Barbell squats - compound lower body movement",
            researchSource = SOURCE_ACSM
        ),
        
        MetDataEntity(
            id = "legs_squats_bodyweight",
            exerciseType = "squats",
            exerciseCategory = "LEGS",
            metCoefficient = 4.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Bodyweight squats - lower body exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "legs_lunges",
            exerciseType = "lunges",
            exerciseCategory = "LEGS",
            metCoefficient = 5.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Lunges - unilateral leg exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "legs_leg_press",
            exerciseType = "leg_press",
            exerciseCategory = "LEGS",
            metCoefficient = 5.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_MACHINE,
            description = "Leg press - machine-based quad exercise",
            researchSource = SOURCE_ACSM
        ),
        
        MetDataEntity(
            id = "legs_calf_raises",
            exerciseType = "calf_raises",
            exerciseCategory = "LEGS",
            metCoefficient = 3.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Calf raises - lower leg isolation exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        // SHOULDERS EXERCISES
        MetDataEntity(
            id = "shoulders_overhead_press",
            exerciseType = "overhead_press",
            exerciseCategory = "SHOULDERS",
            metCoefficient = 5.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BARBELL,
            description = "Overhead press - vertical pressing movement",
            researchSource = SOURCE_ACSM
        ),
        
        MetDataEntity(
            id = "shoulders_lateral_raises",
            exerciseType = "lateral_raises",
            exerciseCategory = "SHOULDERS",
            metCoefficient = 3.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_DUMBBELL,
            description = "Lateral raises - shoulder isolation exercise",
            researchSource = SOURCE_EXERCISE_PHYSIOLOGY
        ),
        
        MetDataEntity(
            id = "shoulders_front_raises",
            exerciseType = "front_raises",
            exerciseCategory = "SHOULDERS",
            metCoefficient = 3.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_DUMBBELL,
            description = "Front raises - anterior deltoid isolation",
            researchSource = SOURCE_EXERCISE_PHYSIOLOGY
        ),
        
        MetDataEntity(
            id = "shoulders_rear_delt_flyes",
            exerciseType = "rear_delt_flyes",
            exerciseCategory = "SHOULDERS",
            metCoefficient = 3.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_DUMBBELL,
            description = "Rear deltoid flyes - posterior deltoid isolation",
            researchSource = SOURCE_EXERCISE_PHYSIOLOGY
        ),
        
        // ARMS EXERCISES
        MetDataEntity(
            id = "arms_bicep_curls",
            exerciseType = "bicep_curls",
            exerciseCategory = "BICEPS",
            metCoefficient = 3.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_DUMBBELL,
            description = "Bicep curls - isolation arm exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "arms_tricep_extensions",
            exerciseType = "tricep_extensions",
            exerciseCategory = "TRICEPS",
            metCoefficient = 3.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_DUMBBELL,
            description = "Tricep extensions - isolation arm exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "arms_tricep_dips",
            exerciseType = "tricep_dips",
            exerciseCategory = "TRICEPS",
            metCoefficient = 4.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Tricep dips - bodyweight arm exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "arms_hammer_curls",
            exerciseType = "hammer_curls",
            exerciseCategory = "BICEPS",
            metCoefficient = 3.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_DUMBBELL,
            description = "Hammer curls - bicep and forearm exercise",
            researchSource = SOURCE_EXERCISE_PHYSIOLOGY
        ),
        
        // CORE EXERCISES
        MetDataEntity(
            id = "core_planks",
            exerciseType = "planks",
            exerciseCategory = "CORE",
            metCoefficient = 3.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Planks - isometric core exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "core_crunches",
            exerciseType = "crunches",
            exerciseCategory = "CORE",
            metCoefficient = 3.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Crunches - abdominal exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "core_russian_twists",
            exerciseType = "russian_twists",
            exerciseCategory = "CORE",
            metCoefficient = 3.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Russian twists - oblique exercise",
            researchSource = SOURCE_EXERCISE_PHYSIOLOGY
        ),
        
        MetDataEntity(
            id = "core_mountain_climbers",
            exerciseType = "mountain_climbers",
            exerciseCategory = "CORE",
            metCoefficient = 5.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Mountain climbers - dynamic core exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        // GLUTES EXERCISES
        MetDataEntity(
            id = "glutes_hip_thrusts",
            exerciseType = "hip_thrusts",
            exerciseCategory = "GLUTES",
            metCoefficient = 4.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BARBELL,
            description = "Hip thrusts - glute-focused exercise",
            researchSource = SOURCE_EXERCISE_PHYSIOLOGY
        ),
        
        MetDataEntity(
            id = "glutes_glute_bridges",
            exerciseType = "glute_bridges",
            exerciseCategory = "GLUTES",
            metCoefficient = 3.5f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Glute bridges - bodyweight glute exercise",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        // CARDIO EXERCISES
        MetDataEntity(
            id = "cardio_walking_light",
            exerciseType = "cardio_light",
            exerciseCategory = "CARDIO",
            metCoefficient = 3.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Light walking - low intensity cardio",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "cardio_jogging_moderate",
            exerciseType = "cardio_moderate",
            exerciseCategory = "CARDIO",
            metCoefficient = 7.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Moderate jogging - moderate intensity cardio",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "cardio_running_vigorous",
            exerciseType = "cardio_vigorous",
            exerciseCategory = "CARDIO",
            metCoefficient = 10.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_BODYWEIGHT,
            description = "Vigorous running - high intensity cardio",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "cardio_cycling_moderate",
            exerciseType = "cycling_moderate",
            exerciseCategory = "CARDIO",
            metCoefficient = 6.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_MACHINE,
            description = "Moderate cycling - stationary bike",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        MetDataEntity(
            id = "cardio_elliptical",
            exerciseType = "elliptical",
            exerciseCategory = "CARDIO",
            metCoefficient = 5.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_MACHINE,
            description = "Elliptical machine - low impact cardio",
            researchSource = SOURCE_COMPENDIUM
        ),
        
        // KETTLEBELL EXERCISES
        MetDataEntity(
            id = "kettlebell_swings",
            exerciseType = "kettlebell_swings",
            exerciseCategory = "LEGS",
            metCoefficient = 8.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_KETTLEBELL,
            description = "Kettlebell swings - explosive hip hinge movement",
            researchSource = SOURCE_EXERCISE_PHYSIOLOGY
        ),
        
        MetDataEntity(
            id = "kettlebell_turkish_getups",
            exerciseType = "turkish_getups",
            exerciseCategory = "CORE",
            metCoefficient = 6.0f,
            intensityMultiplier = 1.0f,
            equipmentType = EQUIPMENT_KETTLEBELL,
            description = "Turkish get-ups - full body functional movement",
            researchSource = SOURCE_EXERCISE_PHYSIOLOGY
        ),
        
        // GENERAL RESISTANCE TRAINING CATEGORIES
        MetDataEntity(
            id = "general_resistance_light",
            exerciseType = "general_resistance",
            exerciseCategory = "GENERAL",
            metCoefficient = 3.0f,
            intensityMultiplier = 1.0f,
            equipmentType = null,
            description = "Light resistance training - general activities",
            researchSource = SOURCE_ACSM
        ),
        
        MetDataEntity(
            id = "general_resistance_moderate",
            exerciseType = "general_resistance",
            exerciseCategory = "GENERAL",
            metCoefficient = 5.0f,
            intensityMultiplier = 1.0f,
            equipmentType = null,
            description = "Moderate resistance training - general activities",
            researchSource = SOURCE_ACSM
        ),
        
        MetDataEntity(
            id = "general_resistance_vigorous",
            exerciseType = "general_resistance",
            exerciseCategory = "GENERAL",
            metCoefficient = 6.0f,
            intensityMultiplier = 1.0f,
            equipmentType = null,
            description = "Vigorous resistance training - general activities",
            researchSource = SOURCE_ACSM
        )
    )
    
    /**
     * Gets default MET values for common exercise categories
     * Used as fallback when specific exercise MET data is not available
     */
    fun getDefaultMetForCategory(category: String): Float = when (category.uppercase()) {
        "CHEST" -> 5.0f
        "BACK" -> 5.5f
        "LEGS" -> 6.0f
        "SHOULDERS" -> 4.5f
        "ARMS", "BICEPS", "TRICEPS" -> 3.0f
        "CORE" -> 3.5f
        "GLUTES" -> 4.0f
        "CARDIO" -> 7.0f
        else -> 4.0f // General moderate resistance training
    }
    
    /**
     * Gets intensity multiplier based on perceived exertion or load percentage
     */
    fun getIntensityMultiplier(rpe: Int? = null, loadPercentage: Int? = null): Float = when {
        rpe != null -> when (rpe) {
            in 1..3 -> 0.8f  // Very light
            in 4..5 -> 1.0f  // Light to moderate
            in 6..7 -> 1.2f  // Moderate to hard
            in 8..9 -> 1.4f  // Hard to very hard
            10 -> 1.6f       // Maximum effort
            else -> 1.0f
        }
        loadPercentage != null -> when (loadPercentage) {
            in 0..50 -> 0.9f   // Light load
            in 51..70 -> 1.0f  // Moderate load
            in 71..85 -> 1.2f  // Heavy load
            in 86..100 -> 1.4f // Very heavy load
            else -> 1.0f
        }
        else -> 1.0f // Default moderate intensity
    }
}