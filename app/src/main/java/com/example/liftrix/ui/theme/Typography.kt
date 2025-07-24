package com.example.liftrix.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Liftrix Typography System
 * Comprehensive Material 3 typography hierarchy with semantic tokens for consistent text styling
 * 
 * Design Principles:
 * - Poppins Bold for headlines and important headers (athletic confidence)
 * - Inter Medium for body text (optimal readability)
 * - Roboto Mono Light for data and statistics (precision clarity)
 * - Enhanced line heights for improved accessibility
 * - Semantic naming for consistent component usage
 */


/**
 * Complete Material 3 Typography System
 * Enhanced typography with improved line heights and athletic branding
 */
val LiftrixTypographySystem = Typography(
    // Display styles - for large prominent text and app branding
    displayLarge = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 68.sp, // Enhanced from Material 3 standard for better readability  
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 56.sp, // Enhanced line height
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 48.sp, // Enhanced line height
        letterSpacing = 0.sp
    ),
    
    // Headline styles - for section headers and screen titles
    headlineLarge = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 44.sp, // Enhanced line height
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 40.sp, // Enhanced line height
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 36.sp, // Enhanced line height
        letterSpacing = 0.sp
    ),
    
    // Title styles - for card headers and component titles
    titleLarge = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 32.sp, // Enhanced line height
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 28.sp, // Enhanced line height
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 24.sp, // Enhanced line height
        letterSpacing = 0.1.sp
    ),
    
    // Body styles - for content text and descriptions using Inter Medium
    bodyLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 28.sp, // Enhanced line height
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 24.sp, // Enhanced line height
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 20.sp, // Enhanced line height
        letterSpacing = 0.4.sp
    ),
    
    // Label styles - for button labels and form inputs using Inter
    labelLarge = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 24.sp, // Enhanced line height
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 20.sp, // Enhanced line height
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 18.sp, // Enhanced line height
        letterSpacing = 0.5.sp
    )
)

/**
 * Semantic Typography Tokens
 * Context-specific typography styles for consistent usage across the app
 */
object LiftrixSemanticTypography {
    
    /**
     * Screen and Navigation Typography
     */
    
    // Screen titles - for main screen headers
    val screenTitle = LiftrixTypographySystem.headlineMedium
    
    // Screen subtitles - for screen descriptions
    val screenSubtitle = LiftrixTypographySystem.bodyLarge
    
    // Navigation labels - for navigation items
    val navigationLabel = LiftrixTypographySystem.labelLarge
    
    /**
     * Card and Component Typography
     */
    
    // Card titles - for UnifiedWorkoutCard titles
    val cardTitle = LiftrixTypographySystem.titleLarge
    
    // Card subtitles - for card secondary information
    val cardSubtitle = LiftrixTypographySystem.bodyMedium
    
    // Card content - for card body text
    val cardContent = LiftrixTypographySystem.bodyMedium
    
    // Card metadata - for timestamps, counts, etc.
    val cardMetadata = LiftrixTypographySystem.bodySmall
    
    /**
     * Button and Action Typography
     */
    
    // Primary button text - for ModernActionButton primary
    val buttonPrimary = LiftrixTypographySystem.labelLarge
    
    // Secondary button text - for ModernActionButton secondary
    val buttonSecondary = LiftrixTypographySystem.labelLarge
    
    // Tertiary button text - for ModernActionButton tertiary
    val buttonTertiary = LiftrixTypographySystem.labelMedium
    
    // Link text - for text links and navigation
    val linkText = LiftrixTypographySystem.bodyMedium
    
    /**
     * Form and Input Typography
     */
    
    // Input labels - for form field labels
    val inputLabel = LiftrixTypographySystem.bodySmall
    
    // Input text - for text field content
    val inputText = LiftrixTypographySystem.bodyMedium
    
    // Input placeholder - for placeholder text
    val inputPlaceholder = LiftrixTypographySystem.bodyMedium
    
    // Input helper - for helper text and validation messages
    val inputHelper = LiftrixTypographySystem.bodySmall
    
    // Input error - for error messages
    val inputError = LiftrixTypographySystem.bodySmall
    
    /**
     * Fitness and Data Typography
     */
    
    // Exercise names - for exercise titles
    val exerciseName = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )
    
    // Set and rep numbers - for exercise data
    val setNumber = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    )
    
    // Weight and measurement input - for numeric input fields
    val weightInput = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Light,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp
    )
    
    // Statistics large - for prominent statistics display
    val statLarge = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Light,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp
    )
    
    // Statistics medium - for secondary statistics
    val statMedium = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Light,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    )
    
    // Statistics small - for compact statistics
    val statSmall = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    )
    
    /**
     * Timer and Session Typography
     */
    
    // Timer large - for main workout timer
    val timerLarge = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Light,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = 0.sp
    )
    
    // Timer medium - for rest timer
    val timerMedium = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Light,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    )
    
    // Timer small - for compact timer display
    val timerSmall = TextStyle(
        fontFamily = RobotoMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
    
    /**
     * Status and Feedback Typography
     */
    
    // Success messages - for positive feedback
    val successMessage = LiftrixTypographySystem.bodyMedium
    
    // Warning messages - for caution alerts
    val warningMessage = LiftrixTypographySystem.bodyMedium
    
    // Error messages - for error feedback
    val errorMessage = LiftrixTypographySystem.bodyMedium
    
    // Progress labels - for progress indicators
    val progressLabel = LiftrixTypographySystem.bodySmall
    
    /**
     * Legacy and Template Typography
     * 
     * Note: "Template" terminology is being phased out in favor of
     * "Creating a workout" as per UI/UX redesign requirements
     */
    
    // Workout creation title - replaces "template" terminology
    val workoutCreationTitle = LiftrixTypographySystem.titleLarge
    
    // Achievement titles - for accomplishment displays
    val achievementTitle = TextStyle(
        fontFamily = PoppinsFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
    
    // Category labels - for exercise categories
    val categoryLabel = TextStyle(
        fontFamily = InterFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.3.sp
    )
}

/**
 * Typography Usage Guidelines
 * 
 * Use semantic tokens for consistent styling:
 * 
 * Screen Headers:
 * - screenTitle for main screen headers
 * - screenSubtitle for screen descriptions
 * 
 * Cards and Components:
 * - cardTitle for UnifiedWorkoutCard titles
 * - cardSubtitle for secondary card information
 * - cardContent for main card text
 * - cardMetadata for timestamps and counts
 * 
 * Buttons:
 * - buttonPrimary for PrimaryActionButton text
 * - buttonSecondary for SecondaryActionButton text
 * - buttonTertiary for TertiaryActionButton text
 * 
 * Forms:
 * - inputLabel for field labels
 * - inputText for input content
 * - inputHelper for helper text
 * - inputError for error messages
 * 
 * Fitness Data:
 * - exerciseName for exercise titles
 * - setNumber for set/rep display
 * - weightInput for numeric inputs
 * - statLarge/Medium/Small for statistics
 * 
 * Timers:
 * - timerLarge for main workout timer
 * - timerMedium for rest timer
 * - timerSmall for compact displays
 */