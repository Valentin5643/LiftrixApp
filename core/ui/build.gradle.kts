plugins {
    id("liftrix.android.compose.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.example.liftrix.core.ui"

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core:design-system"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))
    implementation(project(":core:presentation"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.firebase.perf)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation(libs.hilt.android)
    implementation(libs.timber)
    implementation("javax.inject:javax.inject:1")
}
