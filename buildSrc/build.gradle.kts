plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // No plugin dependencies needed in buildSrc for this project
    // Plugins are applied via version catalog in the main build files
} 