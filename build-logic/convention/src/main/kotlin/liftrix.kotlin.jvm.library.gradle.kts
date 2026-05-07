import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xjvm-default=all",
            "-Xcontext-receivers",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlin.experimental.ExperimentalTypeInference",
            "-XXLanguage:+BreakContinueInInlineLambdas"
        )
    }
}
