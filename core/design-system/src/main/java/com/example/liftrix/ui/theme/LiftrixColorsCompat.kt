package com.example.liftrix.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * BACKWARD COMPATIBILITY STUB for LiftrixColors
 * 
 * This provides the old LiftrixColors object that maps to LiftrixColorsV2
 * so we don't have to update every single file in the codebase.
 * 
 * All colors now map to their LiftrixColorsV2 equivalents.
 */
object LiftrixColors {
    
    // Core colors mapped to V2 equivalents
    val Night = LiftrixColorsV2.Dark.BackgroundPrimary        // #0B0C0B
    val Jet = LiftrixColorsV2.Dark.BackgroundSecondary        // #0F0F0F
    val PersianGreen = LiftrixColorsV2.Teal                   // #00BCD4 (new primary)
    val TiffanyBlue = LiftrixColorsV2.TealLight               // #67E8F9 (new accent)
    val Snow = LiftrixColorsV2.Light.BackgroundPrimary        // #FFFFFF
    
    // Simplified color mappings
    val Primary: Color = PersianGreen
    val Secondary: Color = Jet
    val Error: Color = LiftrixColorsV2.Light.Error            // Maps to V2 error
    
    // Container variations using V2 equivalents
    val PersianGreenContainer10 = LiftrixColorsV2.TealSurface.copy(alpha = 0.1f)
    val PersianGreenContainer20 = LiftrixColorsV2.TealSurface.copy(alpha = 0.2f)
    val PersianGreenContainer30 = LiftrixColorsV2.TealSurface.copy(alpha = 0.3f)
    val TiffanyBlueContainer10 = LiftrixColorsV2.TealLight.copy(alpha = 0.1f)
    val TiffanyBlueContainer20 = LiftrixColorsV2.TealLight.copy(alpha = 0.2f)
    val TiffanyBlueContainer30 = LiftrixColorsV2.TealLight.copy(alpha = 0.3f)
    
    // Theme-based background and surface colors
    val BackgroundLight: Color = Snow
    val BackgroundDark: Color = Night
    val SurfaceLight: Color = Snow
    val SurfaceDark: Color = Jet
    
    // Text and content colors optimized for contrast
    val OnPrimary: Color = Snow
    val OnSecondary: Color = Snow
    val OnError: Color = Snow
    val OnBackground: Color = Night
    val OnSurface: Color = Night
    val OnBackgroundDark: Color = Snow
    val OnSurfaceDark: Color = Snow
    
    // Material 3 Container Colors using V2 equivalents
    val PrimaryContainer: Color = LiftrixColorsV2.TealSurface
    val OnPrimaryContainer: Color = LiftrixColorsV2.TealDark
    val SecondaryContainer: Color = LiftrixColorsV2.Light.BackgroundSecondary
    val OnSecondaryContainer: Color = LiftrixColorsV2.Light.TextPrimary
    val TertiaryContainer: Color = LiftrixColorsV2.TealLight.copy(alpha = 0.2f)
    val OnTertiaryContainer: Color = LiftrixColorsV2.Dark.TextPrimary
    val ErrorContainer: Color = LiftrixColorsV2.Light.ErrorContainer
    val OnErrorContainer: Color = LiftrixColorsV2.Light.TextPrimary
    
    // Material 3 Container Colors (Dark Theme)
    val PrimaryContainerDark: Color = LiftrixColorsV2.TealContainer
    val OnPrimaryContainerDark: Color = LiftrixColorsV2.TealLight
    val SecondaryContainerDark: Color = LiftrixColorsV2.Dark.BackgroundTertiary
    val OnSecondaryContainerDark: Color = LiftrixColorsV2.Dark.TextPrimary
    val TertiaryContainerDark: Color = LiftrixColorsV2.TealDark
    val OnTertiaryContainerDark: Color = LiftrixColorsV2.TealLight
    val ErrorContainerDark: Color = Color(0xFF93000A)
    val OnErrorContainerDark: Color = Snow
    
    // Outline Colors using Teal
    val Outline: Color = LiftrixColorsV2.Teal.copy(alpha = 0.38f)
    val OutlineDark: Color = LiftrixColorsV2.Teal.copy(alpha = 0.60f)
    val OutlineVariant: Color = LiftrixColorsV2.Teal.copy(alpha = 0.12f)
    val OutlineVariantDark: Color = LiftrixColorsV2.Teal.copy(alpha = 0.24f)
    
    // Surface Variants using V2 colors
    val SurfaceVariant: Color = LiftrixColorsV2.Light.BackgroundSecondary
    val OnSurfaceVariant: Color = LiftrixColorsV2.Light.TextSecondary
    val SurfaceVariantDark: Color = LiftrixColorsV2.Dark.BackgroundSecondary
    val OnSurfaceVariantDark: Color = LiftrixColorsV2.Dark.TextSecondary
    
    // Inverse Colors using V2 palette
    val InverseSurface: Color = Jet
    val InverseOnSurface: Color = Snow
    val InversePrimary: Color = TiffanyBlue
    val InverseSurfaceDark: Color = Snow
    val InverseOnSurfaceDark: Color = Night
    val InversePrimaryDark: Color = PersianGreen
}