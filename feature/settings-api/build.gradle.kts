plugins {
    id("liftrix.android.library")
}

android {
    namespace = "com.example.liftrix.feature.settings.api"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
