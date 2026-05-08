package com.example.liftrix.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe route definitions for Liftrix navigation
 * 
 * This sealed class hierarchy replaces string-based navigation with compile-time
 * type safety and kotlinx.serialization support for deep linking and state restoration.
 * 
 * Key features:
 * - Compile-time route validation prevents runtime navigation errors
 * - Type-safe parameters eliminate string manipulation for navigation arguments
 * - Automatic serialization/deserialization for deep linking support
 * - Clear route hierarchy enables easy navigation maintenance
 */
@Serializable
sealed class LiftrixRoute {
    
    /**
     * Home screen - main dashboard with workout feed and discovery
     */
    @Serializable
    data object Home : LiftrixRoute()
    
    /**
     * Workout screen - workout routines and session management
     */
    @Serializable
    data object Workout : LiftrixRoute()
    
    /**
     * Progress dashboard - analytics and workout history
     */
    @Serializable
    data object Progress : LiftrixRoute()
    
    /**
     * Coach screen - AI guidance and workout recommendations
     */
    @Serializable
    data object Coach : LiftrixRoute()
    
    /**
     * Friends/Social screen - social features and sharing
     */
    @Serializable
    data object Friends : LiftrixRoute()
    
    // Social Discovery Routes
    
    /**
     * User search screen for discovering and connecting with other users
     */
    @Serializable
    data object UserSearch : LiftrixRoute()
    
    /**
     * Public profile display screen showing privacy-aware user information
     * 
     * @param userId Unique identifier for the user whose profile to display
     * @param initialAction Optional action to trigger on screen load (e.g., "follow", "message")
     */
    @Serializable
    data class PublicProfile(
        val userId: String,
        val initialAction: String? = null
    ) : LiftrixRoute()
    
    /**
     * Social onboarding screen for first-time social feature setup
     */
    @Serializable
    data object SocialOnboarding : LiftrixRoute()
    
    /**
     * Privacy settings screen for granular social privacy controls
     */
    @Serializable
    data object PrivacySettings : LiftrixRoute()
    
    /**
     * Workout details screen with specific workout ID
     * 
     * @param workoutId Unique identifier for the workout to display
     */
    @Serializable
    data class WorkoutDetails(val workoutId: String) : LiftrixRoute()
    
    /**
     * Exercise selection screen for adding exercises to workout or routine
     * 
     * @param templateId Optional workout routine ID when selecting exercises for routine creation (backend compatibility)
     * @param isForTemplate Whether the selection is for routine creation or active workout (backend compatibility)
     * @param replaceExerciseIndex Index of exercise to replace, null means add new
     */
    @Serializable
    data class ExerciseSelection(
        val templateId: String? = null,
        val isForTemplate: Boolean = false,
        val replaceExerciseIndex: Int? = null
    ) : LiftrixRoute()
    
    /**
     * Active workout session screen
     * 
     * @param templateId Optional workout routine ID to start workout from saved routine (backend compatibility)
     * @param isBlankWorkout Whether to start a blank workout without saved routine
     */
    @Serializable
    data class ActiveWorkout(
        val templateId: String? = null,
        val isBlankWorkout: Boolean = false
    ) : LiftrixRoute()
    
    /**
     * Template creation screen for creating new workout routines
     * @deprecated Use CreateWorkout instead for user-friendly workflow terminology
     * 
     * @param folderId Optional folder ID to create the template in. If null, uses default folder.
     */
    @Deprecated("Use CreateWorkout instead", ReplaceWith("LiftrixRoute.CreateWorkout"))
    @Serializable
    data class TemplateCreation(
        val folderId: String? = null
    ) : LiftrixRoute()
    
    /**
     * Workout creation screen for creating new workout routines (user-friendly "Creating a workout")
     * 
     * @param folderId Optional folder ID to create the workout in. If null, uses default folder.
     */
    @Serializable
    data class CreateWorkout(
        val folderId: String? = null
    ) : LiftrixRoute()
    
    /**
     * Exercise details screen showing specific exercise information
     * 
     * @param exerciseId Unique identifier for the exercise to display
     */
    @Serializable
    data class ExerciseDetails(val exerciseId: String) : LiftrixRoute()
    
    /**
     * Settings screen for user preferences and app configuration
     */
    @Serializable
    data object Settings : LiftrixRoute()
    
    /**
     * Sync settings screen for managing data synchronization preferences
     * Provides comprehensive sync controls, status monitoring, and troubleshooting
     */
    @Serializable
    data object SyncSettings : LiftrixRoute()
    
    /**
     * Widget settings screen for customizing dashboard widgets
     */
    @Serializable
    data object WidgetSettings : LiftrixRoute()
    
    /**
     * Dashboard customization screen - enhanced widget management interface
     */
    @Serializable
    data object DashboardCustomization : LiftrixRoute()
    
    /**
     * Onboarding flow for new user setup
     */
    @Serializable
    data object Onboarding : LiftrixRoute()
    
    /**
     * Anomaly detection dashboard - view and manage workout anomalies
     */
    @Serializable
    data object AnomalyDashboard : LiftrixRoute()
    
    /**
     * Anomaly detection settings - configure detection sensitivity
     */
    @Serializable
    data object AnomalySettings : LiftrixRoute()
    
    /**
     * Edit workout routine screen for modifying saved workout routines
     * 
     * @param workoutId Unique identifier for the workout routine to edit
     */
    @Serializable
    data class EditWorkout(val workoutId: String) : LiftrixRoute()
    
    /**
     * Edit workout session screen for modifying completed workout sessions
     * 
     * @param sessionId Unique identifier for the workout session to edit
     */
    @Serializable
    data class EditSession(val sessionId: String) : LiftrixRoute()
    
    // Guest Mode Routes
    
    /**
     * Guest mode selection screen shown during onboarding
     */
    @Serializable
    data object GuestModeSelection : LiftrixRoute()
    
    /**
     * Guest session dashboard showing current limitations and conversion prompts
     */
    @Serializable
    data object GuestDashboard : LiftrixRoute()
    
    /**
     * Guest-to-registered conversion flow screen
     * 
     * @param source The source screen that triggered the conversion (e.g., "limit_reached", "nudge", "manual")
     * @param returnTo Optional route to return to after successful conversion
     */
    @Serializable
    data class GuestConversion(
        val source: String = "manual",
        val returnTo: String? = null
    ) : LiftrixRoute()
    
    // Authentication Routes
    
    /**
     * Sign-up screen for new user registration
     */
    @Serializable
    data object AuthSignUp : LiftrixRoute()
    
    /**
     * Sign-in screen for existing user authentication
     */
    @Serializable
    data object AuthSignIn : LiftrixRoute()
    
    /**
     * Profile screen - main profile display with achievements and settings
     * 
     * @param userId Optional user ID for viewing other users' profiles (defaults to current user)
     */
    @Serializable
    data class Profile(val userId: String? = null) : LiftrixRoute()
    
    /**
     * Profile edit screen - comprehensive profile editing interface
     */
    @Serializable
    data object ProfileEdit : LiftrixRoute()
    
    /**
     * Image crop screen for profile picture editing
     * 
     * @param imageUri URI of the image to crop (serialized as string)
     */
    @Serializable
    data class ImageCrop(val imageUri: String) : LiftrixRoute()
    
    // Analytics Detail Screen Routes
    
    /**
     * Volume analysis detail screen showing comprehensive volume analytics
     */
    @Serializable
    data object VolumeAnalysisDetail : LiftrixRoute()
    
    /**
     * One rep max detail screen showing strength progression analytics
     */
    @Serializable
    data object OneRmDetail : LiftrixRoute()
    
    /**
     * Muscle group detail screen showing muscle group distribution analytics
     */
    @Serializable
    data object MuscleGroupDetail : LiftrixRoute()
    
    /**
     * Workout frequency detail screen showing frequency and consistency analytics
     */
    @Serializable
    data object WorkoutFrequencyDetail : LiftrixRoute()
    
    /**
     * Exercise ranking detail screen showing exercise performance rankings
     */
    @Serializable
    data object ExerciseRankingDetail : LiftrixRoute()
    
    // Social System Routes (Added for social system completion)
    
    /**
     * Share workout screen for sharing workout sessions and routines
     * 
     * @param workoutId Unique identifier for the workout to share
     */
    @Serializable
    data class ShareWorkout(val workoutId: String) : LiftrixRoute()
    
    /**
     * Post-workout summary screen showing comprehensive workout statistics
     * 
     * @param workoutId Unique identifier for the completed workout
     */
    @Serializable
    data class PostWorkoutSummary(val workoutId: String) : LiftrixRoute()
    
    /**
     * Progress comparison screen for comparing workout progress between users
     * 
     * @param comparisonId Unique identifier for the comparison to display
     * @param shareMode Whether this comparison is being viewed in share mode (default: false)
     */
    @Serializable
    data class ProgressComparison(
        val comparisonId: String,
        val shareMode: Boolean = false
    ) : LiftrixRoute()
    
    /**
     * Notification settings screen for managing social notifications
     */
    @Serializable
    data object NotificationSettings : LiftrixRoute()
    
    /**
     * Followers list screen showing users who follow the specified user
     * 
     * @param userId Unique identifier for the user whose followers to display
     * @param listType Type of list to display (FOLLOWERS, FOLLOWING, PENDING_REQUESTS)
     */
    @Serializable
    data class FollowersList(
        val userId: String,
        val listType: String = "FOLLOWERS"
    ) : LiftrixRoute()
    
    /**
     * Following list screen showing users that the specified user follows
     * 
     * @param userId Unique identifier for the user whose following list to display
     * @param listType Type of list to display (FOLLOWERS, FOLLOWING, PENDING_REQUESTS)
     */
    @Serializable
    data class FollowingList(
        val userId: String,
        val listType: String = "FOLLOWING"
    ) : LiftrixRoute()
    
    /**
     * Gym buddy screen for QR code pairing and gym partner connections
     */
    @Serializable
    data object GymBuddy : LiftrixRoute()

    /**
     * QR scanner screen for in-app gym buddy pairing.
     */
    @Serializable
    data object QRScanner : LiftrixRoute()

    @Serializable
    data class TemplateBuddyShare(val templateId: String) : LiftrixRoute()

    @Serializable
    data class WorkoutSharedWithYou(val shareId: String) : LiftrixRoute()

    @Serializable
    data class WorkoutShareInbox(val senderId: String) : LiftrixRoute()
    
    /**
     * User workouts screen showing all completed workouts with social engagement metrics
     * Displays the user's workout history with likes, comments, and other engagement data
     */
    @Serializable
    data object UserWorkouts : LiftrixRoute()
    
    /**
     * Post creation screen for creating and sharing workout posts
     * 
     * @param workoutId Unique identifier for the workout to create a post from
     */
    @Serializable
    data class PostCreation(val workoutId: String) : LiftrixRoute()
    
    /**
     * Post comments screen for viewing and managing post comments
     * 
     * @param postId Unique identifier for the post to view comments for
     */
    @Serializable
    data class PostComments(val postId: String) : LiftrixRoute()
    
    // Account Management Routes (Added for SPEC-20250116-account-management)
    
    /**
     * Email change screen for updating user email address
     */
    @Serializable
    data object EmailChange : LiftrixRoute()
    
    /**
     * Password change screen for updating user password with strength indicator
     */
    @Serializable
    data object PasswordChange : LiftrixRoute()
    
    /**
     * Username change screen for updating username with availability checking
     */
    @Serializable
    data object UsernameChange : LiftrixRoute()
    
    /**
     * Account deletion flow for permanently deleting user account
     */
    @Serializable
    data object AccountDeletion : LiftrixRoute()
    
    // Help and Support System Routes (Added for SPEC-20250116-app-information)
    
    /**
     * Help center screen with search and categories
     */
    @Serializable
    data object HelpCenter : LiftrixRoute()
    
    /**
     * Help article detail screen for viewing specific help articles
     * 
     * @param articleId Unique identifier for the help article to display
     */
    @Serializable
    data class HelpArticle(val articleId: String) : LiftrixRoute()
    
    /**
     * Contact support screen for creating support tickets
     */
    @Serializable
    data object ContactSupport : LiftrixRoute()
    
    /**
     * Support ticket detail screen for viewing specific support tickets
     * 
     * @param ticketId Unique identifier for the support ticket to display
     */
    @Serializable
    data class SupportTicket(val ticketId: String) : LiftrixRoute()
    
    /**
     * About screen with app information, version details, and credits
     */
    @Serializable
    data object About : LiftrixRoute()
    
    /**
     * Privacy policy screen for viewing legal documents
     */
    @Serializable
    data object PrivacyPolicy : LiftrixRoute()

    /**
     * Terms of service screen for viewing legal documents
     */
    @Serializable
    data object TermsOfService : LiftrixRoute()

    /**
     * AI disclaimer screen for viewing AI usage policy
     */
    @Serializable
    data object AIDisclaimer : LiftrixRoute()

    /**
     * Community guidelines screen for viewing community standards and acceptable behavior
     */
    @Serializable
    data object CommunityGuidelines : LiftrixRoute()

    /**
     * Content moderation policy screen for viewing enforcement guidelines
     */
    @Serializable
    data object ContentModerationPolicy : LiftrixRoute()

    /**
     * Refund & subscription policy screen for billing terms
     */
    @Serializable
    data object RefundSubscriptionPolicy : LiftrixRoute()
    
    /**
     * Data portability screen for importing and exporting workout data
     */
    @Serializable
    data object DataPortability : LiftrixRoute()
    
    // AI Chatbot System Routes (Added for SPEC-20250119-ai-chatbot-frontend)
    
    /**
     * AI Chatbot screen for AI-powered workout guidance
     * 
     * @param conversationId Optional conversation ID to resume existing chat
     * @param workoutContext Optional workout context for AI responses (workout ID or session data)
     */
    @Serializable
    data class AIChatbot(
        val conversationId: String? = null,
        val workoutContext: String? = null
    ) : LiftrixRoute()
    
    /**
     * AI Chat Settings screen for configuring AI chatbot preferences
     * Provides comprehensive controls for language, behavior, usage limits, and data management
     */
    @Serializable
    data object AIChatSettings : LiftrixRoute()
    
    // Custom Exercise Management Routes
    
    /**
     * Custom exercise creation screen for creating personalized exercises
     * Allows users to define exercises with custom metadata, images, and instructions
     */
    @Serializable
    data object CustomExerciseCreation : LiftrixRoute()
    
    /**
     * Custom exercise editing screen for modifying existing custom exercises
     * 
     * @param exerciseId Unique identifier for the custom exercise to edit
     */
    @Serializable
    data class CustomExerciseEdit(val exerciseId: String) : LiftrixRoute()
    
    /**
     * Custom exercise list screen showing all user's custom exercises
     * Supports search, filtering, sorting, and management of custom exercises
     * 
     * @param selectionMode Whether the screen is in selection mode for exercise picking (default: false)
     * @param returnRoute Optional route to return to after exercise selection
     */
    @Serializable
    data class CustomExerciseList(
        val selectionMode: Boolean = false,
        val returnRoute: String? = null
    ) : LiftrixRoute()
    
    // Admin System Routes (Admin-only access)
    
    /**
     * Admin Ban Management screen for user moderation and banning
     * Only accessible to users with admin Firebase custom claims
     */
    @Serializable
    data object AdminBanManagement : LiftrixRoute()
    
    /**
     * Upgrade to Premium screen showing premium features and subscription plans
     * Accessible from settings screen to showcase premium benefits
     */
    @Serializable
    data object UpgradeToPremium : LiftrixRoute()
}
