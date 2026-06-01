plugins {
    id("liftrix.android.compose.library")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.liftrix.feature.auth"
}

dependencies {
    implementation(project(":core:analytics"))
    implementation(project(":core:design-system"))
    implementation(project(":core:domain"))
    implementation(project(":core:domain-common"))
    implementation(project(":core:model"))
    implementation(project(":core:presentation"))
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
    implementation(libs.navigation.compose)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("javax.inject:javax.inject:1")

    ksp(libs.hilt.compiler)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
