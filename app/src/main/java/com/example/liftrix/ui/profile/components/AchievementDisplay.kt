package com.example.liftrix.ui.profile.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.liftrix.domain.model.AchievementType
import com.example.liftrix.domain.model.UserAchievement
import com.example.liftrix.ui.accessibility.AccessibilityEnhancements.enhancedAccessibilitySemantics
import com.example.liftrix.ui.common.AccessibilityUtils.ensureMinimumTouchTarget
import com.example.liftrix.ui.theme.LiftrixSpacing
import timber.log.Timber
import java.time.format.DateTimeFormatter

/**
 * AchievementDisplay - Badge-style achievement visualization component
 * 
 * Displays user achievements with badge-style cards, unlock animations,
 * and accessibility support. Provides filtering by achievement type
 * and detail modal display.
 * 
 * Features:
 * - Badge-style achievement cards with unlock animations
 * - Achievement type filtering (Workout Milestones, Streaks, Consistency, First-Time)
 * - Achievement progress indication for near-unlocks
 * - Accessibility support with semantic descriptions
 * - Achievement detail modal with unlock conditions
 * - Responsive grid layout for different screen sizes
 * - Material 3 design with Persian Green/Tiffany Blue color system
 * 
 * Design System Compliance:
 * - Uses semantic color roles from MaterialTheme
 * - Implements LiftrixSpacing semantic tokens  
 * - WCAG 2.1 AA accessibility compliance
 * - Persian Green/Tiffany Blue badge color system
 * - Follows established animation patterns
 */
@Composable
fun AchievementDisplay(
    achievements: List<com.example.liftrix.domain.model.UserAchievement>,
    modifier: Modifier = Modifier,
    showTypeFilter: Boolean = true,
    maxVisibleCount: Int? = null,
    onAchievementClick: ((com.example.liftrix.domain.model.UserAchievement) -> Unit)? = null
) {
    var selectedType by remember { mutableStateOf<AchievementType?>(null) }
    var showAchievementDetail by remember { mutableStateOf<UserAchievement?>(null) }
    
    // Filter achievements by type
    val filteredAchievements = remember(achievements, selectedType) {
        if (selectedType != null) {
            achievements.filter { it.achievementType == selectedType }
        } else {
            achievements
        }.let { filtered ->
            if (maxVisibleCount != null) {
                filtered.take(maxVisibleCount)
            } else {
                filtered
            }
        }
    }
    
    Timber.d("AchievementDisplay: Showing ${filteredAchievements.size} achievements (filtered by: $selectedType)")
    
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
    ) {
        // Type filter chips
        if (showTypeFilter && achievements.isNotEmpty()) {
            AchievementTypeFilter(
                achievements = achievements,
                selectedType = selectedType,
                onTypeSelected = { selectedType = it }
            )
        }
        
        // Achievement grid
        if (filteredAchievements.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 120.dp),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
                horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(filteredAchievements) { achievement ->
                    AchievementBadge(
                        achievement = achievement,
                        onClick = {
                            onAchievementClick?.invoke(achievement) ?: run {
                                showAchievementDetail = achievement
                            }
                        }
                    )
                }
            }
        } else {
            EmptyAchievementState(selectedType = selectedType)
        }
        
        // Show all achievements link
        if (maxVisibleCount != null && achievements.size > maxVisibleCount) {
            TextButton(
                onClick = { /* Navigate to full achievements screen */ },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("View All ${achievements.size} Achievements")
            }
        }
    }
    
    // Achievement detail modal
    showAchievementDetail?.let { achievement ->
        AchievementDetailModal(
            achievement = achievement,
            onDismiss = { showAchievementDetail = null }
        )
    }
}

@Composable
private fun AchievementTypeFilter(
    achievements: List<UserAchievement>,
    selectedType: AchievementType?,
    onTypeSelected: (AchievementType?) -> Unit
) {
    val availableTypes = remember(achievements) {
        achievements.map { it.achievementType }.distinct().sorted()
    }
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementPaddingSmall),
        contentPadding = PaddingValues(horizontal = LiftrixSpacing.elementPaddingSmall)
    ) {
        item {
            FilterChip(
                selected = selectedType == null,
                onClick = { onTypeSelected(null) },
                label = { 
                    Text(
                        text = "All (${achievements.size})",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        
        items(availableTypes) { type ->
            val count = achievements.count { it.achievementType == type }
            FilterChip(
                selected = selectedType == type,
                onClick = { onTypeSelected(type) },
                label = { 
                    Text(
                        text = "${getAchievementTypeDisplayName(type)} ($count)",
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun AchievementBadge(
    achievement: UserAchievement,
    onClick: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    
    // Animation for badge appearance
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(achievement) {
        isVisible = true
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0.8f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "achievementBadgeScale"
    )
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Card(
            modifier = Modifier
                .aspectRatio(1f)
                .ensureMinimumTouchTarget()
                .clickable(role = Role.Button) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .enhancedAccessibilitySemantics(
                    description = "${achievement.title} achievement. ${achievement.description}",
                    role = Role.Button,
                    stateDescription = "Achievement badge. Double tap to view details.",
                    isEnabled = true
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = getAchievementColor(achievement.achievementType)
            ),
            border = BorderStroke(
                width = 2.dp,
                color = getAchievementBorderColor(achievement.achievementType)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(LiftrixSpacing.elementPaddingLarge)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Achievement icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = getAchievementBorderColor(achievement.achievementType),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = getAchievementIcon(achievement.achievementType),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(LiftrixSpacing.elementPaddingSmall))
                
                // Achievement title
                Text(
                    text = achievement.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Unlock date
                Text(
                    text = formatUnlockDate(achievement.unlockedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AchievementDetailModal(
    achievement: UserAchievement,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LiftrixSpacing.cardPadding),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(LiftrixSpacing.modalPadding),
                verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
            ) {
                // Header with icon and close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = getAchievementColor(achievement.achievementType),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getAchievementIcon(achievement.achievementType),
                                contentDescription = null,
                                tint = getAchievementBorderColor(achievement.achievementType),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        
                        Column {
                            Text(
                                text = achievement.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = getAchievementTypeDisplayName(achievement.achievementType),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Divider(color = MaterialTheme.colorScheme.outlineVariant)
                
                // Achievement description
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                // Unlock information
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Unlocked:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = achievement.unlockedAt.format(
                            DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' h:mm a")
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyAchievementState(
    selectedType: AchievementType?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .padding(LiftrixSpacing.modalPadding)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.elementSpacing)
        ) {
            Icon(
                imageVector = Icons.Default.Stars,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            
            Text(
                text = if (selectedType != null) {
                    "No ${getAchievementTypeDisplayName(selectedType).lowercase()} yet"
                } else {
                    "No achievements yet"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Complete workouts and maintain streaks to unlock achievements!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Helper functions
private fun getAchievementTypeDisplayName(type: AchievementType): String {
    return when (type) {
        AchievementType.WORKOUT_MILESTONE -> "Workout Milestones"
        AchievementType.STREAK_ACHIEVEMENT -> "Streaks"
        AchievementType.CONSISTENCY_BADGE -> "Consistency"
        AchievementType.FIRST_TIME_EVENTS -> "First-Time Events"
    }
}

private fun getAchievementIcon(type: AchievementType): ImageVector {
    return when (type) {
        AchievementType.WORKOUT_MILESTONE -> Icons.Default.FitnessCenter
        AchievementType.STREAK_ACHIEVEMENT -> Icons.Default.Whatshot
        AchievementType.CONSISTENCY_BADGE -> Icons.Default.Schedule
        AchievementType.FIRST_TIME_EVENTS -> Icons.Default.Star
    }
}

@Composable
private fun getAchievementColor(type: AchievementType): Color {
    return when (type) {
        AchievementType.WORKOUT_MILESTONE -> MaterialTheme.colorScheme.primaryContainer
        AchievementType.STREAK_ACHIEVEMENT -> MaterialTheme.colorScheme.secondaryContainer
        AchievementType.CONSISTENCY_BADGE -> MaterialTheme.colorScheme.tertiaryContainer
        AchievementType.FIRST_TIME_EVENTS -> MaterialTheme.colorScheme.errorContainer
    }
}

@Composable
private fun getAchievementBorderColor(type: AchievementType): Color {
    return when (type) {
        AchievementType.WORKOUT_MILESTONE -> MaterialTheme.colorScheme.primary
        AchievementType.STREAK_ACHIEVEMENT -> MaterialTheme.colorScheme.secondary
        AchievementType.CONSISTENCY_BADGE -> MaterialTheme.colorScheme.tertiary
        AchievementType.FIRST_TIME_EVENTS -> MaterialTheme.colorScheme.error
    }
}

private fun formatUnlockDate(unlockedAt: java.time.LocalDateTime): String {
    val now = java.time.LocalDateTime.now()
    val period = java.time.Period.between(unlockedAt.toLocalDate(), now.toLocalDate())
    
    return when {
        period.days == 0 -> "Today"
        period.days == 1 -> "Yesterday"
        period.days < 7 -> "${period.days} days ago"
        period.days < 30 -> "${period.days / 7} weeks ago"
        period.months < 12 -> "${period.months} months ago"
        else -> "${period.years} years ago"
    }
}

private fun generateAchievementContentDescription(achievement: UserAchievement): String {
    return buildString {
        append("Achievement: ")
        append(achievement.title)
        append(". ")
        append(achievement.description)
        append(". Unlocked ")
        append(formatUnlockDate(achievement.unlockedAt))
        append(". Type: ")
        append(getAchievementTypeDisplayName(achievement.achievementType))
    }
}