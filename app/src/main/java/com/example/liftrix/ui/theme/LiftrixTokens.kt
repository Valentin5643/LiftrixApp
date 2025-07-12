package com.example.liftrix.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Liftrix Design System Tokens
 * Centralized design tokens for consistent spacing, elevation, corner radius, opacity, touch targets, and colors
 */
object LiftrixTokens {
    
    /**
     * Brand Color Tokens
     * Core brand colors used throughout the application
     */
    object BrandColors {
        val Teal: Color = Color(0xFF20C9B7)
        val Indigo: Color = Color(0xFF2A3B7D) 
        val Coral: Color = Color(0xFFFF6B6B)
    }
    
    /**
     * Surface Color Tokens
     * OLED-optimized surface colors for dark-first architecture
     */
    object SurfaceColors {
        // OLED-optimized backgrounds
        val BackgroundDark: Color = Color(0xFF0F0F0F)  // Deep black for OLED
        val BackgroundLight: Color = Color(0xFFF8F9FA)
        
        // Surface variations
        val SurfaceDark: Color = Color(0xFF1E1E1E)
        val SurfaceLight: Color = Color(0xFFFFFFFF)
        
        // Container surfaces
        val SurfaceContainerDark: Color = Color(0xFF1A1A1A)
        val SurfaceContainerLight: Color = Color(0xFFF3F4F6)
    }
    
    /**
     * Content Color Tokens
     * Text and icon colors with proper contrast ratios
     */
    object ContentColors {
        // High emphasis text (87% opacity equivalent)
        val OnSurfaceDark: Color = Color(0xFFE6E1E5)
        val OnSurfaceLight: Color = Color(0xFF1C1B1F)
        
        // Medium emphasis text (60% opacity equivalent)
        val OnSurfaceVariantDark: Color = Color(0xFFCAC4CF)
        val OnSurfaceVariantLight: Color = Color(0xFF49454E)
        
        // Low emphasis text (38% opacity equivalent)
        val OnSurfaceDisabledDark: Color = Color(0xFF938F99)
        val OnSurfaceDisabledLight: Color = Color(0xFF79747E)
    }
    
    /**
     * Semantic Color Tokens
     * Purpose-driven color assignments
     */
    object SemanticColors {
        // Primary action colors
        val ActionPrimary: Color = BrandColors.Teal
        val ActionSecondary: Color = BrandColors.Indigo
        val ActionTertiary: Color = BrandColors.Coral
        
        // State colors
        val Success: Color = Color(0xFF4CAF50)
        val Warning: Color = Color(0xFFFF9800)
        val Error: Color = Color(0xFFFF4444)
        val Info: Color = BrandColors.Indigo
        
        // Interactive colors
        val Interactive: Color = BrandColors.Teal
        val InteractiveHover: Color = Color(0xFF1DB5A6)
        val InteractivePressed: Color = Color(0xFF00695C)
        val InteractiveDisabled: Color = Color(0xFF79747E)
    }
    
    /**
     * Material 3 Color Role Tokens
     * Complete Material 3 color system mapping
     */
    object ColorRoles {
        // Primary color roles
        val Primary: Color = BrandColors.Teal
        val OnPrimary: Color = Color.White
        val PrimaryContainer: Color = Color(0xFFB2F2EA)
        val OnPrimaryContainer: Color = Color(0xFF003A35)
        val PrimaryContainerDark: Color = Color(0xFF005047)
        val OnPrimaryContainerDark: Color = Color(0xFFB2F2EA)
        
        // Secondary color roles
        val Secondary: Color = BrandColors.Indigo
        val OnSecondary: Color = Color.White
        val SecondaryContainer: Color = Color(0xFFDDE1FF)
        val OnSecondaryContainer: Color = Color(0xFF0F1B37)
        val SecondaryContainerDark: Color = Color(0xFF1E2A4E)
        val OnSecondaryContainerDark: Color = Color(0xFFDDE1FF)
        
        // Tertiary color roles
        val Tertiary: Color = BrandColors.Coral
        val OnTertiary: Color = Color.White
        val TertiaryContainer: Color = Color(0xFFFFDAD8)
        val OnTertiaryContainer: Color = Color(0xFF410006)
        val TertiaryContainerDark: Color = Color(0xFF5F0008)
        val OnTertiaryContainerDark: Color = Color(0xFFFFDAD8)
        
        // Error color roles
        val Error: Color = SemanticColors.Error
        val OnError: Color = Color.White
        val ErrorContainer: Color = Color(0xFFFFDAD6)
        val OnErrorContainer: Color = Color(0xFF410002)
        val ErrorContainerDark: Color = Color(0xFF93000A)
        val OnErrorContainerDark: Color = Color(0xFFFFDAD6)
        
        // Outline color roles
        val Outline: Color = Color(0xFF79747E)
        val OutlineVariant: Color = Color(0xFFCAC4CF)
        val OutlineDark: Color = Color(0xFF938F99)
        val OutlineVariantDark: Color = Color(0xFF49454E)
        
        // Inverse color roles
        val InverseSurface: Color = Color(0xFF313033)
        val InverseOnSurface: Color = Color(0xFFF4EFF4)
        val InversePrimary: Color = Color(0xFFB2F2EA)
        val InverseSurfaceDark: Color = Color(0xFFE6E1E5)
        val InverseOnSurfaceDark: Color = Color(0xFF313033)
        val InversePrimaryDark: Color = Color(0xFF005047)
    }
    
    /**
     * Accessibility Color Tokens
     * WCAG 2.1 AA compliant color combinations
     */
    object AccessibilityColors {
        // High contrast pairs for critical content
        val HighContrastTextDark: Color = Color(0xFFFFFFFF)
        val HighContrastTextLight: Color = Color(0xFF000000)
        
        // Focus indicators
        val FocusIndicator: Color = BrandColors.Teal
        val FocusIndicatorHigh: Color = Color(0xFF4DD0C7)
        
        // Selection colors
        val SelectionBackground: Color = BrandColors.Teal.copy(alpha = 0.12f)
        val SelectionText: Color = BrandColors.Teal
    }
    
    /**
     * Elevation tokens for consistent depth hierarchy
     */
    object Elevation {
        val Level0 = 0.dp
        val Level1 = 2.dp
        val Level2 = 4.dp
        val Level3 = 8.dp
        val Level4 = 12.dp
        val Level5 = 16.dp
    }
    
    /**
     * Corner radius tokens for consistent rounded corners
     */
    object CornerRadius {
        val None = 0.dp
        val Small = 4.dp
        val Medium = 8.dp
        val Large = 12.dp
        val ExtraLarge = 16.dp
        val Full = 50.dp
    }
    
    /**
     * Opacity tokens for consistent transparency levels
     */
    object Opacity {
        const val Disabled = 0.38f
        const val Medium = 0.60f
        const val High = 0.87f
        const val Full = 1.0f
    }
    
    /**
     * Spacing tokens for consistent layout spacing
     */
    object Spacing {
        val None = 0.dp
        val ExtraSmall = 4.dp
        val Small = 8.dp
        val Medium = 12.dp
        val Large = 16.dp
        val ExtraLarge = 24.dp
        val XXLarge = 32.dp
    }
    
    /**
     * Touch target tokens for accessibility and usability
     */
    object TouchTarget {
        val Minimum = 48.dp
        val IconSmall = 20.dp
        val IconMedium = 24.dp
        val IconLarge = 32.dp
        val IconExtraLarge = 40.dp
        val Recommended = 48.dp
    }
}

/**
 * Extension functions for easy brand color access
 */
val Color.Companion.LiftrixTeal: Color get() = LiftrixTokens.BrandColors.Teal
val Color.Companion.LiftrixIndigo: Color get() = LiftrixTokens.BrandColors.Indigo
val Color.Companion.LiftrixCoral: Color get() = LiftrixTokens.BrandColors.Coral

/**
 * Semantic color access functions
 */
object LiftrixSemanticColors {
    fun primary(): Color = LiftrixTokens.SemanticColors.ActionPrimary
    fun secondary(): Color = LiftrixTokens.SemanticColors.ActionSecondary
    fun tertiary(): Color = LiftrixTokens.SemanticColors.ActionTertiary
    fun success(): Color = LiftrixTokens.SemanticColors.Success
    fun warning(): Color = LiftrixTokens.SemanticColors.Warning
    fun error(): Color = LiftrixTokens.SemanticColors.Error
    fun info(): Color = LiftrixTokens.SemanticColors.Info
} 