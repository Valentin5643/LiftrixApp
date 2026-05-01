package com.example.liftrix.domain.model


/**
 * Domain abstraction for trends in statistics
 */
sealed class TrendData {
    data class Positive(val percentage: Float, val label: String = "increase") : TrendData()
    data class Negative(val percentage: Float, val label: String = "decrease") : TrendData()
    data class Neutral(val label: String = "no change") : TrendData()
}

/**
 * Domain abstraction for icons - UI layer maps to actual ImageVector
 */
sealed class IconData {
    object FitnessCenter : IconData()
    object TrendingUp : IconData()
    object Schedule : IconData()
    object LocalFireDepartment : IconData()
    object ChevronRight : IconData()
    data class Custom(val identifier: String) : IconData()
}

/**
 * Sealed class representing different types of card data for enhanced UI components
 * Pure domain model without UI dependencies - UI layer maps these to concrete UI types
 */
sealed class CardData {
    
    /**
     * Data for StatCard component - large stat display cards
     */
    data class Stats(
        val title: String,
        val value: String,
        val subtitle: String? = null,
        val trend: TrendData? = null,
        val icon: IconData? = null,
        val contentDescription: String? = null
    ) : CardData()
    
    /**
     * Data for ActivityCard component - smaller activity panels
     */
    data class Activity(
        val title: String,
        val subtitle: String,
        val icon: IconData,
        val trailing: String? = null,
        val showChevron: Boolean = true,
        val contentDescription: String? = null
    ) : CardData()
}

/**
 * Data class for workout template preview in enhanced creation flow
 */
data class WorkoutTemplatePreview(
    val name: String,
    val description: String? = null,
    val exerciseCount: Int,
    val estimatedDuration: String,
    val targetMuscleGroups: List<String>,
    val difficulty: String,
    val lastUsed: String? = null,
    val isPopular: Boolean = false
) 