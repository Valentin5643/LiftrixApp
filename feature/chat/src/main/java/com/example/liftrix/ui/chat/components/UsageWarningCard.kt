package com.example.liftrix.ui.chat.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.example.liftrix.ui.theme.LiftrixColorsV2
import com.example.liftrix.ui.theme.LiftrixSpacing
import com.example.liftrix.domain.model.chat.UsageLimits
import com.example.liftrix.ui.chat.Language

/**
 * Usage warning card component for displaying AI chat usage limits.
 * 
 * Shows progressive warnings as users approach their daily message or monthly token limits:
 * - Green: Normal usage (80%+ remaining)
 * - Orange: Approaching limit (20-80% remaining) 
 * - Red: Critical/exhausted (0-20% remaining)
 * 
 * Features:
 * - Adaptive color coding based on usage thresholds
 * - Multi-language support for warning messages
 * - Progress indicators for visual usage tracking
 * - Auto-dismiss functionality for non-critical warnings
 * - Animated transitions for smooth UX
 * 
 * @param limits Current usage limits data
 * @param currentLanguage Interface language for localization
 * @param onDismiss Optional callback for dismissing the warning
 * @param modifier Modifier for the component
 */
@Composable
fun UsageWarningCard(
    limits: UsageLimits?,
    currentLanguage: Language,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    limits?.let { usageLimits ->
        val warningLevel = determineWarningLevel(usageLimits)
        if (warningLevel == WarningLevel.NONE) return
        
        Card(
            colors = CardDefaults.cardColors(
                containerColor = getWarningColor(warningLevel)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = RoundedCornerShape(12.dp),
            modifier = modifier
        ) {
            Column(
                modifier = Modifier.padding(LiftrixSpacing.cardPadding)
            ) {
                UsageWarningHeader(
                    warningLevel = warningLevel,
                    currentLanguage = currentLanguage,
                    onDismiss = onDismiss
                )
                
                UsageWarningContent(
                    limits = usageLimits,
                    warningLevel = warningLevel,
                    currentLanguage = currentLanguage
                )
                
                if (warningLevel != WarningLevel.CRITICAL) {
                    UsageProgressIndicators(
                        limits = usageLimits,
                        modifier = Modifier.padding(top = LiftrixSpacing.small)
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageWarningHeader(
    warningLevel: WarningLevel,
    currentLanguage: Language,
    onDismiss: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getWarningIcon(warningLevel),
                contentDescription = getWarningTitle(warningLevel, currentLanguage),
                modifier = Modifier.size(20.dp),
                tint = getWarningIconColor(warningLevel)
            )
            
            Text(
                text = getWarningTitle(warningLevel, currentLanguage),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = getWarningTextColor(warningLevel)
            )
        }
        
        onDismiss?.let { dismiss ->
            IconButton(
                onClick = dismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = if (currentLanguage == Language.ROMANIAN) 
                        "Închide" else "Dismiss",
                    modifier = Modifier.size(16.dp),
                    tint = getWarningTextColor(warningLevel).copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun UsageWarningContent(
    limits: UsageLimits,
    warningLevel: WarningLevel,
    currentLanguage: Language
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        // Daily messages warning
        if (limits.isNearDailyLimit || limits.dailyMessagesRemaining <= 0) {
            Text(
                text = getDailyMessageText(limits, currentLanguage),
                style = MaterialTheme.typography.bodySmall,
                color = getWarningTextColor(warningLevel)
            )
        }
        
        // Monthly tokens warning
        if (limits.isNearMonthlyLimit || limits.monthlyTokensRemaining <= 0) {
            Text(
                text = getMonthlyTokenText(limits, currentLanguage),
                style = MaterialTheme.typography.bodySmall,
                color = getWarningTextColor(warningLevel)
            )
        }
        
        // Additional guidance for critical state
        if (warningLevel == WarningLevel.CRITICAL) {
            Text(
                text = getCriticalGuidanceText(currentLanguage),
                style = MaterialTheme.typography.bodySmall,
                color = getWarningTextColor(warningLevel),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun UsageProgressIndicators(
    limits: UsageLimits,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(LiftrixSpacing.small)
    ) {
        // Daily messages progress
        UsageProgressBar(
            label = "Daily Messages",
            current = 100 - limits.dailyMessagesRemaining, // Assuming 100 daily limit
            total = 100,
            color = if (limits.isNearDailyLimit) 
                MaterialTheme.colorScheme.tertiary 
            else 
                LiftrixColorsV2.primary
        )
        
        // Monthly tokens progress (simplified visualization)
        if (limits.monthlyTokensRemaining < 50000) { // Show when approaching limit
            UsageProgressBar(
                label = "Monthly Tokens",
                current = 100000 - limits.monthlyTokensRemaining, // Assuming 100k monthly limit
                total = 100000,
                color = if (limits.isNearMonthlyLimit) 
                    MaterialTheme.colorScheme.tertiary 
                else 
                    LiftrixColorsV2.primary,
                showAsPercentage = true
            )
        }
    }
}

@Composable
private fun UsageProgressBar(
    label: String,
    current: Int,
    total: Int,
    color: androidx.compose.ui.graphics.Color,
    showAsPercentage: Boolean = false,
    modifier: Modifier = Modifier
) {
    val progress = (current.toFloat() / total).coerceIn(0f, 1f)
    
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = LiftrixColorsV2.onSurfaceVariant.copy(alpha = 0.8f)
            )
            
            Text(
                text = if (showAsPercentage) 
                    "${(progress * 100).toInt()}%" 
                else 
                    "$current/$total",
                style = MaterialTheme.typography.labelSmall,
                color = LiftrixColorsV2.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
        
        LinearProgressIndicator(
            progress = progress,
            color = color,
            trackColor = color.copy(alpha = 0.3f),
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
        )
    }
}

/**
 * Compact usage warning for minimal space scenarios.
 */
@Composable
fun CompactUsageWarning(
    limits: UsageLimits?,
    currentLanguage: Language,
    modifier: Modifier = Modifier
) {
    limits?.let { usageLimits ->
        val warningLevel = determineWarningLevel(usageLimits)
        if (warningLevel == WarningLevel.NONE) return
        
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(LiftrixSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getWarningIcon(warningLevel),
                contentDescription = getCompactWarningText(usageLimits, currentLanguage),
                modifier = Modifier.size(16.dp),
                tint = getWarningIconColor(warningLevel)
            )
            
            Text(
                text = getCompactWarningText(usageLimits, currentLanguage),
                style = MaterialTheme.typography.labelSmall,
                color = getWarningTextColor(warningLevel)
            )
        }
    }
}

/**
 * Animated usage warning with slide and fade transitions.
 */
@Composable
fun AnimatedUsageWarning(
    limits: UsageLimits?,
    currentLanguage: Language,
    isVisible: Boolean,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.8f)
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.8f)
        ) + fadeOut(),
        modifier = modifier
    ) {
        UsageWarningCard(
            limits = limits,
            currentLanguage = currentLanguage,
            onDismiss = onDismiss
        )
    }
}

// Supporting enums and helper functions

private enum class WarningLevel {
    NONE, LOW, MEDIUM, HIGH, CRITICAL
}

private fun determineWarningLevel(limits: UsageLimits): WarningLevel {
    return when {
        limits.dailyMessagesRemaining <= 0 || limits.monthlyTokensRemaining <= 0 -> WarningLevel.CRITICAL
        limits.dailyMessagesRemaining <= 5 || limits.monthlyTokensRemaining <= 1000 -> WarningLevel.HIGH
        limits.isNearDailyLimit || limits.isNearMonthlyLimit -> WarningLevel.MEDIUM
        limits.dailyMessagesRemaining <= 20 || limits.monthlyTokensRemaining <= 5000 -> WarningLevel.LOW
        else -> WarningLevel.NONE
    }
}

@Composable
private fun getWarningColor(level: WarningLevel): androidx.compose.ui.graphics.Color {
    return when (level) {
        WarningLevel.CRITICAL -> MaterialTheme.colorScheme.errorContainer
        WarningLevel.HIGH -> MaterialTheme.colorScheme.tertiaryContainer
        WarningLevel.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer
        WarningLevel.LOW -> LiftrixColorsV2.surfaceVariant
        WarningLevel.NONE -> LiftrixColorsV2.surface
    }
}

@Composable
private fun getWarningTextColor(level: WarningLevel): androidx.compose.ui.graphics.Color {
    return when (level) {
        WarningLevel.CRITICAL -> MaterialTheme.colorScheme.onErrorContainer
        WarningLevel.HIGH -> MaterialTheme.colorScheme.onTertiaryContainer
        WarningLevel.MEDIUM -> MaterialTheme.colorScheme.onSecondaryContainer
        WarningLevel.LOW -> LiftrixColorsV2.onSurfaceVariant
        WarningLevel.NONE -> LiftrixColorsV2.onSurface
    }
}

@Composable
private fun getWarningIconColor(level: WarningLevel): androidx.compose.ui.graphics.Color {
    return when (level) {
        WarningLevel.CRITICAL -> MaterialTheme.colorScheme.error
        WarningLevel.HIGH -> MaterialTheme.colorScheme.tertiary
        WarningLevel.MEDIUM -> MaterialTheme.colorScheme.secondary
        WarningLevel.LOW -> LiftrixColorsV2.primary
        WarningLevel.NONE -> LiftrixColorsV2.onSurface
    }
}

private fun getWarningIcon(level: WarningLevel): androidx.compose.ui.graphics.vector.ImageVector {
    return when (level) {
        WarningLevel.CRITICAL -> Icons.Default.Error
        WarningLevel.HIGH -> Icons.Default.Warning
        WarningLevel.MEDIUM -> Icons.Default.Info
        WarningLevel.LOW -> Icons.Default.Info
        WarningLevel.NONE -> Icons.Default.Info
    }
}

private fun getWarningTitle(level: WarningLevel, language: Language): String {
    return when (language) {
        Language.ROMANIAN -> when (level) {
            WarningLevel.CRITICAL -> "Limită Atinsă"
            WarningLevel.HIGH -> "Aproape de Limită"
            WarningLevel.MEDIUM -> "Alertă Utilizare"
            WarningLevel.LOW -> "Monitorizare Utilizare"
            WarningLevel.NONE -> ""
        }
        Language.ENGLISH -> when (level) {
            WarningLevel.CRITICAL -> "Limit Reached"
            WarningLevel.HIGH -> "Approaching Limit"
            WarningLevel.MEDIUM -> "Usage Alert"
            WarningLevel.LOW -> "Usage Monitor"
            WarningLevel.NONE -> ""
        }
    }
}

private fun getDailyMessageText(limits: UsageLimits, language: Language): String {
    return when (language) {
        Language.ROMANIAN -> when {
            limits.dailyMessagesRemaining <= 0 -> "Nu mai aveți mesaje disponibile astăzi"
            else -> "${limits.dailyMessagesRemaining} mesaje rămase azi"
        }
        Language.ENGLISH -> when {
            limits.dailyMessagesRemaining <= 0 -> "No messages remaining today"
            else -> "${limits.dailyMessagesRemaining} messages left today"
        }
    }
}

private fun getMonthlyTokenText(limits: UsageLimits, language: Language): String {
    return when (language) {
        Language.ROMANIAN -> when {
            limits.monthlyTokensRemaining <= 0 -> "Nu mai aveți tokeni disponibili luna aceasta"
            else -> "${limits.monthlyTokensRemaining} tokeni rămași luna aceasta"
        }
        Language.ENGLISH -> when {
            limits.monthlyTokensRemaining <= 0 -> "No tokens remaining this month"
            else -> "${limits.monthlyTokensRemaining} tokens left this month"
        }
    }
}

private fun getCriticalGuidanceText(language: Language): String {
    return when (language) {
        Language.ROMANIAN -> "Limitele se resetează zilnic/lunar. Reveniți mai târziu pentru mai multe mesaje."
        Language.ENGLISH -> "Limits reset daily/monthly. Please return later for more messages."
    }
}

private fun getCompactWarningText(limits: UsageLimits, language: Language): String {
    return when (language) {
        Language.ROMANIAN -> when {
            limits.dailyMessagesRemaining <= 0 -> "Fără mesaje"
            limits.dailyMessagesRemaining <= 5 -> "${limits.dailyMessagesRemaining} rămase"
            else -> "${limits.dailyMessagesRemaining} mesaje"
        }
        Language.ENGLISH -> when {
            limits.dailyMessagesRemaining <= 0 -> "No messages"
            limits.dailyMessagesRemaining <= 5 -> "${limits.dailyMessagesRemaining} left"
            else -> "${limits.dailyMessagesRemaining} messages"
        }
    }
}
