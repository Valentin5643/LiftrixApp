package com.example.liftrix.ui.onboarding.accessibility

import android.content.Context
import android.content.res.Configuration
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Comprehensive accessibility utilities for onboarding flow compliance with WCAG 2.1 AA standards.
 * Provides TalkBack support, focus management, high contrast detection, and semantic enhancements.
 */
object AccessibilityUtils {

    /**
     * Enhanced semantic modifier for interactive components with full accessibility support.
     * 
     * @param contentDescription Description for screen readers
     * @param role Semantic role (Button, TextInput, etc.)
     * @param stateDescription Current state for screen readers
     * @param isHeading Whether this element is a heading
     * @param traversalIndex Order for accessibility traversal
     * @param testTag Tag for UI testing
     * @param liveRegion Whether this is a live region for announcements
     */
    fun Modifier.enhancedAccessibilitySemantics(
        contentDescription: String,
        role: Role? = null,
        stateDescription: String? = null,
        isHeading: Boolean = false,
        traversalIndex: Float? = null,
        testTag: String? = null,
        liveRegion: LiveRegionMode? = null
    ): Modifier = this.semantics {
        this.contentDescription = contentDescription
        role?.let { this.role = it }
        stateDescription?.let { this.stateDescription = it }
        if (isHeading) this.heading()
        traversalIndex?.let { this.traversalIndex = it }
        testTag?.let { this.testTag = it }
        liveRegion?.let { this.liveRegion = it }
    }

    /**
     * Creates a focus requester with automatic focus management for accessibility.
     */
    @Composable
    fun rememberAccessibilityFocusRequester(): FocusRequester {
        return remember { FocusRequester() }
    }

    /**
     * Announces text to screen readers with optional delay.
     * 
     * @param text Text to announce
     * @param delayMs Delay before announcement (default 100ms)
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
     * Use this from coroutine contexts or non-composable functions.
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
     * Manages focus transition during navigation with accessibility support.
     * 
     * @param focusRequester FocusRequester to trigger focus
     * @param condition When to request focus
     * @param delayMs Delay before focusing (default 300ms for navigation)
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
     * Provides high contrast colors when needed.
     */
    @Composable
    fun getAccessibleColors(): AccessibleColors {
        val isHighContrast = isHighContrastEnabled()
        val colorScheme = MaterialTheme.colorScheme
        
        return if (isHighContrast) {
            AccessibleColors(
                primary = Color.White,
                onPrimary = Color.Black,
                error = Color.Red,
                onError = Color.White,
                surface = Color.Black,
                onSurface = Color.White
            )
        } else {
            AccessibleColors(
                primary = colorScheme.primary,
                onPrimary = colorScheme.onPrimary,
                error = colorScheme.error,
                onError = colorScheme.onError,
                surface = colorScheme.surface,
                onSurface = colorScheme.onSurface
            )
        }
    }

    /**
     * Ensures minimum touch target size for accessibility (44dp).
     */
    fun Modifier.minimumTouchTarget(): Modifier = this.size(44.dp)

    /**
     * Modifier for clickable elements with enhanced accessibility support.
     */
    fun Modifier.accessibleClickable(
        contentDescription: String,
        role: Role = Role.Button,
        stateDescription: String? = null,
        enabled: Boolean = true
    ): Modifier = this
        .minimumTouchTarget()
        .enhancedAccessibilitySemantics(
            contentDescription = contentDescription,
            role = role,
            stateDescription = stateDescription ?: if (enabled) "Enabled" else "Disabled"
        )

    /**
     * Modifier for text input fields with comprehensive accessibility.
     */
    fun Modifier.accessibleTextInput(
        label: String,
        value: String,
        isError: Boolean = false,
        isRequired: Boolean = false,
        helperText: String? = null
    ): Modifier {
        val stateDescription = buildString {
            if (isRequired) append("Required field. ")
            if (isError) append("Invalid input. ")
            if (value.isNotEmpty()) append("Current value: $value. ")
            helperText?.let { append(it) }
        }
        
        return this.enhancedAccessibilitySemantics(
            contentDescription = if (isRequired) "$label, required" else label,
            stateDescription = stateDescription.trim()
        )
    }

    /**
     * Modifier for navigation elements with step information.
     */
    fun Modifier.accessibleNavigation(
        description: String,
        currentStep: Int,
        totalSteps: Int,
        canNavigate: Boolean = true
    ): Modifier {
        val stateDescription = "Step $currentStep of $totalSteps. ${if (canNavigate) "Available" else "Complete current step to continue"}"
        
        return this.enhancedAccessibilitySemantics(
            contentDescription = description,
            role = Role.Button,
            stateDescription = stateDescription
        )
    }

    /**
     * Modifier for progress indicators with descriptive information.
     */
    fun Modifier.accessibleProgress(
        currentStep: Int,
        totalSteps: Int,
        stepName: String
    ): Modifier {
        val progressPercentage = ((currentStep.toFloat() / totalSteps) * 100).toInt()
        val description = "Progress: $progressPercentage percent complete. Current step: $stepName"
        
        return this.enhancedAccessibilitySemantics(
            contentDescription = description,
            role = Role.Button,
            liveRegion = LiveRegionMode.Polite
        )
    }

    /**
     * Modifier for error and validation messages.
     */
    fun Modifier.accessibleErrorMessage(
        message: String,
        isError: Boolean = true
    ): Modifier = this.enhancedAccessibilitySemantics(
        contentDescription = if (isError) "Error: $message" else "Information: $message",
        liveRegion = if (isError) LiveRegionMode.Assertive else LiveRegionMode.Polite
    )

    /**
     * Modifier for headings with proper semantic structure.
     */
    fun Modifier.accessibleHeading(
        text: String,
        level: Int = 1
    ): Modifier = this.enhancedAccessibilitySemantics(
        contentDescription = text,
        isHeading = true,
        traversalIndex = level.toFloat()
    )

    /**
     * Validates accessibility compliance for components.
     */
    fun validateAccessibilityCompliance(
        hasContentDescription: Boolean,
        hasSufficientTouchTarget: Boolean,
        hasProperRole: Boolean,
        hasStateDescription: Boolean = true
    ): AccessibilityComplianceResult {
        val issues = mutableListOf<String>()
        
        if (!hasContentDescription) {
            issues.add("Missing content description for screen readers")
        }
        if (!hasSufficientTouchTarget) {
            issues.add("Touch target smaller than 44dp minimum")
        }
        if (!hasProperRole) {
            issues.add("Missing or incorrect semantic role")
        }
        if (!hasStateDescription) {
            issues.add("Missing state description for dynamic content")
        }
        
        return AccessibilityComplianceResult(
            isCompliant = issues.isEmpty(),
            issues = issues,
            score = ((4 - issues.size) * 25) // Percentage score
        )
    }
}

/**
 * Data class for accessible color scheme.
 */
data class AccessibleColors(
    val primary: Color,
    val onPrimary: Color,
    val error: Color,
    val onError: Color,
    val surface: Color,
    val onSurface: Color
)

/**
 * Result of accessibility compliance validation.
 */
data class AccessibilityComplianceResult(
    val isCompliant: Boolean,
    val issues: List<String>,
    val score: Int // Percentage score (0-100)
)

/**
 * Focus management utility for screen transitions.
 */
class OnboardingFocusManager {
    private val focusRequesters = mutableMapOf<String, FocusRequester>()
    
    fun getFocusRequester(key: String): FocusRequester {
        return focusRequesters.getOrPut(key) { FocusRequester() }
    }
    
    suspend fun requestFocusWithDelay(key: String, delayMs: Long = 300L) {
        delay(delayMs)
        focusRequesters[key]?.requestFocus()
    }
    
    fun clearFocus() {
        focusRequesters.clear()
    }
}

/**
 * Composable for announcing step changes to screen readers.
 */
@Composable
fun OnboardingStepAnnouncer(
    stepName: String,
    stepNumber: Int,
    totalSteps: Int,
    isComplete: Boolean = false
) {
    val announcement = if (isComplete) {
        "Step completed: $stepName"
    } else {
        "Now on step $stepNumber of $totalSteps: $stepName"
    }
    
    AccessibilityUtils.announceForAccessibility(announcement)
}

/**
 * Composable for managing accessibility focus during navigation.
 */
@Composable
fun OnboardingAccessibilityFocus(
    stepChanged: Boolean,
    focusTarget: String
) {
    val focusManager = remember { OnboardingFocusManager() }
    val focusRequester = focusManager.getFocusRequester(focusTarget)
    
    AccessibilityUtils.manageFocusTransition(
        focusRequester = focusRequester,
        condition = stepChanged
    )
    
    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
        }
    }
}