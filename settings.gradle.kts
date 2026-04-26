pluginManagement { 
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
rootProject.name = "Liftrix"
include(":app")
include(":lint-rules")
include(":user-scoping-annotations")
include(":user-scoping-processor")
