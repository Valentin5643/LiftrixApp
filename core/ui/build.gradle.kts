plugins {
    id("liftrix.android.compose.library")
}

android {
    namespace = "com.example.liftrix.core.ui"
}

dependencies {
    implementation(project(":core:design-system"))
    implementation(project(":core:domain"))
    implementation(project(":core:model"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.coil.compose)
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation(libs.hilt.android)
    implementation(libs.timber)
    implementation("javax.inject:javax.inject:1")
}
