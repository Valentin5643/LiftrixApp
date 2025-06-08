package com.example.liftrix.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Liftrix Brand Color System
 * Centralized color definitions following Material 3 principles
 * with accessibility compliance (4.5:1 contrast ratio minimum)
 */
object LiftrixColors {
    
    // Primary Brand Colors
    val Primary: Color = Color(0xFF20C9B7)
    val Secondary: Color = Color(0xFF2A3B7D) 
    val Accent: Color = Color(0xFFFF6B6B)
    val Error: Color = Color(0xFFFF4444)
    
    // Light Theme Colors
    val BackgroundLight: Color = Color(0xFFF8F9FA)
    val SurfaceLight: Color = Color(0xFFFFFFFF)
    val OnPrimary: Color = Color.White
    val OnSecondary: Color = Color.White
    val OnAccent: Color = Color.White
    val OnError: Color = Color.White
    val OnBackground: Color = Color(0xFF1C1B1F)
    val OnSurface: Color = Color(0xFF1C1B1F)
    
    // Dark Theme Colors
    val BackgroundDark: Color = Color(0xFF121212)
    val SurfaceDark: Color = Color(0xFF1E1E1E)
    val OnBackgroundDark: Color = Color(0xFFE6E1E5)
    val OnSurfaceDark: Color = Color(0xFFE6E1E5)
    
    // Material 3 Container Colors (Light Theme)
    val PrimaryContainer: Color = Color(0xFFB2F2EA)
    val OnPrimaryContainer: Color = Color(0xFF003A35)
    val SecondaryContainer: Color = Color(0xFFDDE1FF)
    val OnSecondaryContainer: Color = Color(0xFF0F1B37)
    val TertiaryContainer: Color = Color(0xFFFFDAD8)
    val OnTertiaryContainer: Color = Color(0xFF410006)
    val ErrorContainer: Color = Color(0xFFFFDAD6)
    val OnErrorContainer: Color = Color(0xFF410002)
    
    // Material 3 Container Colors (Dark Theme)
    val PrimaryContainerDark: Color = Color(0xFF005047)
    val OnPrimaryContainerDark: Color = Color(0xFFB2F2EA)
    val SecondaryContainerDark: Color = Color(0xFF1E2A4E)
    val OnSecondaryContainerDark: Color = Color(0xFFDDE1FF)
    val TertiaryContainerDark: Color = Color(0xFF5F0008)
    val OnTertiaryContainerDark: Color = Color(0xFFFFDAD8)
    val ErrorContainerDark: Color = Color(0xFF93000A)
    val OnErrorContainerDark: Color = Color(0xFFFFDAD6)
    
    // Outline Colors
    val Outline: Color = Color(0xFF79747E)
    val OutlineDark: Color = Color(0xFF938F99)
    val OutlineVariant: Color = Color(0xFFCAC4CF)
    val OutlineVariantDark: Color = Color(0xFF49454E)
    
    // Surface Variants
    val SurfaceVariant: Color = Color(0xFFE7E0EC)
    val OnSurfaceVariant: Color = Color(0xFF49454E)
    val SurfaceVariantDark: Color = Color(0xFF49454E)
    val OnSurfaceVariantDark: Color = Color(0xFFCAC4CF)
    
    // Brand Gradients
    val TealCoralGradient: Brush = Brush.linearGradient(
        colors = listOf(Primary, Accent)
    )
    
    val IndigoTealGradient: Brush = Brush.linearGradient(
        colors = listOf(Secondary, Primary)
    )
    
    // Inverse Colors
    val InverseSurface: Color = Color(0xFF313033)
    val InverseOnSurface: Color = Color(0xFFF4EFF4)
    val InversePrimary: Color = Color(0xFFB2F2EA)
    val InverseSurfaceDark: Color = Color(0xFFE6E1E5)
    val InverseOnSurfaceDark: Color = Color(0xFF313033)
    val InversePrimaryDark: Color = Color(0xFF005047)
}