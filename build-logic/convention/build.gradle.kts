plugins {
    `kotlin-dsl`
}

group = "com.example.liftrix.buildlogic"

dependencies {
    implementation(libs.android.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation("org.jetbrains.kotlin.plugin.compose:org.jetbrains.kotlin.plugin.compose.gradle.plugin:${libs.versions.kotlin.get()}")
}
