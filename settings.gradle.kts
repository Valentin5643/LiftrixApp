pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Build cache configuration for faster builds
buildCache {
    local {
        directory = File(rootDir, "build-cache")
        removeUnusedEntriesAfterDays = 30
    }
}

rootProject.name = "Liftrix"
include(":app")
include(":core:analytics")
include(":core:domain")
include(":core:domain-common")
include(":core:design-system")
include(":core:model")
include(":core:network")
include(":core:presentation")
include(":core:ui")
include(":feature:auth")
include(":feature:home")
include(":feature:profile")
include(":feature:progress")
include(":lint-rules")
include(":user-scoping-annotations")
include(":user-scoping-processor")
