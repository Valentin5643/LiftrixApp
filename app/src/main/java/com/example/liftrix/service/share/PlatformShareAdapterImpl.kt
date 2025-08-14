package com.example.liftrix.service.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.example.liftrix.domain.model.ShareableContent
import com.example.liftrix.domain.model.ShareableContentType
import com.example.liftrix.domain.model.SocialPlatform
import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.share.PlatformConfig
import com.example.liftrix.domain.share.PlatformShareAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of platform share adapter for external social platforms.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@Singleton
class PlatformShareAdapterImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PlatformShareAdapter {
    
    companion object {
        // Platform configurations
        private val PLATFORM_CONFIGS = mapOf(
            SocialPlatform.INSTAGRAM to PlatformConfig(
                packageName = "com.instagram.android",
                intentAction = Intent.ACTION_SEND,
                mimeType = "image/*",
                maxTextLength = 2200,
                optimalImageDimensions = 1080 to 1080,
                supportsHashtags = true,
                supportsImages = true,
                supportsVideo = true
            ),
            SocialPlatform.WHATSAPP to PlatformConfig(
                packageName = "com.whatsapp",
                intentAction = Intent.ACTION_SEND,
                mimeType = "text/plain",
                maxTextLength = null,
                optimalImageDimensions = 600 to 600,
                supportsHashtags = false,
                supportsImages = true,
                supportsVideo = true
            ),
            SocialPlatform.TWITTER to PlatformConfig(
                packageName = "com.twitter.android",
                intentAction = Intent.ACTION_SEND,
                mimeType = "text/plain",
                maxTextLength = 280,
                optimalImageDimensions = 1200 to 675,
                supportsHashtags = true,
                supportsImages = true,
                supportsVideo = true
            ),
            SocialPlatform.FACEBOOK to PlatformConfig(
                packageName = "com.facebook.katana",
                intentAction = Intent.ACTION_SEND,
                mimeType = "text/plain",
                maxTextLength = 63206,
                optimalImageDimensions = 1200 to 630,
                supportsHashtags = true,
                supportsImages = true,
                supportsVideo = true
            ),
            SocialPlatform.TELEGRAM to PlatformConfig(
                packageName = "org.telegram.messenger",
                intentAction = Intent.ACTION_SEND,
                mimeType = "text/plain",
                maxTextLength = 4096,
                optimalImageDimensions = 1280 to 720,
                supportsHashtags = true,
                supportsImages = true,
                supportsVideo = true
            ),
            SocialPlatform.DISCORD to PlatformConfig(
                packageName = "com.discord",
                intentAction = Intent.ACTION_SEND,
                mimeType = "text/plain",
                maxTextLength = 2000,
                optimalImageDimensions = 1920 to 1080,
                supportsHashtags = false,
                supportsImages = true,
                supportsVideo = true
            )
        )
        
        // Fitness-related hashtags by platform
        private val FITNESS_HASHTAGS = mapOf(
            SocialPlatform.INSTAGRAM to listOf(
                "#fitness", "#workout", "#gym", "#gains", "#strength",
                "#bodybuilding", "#powerlifting", "#cardio", "#training",
                "#liftrix", "#progressphoto", "#transformation"
            ),
            SocialPlatform.TWITTER to listOf(
                "#fitness", "#workout", "#gym", "#gains", "#strength",
                "#training", "#liftrix", "#fitnessjourney"
            ),
            SocialPlatform.FACEBOOK to listOf(
                "#fitness", "#workout", "#gym", "#training", "#liftrix"
            ),
            SocialPlatform.TELEGRAM to listOf(
                "#fitness", "#workout", "#gym", "#liftrix"
            )
        )
    }
    
    override suspend fun shareContent(
        platform: SocialPlatform,
        content: ShareableContent,
        customText: String?,
        imageUrl: String?
    ): LiftrixResult<Intent> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PLATFORM_SHARE_FAILED",
                errorMessage = "Failed to share to ${platform.name}: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "SHARE_TO_PLATFORM",
                    "platform" to platform.name,
                    "content_type" to content.type.name
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            Timber.d("📤 Sharing content to ${platform.name}")
            
            val config = PLATFORM_CONFIGS[platform]
                ?: throw IllegalArgumentException("Unsupported platform: ${platform.name}")
            
            // Check if platform is available
            if (!isPlatformAvailable(platform)) {
                throw IllegalStateException("${platform.name} app is not installed")
            }
            
            // Create platform-specific intent
            val intent = when (platform) {
                SocialPlatform.INSTAGRAM -> createInstagramIntent(content, customText, imageUrl)
                SocialPlatform.WHATSAPP -> createWhatsAppIntent(content, customText, imageUrl)
                SocialPlatform.TWITTER -> createTwitterIntent(content, customText, imageUrl)
                SocialPlatform.FACEBOOK -> createFacebookIntent(content, customText, imageUrl)
                SocialPlatform.TELEGRAM -> createTelegramIntent(content, customText, imageUrl)
                SocialPlatform.DISCORD -> createDiscordIntent(content, customText, imageUrl)
            }
            
            Timber.i("✅ Share intent created for ${platform.name}")
            intent
        }
    }
    
    override suspend fun isPlatformAvailable(platform: SocialPlatform): Boolean = withContext(Dispatchers.IO) {
        val config = PLATFORM_CONFIGS[platform] ?: return@withContext false
        
        return@withContext try {
            context.packageManager.getPackageInfo(config.packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    override fun getOptimalImageDimensions(platform: SocialPlatform): Pair<Int, Int> {
        return PLATFORM_CONFIGS[platform]?.optimalImageDimensions ?: (1080 to 1080)
    }
    
    override fun getMaxTextLength(platform: SocialPlatform): Int? {
        return PLATFORM_CONFIGS[platform]?.maxTextLength
    }
    
    override suspend fun getRecommendedHashtags(
        platform: SocialPlatform,
        content: ShareableContent
    ): List<String> = withContext(Dispatchers.IO) {
        val baseHashtags = FITNESS_HASHTAGS[platform] ?: emptyList()
        
        // Add content-specific hashtags
        val contentHashtags = when (content.type) {
            ShareableContentType.WORKOUT -> listOf("#workoutcomplete", "#training")
            ShareableContentType.PR -> listOf("#personalrecord", "#pr", "#gains")
            ShareableContentType.PROGRESS -> listOf("#transformation", "#progressphoto")
            ShareableContentType.ROUTINE -> listOf("#workoutroutine", "#training")
        }
        
        // Add dynamic hashtags based on content metadata
        val dynamicHashtags = generateDynamicHashtags(content)
        
        (baseHashtags + contentHashtags + dynamicHashtags).distinct().take(10)
    }
    
    private fun createInstagramIntent(
        content: ShareableContent,
        customText: String?,
        imageUrl: String?
    ): Intent {
        return Intent().apply {
            action = Intent.ACTION_SEND
            setPackage("com.instagram.android")
            
            if (imageUrl != null) {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUrl))
            } else {
                type = "text/plain"
            }
            
            val text = buildShareText(content, customText, SocialPlatform.INSTAGRAM)
            putExtra(Intent.EXTRA_TEXT, text)
            
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    private fun createWhatsAppIntent(
        content: ShareableContent,
        customText: String?,
        imageUrl: String?
    ): Intent {
        return Intent().apply {
            action = Intent.ACTION_SEND
            setPackage("com.whatsapp")
            
            if (imageUrl != null) {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUrl))
            } else {
                type = "text/plain"
            }
            
            val text = buildShareText(content, customText, SocialPlatform.WHATSAPP)
            putExtra(Intent.EXTRA_TEXT, text)
            
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    private fun createTwitterIntent(
        content: ShareableContent,
        customText: String?,
        imageUrl: String?
    ): Intent {
        return Intent().apply {
            action = Intent.ACTION_SEND
            setPackage("com.twitter.android")
            
            if (imageUrl != null) {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUrl))
            } else {
                type = "text/plain"
            }
            
            val text = buildShareText(content, customText, SocialPlatform.TWITTER)
            putExtra(Intent.EXTRA_TEXT, text)
            
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    private fun createFacebookIntent(
        content: ShareableContent,
        customText: String?,
        imageUrl: String?
    ): Intent {
        return Intent().apply {
            action = Intent.ACTION_SEND
            setPackage("com.facebook.katana")
            
            if (imageUrl != null) {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUrl))
            } else {
                type = "text/plain"
            }
            
            val text = buildShareText(content, customText, SocialPlatform.FACEBOOK)
            putExtra(Intent.EXTRA_TEXT, text)
            
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    private fun createTelegramIntent(
        content: ShareableContent,
        customText: String?,
        imageUrl: String?
    ): Intent {
        return Intent().apply {
            action = Intent.ACTION_SEND
            setPackage("org.telegram.messenger")
            
            if (imageUrl != null) {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUrl))
            } else {
                type = "text/plain"
            }
            
            val text = buildShareText(content, customText, SocialPlatform.TELEGRAM)
            putExtra(Intent.EXTRA_TEXT, text)
            
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    private fun createDiscordIntent(
        content: ShareableContent,
        customText: String?,
        imageUrl: String?
    ): Intent {
        return Intent().apply {
            action = Intent.ACTION_SEND
            setPackage("com.discord")
            
            if (imageUrl != null) {
                type = "image/*"
                putExtra(Intent.EXTRA_STREAM, Uri.parse(imageUrl))
            } else {
                type = "text/plain"
            }
            
            val text = buildShareText(content, customText, SocialPlatform.DISCORD)
            putExtra(Intent.EXTRA_TEXT, text)
            
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
    
    private fun buildShareText(
        content: ShareableContent,
        customText: String?,
        platform: SocialPlatform
    ): String {
        val config = PLATFORM_CONFIGS[platform]!!
        
        val parts = mutableListOf<String>()
        
        // Add custom text first if provided
        customText?.let { parts.add(it) }
        
        // Add content title if no custom text
        if (customText.isNullOrBlank()) {
            parts.add(content.title)
        }
        
        // Add content subtitle if available
        content.subtitle?.let { subtitle ->
            if (customText.isNullOrBlank()) {
                parts.add(subtitle)
            }
        }
        
        // Add stats for certain content types
        if (content.type == ShareableContentType.WORKOUT && content.stats.isNotEmpty()) {
            val statsText = buildStatsText(content.stats)
            parts.add(statsText)
        }
        
        // Add hashtags if platform supports them
        if (config.supportsHashtags) {
            val hashtags = FITNESS_HASHTAGS[platform]?.take(3)?.joinToString(" ") ?: ""
            if (hashtags.isNotBlank()) {
                parts.add(hashtags)
            }
        }
        
        // Add app promotion
        parts.add("Shared from Liftrix")
        
        val fullText = parts.joinToString("\n\n")
        
        // Truncate if exceeds platform limit
        return config.maxTextLength?.let { maxLength ->
            if (fullText.length > maxLength) {
                fullText.take(maxLength - 3) + "..."
            } else {
                fullText
            }
        } ?: fullText
    }
    
    private fun buildStatsText(stats: Map<String, Any>): String {
        return stats.entries.take(3).joinToString(" | ") { (key, value) ->
            "${key.replace("_", " ").capitalize()}: $value"
        }
    }
    
    private fun generateDynamicHashtags(content: ShareableContent): List<String> {
        val hashtags = mutableListOf<String>()
        
        // Add hashtags based on content metadata
        content.metadata["muscleGroups"]?.let { muscleGroups ->
            if (muscleGroups is List<*>) {
                muscleGroups.filterIsInstance<String>().forEach { muscle ->
                    hashtags.add("#${muscle.lowercase()}")
                }
            }
        }
        
        content.metadata["exerciseType"]?.let { exerciseType ->
            hashtags.add("#${exerciseType.toString().lowercase()}")
        }
        
        return hashtags.take(3)
    }
}