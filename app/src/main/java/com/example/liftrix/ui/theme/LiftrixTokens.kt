package com.example.liftrix.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.pow

/**
 * Liftrix Design System Tokens
 * Centralized design tokens for consistent spacing, elevation, corner radius, opacity, touch targets, and colors
 */
object LiftrixTokens {
    
    /**
     * Brand Color Tokens
     * Updated to use Persian Green as primary with new 5-color palette
     */
    object BrandColors {
        val Primary = LiftrixColors.PersianGreen      // #339989
        val Secondary = LiftrixColors.TiffanyBlue     // #7DE2D1
    }
    
    /**
     * Surface Color Tokens
     * Consolidated Snow/Night/Jet system for 5-color palette
     */
    object SurfaceColors {
        val Background = LiftrixColors.Snow           // Light theme
        val BackgroundDark = LiftrixColors.Night     // Dark theme (OLED optimized)
        val Surface = LiftrixColors.Snow             // Light theme
        val SurfaceDark = LiftrixColors.Jet          // Dark theme
    }
    
    /**
     * Content Color Tokens
     * Simplified Night/Snow text system for 5-color palette
     */
    object ContentColors {
        val Primary = LiftrixColors.Night            // Text on light surfaces
        val PrimaryDark = LiftrixColors.Snow         // Text on dark surfaces
        val Secondary = LiftrixColors.Jet            // Secondary text
        val SecondaryDark = LiftrixColors.Snow       // Secondary text (dark)
    }
    
    /**
     * Semantic Color Tokens
     * Updated action assignments using new 5-color palette
     */
    object SemanticColors {
        val Success = LiftrixColors.PersianGreen     // Success states
        val Warning = LiftrixColors.TiffanyBlue      // Warning states  
        val Error = LiftrixColors.Error              // Error states (exception)
        val Info = LiftrixColors.TiffanyBlue         // Info states
    }
    
    /**
     * Material 3 Color Role Tokens
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
val Color.Companion.LiftrixPersianGreen: Color get() = LiftrixTokens.BrandColors.Primary
val Color.Companion.LiftrixTiffanyBlue: Color get() = LiftrixTokens.BrandColors.Secondary

/**
 * Semantic color access functions with contrast validation
 */
object LiftrixSemanticColors {
    fun success(): Color = LiftrixTokens.SemanticColors.Success
    fun warning(): Color = LiftrixTokens.SemanticColors.Warning
    fun error(): Color = LiftrixTokens.SemanticColors.Error
    fun info(): Color = LiftrixTokens.SemanticColors.Info
}




 