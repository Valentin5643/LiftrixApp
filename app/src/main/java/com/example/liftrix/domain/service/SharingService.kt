package com.example.liftrix.domain.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.usecase.export.ExportResult
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for sharing exported workout data files through Android's sharing system.
 * 
 * This service provides secure file sharing capabilities using Android's FileProvider
 * for safe sharing of exported workout data with external applications while maintaining
 * proper security boundaries and user privacy.
 * 
 * Key Features:
 * - Secure file sharing via FileProvider to prevent file system exposure
 * - Multiple sharing options (email, cloud storage, messaging apps)
 * - Automatic MIME type detection for proper app selection
 * - Comprehensive error handling for sharing failures
 * - Analytics tracking for sharing usage patterns
 * - Temporary file cleanup to manage storage space
 * 
 * Security Considerations:
 * - Uses FileProvider to prevent direct file system access
 * - Grants temporary read permissions only to receiving apps
 * - Validates file existence and permissions before sharing
 * - Logs sharing activities for security auditing
 * 
 * @property context Application context for system service access
 */
@Singleton
class SharingService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val FILE_PROVIDER_AUTHORITY = "com.example.liftrix.fileprovider"
        private const val EXPORT_DIRECTORY = "exports"
    }
    
    /**
     * Creates and launches a sharing intent for an exported workout data file.
     * 
     * This method handles the complete sharing flow including file validation,
     * URI generation, intent creation, and error handling.
     * 
     * @param exportResult The export result containing file information
     * @param shareTitle Custom title for the sharing dialog
     * @param shareText Additional text to include in the share
     * @return LiftrixResult containing the sharing intent or error information
     */
    suspend fun shareExportedFile(
        exportResult: ExportResult,
        shareTitle: String = "Share Workout Data",
        shareText: String = "Here's my workout data exported from Liftrix"
    ): LiftrixResult<Intent> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SHARE_EXPORT",
                errorMessage = "Failed to share exported file",
                analyticsContext = mapOf(
                    "export_id" to exportResult.exportId,
                    "format" to exportResult.format.name,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        Timber.d("Sharing exported file: ${exportResult.exportId}")
        
        // Validate file exists and is readable
        val file = exportResult.file
        if (!file.exists()) {
            throw IllegalStateException("Export file no longer exists: ${file.absolutePath}")
        }
        
        if (!file.canRead()) {
            throw IllegalStateException("Cannot read export file: ${file.absolutePath}")
        }
        
        // Generate secure URI using FileProvider
        val fileUri = generateSecureFileUri(file)
        
        // Determine MIME type based on export format
        val mimeType = determineMimeType(exportResult.format)
        
        // Create sharing intent
        val shareIntent = createSharingIntent(
            fileUri = fileUri,
            mimeType = mimeType,
            shareTitle = shareTitle,
            shareText = shareText,
            exportResult = exportResult
        )
        
        // Create chooser intent for better UX
        val chooserIntent = Intent.createChooser(shareIntent, shareTitle)
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        Timber.d("Sharing intent created successfully for export: ${exportResult.exportId}")
        
        chooserIntent
    }
    
    /**
     * Shares an exported file directly with a specific app package.
     * 
     * This method allows for targeted sharing to specific applications
     * such as email clients or cloud storage apps.
     * 
     * @param exportResult The export result containing file information
     * @param targetPackage Package name of the target application
     * @param targetActivity Optional specific activity to target
     * @return LiftrixResult containing the targeted sharing intent
     */
    suspend fun shareWithApp(
        exportResult: ExportResult,
        targetPackage: String,
        targetActivity: String? = null
    ): LiftrixResult<Intent> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SHARE_WITH_APP",
                errorMessage = "Failed to share with specific app",
                analyticsContext = mapOf(
                    "export_id" to exportResult.exportId,
                    "target_package" to targetPackage,
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        Timber.d("Sharing with specific app: $targetPackage")
        
        val file = exportResult.file
        if (!file.exists() || !file.canRead()) {
            throw IllegalStateException("Export file is not accessible")
        }
        
        val fileUri = generateSecureFileUri(file)
        val mimeType = determineMimeType(exportResult.format)
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, "Workout Data Export")
            putExtra(Intent.EXTRA_TEXT, generateShareText(exportResult))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            // Set target package and activity
            setPackage(targetPackage)
            targetActivity?.let { activity ->
                component = android.content.ComponentName(targetPackage, activity)
            }
        }
        
        shareIntent
    }
    
    /**
     * Creates an email-specific sharing intent with pre-filled subject and body.
     * 
     * This method optimizes the sharing experience for email applications
     * by providing appropriate subject lines and body text.
     * 
     * @param exportResult The export result containing file information
     * @param recipientEmail Optional recipient email address
     * @param customSubject Custom email subject
     * @param customBody Custom email body
     * @return LiftrixResult containing the email sharing intent
     */
    suspend fun shareViaEmail(
        exportResult: ExportResult,
        recipientEmail: String? = null,
        customSubject: String? = null,
        customBody: String? = null
    ): LiftrixResult<Intent> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "SHARE_VIA_EMAIL",
                errorMessage = "Failed to create email sharing intent",
                analyticsContext = mapOf(
                    "export_id" to exportResult.exportId,
                    "has_recipient" to (recipientEmail != null).toString(),
                    "error" to throwable.message.orEmpty()
                )
            )
        }
    ) {
        Timber.d("Creating email sharing intent for export: ${exportResult.exportId}")
        
        val file = exportResult.file
        if (!file.exists() || !file.canRead()) {
            throw IllegalStateException("Export file is not accessible")
        }
        
        val fileUri = generateSecureFileUri(file)
        val subject = customSubject ?: "Workout Data Export - ${exportResult.format.name}"
        val body = customBody ?: generateEmailBody(exportResult)
        
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822" // Specifically target email apps
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            recipientEmail?.let { email ->
                putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
            }
        }
        
        // Create chooser to show only email apps
        val chooserIntent = Intent.createChooser(emailIntent, "Send workout data via email")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        
        chooserIntent
    }
    
    /**
     * Gets available sharing apps for the exported file format.
     * 
     * This method queries the system for applications that can handle
     * the specific file format, allowing for intelligent sharing suggestions.
     * 
     * @param exportResult The export result to check compatibility for
     * @return List of package names that can handle the file format
     */
    fun getAvailableSharingApps(exportResult: ExportResult): List<String> {
        return try {
            val mimeType = determineMimeType(exportResult.format)
            val testIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
            }
            
            val packageManager = context.packageManager
            val resolveInfos = packageManager.queryIntentActivities(testIntent, 0)
            
            resolveInfos.map { it.activityInfo.packageName }.distinct()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get available sharing apps")
            emptyList()
        }
    }
    
    /**
     * Cleans up temporary export files to manage storage space.
     * 
     * This method should be called periodically to remove old export files
     * and prevent storage accumulation.
     * 
     * @param maxAgeMillis Maximum age of files to keep (default 24 hours)
     * @return Number of files cleaned up
     */
    suspend fun cleanupOldExports(maxAgeMillis: Long = 24 * 60 * 60 * 1000): Int {
        return try {
            val exportsDir = File(context.filesDir, EXPORT_DIRECTORY)
            if (!exportsDir.exists()) return 0
            
            val currentTime = System.currentTimeMillis()
            var cleanedCount = 0
            
            exportsDir.listFiles()?.forEach { file ->
                if (file.isFile && (currentTime - file.lastModified()) > maxAgeMillis) {
                    if (file.delete()) {
                        cleanedCount++
                        Timber.d("Cleaned up old export file: ${file.name}")
                    }
                }
            }
            
            Timber.i("Cleaned up $cleanedCount old export files")
            cleanedCount
        } catch (e: Exception) {
            Timber.e(e, "Failed to cleanup old exports")
            0
        }
    }
    
    /**
     * Generates a secure URI for the export file using FileProvider.
     */
    private fun generateSecureFileUri(file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                FILE_PROVIDER_AUTHORITY,
                file
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate FileProvider URI for: ${file.absolutePath}")
            throw IllegalStateException("Cannot create secure URI for export file", e)
        }
    }
    
    /**
     * Determines the appropriate MIME type for the export format.
     */
    private fun determineMimeType(format: com.example.liftrix.domain.usecase.export.ExportFormat): String {
        return when (format) {
            com.example.liftrix.domain.usecase.export.ExportFormat.JSON -> "application/json"
            com.example.liftrix.domain.usecase.export.ExportFormat.CSV -> "text/csv"
            com.example.liftrix.domain.usecase.export.ExportFormat.TCX -> "application/vnd.garmin.tcx+xml"
            com.example.liftrix.domain.usecase.export.ExportFormat.FIT -> "application/vnd.ant.fit"
        }
    }
    
    /**
     * Creates the main sharing intent with proper configuration.
     */
    private fun createSharingIntent(
        fileUri: Uri,
        mimeType: String,
        shareTitle: String,
        shareText: String,
        exportResult: ExportResult
    ): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, fileUri)
            putExtra(Intent.EXTRA_SUBJECT, shareTitle)
            putExtra(Intent.EXTRA_TEXT, generateShareText(exportResult, shareText))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    /**
     * Generates appropriate share text based on export details.
     */
    private fun generateShareText(exportResult: ExportResult, customText: String? = null): String {
        if (customText != null) return customText
        
        val formatName = exportResult.format.name
        val recordCount = exportResult.recordCount
        val fileName = exportResult.file.name
        
        return """
            I'm sharing my workout data exported from Liftrix!
            
            Format: $formatName
            Records: $recordCount workouts
            File: $fileName
            
            Get Liftrix to track your own workouts: https://liftrix.app
        """.trimIndent()
    }
    
    /**
     * Generates email-specific body text with detailed information.
     */
    private fun generateEmailBody(exportResult: ExportResult): String {
        val formatName = exportResult.format.name
        val recordCount = exportResult.recordCount
        val fileName = exportResult.file.name
        val fileSize = formatFileSize(exportResult.file.length())
        
        return """
            Hi,
            
            I'm sharing my workout data exported from Liftrix. Please find the attached file with my fitness progress.
            
            Export Details:
            • Format: $formatName
            • Total Workouts: $recordCount
            • File Name: $fileName
            • File Size: $fileSize
            
            This data includes my workout history and can be imported into other fitness applications that support the $formatName format.
            
            Best regards,
            Exported from Liftrix - https://liftrix.app
        """.trimIndent()
    }
    
    /**
     * Formats file size for display in sharing text.
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}

/**
 * Sharing options configuration for customizing the sharing experience.
 */
data class SharingOptions(
    val shareTitle: String = "Share Workout Data",
    val shareText: String? = null,
    val includeAppPromotion: Boolean = true,
    val emailSubject: String? = null,
    val emailBody: String? = null,
    val preferredApps: List<String> = emptyList()
)

/**
 * Result of a sharing operation with success status and metadata.
 */
data class SharingResult(
    val isSuccess: Boolean,
    val sharedWith: String? = null,
    val errorMessage: String? = null,
    val analyticsData: Map<String, Any> = emptyMap()
)

/**
 * ★ Insight ─────────────────────────────────────
 * - SharingService implements secure file sharing using Android FileProvider to prevent direct file system exposure
 * - Provides multiple sharing strategies including general sharing, targeted app sharing, and email-optimized sharing
 * - Includes comprehensive cleanup mechanisms and analytics tracking for optimal storage management and usage insights
 * ─────────────────────────────────────────────────
 */