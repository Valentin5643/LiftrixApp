plugins {
    id("liftrix.android.compose.library")
}

android {
    namespace = "com.example.liftrix.core.designsystem"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
}
