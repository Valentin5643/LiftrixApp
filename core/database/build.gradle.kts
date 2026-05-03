plugins {
    id("liftrix.android.library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.liftrix.core.database"

    defaultConfig {
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("room.incremental", "true")
            arg("room.expandProjection", "true")
        }
    }
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain"))
    implementation(project(":core:domain-common"))
    implementation(project(":user-scoping-annotations"))

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation("androidx.room:room-paging:2.5.2")
    implementation("androidx.paging:paging-runtime:3.2.1")
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.timber)
    implementation(libs.hilt.android)
    implementation("net.zetetic:sqlcipher-android:4.6.0@aar")
    implementation("androidx.sqlite:sqlite:2.4.0")
    implementation("javax.inject:javax.inject:1")

    add("kspDebug", libs.room.compiler)
    add("kspRelease", libs.room.compiler)
}
