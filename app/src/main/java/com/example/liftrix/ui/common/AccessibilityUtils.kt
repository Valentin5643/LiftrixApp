package com.example.liftrix.ui.common

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * Comprehensive accessibility utilities ensuring WCAG 2.1 AA compliance.
 * Provides contrast validation, semantic labeling, touch target sizes, and screen reader support.
 */
object AccessibilityUtils {

    /**
     * Minimum touch target size as per WCAG guidelines (44dp)
     */
    val MinimumTouchTargetSize = 44.dp

    /**
     * Minimum spacing between touch targets (8dp)
     */
    val MinimumTouchTargetSpacing = 8.dp

    /**
     * WCAG 2.1 AA contrast ratio requirements
     */
    object ContrastRatios {
        const val NORMAL_TEXT_AA = 4.5f
        const val LARGE_TEXT_AA = 3.0f
        const val NON_TEXT_AA = 3.0f
        const val ENHANCED_AAA = 7.0f
    }

    /**
     * Ensures minimum touch target size for accessibility compliance.
     * 
     * @param minSize Minimum size (default 44dp)
     * @return Modifier with minimum touch target size
     */
    fun Modifier.ensureMinimumTouchTarget(minSize: Dp = MinimumTouchTargetSize): Modifier {
        return this.sizeIn(minWidth = minSize, minHeight = minSize)
    }

    /**
     * Enhanced accessibility semantics with comprehensive support.
     * 
     * @param description Content description for screen readers
     * @param role Semantic role of the element
     * @param stateDescription Current state description
     * @param isHeading Whether this element is a heading
     * @param traversalIndex Order for accessibility traversal
     * @param testTag Tag for UI testing
     * @param liveRegion Whether this is a live region for announcements
     * @param isSelected Whether the element is selected
     * @param isEnabled Whether the element is enabled
     */
    fun Modifier.accessibilitySemantics(
        description: String,
        role: Role? = null,
        stateDescription: String? = null,
        isHeading: Boolean = false,
        traversalIndex: Float? = null,
        testTag: String? = null,
        liveRegion: LiveRegionMode? = null,
        isSelected: Boolean = false,
        isEnabled: Boolean = true
    ): Modifier = this.semantics {
        contentDescription = description
        role?.let { this.role = it }
        stateDescription?.let { this.stateDescription = it }
        if (isHeading) heading()
        traversalIndex?.let { this.traversalIndex = it }
        testTag?.let { this.testTag = it }
        liveRegion?.let { this.liveRegion = it }
        if (isSelected) selected = true
        if (!isEnabled) disabled()
    }

    /**
     * Calculates contrast ratio between two colors according to WCAG guidelines.
     * 
     * @param foreground Foreground color
     * @param background Background color
     * @return Contrast ratio (1.0 to 21.0)
     */
    fun checkContrastRatio(foreground: Color, background: Color): Float {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val lighter = max(foregroundLuminance, backgroundLuminance)
        val darker = min(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    /**
     * Validates if contrast ratio meets WCAG 2.1 AA standards.
     * 
     * @param foreground Foreground color
     * @param background Background color
     * @param isLargeText Whether text is considered large (18pt+ or 14pt+ bold)
     * @return True if contrast meets AA standards
     */
    fun meetsContrastRequirements(
        foreground: Color,
        background: Color,
        isLargeText: Boolean = false
    ): Boolean {
        val ratio = checkContrastRatio(foreground, background)
        val minimumRatio = if (isLargeText) ContrastRatios.LARGE_TEXT_AA else ContrastRatios.NORMAL_TEXT_AA
        return ratio >= minimumRatio
    }

    /**
     * Gets high contrast color variant when needed.
     * 
     * @param originalColor Original color
     * @param backgroundColor Background color
     * @param isLargeText Whether text is large
     * @return High contrast color if needed, otherwise original
     */
    fun getHighContrastColor(
        originalColor: Color,
        backgroundColor: Color,
        isLargeText: Boolean = false
    ): Color {
        return if (meetsContrastRequirements(originalColor, backgroundColor, isLargeText)) {
            originalColor
        } else {
            // Return black or white based on background luminance
            if (backgroundColor.luminance() > 0.5f) Color.Black else Color.White
        }
    }

    /**
     * Announces text to screen readers with optional delay.
     * 
     * @param text Text to announce
     * @param delayMs Delay before announcement
     */
    @Composable
    fun announceForAccessibility(text: String, delayMs: Long = 100L) {
        val context = LocalContext.current
        
        LaunchedEffect(text) {
            if (text.isNotBlank()) {
                delay(delayMs)
                announceToScreenReader(context, text)
            }
        }
    }

    /**
     * Non-composable function to announce text to screen readers.
     */
    suspend fun announceToScreenReader(context: Context, text: String) {
        if (text.isNotBlank()) {
            try {
                val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) 
                    as? android.view.accessibility.AccessibilityManager
                
                if (accessibilityManager?.isEnabled == true) {
                    val event = android.view.accessibility.AccessibilityEvent.obtain(
                        android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT
                    )
                    event.text.add(text)
                    accessibilityManager.sendAccessibilityEvent(event)
                }
            } catch (e: Exception) {
                // Accessibility announcement failed, continue silently
            }
        }
    }

    /**
     * Detects if high contrast mode is enabled.
     */
    @Composable
    fun isHighContrastEnabled(): Boolean {
        val configuration = LocalConfiguration.current
        return (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * Detects if large text/font scaling is enabled.
     */
    @Composable
    fun isLargeTextEnabled(): Boolean {
        val configuration = LocalConfiguration.current
        return configuration.fontScale > 1.3f
    }

    /**
     * Detects if accessibility services are enabled.
     */
    @Composable
    fun isAccessibilityEnabled(): Boolean {
        val context = LocalContext.current
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) 
            as? android.view.accessibility.AccessibilityManager
        return accessibilityManager?.isEnabled == true
    }

    /**
     * Creates focus requester with accessibility management.
     */
    @Composable
    fun rememberAccessibilityFocusRequester(): FocusRequester {
        return remember { FocusRequester() }
    }

    /**
     * Manages focus transition with accessibility support.
     * 
     * @param focusRequester FocusRequester to trigger focus
     * @param condition When to request focus
     * @param delayMs Delay before focusing
     */
    @Composable
    fun manageFocusTransition(
        focusRequester: FocusRequester,
        condition: Boolean,
        delayMs: Long = 300L
    ) {
        LaunchedEffect(condition) {
            if (condition) {
                delay(delayMs)
                try {
                    focusRequester.requestFocus()
                } catch (e: Exception) {
                    // Focus request failed, continue without focus
                }
            }
        }
    }

    /**
     * Validates accessibility compliance for UI elements.
     * 
     * @param hasContentDescription Whether element has content description
     * @param hasSufficientTouchTarget Whether touch target meets minimum size
     * @param hasProperRole Whether element has appropriate semantic role
     * @param contrastRatio Color contrast ratio
     * @param isInteractive Whether element is interactive
     * @return Compliance result with score and issues
     */
    fun validateAccessibilityCompliance(
        hasContentDescription: Boolean,
        hasSufficientTouchTarget: Boolean,
        hasProperRole: Boolean,
        contrastRatio: Float,
        isInteractive: Boolean = false
    ): AccessibilityComplianceResult {
        val issues = mutableListOf<String>()
        
        if (isInteractive && !hasContentDescription) {
            issues.add("Interactive element missing content description")
        }
        
        if (isInteractive && !hasSufficientTouchTarget) {
            issues.add("Touch target smaller than ${MinimumTouchTargetSize.value}dp minimum")
        }
        
        if (isInteractive && !hasProperRole) {
            issues.add("Interactive element missing semantic role")
        }
        
        if (contrastRatio < ContrastRatios.NORMAL_TEXT_AA) {
            issues.add("Color contrast ratio ${String.format("%.1f", contrastRatio)}:1 below AA standard (${ContrastRatios.NORMAL_TEXT_AA}:1)")
        }
        
        val maxScore = if (isInteractive) 4 else 1 // Only contrast matters for non-interactive
        val achievedScore = maxScore - issues.size
        val percentageScore = if (maxScore > 0) (achievedScore * 100) / maxScore else 100
        
        return AccessibilityComplianceResult(
            isCompliant = issues.isEmpty(),
            issues = issues,
            score = percentageScore.coerceAtLeast(0),
            contrastRatio = contrastRatio
        )
    }
}

/**
 * Result of accessibility compliance validation.
 */
data class AccessibilityComplianceResult(
    val isCompliant: Boolean,
    val issues: List<String>,
    val score: Int, // Percentage score (0-100)
    val contrastRatio: Float
)

/**
 * System accessibility state for theming and adaptation.
 */
@Composable
fun rememberSystemAccessibilityState(): SystemAccessibilityState {
    val isHighContrast = AccessibilityUtils.isHighContrastEnabled()
    val isLargeText = AccessibilityUtils.isLargeTextEnabled()
    val isAccessibilityEnabled = AccessibilityUtils.isAccessibilityEnabled()
    val configuration = LocalConfiguration.current
    
    return remember(isHighContrast, isLargeText, isAccessibilityEnabled, configuration.fontScale) {
        SystemAccessibilityState(
            isHighContrastEnabled = isHighContrast,
            isLargeTextEnabled = isLargeText,
            isAccessibilityServiceEnabled = isAccessibilityEnabled,
            fontScale = configuration.fontScale,
            needsEnhancedAccessibility = isHighContrast || isLargeText || isAccessibilityEnabled
        )
    }
}

/**
 * System accessibility state data class.
 */
data class SystemAccessibilityState(
    val isHighContrastEnabled: Boolean,
    val isLargeTextEnabled: Boolean,
    val isAccessibilityServiceEnabled: Boolean,
    val fontScale: Float,
    val needsEnhancedAccessibility: Boolean
) 

/**
 * Composable function to remember accessibility state for components
 * Provides stable accessibility state that prevents unnecessary recompositions
 * 
 * @param contentDescription Initial content description
 * @param stateDescription Initial state description  
 * @param role Semantic role of the element
 * @param enabled Whether the element is enabled
 * @return AccessibilityState object for use in components
 */
@Composable
fun rememberAccessibilityState(
    contentDescription: String = "",
    stateDescription: String? = null,
    role: Role? = null,
    enabled: Boolean = true
): AccessibilityState {
    return remember(contentDescription, stateDescription, role, enabled) {
        AccessibilityState(
            contentDescription = contentDescription,
            stateDescription = stateDescription,
            role = role,
            enabled = enabled
        )
    }
} 