package com.example.liftrix.domain.model

import androidx.compose.ui.graphics.vector.ImageVector
import com.example.liftrix.ui.components.cards.Trend

/**
 * Sealed class representing different types of card data for enhanced UI components
 * Supports StatCard and ActivityCard integration with existing ViewModels
 */
sealed class CardData {
    
    /**
     * Data for StatCard component - large stat display cards
     */
    data class Stats(
        val title: String,
        val value: String,
        val subtitle: String? = null,
        val trend: Trend? = null,
        val icon: ImageVector? = null,
        val contentDescription: String? = null
    ) : CardData()
    
    /**
     * Data for ActivityCard component - smaller activity panels
     */
    data class Activity(
        val title: String,
        val subtitle: String,
        val icon: ImageVector,
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