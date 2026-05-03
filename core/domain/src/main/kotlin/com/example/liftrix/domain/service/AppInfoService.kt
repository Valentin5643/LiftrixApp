package com.example.liftrix.domain.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.support.DeviceInfo

/**
 * Service interface for application information and device details
 * Provides build information, device specs, and legal document access
 */
interface AppInfoService {
    
    /**
     * Gets the current app version name
     * @return App version string (e.g., "1.2.3")
     */
    fun getAppVersion(): String
    
    /**
     * Gets the current app version code
     * @return App version code as integer
     */
    fun getAppVersionCode(): Int
    
    /**
     * Gets the build number for this app version
     * @return Build number as integer
     */
    fun getBuildNumber(): Int
    
    /**
     * Gets the build type (debug, release, etc.)
     * @return Build type string
     */
    fun getBuildType(): String
    
    /**
     * Gets the build flavor if configured
     * @return Build flavor string or null
     */
    fun getBuildFlavor(): String?
    
    /**
     * Gets comprehensive device information
     * @return DeviceInfo object with device specifications
     */
    fun getDeviceInfo(): DeviceInfo
    
    /**
     * Gets system information formatted for support tickets
     * @return Formatted system information string
     */
    fun getSystemInfoForSupport(): String
    
    /**
     * Retrieves the current privacy policy content
     * @return LiftrixResult containing privacy policy text or URL
     */
    suspend fun getPrivacyPolicy(): LiftrixResult<String>
    
    /**
     * Retrieves the current terms of service content
     * @return LiftrixResult containing terms of service text or URL
     */
    suspend fun getTermsOfService(): LiftrixResult<String>
    
    /**
     * Retrieves list of open source licenses used in the app
     * @return LiftrixResult containing list of license information
     */
    suspend fun getLicenses(): LiftrixResult<List<License>>
    
    /**
     * Gets app configuration information
     * @return LiftrixResult containing app configuration details
     */
    suspend fun getAppConfiguration(): LiftrixResult<AppConfiguration>
    
    /**
     * Checks if the app is running in debug mode
     * @return True if debug build
     */
    fun isDebugBuild(): Boolean
    
    /**
     * Gets the minimum supported Android API level
     * @return Minimum API level as integer
     */
    fun getMinSdkVersion(): Int
    
    /**
     * Gets the target Android API level
     * @return Target API level as integer
     */
    fun getTargetSdkVersion(): Int
    
    /**
     * Gets the compiled Android API level
     * @return Compiled API level as integer
     */
    fun getCompileSdkVersion(): Int
    
    /**
     * Gets the app package name
     * @return Package name string
     */
    fun getPackageName(): String
    
    /**
     * Gets the app signature information for security validation
     * @return LiftrixResult containing signature information
     */
    suspend fun getAppSignature(): LiftrixResult<String>
    
    /**
     * Checks if the app is installed from an official source (Google Play)
     * @return True if installed from official source
     */
    fun isOfficialInstallation(): Boolean
}

/**
 * Open source license information
 */
data class License(
    val name: String,
    val library: String,
    val licenseType: String,
    val licenseText: String,
    val url: String? = null,
    val version: String? = null
) {
    companion object {
        /**
         * Common license types
         */
        object Type {
            const val APACHE_2_0 = "Apache 2.0"
            const val MIT = "MIT"
            const val BSD = "BSD"
            const val GPL_V2 = "GPL v2"
            const val GPL_V3 = "GPL v3"
            const val LGPL = "LGPL"
            const val CREATIVE_COMMONS = "Creative Commons"
        }
    }
}

/**
 * Application configuration information
 */
data class AppConfiguration(
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Int,
    val buildType: String,
    val buildFlavor: String?,
    val minSdkVersion: Int,
    val targetSdkVersion: Int,
    val compileSdkVersion: Int,
    val isDebugBuild: Boolean,
    val isOfficialBuild: Boolean,
    val buildTimestamp: java.time.Instant,
    val gitCommitHash: String? = null,
    val buildMachine: String? = null
) {
    /**
     * Gets a formatted version string for display
     */
    fun getFormattedVersion(): String {
        return buildString {
            append(versionName)
            append(" (")
            append(versionCode)
            append(")")
            if (isDebugBuild) {
                append(" DEBUG")
            }
            buildFlavor?.let { flavor ->
                append(" - ")
                append(flavor.uppercase())
            }
        }
    }
    
    /**
     * Gets build information for support tickets
     */
    fun getBuildInfoForSupport(): String {
        return buildString {
            appendLine("App: $appName")
            appendLine("Version: ${getFormattedVersion()}")
            appendLine("Package: $packageName")
            appendLine("Target SDK: $targetSdkVersion")
            appendLine("Min SDK: $minSdkVersion")
            appendLine("Build Type: $buildType")
            buildFlavor?.let { appendLine("Flavor: $it") }
            gitCommitHash?.let { appendLine("Commit: ${it.take(8)}") }
            appendLine("Official: $isOfficialBuild")
        }.trim()
    }
}