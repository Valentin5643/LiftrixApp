package com.example.liftrix.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import timber.log.Timber

/**
 * Theme Migration Utilities
 * Provides helper functions for gradually migrating from V1 (5-color system) to V2 (Teal-based system)
 * Enables safe rollback and AB testing between theme versions
 */
object ThemeMigrationUtils {
    
    /**
     * Maps V1 colors to their V2 equivalents for consistent migration
     */
    fun mapV1ToV2Color(v1Color: Color): Color {
        return when (v1Color) {
            // Persian Green → Teal
            LiftrixColors.PersianGreen -> LiftrixColorsV2.Teal
            Color(0xFF339989) -> LiftrixColorsV2.Teal
            
            // Tiffany Blue → Teal Hover
            LiftrixColors.TiffanyBlue -> LiftrixColorsV2.TealHover
            Color(0xFF7DE2D1) -> LiftrixColorsV2.TealHover
            
            // Night → Pure Black
            LiftrixColors.Night -> LiftrixColorsV2.Dark.BackgroundPrimary
            Color(0xFF131515) -> LiftrixColorsV2.Dark.BackgroundPrimary
            
            // Jet → Dark Secondary Background
            LiftrixColors.Jet -> LiftrixColorsV2.Dark.BackgroundSecondary
            Color(0xFF2B2C28) -> LiftrixColorsV2.Dark.BackgroundSecondary
            
            // Snow → Pure White
            LiftrixColors.Snow -> LiftrixColorsV2.Light.BackgroundPrimary
            Color(0xFFFFFAFB) -> LiftrixColorsV2.Light.BackgroundPrimary
            
            // Error colors remain consistent
            LiftrixColors.Error -> LiftrixColorsV2.DataViz.Negative
            Color(0xFFFF4444) -> LiftrixColorsV2.DataViz.Negative
            
            // Container colors
            LiftrixColors.PrimaryContainer -> LiftrixColorsV2.TealSurface
            LiftrixColors.PrimaryContainerDark -> LiftrixColorsV2.TealContainer
            LiftrixColors.SecondaryContainer -> LiftrixColorsV2.Light.BackgroundTertiary
            LiftrixColors.SecondaryContainerDark -> LiftrixColorsV2.Dark.BackgroundTertiary
            
            // If no mapping found, return the original color
            else -> {
                Timber.w("ThemeMigrationUtils: No V2 mapping found for color $v1Color, using original")
                v1Color
            }
        }
    }
    
    /**
     * Maps V2 colors back to their V1 equivalents for rollback scenarios
     */
    fun mapV2ToV1Color(v2Color: Color): Color {
        return when (v2Color) {
            // Teal → Persian Green
            LiftrixColorsV2.Teal -> LiftrixColors.PersianGreen
            Color(0xFF06B6D4) -> LiftrixColors.PersianGreen
            
            // Teal Hover → Tiffany Blue
            LiftrixColorsV2.TealHover -> LiftrixColors.TiffanyBlue
            Color(0xFF0891B2) -> LiftrixColors.TiffanyBlue
            
            // Pure Black → Night
            LiftrixColorsV2.Dark.BackgroundPrimary -> LiftrixColors.Night
            Color(0xFF000000) -> LiftrixColors.Night
            
            // Dark Secondary Background → Jet
            LiftrixColorsV2.Dark.BackgroundSecondary -> LiftrixColors.Jet
            Color(0xFF1A1A1A) -> LiftrixColors.Jet
            
            // Pure White → Snow
            LiftrixColorsV2.Light.BackgroundPrimary -> LiftrixColors.Snow
            Color(0xFFFFFFFF) -> LiftrixColors.Snow
            
            // Error colors
            LiftrixColorsV2.DataViz.Negative -> LiftrixColors.Error
            Color(0xFFEF4444) -> LiftrixColors.Error
            
            // Container colors
            LiftrixColorsV2.TealSurface -> LiftrixColors.PrimaryContainer
            LiftrixColorsV2.TealContainer -> LiftrixColors.PrimaryContainerDark
            LiftrixColorsV2.Light.BackgroundTertiary -> LiftrixColors.SecondaryContainer
            LiftrixColorsV2.Dark.BackgroundTertiary -> LiftrixColors.SecondaryContainerDark
            
            // If no mapping found, return the original color
            else -> {
                Timber.w("ThemeMigrationUtils: No V1 mapping found for color $v2Color, using original")
                v2Color
            }
        }
    }
    
    /**
     * Migrates a V1 color scheme to V2 equivalent
     */
    fun migrateColorSchemeToV2(v1ColorScheme: ColorScheme): ColorScheme {
        Timber.d("ThemeMigrationUtils: Migrating V1 color scheme to V2")
        
        return v1ColorScheme.copy(
            primary = mapV1ToV2Color(v1ColorScheme.primary),
            onPrimary = mapV1ToV2Color(v1ColorScheme.onPrimary),
            primaryContainer = mapV1ToV2Color(v1ColorScheme.primaryContainer),
            onPrimaryContainer = mapV1ToV2Color(v1ColorScheme.onPrimaryContainer),
            
            secondary = mapV1ToV2Color(v1ColorScheme.secondary),
            onSecondary = mapV1ToV2Color(v1ColorScheme.onSecondary),
            secondaryContainer = mapV1ToV2Color(v1ColorScheme.secondaryContainer),
            onSecondaryContainer = mapV1ToV2Color(v1ColorScheme.onSecondaryContainer),
            
            tertiary = mapV1ToV2Color(v1ColorScheme.tertiary),
            onTertiary = mapV1ToV2Color(v1ColorScheme.onTertiary),
            tertiaryContainer = mapV1ToV2Color(v1ColorScheme.tertiaryContainer),
            onTertiaryContainer = mapV1ToV2Color(v1ColorScheme.onTertiaryContainer),
            
            background = mapV1ToV2Color(v1ColorScheme.background),
            onBackground = mapV1ToV2Color(v1ColorScheme.onBackground),
            
            surface = mapV1ToV2Color(v1ColorScheme.surface),
            onSurface = mapV1ToV2Color(v1ColorScheme.onSurface),
            surfaceVariant = mapV1ToV2Color(v1ColorScheme.surfaceVariant),
            onSurfaceVariant = mapV1ToV2Color(v1ColorScheme.onSurfaceVariant),
            
            error = mapV1ToV2Color(v1ColorScheme.error),
            onError = mapV1ToV2Color(v1ColorScheme.onError),
            errorContainer = mapV1ToV2Color(v1ColorScheme.errorContainer),
            onErrorContainer = mapV1ToV2Color(v1ColorScheme.onErrorContainer),
            
            outline = mapV1ToV2Color(v1ColorScheme.outline),
            outlineVariant = mapV1ToV2Color(v1ColorScheme.outlineVariant),
            
            inverseSurface = mapV1ToV2Color(v1ColorScheme.inverseSurface),
            inverseOnSurface = mapV1ToV2Color(v1ColorScheme.inverseOnSurface),
            inversePrimary = mapV1ToV2Color(v1ColorScheme.inversePrimary)
        )
    }
    
    /**
     * Migrates a V2 color scheme back to V1 equivalent
     */
    fun migrateColorSchemeToV1(v2ColorScheme: ColorScheme): ColorScheme {
        Timber.d("ThemeMigrationUtils: Migrating V2 color scheme to V1")
        
        return v2ColorScheme.copy(
            primary = mapV2ToV1Color(v2ColorScheme.primary),
            onPrimary = mapV2ToV1Color(v2ColorScheme.onPrimary),
            primaryContainer = mapV2ToV1Color(v2ColorScheme.primaryContainer),
            onPrimaryContainer = mapV2ToV1Color(v2ColorScheme.onPrimaryContainer),
            
            secondary = mapV2ToV1Color(v2ColorScheme.secondary),
            onSecondary = mapV2ToV1Color(v2ColorScheme.onSecondary),
            secondaryContainer = mapV2ToV1Color(v2ColorScheme.secondaryContainer),
            onSecondaryContainer = mapV2ToV1Color(v2ColorScheme.onSecondaryContainer),
            
            tertiary = mapV2ToV1Color(v2ColorScheme.tertiary),
            onTertiary = mapV2ToV1Color(v2ColorScheme.onTertiary),
            tertiaryContainer = mapV2ToV1Color(v2ColorScheme.tertiaryContainer),
            onTertiaryContainer = mapV2ToV1Color(v2ColorScheme.onTertiaryContainer),
            
            background = mapV2ToV1Color(v2ColorScheme.background),
            onBackground = mapV2ToV1Color(v2ColorScheme.onBackground),
            
            surface = mapV2ToV1Color(v2ColorScheme.surface),
            onSurface = mapV2ToV1Color(v2ColorScheme.onSurface),
            surfaceVariant = mapV2ToV1Color(v2ColorScheme.surfaceVariant),
            onSurfaceVariant = mapV2ToV1Color(v2ColorScheme.onSurfaceVariant),
            
            error = mapV2ToV1Color(v2ColorScheme.error),
            onError = mapV2ToV1Color(v2ColorScheme.onError),
            errorContainer = mapV2ToV1Color(v2ColorScheme.errorContainer),
            onErrorContainer = mapV2ToV1Color(v2ColorScheme.onErrorContainer),
            
            outline = mapV2ToV1Color(v2ColorScheme.outline),
            outlineVariant = mapV2ToV1Color(v2ColorScheme.outlineVariant),
            
            inverseSurface = mapV2ToV1Color(v2ColorScheme.inverseSurface),
            inverseOnSurface = mapV2ToV1Color(v2ColorScheme.inverseOnSurface),
            inversePrimary = mapV2ToV1Color(v2ColorScheme.inversePrimary)
        )
    }
    
    /**
     * Get migration report for tracking color replacements
     */
    fun getMigrationReport(): MigrationReport {
        val v1ToV2Mappings = mapOf(
            "Persian Green (#339989)" to "Teal (#06B6D4)",
            "Tiffany Blue (#7DE2D1)" to "Teal Hover (#0891B2)",
            "Night (#131515)" to "Pure Black (#000000)",
            "Jet (#2B2C28)" to "Dark Secondary (#1A1A1A)",
            "Snow (#FFFAFB)" to "Pure White (#FFFFFF)"
        )
        
        val migrationBenefits = listOf(
            "Improved WCAG AA contrast ratios",
            "OLED-optimized pure black backgrounds",
            "Modern, premium visual aesthetic",
            "Better Material 3 integration",
            "Enhanced data visualization palette"
        )
        
        val rollbackRisks = listOf(
            "Visual inconsistency during transition",
            "User adaptation period required",
            "Potential component styling adjustments needed"
        )
        
        return MigrationReport(
            v1ToV2Mappings = v1ToV2Mappings,
            migrationBenefits = migrationBenefits,
            rollbackRisks = rollbackRisks,
            recommendedApproach = "Gradual rollout with feature flags and A/B testing"
        )
    }
    
    /**
     * Validates that a color scheme migration was successful
     */
    fun validateMigration(
        originalScheme: ColorScheme,
        migratedScheme: ColorScheme,
        targetVersion: ThemeVersion
    ): MigrationValidationResult {
        val issues = mutableListOf<String>()
        var successfulMappings = 0
        val totalColors = 24 // Standard Material 3 color count
        
        // Validate primary colors
        val expectedPrimary = when (targetVersion) {
            ThemeVersion.V1 -> LiftrixColors.PersianGreen
            ThemeVersion.V2 -> LiftrixColorsV2.Teal
        }
        
        if (migratedScheme.primary != expectedPrimary) {
            issues.add("Primary color not migrated correctly")
        } else {
            successfulMappings++
        }
        
        // Validate background colors
        val expectedBackground = when (targetVersion) {
            ThemeVersion.V1 -> LiftrixColors.Snow
            ThemeVersion.V2 -> LiftrixColorsV2.Light.BackgroundPrimary
        }
        
        // Note: This is a simplified validation - full validation would check all color roles
        if (migratedScheme.background == expectedBackground) {
            successfulMappings++
        } else {
            issues.add("Background color not migrated correctly")
        }
        
        val migrationAccuracy = (successfulMappings.toFloat() / totalColors) * 100f
        val isSuccessful = migrationAccuracy >= 90f && issues.isEmpty()
        
        Timber.i("ThemeMigrationUtils: Migration validation - " +
                "Accuracy: ${migrationAccuracy.toInt()}%, " +
                "Issues: ${issues.size}, " +
                "Successful: $isSuccessful")
        
        return MigrationValidationResult(
            isSuccessful = isSuccessful,
            migrationAccuracy = migrationAccuracy,
            issues = issues,
            successfulMappings = successfulMappings,
            totalColors = totalColors
        )
    }
    
    /**
     * Gets the appropriate color scheme based on theme version
     */
    fun getColorSchemeForVersion(
        themeVersion: ThemeVersion,
        isDarkTheme: Boolean
    ): ColorScheme {
        return when (themeVersion) {
            ThemeVersion.V1 -> {
                ColorSystemOptimizations.getColorScheme(isDarkTheme)
            }
            ThemeVersion.V2 -> {
                ColorSystemOptimizations.getColorSchemeV2(isDarkTheme)
            }
        }
    }
    
    /**
     * Creates a hybrid color scheme for gradual migration
     * Uses V2 primary colors with V1 surface colors for smoother transition
     */
    fun createHybridColorScheme(
        v1Scheme: ColorScheme,
        v2Scheme: ColorScheme,
        migrationProgress: Float // 0.0f = full V1, 1.0f = full V2
    ): ColorScheme {
        require(migrationProgress in 0f..1f) {
            "Migration progress must be between 0.0 and 1.0"
        }
        
        // Interpolate between V1 and V2 colors based on progress
        fun interpolateColor(v1Color: Color, v2Color: Color): Color {
            return Color(
                red = v1Color.red + (v2Color.red - v1Color.red) * migrationProgress,
                green = v1Color.green + (v2Color.green - v1Color.green) * migrationProgress,
                blue = v1Color.blue + (v2Color.blue - v1Color.blue) * migrationProgress,
                alpha = v1Color.alpha + (v2Color.alpha - v1Color.alpha) * migrationProgress
            )
        }
        
        Timber.d("ThemeMigrationUtils: Creating hybrid color scheme with ${(migrationProgress * 100).toInt()}% V2 migration")
        
        return v1Scheme.copy(
            primary = interpolateColor(v1Scheme.primary, v2Scheme.primary),
            onPrimary = interpolateColor(v1Scheme.onPrimary, v2Scheme.onPrimary),
            primaryContainer = interpolateColor(v1Scheme.primaryContainer, v2Scheme.primaryContainer),
            onPrimaryContainer = interpolateColor(v1Scheme.onPrimaryContainer, v2Scheme.onPrimaryContainer),
            
            secondary = interpolateColor(v1Scheme.secondary, v2Scheme.secondary),
            onSecondary = interpolateColor(v1Scheme.onSecondary, v2Scheme.onSecondary),
            
            // Keep surface colors more stable during transition
            background = if (migrationProgress > 0.7f) v2Scheme.background else v1Scheme.background,
            onBackground = if (migrationProgress > 0.7f) v2Scheme.onBackground else v1Scheme.onBackground,
            surface = if (migrationProgress > 0.7f) v2Scheme.surface else v1Scheme.surface,
            onSurface = if (migrationProgress > 0.7f) v2Scheme.onSurface else v1Scheme.onSurface
        )
    }
}

/**
 * Data class representing a complete migration report
 */
data class MigrationReport(
    val v1ToV2Mappings: Map<String, String>,
    val migrationBenefits: List<String>,
    val rollbackRisks: List<String>,
    val recommendedApproach: String
)

/**
 * Data class representing migration validation results
 */
data class MigrationValidationResult(
    val isSuccessful: Boolean,
    val migrationAccuracy: Float,
    val issues: List<String>,
    val successfulMappings: Int,
    val totalColors: Int
)

/**
 * Extension functions for easy migration access
 */
fun ColorScheme.migrateToV2(): ColorScheme = ThemeMigrationUtils.migrateColorSchemeToV2(this)
fun ColorScheme.migrateToV1(): ColorScheme = ThemeMigrationUtils.migrateColorSchemeToV1(this)

/**
 * Extension function for color migration
 */
fun Color.migrateToV2(): Color = ThemeMigrationUtils.mapV1ToV2Color(this)
fun Color.migrateToV1(): Color = ThemeMigrationUtils.mapV2ToV1Color(this)