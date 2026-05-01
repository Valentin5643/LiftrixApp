plugins {
    id("liftrix.android.compose.library")
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.timber)
    implementation("javax.inject:javax.inject:1")
}
