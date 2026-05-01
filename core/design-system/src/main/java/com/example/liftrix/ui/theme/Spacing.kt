package com.example.liftrix.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Liftrix Semantic Spacing System
 * Unified spacing tokens for consistent layout spacing throughout the application
 * Following Material 3 spacing principles with Liftrix-specific adaptations
 */
object LiftrixSpacing {
    
    // Core spacing hierarchy based on 4dp baseline grid
    val none = 0.dp
    val extraSmall = 4.dp
    val small = 8.dp
    val medium = 12.dp
    val large = 16.dp
    val extraLarge = 24.dp
    val xxLarge = 32.dp
    val xxxLarge = 48.dp
    
    // Semantic spacing tokens for specific UI elements
    
    /**
     * Card-specific spacing tokens
     * Used for consistent card layouts across workout screens
     */
    val cardSpacing = 16.dp        // Space between cards
    val cardPadding = 12.dp        // Internal card padding
    val cardContentSpacing = 8.dp  // Space between content elements within cards
    
    /**
     * Element spacing tokens
     * Used for spacing between UI elements within components
     */
    val elementSpacing = 8.dp      // Standard spacing between elements
    val elementPaddingSmall = 4.dp // Small padding for tight layouts
    val elementPaddingMedium = 8.dp // Medium padding for standard layouts
    val elementPaddingLarge = 12.dp // Large padding for spacious layouts
    
    /**
     * Touch target and accessibility spacing
     * Ensures WCAG 2.1 AA compliance for interactive elements
     */
    val touchTarget = 48.dp        // Minimum touch target size
    val touchTargetPadding = 12.dp // Padding around touch targets
    val interactivePadding = 16.dp // Padding for interactive elements
    
    /**
     * Screen-level spacing tokens
     * Used for consistent screen layouts and navigation
     */
    val screenPadding = 16.dp      // Standard screen edge padding
    val screenContentSpacing = 24.dp // Space between major screen sections
    val navigationPadding = 8.dp   // Padding around navigation elements
    
    /**
     * Typography spacing tokens
     * Used for consistent text layouts and reading flow
     */
    val textLineSpacing = 4.dp     // Space between related text lines
    val textSectionSpacing = 16.dp // Space between text sections
    val textParagraphSpacing = 12.dp // Space between paragraphs
    
    /**
     * Form and input spacing tokens
     * Used for consistent form layouts and input field spacing
     */
    val formFieldSpacing = 16.dp   // Space between form fields
    val formSectionSpacing = 24.dp // Space between form sections
    val inputPadding = 12.dp       // Internal padding for input fields
    val buttonSpacing = 8.dp       // Space between buttons
    
    /**
     * Workout-specific spacing tokens
     * Used for exercise cards, set displays, and timer layouts
     */
    val exerciseCardSpacing = 12.dp // Space between exercise cards
    val exerciseItemSpacing = 8.dp  // Space between exercise items (sets, reps)
    val timerPadding = 16.dp       // Padding around timer display
    val progressIndicatorSpacing = 4.dp // Space around progress indicators
    
    /**
     * Modal and dialog spacing tokens
     * Used for consistent modal layouts and dialog spacing
     */
    val modalPadding = 24.dp       // Internal modal padding
    val dialogPadding = 16.dp      // Internal dialog padding
    val bottomSheetPadding = 16.dp // Internal bottom sheet padding
    
    /**
     * List and grid spacing tokens
     * Used for consistent list layouts and grid spacing
     */
    val listItemSpacing = 8.dp     // Space between list items
    val listSectionSpacing = 16.dp // Space between list sections
    val gridItemSpacing = 8.dp     // Space between grid items
    val gridSectionSpacing = 16.dp // Space between grid sections
}

/**
 * Legacy spacing references for backward compatibility
 * These should be gradually replaced with semantic spacing tokens
 */
@Deprecated("Use LiftrixSpacing.cardSpacing instead", ReplaceWith("LiftrixSpacing.cardSpacing"))
val CardSpacing = LiftrixSpacing.cardSpacing

@Deprecated("Use LiftrixSpacing.cardPadding instead", ReplaceWith("LiftrixSpacing.cardPadding"))
val CardPadding = LiftrixSpacing.cardPadding

@Deprecated("Use LiftrixSpacing.elementSpacing instead", ReplaceWith("LiftrixSpacing.elementSpacing"))
val ElementSpacing = LiftrixSpacing.elementSpacing

@Deprecated("Use LiftrixSpacing.touchTarget instead", ReplaceWith("LiftrixSpacing.touchTarget"))
val TouchTarget = LiftrixSpacing.touchTarget