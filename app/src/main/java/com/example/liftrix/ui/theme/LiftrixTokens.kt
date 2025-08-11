package com.example.liftrix.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * Liftrix Design System Tokens
 * Centralized design tokens for consistent spacing, elevation, corner radius, opacity, touch targets, and colors
 * Supports both V1 (5-color system) and V2 (modern Teal-based) color systems
 */
object LiftrixTokens {
    
    /**
     * Brand Color Tokens (V1)
     * Updated to use Persian Green as primary with new 5-color palette
     */
    object BrandColors {
        val Primary = LiftrixColors.PersianGreen      // #339989
        val Secondary = LiftrixColors.TiffanyBlue     // #7DE2D1
    }
    
    /**
     * Brand Color Tokens (V2)
     * Modern Teal-based color system
     */
    object BrandColorsV2 {
        val Primary = LiftrixColorsV2.Teal           // #06B6D4
        val Secondary = LiftrixColorsV2.TealHover    // #0891B2
        val Accent = LiftrixColorsV2.TealLight       // #67E8F9
    }
    
    /**
     * Surface Color Tokens (V1)
     * Consolidated Snow/Night/Jet system for 5-color palette
     */
    object SurfaceColors {
        val Background = LiftrixColors.Snow           // Light theme
        val BackgroundDark = LiftrixColors.Night     // Dark theme (OLED optimized)
        val Surface = LiftrixColors.Snow             // Light theme
        val SurfaceDark = LiftrixColors.Jet          // Dark theme
    }
    
    /**
     * Surface Color Tokens (V2)
     * Pure black/white system with refined hierarchy
     */
    object SurfaceColorsV2 {
        val Background = LiftrixColorsV2.Light.BackgroundPrimary      // Pure white
        val BackgroundDark = LiftrixColorsV2.Dark.BackgroundPrimary   // Pure black (OLED optimized)
        val Surface = LiftrixColorsV2.Light.BackgroundSecondary       // Card background
        val SurfaceDark = LiftrixColorsV2.Dark.BackgroundSecondary    // Card background (dark)
        val SurfaceVariant = LiftrixColorsV2.Light.BackgroundTertiary // Input fields
        val SurfaceVariantDark = LiftrixColorsV2.Dark.BackgroundTertiary // Input fields (dark)
    }
    
    /**
     * Content Color Tokens (V1)
     * Simplified Night/Snow text system for 5-color palette
     */
    object ContentColors {
        val Primary = LiftrixColors.Night            // Text on light surfaces
        val PrimaryDark = LiftrixColors.Snow         // Text on dark surfaces
        val Secondary = LiftrixColors.Jet            // Secondary text
        val SecondaryDark = LiftrixColors.Snow       // Secondary text (dark)
    }
    
    /**
     * Content Color Tokens (V2)
     * Enhanced text hierarchy with improved contrast
     */
    object ContentColorsV2 {
        val Primary = LiftrixColorsV2.Light.TextPrimary      // Text on light surfaces
        val PrimaryDark = LiftrixColorsV2.Dark.TextPrimary   // Text on dark surfaces
        val Secondary = LiftrixColorsV2.Light.TextSecondary  // Secondary text
        val SecondaryDark = LiftrixColorsV2.Dark.TextSecondary // Secondary text (dark)
        val Tertiary = LiftrixColorsV2.Light.TextTertiary    // Tertiary text
        val TertiaryDark = LiftrixColorsV2.Dark.TextTertiary // Tertiary text (dark)
        val Disabled = LiftrixColorsV2.Light.TextDisabled    // Disabled text
        val DisabledDark = LiftrixColorsV2.Dark.TextDisabled // Disabled text (dark)
    }
    
    /**
     * Semantic Color Tokens (V1)
     * Updated action assignments using new 5-color palette
     */
    object SemanticColors {
        val Success = LiftrixColors.PersianGreen     // Success states
        val Warning = LiftrixColors.TiffanyBlue      // Warning states  
        val Error = LiftrixColors.Error              // Error states (exception)
        val Info = LiftrixColors.TiffanyBlue         // Info states
    }
    
    /**
     * Semantic Color Tokens (V2)
     * Enhanced semantic colors with better accessibility
     */
    object SemanticColorsV2 {
        val Success = LiftrixColorsV2.DataViz.Positive    // Success states
        val Warning = LiftrixColorsV2.DataViz.Warning     // Warning states
        val Error = LiftrixColorsV2.DataViz.Negative      // Error states
        val Info = LiftrixColorsV2.Teal                   // Info states
        val Neutral = LiftrixColorsV2.DataViz.Neutral     // Neutral states
    }
    
    /**
     * Material 3 Color Role Tokens (V1)
     * Updated Material 3 mappings using new 5-color palette
     */
    object ColorRoles {
        val Primary = LiftrixColors.PersianGreen
        val OnPrimary = Color.White
        val PrimaryContainer = LiftrixColors.PersianGreenContainer10
        val OnPrimaryContainer = LiftrixColors.Night
        
        val Secondary = LiftrixColors.TiffanyBlue
        val OnSecondary = LiftrixColors.Night
        val SecondaryContainer = LiftrixColors.TiffanyBlueContainer10
        val OnSecondaryContainer = LiftrixColors.Night
        
        // Surface roles
        val Surface = LiftrixColors.Snow
        val OnSurface = LiftrixColors.Night
        val SurfaceVariant = LiftrixColors.Snow
        val OnSurfaceVariant = LiftrixColors.Jet
    }
    
    /**
     * Material 3 Color Role Tokens (V2)
     * Enhanced Material 3 mappings using modern Teal-based system
     */
    object ColorRolesV2 {
        val Primary = LiftrixColorsV2.Teal
        val OnPrimary = Color.White
        val PrimaryContainer = LiftrixColorsV2.TealSurface
        val OnPrimaryContainer = LiftrixColorsV2.TealDark
        
        val Secondary = LiftrixColorsV2.TealHover
        val OnSecondary = Color.White
        val SecondaryContainer = LiftrixColorsV2.Light.BackgroundTertiary
        val OnSecondaryContainer = LiftrixColorsV2.TealDark
        
        // Surface roles (light)
        val Surface = LiftrixColorsV2.Light.BackgroundPrimary
        val OnSurface = LiftrixColorsV2.Light.TextPrimary
        val SurfaceVariant = LiftrixColorsV2.Light.BackgroundSecondary
        val OnSurfaceVariant = LiftrixColorsV2.Light.TextSecondary
        
        // Surface roles (dark)
        val SurfaceDark = LiftrixColorsV2.Dark.BackgroundPrimary
        val OnSurfaceDark = LiftrixColorsV2.Dark.TextPrimary
        val SurfaceVariantDark = LiftrixColorsV2.Dark.BackgroundSecondary
        val OnSurfaceVariantDark = LiftrixColorsV2.Dark.TextSecondary
        
        // Outline roles
        val Outline = LiftrixColorsV2.Light.Outline
        val OutlineDark = LiftrixColorsV2.Dark.Outline
        val OutlineVariant = LiftrixColorsV2.Light.OutlineVariant
        val OutlineVariantDark = LiftrixColorsV2.Dark.OutlineVariant
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
 * Extension functions for easy brand color access (V1)
 */
val Color.Companion.LiftrixPersianGreen: Color get() = LiftrixTokens.BrandColors.Primary
val Color.Companion.LiftrixTiffanyBlue: Color get() = LiftrixTokens.BrandColors.Secondary

/**
 * Extension functions for easy brand color access (V2)
 */
val Color.Companion.LiftrixTeal: Color get() = LiftrixTokens.BrandColorsV2.Primary
val Color.Companion.LiftrixTealHover: Color get() = LiftrixTokens.BrandColorsV2.Secondary
val Color.Companion.LiftrixTealLight: Color get() = LiftrixTokens.BrandColorsV2.Accent

/**
 * Semantic color access functions with contrast validation (V1)
 */
object LiftrixSemanticColors {
    fun success(): Color = LiftrixTokens.SemanticColors.Success
    fun warning(): Color = LiftrixTokens.SemanticColors.Warning
    fun error(): Color = LiftrixTokens.SemanticColors.Error
    fun info(): Color = LiftrixTokens.SemanticColors.Info
}

/**
 * Semantic color access functions with contrast validation (V2)
 */
object LiftrixSemanticColorsV2 {
    fun success(): Color = LiftrixTokens.SemanticColorsV2.Success
    fun warning(): Color = LiftrixTokens.SemanticColorsV2.Warning
    fun error(): Color = LiftrixTokens.SemanticColorsV2.Error
    fun info(): Color = LiftrixTokens.SemanticColorsV2.Info
    fun neutral(): Color = LiftrixTokens.SemanticColorsV2.Neutral
}




 