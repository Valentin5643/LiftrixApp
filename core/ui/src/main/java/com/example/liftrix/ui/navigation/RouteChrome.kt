package com.example.liftrix.ui.navigation

import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import kotlin.reflect.KClass

data class RouteChrome(
    val title: String = "",
    val showBack: Boolean = false,
    val showBottomBar: Boolean = true,
    val showTopBar: Boolean = true,
    val showFab: Boolean = true
)

data class RouteMetadata(
    val routeClass: KClass<out LiftrixRoute>,
    val feature: RouteFeature,
    val chrome: RouteChrome,
    val hideSettingsAction: Boolean = false,
    val showSearchAction: Boolean = false
)

enum class RouteFeature {
    AUTH_ONBOARDING,
    HOME,
    WORKOUT,
    ACTIVE_WORKOUT,
    PROGRESS_ANALYTICS,
    SOCIAL,
    PROFILE_SETTINGS,
    AI_RECOMMENDATIONS
}

object LiftrixRouteMetadata {
    private fun mainTab(
        routeClass: KClass<out LiftrixRoute>,
        feature: RouteFeature,
        title: String,
        showFab: Boolean = true
    ) = RouteMetadata(
        routeClass = routeClass,
        feature = feature,
        chrome = RouteChrome(title = title, showFab = showFab)
    )

    private fun detail(
        routeClass: KClass<out LiftrixRoute>,
        feature: RouteFeature,
        title: String,
        showFab: Boolean = true,
        hideSettingsAction: Boolean = false,
        showSearchAction: Boolean = false
    ) = RouteMetadata(
        routeClass = routeClass,
        feature = feature,
        chrome = RouteChrome(title = title, showBack = true, showFab = showFab),
        hideSettingsAction = hideSettingsAction,
        showSearchAction = showSearchAction
    )

    private fun untitled(
        routeClass: KClass<out LiftrixRoute>,
        feature: RouteFeature,
        showFab: Boolean = true,
        hideSettingsAction: Boolean = false
    ) = RouteMetadata(
        routeClass = routeClass,
        feature = feature,
        chrome = RouteChrome(showFab = showFab),
        hideSettingsAction = hideSettingsAction
    )

    val bottomNavRoutes: Set<KClass<out LiftrixRoute>> = setOf(
        LiftrixRoute.Home::class,
        LiftrixRoute.Workout::class,
        LiftrixRoute.Progress::class,
        LiftrixRoute.Coach::class
    )

    private val routes = listOf(
        mainTab(LiftrixRoute.Home::class, RouteFeature.HOME, "Liftrix"),
        mainTab(LiftrixRoute.Workout::class, RouteFeature.WORKOUT, "Workout"),
        mainTab(LiftrixRoute.Progress::class, RouteFeature.PROGRESS_ANALYTICS, "Progress"),
        mainTab(LiftrixRoute.Coach::class, RouteFeature.AI_RECOMMENDATIONS, "Coach", showFab = false),

        untitled(LiftrixRoute.Onboarding::class, RouteFeature.AUTH_ONBOARDING),
        untitled(LiftrixRoute.AuthSignUp::class, RouteFeature.AUTH_ONBOARDING, showFab = false),
        untitled(LiftrixRoute.AuthSignIn::class, RouteFeature.AUTH_ONBOARDING, showFab = false),

        detail(LiftrixRoute.ActiveWorkout::class, RouteFeature.ACTIVE_WORKOUT, "Active Workout", showFab = false),
        detail(LiftrixRoute.TemplateCreation::class, RouteFeature.WORKOUT, "Create Template"),
        detail(LiftrixRoute.CreateWorkout::class, RouteFeature.WORKOUT, ""),
        detail(LiftrixRoute.EditWorkout::class, RouteFeature.WORKOUT, "Edit Workout"),
        detail(LiftrixRoute.EditSession::class, RouteFeature.WORKOUT, ""),
        detail(LiftrixRoute.ExerciseSelection::class, RouteFeature.WORKOUT, "Add Exercise"),
        detail(LiftrixRoute.CustomExerciseCreation::class, RouteFeature.WORKOUT, "Create Exercise"),
        detail(LiftrixRoute.CustomExerciseEdit::class, RouteFeature.WORKOUT, "Edit Exercise"),
        detail(LiftrixRoute.CustomExerciseList::class, RouteFeature.WORKOUT, "My Exercises"),
        detail(LiftrixRoute.WorkoutDetails::class, RouteFeature.WORKOUT, "Workout Details", showFab = false),
        detail(LiftrixRoute.ExerciseDetails::class, RouteFeature.WORKOUT, "Exercise Details"),
        untitled(LiftrixRoute.UserWorkouts::class, RouteFeature.WORKOUT),
        untitled(LiftrixRoute.PostWorkoutSummary::class, RouteFeature.ACTIVE_WORKOUT, showFab = false),

        detail(LiftrixRoute.VolumeAnalysisDetail::class, RouteFeature.PROGRESS_ANALYTICS, "Volume Analysis"),
        detail(LiftrixRoute.OneRmDetail::class, RouteFeature.PROGRESS_ANALYTICS, "1RM Progression"),
        detail(LiftrixRoute.MuscleGroupDetail::class, RouteFeature.PROGRESS_ANALYTICS, "Muscle Groups"),
        detail(LiftrixRoute.MuscleHeatmapDetail::class, RouteFeature.PROGRESS_ANALYTICS, "Muscle Heatmap"),
        detail(LiftrixRoute.WorkoutFrequencyDetail::class, RouteFeature.PROGRESS_ANALYTICS, "Workout Frequency"),
        detail(LiftrixRoute.ExerciseRankingDetail::class, RouteFeature.PROGRESS_ANALYTICS, ""),
        detail(LiftrixRoute.StrengthForecastDetail::class, RouteFeature.PROGRESS_ANALYTICS, "Strength Forecast"),
        detail(LiftrixRoute.ProgressComparison::class, RouteFeature.PROGRESS_ANALYTICS, "Progress Comparison"),
        detail(LiftrixRoute.AnomalyDashboard::class, RouteFeature.PROGRESS_ANALYTICS, "Anomaly Detection"),
        detail(LiftrixRoute.AnomalySettings::class, RouteFeature.PROGRESS_ANALYTICS, "Detection Settings", showFab = false, hideSettingsAction = true),

        detail(LiftrixRoute.Friends::class, RouteFeature.SOCIAL, "Friends", showSearchAction = true),
        untitled(LiftrixRoute.UserSearch::class, RouteFeature.SOCIAL),
        detail(LiftrixRoute.SocialOnboarding::class, RouteFeature.SOCIAL, ""),
        detail(LiftrixRoute.ShareWorkout::class, RouteFeature.SOCIAL, "Share Workout"),
        detail(LiftrixRoute.GymBuddy::class, RouteFeature.SOCIAL, "Gym Buddy"),
        untitled(LiftrixRoute.QRScanner::class, RouteFeature.SOCIAL),
        detail(LiftrixRoute.TemplateBuddyShare::class, RouteFeature.SOCIAL, ""),
        untitled(LiftrixRoute.WorkoutSharedWithYou::class, RouteFeature.SOCIAL),
        untitled(LiftrixRoute.WorkoutShareInbox::class, RouteFeature.SOCIAL),
        detail(LiftrixRoute.PostCreation::class, RouteFeature.SOCIAL, "Create Post", showFab = false),
        detail(LiftrixRoute.PostComments::class, RouteFeature.SOCIAL, "Comments"),

        detail(LiftrixRoute.PublicProfile::class, RouteFeature.PROFILE_SETTINGS, "Profile"),
        detail(LiftrixRoute.FollowersList::class, RouteFeature.PROFILE_SETTINGS, "Followers"),
        detail(LiftrixRoute.FollowingList::class, RouteFeature.PROFILE_SETTINGS, "Following"),
        detail(LiftrixRoute.Profile::class, RouteFeature.PROFILE_SETTINGS, "Profile"),
        detail(LiftrixRoute.ProfileEdit::class, RouteFeature.PROFILE_SETTINGS, "Edit Profile"),
        detail(LiftrixRoute.ImageCrop::class, RouteFeature.PROFILE_SETTINGS, "Crop Image"),

        detail(LiftrixRoute.Settings::class, RouteFeature.PROFILE_SETTINGS, "Settings", showFab = false, hideSettingsAction = true),
        detail(LiftrixRoute.SyncSettings::class, RouteFeature.PROFILE_SETTINGS, "Sync Settings", showFab = false, hideSettingsAction = true),
        detail(LiftrixRoute.WidgetSettings::class, RouteFeature.PROFILE_SETTINGS, "Widget Settings", showFab = false, hideSettingsAction = true),
        detail(LiftrixRoute.DashboardCustomization::class, RouteFeature.PROFILE_SETTINGS, "Dashboard", showFab = false),
        detail(LiftrixRoute.NotificationSettings::class, RouteFeature.PROFILE_SETTINGS, "Notification Settings", showFab = false, hideSettingsAction = true),
        detail(LiftrixRoute.PrivacySettings::class, RouteFeature.PROFILE_SETTINGS, "Privacy", showFab = false, hideSettingsAction = true),
        detail(LiftrixRoute.EmailChange::class, RouteFeature.PROFILE_SETTINGS, "Change Email", showFab = false),
        detail(LiftrixRoute.PasswordChange::class, RouteFeature.PROFILE_SETTINGS, "Change Password", showFab = false),
        detail(LiftrixRoute.UsernameChange::class, RouteFeature.PROFILE_SETTINGS, "Change Username", showFab = false),
        detail(LiftrixRoute.AccountDeletion::class, RouteFeature.PROFILE_SETTINGS, "Delete Account", showFab = false),
        detail(LiftrixRoute.HelpCenter::class, RouteFeature.PROFILE_SETTINGS, "Help", showFab = false),
        detail(LiftrixRoute.HelpArticle::class, RouteFeature.PROFILE_SETTINGS, "Help"),
        detail(LiftrixRoute.ContactSupport::class, RouteFeature.PROFILE_SETTINGS, "Support", showFab = false),
        detail(LiftrixRoute.SupportTicket::class, RouteFeature.PROFILE_SETTINGS, "Support"),
        detail(LiftrixRoute.About::class, RouteFeature.PROFILE_SETTINGS, "About", showFab = false),
        detail(LiftrixRoute.PrivacyPolicy::class, RouteFeature.PROFILE_SETTINGS, "Privacy Policy", showFab = false),
        detail(LiftrixRoute.TermsOfService::class, RouteFeature.PROFILE_SETTINGS, "Terms", showFab = false),
        detail(LiftrixRoute.AIDisclaimer::class, RouteFeature.PROFILE_SETTINGS, "AI Disclaimer"),
        detail(LiftrixRoute.CommunityGuidelines::class, RouteFeature.PROFILE_SETTINGS, "Guidelines"),
        detail(LiftrixRoute.ContentModerationPolicy::class, RouteFeature.PROFILE_SETTINGS, "Moderation Policy"),
        detail(LiftrixRoute.RefundSubscriptionPolicy::class, RouteFeature.PROFILE_SETTINGS, "Refund Policy"),
        detail(LiftrixRoute.DataPortability::class, RouteFeature.PROFILE_SETTINGS, "Data Portability", showFab = false),
        detail(LiftrixRoute.ExportProgressReport::class, RouteFeature.PROFILE_SETTINGS, "Progress Report", showFab = false),
        detail(LiftrixRoute.AdminBanManagement::class, RouteFeature.PROFILE_SETTINGS, "Ban Management"),
        detail(LiftrixRoute.UpgradeToPremium::class, RouteFeature.PROFILE_SETTINGS, "Premium"),

        untitled(LiftrixRoute.AIChatbot::class, RouteFeature.AI_RECOMMENDATIONS, showFab = false),
        detail(LiftrixRoute.AIChatSettings::class, RouteFeature.AI_RECOMMENDATIONS, "AI Settings", showFab = false, hideSettingsAction = true)
    )

    private val byClass = routes.associateBy { it.routeClass }

    fun metadataFor(routeClass: KClass<out LiftrixRoute>): RouteMetadata? = byClass[routeClass]

    fun metadataFor(destination: NavDestination?): RouteMetadata? {
        if (destination == null) return null
        return routes.firstOrNull { metadata ->
            destination.hierarchy.any { it.matches(metadata.routeClass) }
        }
    }
}

fun NavDestination?.routeChrome(): RouteChrome =
    LiftrixRouteMetadata.metadataFor(this)?.chrome ?: RouteChrome()

fun NavDestination?.routeMetadata(): RouteMetadata? =
    LiftrixRouteMetadata.metadataFor(this)

fun NavDestination?.isRoute(routeClass: KClass<out LiftrixRoute>): Boolean {
    if (this == null) return false
    return hierarchy.any { it.matches(routeClass) }
}

private fun NavDestination.matches(routeClass: KClass<out LiftrixRoute>): Boolean {
    val routeName = route ?: return false
    val qualifiedName = routeClass.qualifiedName
    val simpleName = routeClass.simpleName

    return routeName == qualifiedName ||
        routeName.startsWith("$qualifiedName/") ||
        routeName.startsWith("$qualifiedName?") ||
        routeName == simpleName ||
        routeName.startsWith("$simpleName/") ||
        routeName.startsWith("$simpleName?")
}
