plugins {
    id("liftrix.kotlin.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:domain-common"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
}
