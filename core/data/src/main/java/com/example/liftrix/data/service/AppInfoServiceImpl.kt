package com.example.liftrix.data.service

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.liftrix.core.data.BuildConfig
import com.example.liftrix.data.local.dao.AppConfigDao
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.model.support.DeviceInfo
import com.example.liftrix.domain.service.AppConfiguration
import com.example.liftrix.domain.service.AppInfoService
import com.example.liftrix.domain.service.License
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AppInfoService providing application and device information
 * 
 * Features:
 * - Build configuration access
 * - Device information collection
 * - Legal document retrieval
 * - App signature validation
 * - Installation source verification
 */
@Singleton
class AppInfoServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appConfigDao: AppConfigDao,
    private val remoteConfig: FirebaseRemoteConfig
) : AppInfoService {
    
    companion object {
        private const val PRIVACY_POLICY_URL_KEY = "privacy_policy_url"
        private const val TERMS_OF_SERVICE_URL_KEY = "terms_of_service_url"
        private const val DEFAULT_PRIVACY_POLICY_URL = "https://liftrix.app/privacy"
        private const val DEFAULT_TERMS_URL = "https://liftrix.app/terms"
    }
    
    override fun getAppVersion(): String = BuildConfig.VERSION_NAME
    
    override fun getAppVersionCode(): Int = BuildConfig.VERSION_CODE
    
    override fun getBuildNumber(): Int = BuildConfig.VERSION_CODE
    
    override fun getBuildType(): String = BuildConfig.BUILD_TYPE
    
    override fun getBuildFlavor(): String? = try {
        // BuildConfig.FLAVOR is not available by default in modern Android builds
        // Return null as no flavor is configured in this project
        null
    } catch (e: Exception) {
        null
    }
    
    override fun getDeviceInfo(): DeviceInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val displayMetrics = context.resources.displayMetrics
        val screenResolution = "${displayMetrics.widthPixels}x${displayMetrics.heightPixels}"
        
        return DeviceInfo(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            appVersion = getAppVersion(),
            buildNumber = getBuildNumber().toString(),
            deviceId = null, // For privacy, we don't collect device ID
            screenResolution = screenResolution,
            totalMemoryMB = memoryInfo.totalMem / (1024 * 1024),
            availableMemoryMB = memoryInfo.availMem / (1024 * 1024),
            deviceLanguage = context.resources.configuration.locales[0].toString(),
            networkType = getNetworkType()
        )
    }
    
    override fun getSystemInfoForSupport(): String {
        val deviceInfo = getDeviceInfo()
        return buildString {
            appendLine("=== System Information ===")
            appendLine(deviceInfo.formatForSupport())
            appendLine()
            appendLine("=== Build Information ===")
            appendLine("Build Type: ${getBuildType()}")
            getBuildFlavor()?.let { appendLine("Build Flavor: $it") }
            appendLine("Package: ${getPackageName()}")
            appendLine("Debug Build: ${isDebugBuild()}")
            appendLine("Official Install: ${isOfficialInstallation()}")
            appendLine()
            appendLine("=== SDK Information ===")
            appendLine("Min SDK: ${getMinSdkVersion()}")
            appendLine("Target SDK: ${getTargetSdkVersion()}")
            appendLine("Compile SDK: ${getCompileSdkVersion()}")
        }.trim()
    }
    
    override suspend fun getPrivacyPolicy(): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to fetch privacy policy",
                analyticsContext = mapOf(
                    "operation" to "GET_PRIVACY_POLICY",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Try to get URL from remote config
                remoteConfig.fetchAndActivate().await()
                val privacyPolicyUrl = remoteConfig.getString(PRIVACY_POLICY_URL_KEY)
                    .takeIf { it.isNotBlank() } ?: DEFAULT_PRIVACY_POLICY_URL
                
                // Cache the URL for offline access
                appConfigDao.setStringConfig(PRIVACY_POLICY_URL_KEY, privacyPolicyUrl)
                
                privacyPolicyUrl
            } catch (e: Exception) {
                // Fallback to cached value
                val cachedUrl = appConfigDao.getConfigValue(PRIVACY_POLICY_URL_KEY)
                cachedUrl ?: DEFAULT_PRIVACY_POLICY_URL
            }
        }
    }
    
    override suspend fun getTermsOfService(): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.NetworkError(
                errorMessage = "Failed to fetch terms of service",
                analyticsContext = mapOf(
                    "operation" to "GET_TERMS_OF_SERVICE",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            try {
                // Try to get URL from remote config
                remoteConfig.fetchAndActivate().await()
                val termsUrl = remoteConfig.getString(TERMS_OF_SERVICE_URL_KEY)
                    .takeIf { it.isNotBlank() } ?: DEFAULT_TERMS_URL
                
                // Cache the URL for offline access
                appConfigDao.setStringConfig(TERMS_OF_SERVICE_URL_KEY, termsUrl)
                
                termsUrl
            } catch (e: Exception) {
                // Fallback to cached value
                val cachedUrl = appConfigDao.getConfigValue(TERMS_OF_SERVICE_URL_KEY)
                cachedUrl ?: DEFAULT_TERMS_URL
            }
        }
    }
    
    override suspend fun getLicenses(): LiftrixResult<List<License>> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "LICENSES_FETCH_FAILED",
                errorMessage = "Failed to fetch license information",
                analyticsContext = mapOf(
                    "operation" to "GET_LICENSES",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            // Return a list of licenses used in the app
            // In a real implementation, this might be generated by a Gradle plugin
            listOf(
                License(
                    name = "Jetpack Compose",
                    library = "androidx.compose",
                    licenseType = "Apache 2.0",
                    licenseText = getApacheLicenseText(),
                    url = "https://developer.android.com/jetpack/compose",
                    version = "1.5.0"
                ),
                License(
                    name = "Room Database",
                    library = "androidx.room",
                    licenseType = "Apache 2.0",
                    licenseText = getApacheLicenseText(),
                    url = "https://developer.android.com/jetpack/androidx/releases/room",
                    version = "2.5.0"
                ),
                License(
                    name = "Dagger Hilt",
                    library = "com.google.dagger",
                    licenseType = "Apache 2.0",
                    licenseText = getApacheLicenseText(),
                    url = "https://dagger.dev/hilt/",
                    version = "2.48"
                ),
                License(
                    name = "Firebase",
                    library = "com.google.firebase",
                    licenseType = "Apache 2.0",
                    licenseText = getApacheLicenseText(),
                    url = "https://firebase.google.com/",
                    version = "32.3.1"
                ),
                License(
                    name = "Timber",
                    library = "com.jakewharton.timber",
                    licenseType = "Apache 2.0",
                    licenseText = getApacheLicenseText(),
                    url = "https://github.com/JakeWharton/timber",
                    version = "5.0.1"
                ),
                License(
                    name = "Kotlin Coroutines",
                    library = "org.jetbrains.kotlinx.coroutines",
                    licenseType = "Apache 2.0",
                    licenseText = getApacheLicenseText(),
                    url = "https://github.com/Kotlin/kotlinx.coroutines",
                    version = "1.7.3"
                )
            )
        }
    }
    
    override suspend fun getAppConfiguration(): LiftrixResult<AppConfiguration> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "APP_CONFIG_FETCH_FAILED",
                errorMessage = "Failed to fetch app configuration",
                analyticsContext = mapOf(
                    "operation" to "GET_APP_CONFIGURATION",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            AppConfiguration(
                appName = getAppName(),
                packageName = getPackageName(),
                versionName = getAppVersion(),
                versionCode = getAppVersionCode(),
                buildType = getBuildType(),
                buildFlavor = getBuildFlavor(),
                minSdkVersion = getMinSdkVersion(),
                targetSdkVersion = getTargetSdkVersion(),
                compileSdkVersion = getCompileSdkVersion(),
                isDebugBuild = isDebugBuild(),
                isOfficialBuild = isOfficialInstallation(),
                buildTimestamp = getBuildTimestamp(),
                gitCommitHash = getGitCommitHash(),
                buildMachine = getBuildMachine()
            )
        }
    }
    
    override fun isDebugBuild(): Boolean = BuildConfig.DEBUG
    
    override fun getMinSdkVersion(): Int {
        return try {
            val applicationInfo = context.applicationInfo
            applicationInfo.minSdkVersion
        } catch (e: Exception) {
            21 // Default minimum for our app
        }
    }
    
    override fun getTargetSdkVersion(): Int {
        return try {
            val applicationInfo = context.applicationInfo
            applicationInfo.targetSdkVersion
        } catch (e: Exception) {
            Build.VERSION.SDK_INT
        }
    }
    
    override fun getCompileSdkVersion(): Int {
        return try {
            // Use the target SDK version from ApplicationInfo
            // BuildConfig.TARGET_SDK_VERSION is not available by default
            context.applicationInfo.targetSdkVersion
        } catch (e: Exception) {
            35 // Fallback to the compile SDK version from build.gradle
        }
    }
    
    override fun getPackageName(): String = context.packageName
    
    override suspend fun getAppSignature(): LiftrixResult<String> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "APP_SIGNATURE_FETCH_FAILED",
                errorMessage = "Failed to fetch app signature",
                analyticsContext = mapOf(
                    "operation" to "GET_APP_SIGNATURE",
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            try {
                val packageManager = context.packageManager
                val packageInfo = packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.GET_SIGNATURES
                )
                
                val signature = packageInfo.signatures?.firstOrNull()
                if (signature != null) {
                    val md = MessageDigest.getInstance("SHA-256")
                    val digest = md.digest(signature.toByteArray())
                    digest.joinToString("") { "%02x".format(it) }
                } else {
                    "signature_not_available"
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to get app signature")
                "signature_error"
            }
        }
    }
    
    override fun isOfficialInstallation(): Boolean {
        return try {
            val packageManager = context.packageManager
            val installer = packageManager.getInstallerPackageName(context.packageName)
            
            // Check if installed from Google Play Store or other official sources
            installer in listOf(
                "com.android.vending", // Google Play Store
                "com.google.android.packageinstaller", // Package Installer
                "com.amazon.venezia" // Amazon Appstore
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to check installation source")
            false
        }
    }
    
    /**
     * Gets the app name from the application label
     */
    private fun getAppName(): String {
        return try {
            val applicationInfo = context.applicationInfo
            val packageManager = context.packageManager
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: Exception) {
            "Liftrix"
        }
    }
    
    /**
     * Gets the current network type
     */
    private fun getNetworkType(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as android.net.ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            when {
                networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
                networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "Unknown"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }
    
    /**
     * Gets the build timestamp
     */
    private fun getBuildTimestamp(): Instant {
        return try {
            // In a real implementation, this would be injected at build time
            // BuildConfig.BUILD_TIME_MILLIS not available - use current time as fallback
            Instant.now()
        } catch (e: Exception) {
            Instant.now()
        }
    }
    
    /**
     * Gets the Git commit hash (if available)
     */
    private fun getGitCommitHash(): String? {
        return try {
            // BuildConfig.GIT_COMMIT_HASH not available
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets the build machine info (if available)
     */
    private fun getBuildMachine(): String? {
        return try {
            // BuildConfig.BUILD_MACHINE not available
            null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Returns the Apache 2.0 license text
     */
    private fun getApacheLicenseText(): String {
        return """
            Licensed under the Apache License, Version 2.0 (the "License");
            you may not use this file except in compliance with the License.
            You may obtain a copy of the License at
            
                http://www.apache.org/licenses/LICENSE-2.0
            
            Unless required by applicable law or agreed to in writing, software
            distributed under the License is distributed on an "AS IS" BASIS,
            WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            See the License for the specific language governing permissions and
            limitations under the License.
        """.trimIndent()
    }
}

