plugins {
    id("liftrix.android.compose.library")
}

android {
    namespace = "com.example.liftrix.core.ui"
}

dependencies {
    implementation(project(":core:design-system"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.androidx.ui.tooling.preview)
}
