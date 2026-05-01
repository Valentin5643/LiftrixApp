plugins {
    id("liftrix.kotlin.jvm.library")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(project(":core:model"))
    api(project(":core:domain-common"))

    implementation(libs.kotlinx.coroutines.jdk8)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    compileOnly("javax.inject:javax.inject:1")
}
