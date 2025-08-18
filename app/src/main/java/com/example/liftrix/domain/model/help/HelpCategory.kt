package com.example.liftrix.domain.model.help

/**
 * Domain model representing a help category
 * Contains metadata about a category of help articles
 */
data class HelpCategory(
    val name: String,
    val displayName: String,
    val description: String,
    val sortOrder: Int,
    val iconName: String? = null,
    val articleCount: Int = 0,
    val isEnabled: Boolean = true
) {
    companion object {
        /**
         * Category identifiers as constants
         */
        object Category {
            const val GETTING_STARTED = "getting_started"
            const val WORKOUTS = "workouts"
            const val EXERCISES = "exercises"
            const val PROGRESS_TRACKING = "progress_tracking"
            const val SOCIAL_FEATURES = "social_features"
            const val SETTINGS = "settings"
            const val TROUBLESHOOTING = "troubleshooting"
            const val ACCOUNT = "account"
            const val SYNC = "sync"
            const val PREMIUM_FEATURES = "premium_features"
        }

        /**
         * Default categories for the help system
         */
        fun getDefaultCategories(): List<HelpCategory> = listOf(
            HelpCategory(
                name = Category.GETTING_STARTED,
                displayName = "Getting Started",
                description = "Learn the basics of using Liftrix",
                sortOrder = 1,
                iconName = "rocket_launch"
            ),
            HelpCategory(
                name = Category.WORKOUTS,
                displayName = "Workouts",
                description = "Creating and managing your workouts",
                sortOrder = 2,
                iconName = "fitness_center"
            ),
            HelpCategory(
                name = Category.EXERCISES,
                displayName = "Exercises",
                description = "Exercise library and custom exercises",
                sortOrder = 3,
                iconName = "sports_gymnastics"
            ),
            HelpCategory(
                name = Category.PROGRESS_TRACKING,
                displayName = "Progress Tracking",
                description = "Monitor your fitness progress",
                sortOrder = 4,
                iconName = "trending_up"
            ),
            HelpCategory(
                name = Category.SOCIAL_FEATURES,
                displayName = "Social Features",
                description = "Connect with gym buddies and share workouts",
                sortOrder = 5,
                iconName = "people"
            ),
            HelpCategory(
                name = Category.SETTINGS,
                displayName = "Settings",
                description = "Customize your app experience",
                sortOrder = 6,
                iconName = "settings"
            ),
            HelpCategory(
                name = Category.TROUBLESHOOTING,
                displayName = "Troubleshooting",
                description = "Solve common issues",
                sortOrder = 7,
                iconName = "build"
            ),
            HelpCategory(
                name = Category.ACCOUNT,
                displayName = "Account",
                description = "Account management and privacy",
                sortOrder = 8,
                iconName = "account_circle"
            ),
            HelpCategory(
                name = Category.SYNC,
                displayName = "Sync & Backup",
                description = "Data synchronization and backup",
                sortOrder = 9,
                iconName = "sync"
            ),
            HelpCategory(
                name = Category.PREMIUM_FEATURES,
                displayName = "Premium Features",
                description = "Advanced features for premium users",
                sortOrder = 10,
                iconName = "star"
            )
        )
        
        /**
         * Gets a category by its name identifier
         */
        fun getCategoryByName(name: String): HelpCategory? {
            return getDefaultCategories().find { it.name == name }
        }
        
        /**
         * Gets all enabled categories
         */
        fun getEnabledCategories(): List<HelpCategory> {
            return getDefaultCategories().filter { it.isEnabled }
        }
        
        /**
         * Gets categories sorted by their sort order
         */
        fun getSortedCategories(): List<HelpCategory> {
            return getDefaultCategories().sortedBy { it.sortOrder }
        }
    }
}