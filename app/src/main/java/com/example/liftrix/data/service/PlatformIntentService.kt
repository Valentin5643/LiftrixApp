package com.example.liftrix.data.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.liftrix.domain.usecase.sharing.ShareableContent
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for creating platform-specific Android Intents for sharing
 * 
 * This service handles the Android-specific logic for launching share intents
 * to various social media platforms and messaging apps. It includes:
 * - App availability checking
 * - Platform-specific intent creation
 * - Fallback handling when target apps aren't installed
 */
@Singleton
class PlatformIntentService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"
        private const val INSTAGRAM_STORY_MIME_TYPE = "image/*"
    }
    
    /**
     * Creates an Instagram Story sharing intent
     * 
     * @param content The shareable content to post to Instagram Stories
     * @return Intent for Instagram Story sharing, or generic fallback if Instagram not installed
     */
    fun shareToInstagramStory(content: ShareableContent): Intent {
        return if (isAppInstalled(INSTAGRAM_PACKAGE)) {
            createInstagramStoryIntent(content)
        } else {
            Timber.w("Instagram not installed, falling back to generic sharing")
            createGenericShareIntent(content)
        }
    }
    
    /**
     * Creates a WhatsApp sharing intent
     * 
     * @param content The shareable content to share via WhatsApp
     * @return Intent for WhatsApp sharing, or generic fallback if WhatsApp not installed
     */
    fun shareToWhatsApp(content: ShareableContent): Intent {
        return if (isAppInstalled(WHATSAPP_PACKAGE)) {
            createWhatsAppIntent(content)
        } else {
            Timber.w("WhatsApp not installed, falling back to generic sharing")
            createGenericShareIntent(content)
        }
    }
    
    /**
     * Creates a generic sharing intent using Android's share chooser
     * 
     * @param content The shareable content
     * @return Intent for generic platform sharing
     */
    fun shareGeneric(content: ShareableContent): Intent {
        return createGenericShareIntent(content)
    }
    
    /**
     * Creates Instagram Story specific intent with proper formatting
     */
    private fun createInstagramStoryIntent(content: ShareableContent): Intent {
        val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setPackage(INSTAGRAM_PACKAGE)
            type = INSTAGRAM_STORY_MIME_TYPE
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        
        // Add text overlay for Instagram Stories
        val storyText = buildInstagramStoryText(content)
        intent.putExtra("interactive_asset_uri", storyText)
        intent.putExtra("content_url", content.linkUrl ?: "")
        
        // Add background color for better visibility
        intent.putExtra("top_background_color", "#1F2937") // Dark gray
        intent.putExtra("bottom_background_color", "#111827") // Darker gray
        
        // Add sticker text
        if (content.message.isNotEmpty()) {
            intent.putExtra("interactive_asset_uri", content.message)
        }
        
        return intent
    }
    
    /**
     * Creates WhatsApp specific intent
     */
    private fun createWhatsAppIntent(content: ShareableContent): Intent {
        val intent = Intent(Intent.ACTION_SEND).apply {
            setPackage(WHATSAPP_PACKAGE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, content.message)
        }
        
        // Add subject if available
        if (content.title.isNotEmpty()) {
            intent.putExtra(Intent.EXTRA_SUBJECT, content.title)
        }
        
        return intent
    }
    
    /**
     * Creates generic share intent using Android's native share chooser
     */
    private fun createGenericShareIntent(content: ShareableContent): Intent {
        val shareText = buildGenericShareText(content)
        
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, content.title)
        }
        
        // Create chooser intent
        return Intent.createChooser(intent, "Share workout via...").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
    
    /**
     * Builds Instagram Story formatted text with hashtags
     */
    private fun buildInstagramStoryText(content: ShareableContent): String {
        return buildString {
            append(content.message)
            
            if (content.hashtags.isNotEmpty()) {
                appendLine()
                appendLine()
                append(content.hashtags.joinToString(" ") { "#$it" })
            }
            
            if (content.linkUrl != null) {
                appendLine()
                appendLine()
                append("Link: ${content.linkUrl}")
            }
        }
    }
    
    /**
     * Builds generic share text combining all content elements
     */
    private fun buildGenericShareText(content: ShareableContent): String {
        return buildString {
            if (content.title.isNotEmpty()) {
                appendLine(content.title)
                appendLine()
            }
            
            append(content.message)
            
            if (content.linkUrl != null) {
                appendLine()
                appendLine()
                append(content.linkUrl)
            }
            
            if (content.hashtags.isNotEmpty()) {
                appendLine()
                appendLine()
                append(content.hashtags.joinToString(" ") { "#$it" })
            }
        }
    }
    
    /**
     * Checks if a specific app package is installed on the device
     * 
     * @param packageName The package name to check
     * @return true if the app is installed, false otherwise
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}