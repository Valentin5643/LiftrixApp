plugins {
    id("liftrix.android.compose.library")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.liftrix.feature.settings"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "VERSION_NAME", "\"1.0\"")
        buildConfigField("int", "VERSION_CODE", "1")
        buildConfigField("String", "APPLICATION_ID", "\"com.example.liftrix\"")
    }
}

dependencies {
    implementation(project(":core:analytics"))
    implementation(project(":core:data"))
    implementation(project(":core:design-system"))
    implementation(project(":core:domain"))
    implementation(project(":core:domain-common"))
    implementation(project(":core:model"))
    implementation(project(":core:notifications"))
    implementation(project(":core:presentation"))
    implementation(project(":core:sync"))
    implementation(project(":core:ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.timber)
    implementation("eu.bambooapps:compose-material3-pullrefresh:1.1.1")
    implementation("javax.inject:javax.inject:1")

    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.ui.tooling)
}
