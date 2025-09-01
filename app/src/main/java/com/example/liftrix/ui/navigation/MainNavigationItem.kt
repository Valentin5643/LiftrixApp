package com.example.liftrix.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation destinations for the main bottom navigation tabs.
 * 
 * Defines the four primary navigation destinations with their routes, labels, and icons.
 * Uses Material3 design system with filled icons for selected state and outlined for unselected.
 * 
 * @param route The navigation route string used by NavController
 * @param label The display label for the navigation tab
 * @param icon The default (outlined) icon for unselected state
 * @param selectedIcon The filled icon for selected state
 */
enum class MainNavigationItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon
) {
    HOME(
        route = "home",
        label = "Home",
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home
    ),
    WORKOUT(
        route = "workout", 
        label = "Workout",
        icon = Icons.Outlined.FitnessCenter,
        selectedIcon = Icons.Filled.FitnessCenter
    ),
    PROGRESS(
        route = "progress",
        label = "Progress", 
        icon = Icons.Outlined.TrendingUp,
        selectedIcon = Icons.Filled.TrendingUp
    ),
} 