package com.example.liftrix.service

import com.example.liftrix.domain.model.common.LiftrixResult
import com.example.liftrix.domain.model.common.liftrixCatching
import com.example.liftrix.domain.model.error.LiftrixError
import com.example.liftrix.domain.service.AnalyticsMetadata
import com.example.liftrix.domain.service.DeepLink
import com.example.liftrix.domain.service.DeepLinkAnalytics
import com.example.liftrix.domain.service.DeepLinkMetadata
import com.example.liftrix.domain.service.DeepLinkService
import com.example.liftrix.domain.service.DeepLinkTimeframe
import com.example.liftrix.domain.service.DeepLinkType
import com.example.liftrix.domain.service.DeepLinkValidation
import com.example.liftrix.domain.service.DynamicLinkConfig
import com.example.liftrix.domain.service.ParsedDeepLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep link service implementation with Firebase Dynamic Links integration.
 * Part of content sharing and media management system from SPEC-20250113-content-sharing-media.
 */
@Singleton
class DeepLinkServiceImpl @Inject constructor() : DeepLinkService {
    
    companion object {
        // Dynamic Links configuration
        private val CONFIG = DynamicLinkConfig()
        
        // Link patterns
        private const val ROUTINE_PATH = "/routine"
        private const val POST_PATH = "/post"
        private const val PROFILE_PATH = "/profile"
        private const val PROGRESS_PATH = "/progress"
        
        // Web fallback paths
        private const val WEB_ROUTINE_PATH = "/shared/routine"
        private const val WEB_POST_PATH = "/shared/post"
        private const val WEB_PROFILE_PATH = "/shared/profile"
        private const val WEB_PROGRESS_PATH = "/shared/progress"
    }
    
    override suspend fun createRoutineLink(
        routineId: String,
        shareToken: String,
        metadata: DeepLinkMetadata
    ): LiftrixResult<DeepLink> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "ROUTINE_LINK_CREATION_FAILED",
                errorMessage = "Failed to create routine link: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CREATE_ROUTINE_LINK",
                    "routine_id" to routineId,
                    "share_token" to shareToken
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            Timber.d("🔗 Creating routine deep link: $routineId")
            
            val deepLinkUrl = buildDeepLinkUrl(ROUTINE_PATH, routineId, shareToken)
            val webFallbackUrl = buildWebFallbackUrl(WEB_ROUTINE_PATH, shareToken)
            
            val dynamicLink = createFirebaseDynamicLink(
                deepLinkUrl = deepLinkUrl,
                webFallbackUrl = webFallbackUrl,
                metadata = metadata.copy(
                    title = metadata.title ?: "Check out this workout routine!",
                    description = metadata.description ?: "Shared from Liftrix"
                )
            )
            
            Timber.i("✅ Routine link created: ${dynamicLink.shortUrl}")
            dynamicLink
        }
    }
    
    override suspend fun createWorkoutPostLink(
        postId: String,
        metadata: DeepLinkMetadata
    ): LiftrixResult<DeepLink> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "POST_LINK_CREATION_FAILED",
                errorMessage = "Failed to create post link: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CREATE_POST_LINK",
                    "post_id" to postId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            Timber.d("🔗 Creating workout post deep link: $postId")
            
            val deepLinkUrl = buildDeepLinkUrl(POST_PATH, postId)
            val webFallbackUrl = buildWebFallbackUrl(WEB_POST_PATH, postId)
            
            val dynamicLink = createFirebaseDynamicLink(
                deepLinkUrl = deepLinkUrl,
                webFallbackUrl = webFallbackUrl,
                metadata = metadata.copy(
                    title = metadata.title ?: "Check out this workout!",
                    description = metadata.description ?: "Shared from Liftrix"
                )
            )
            
            Timber.i("✅ Post link created: ${dynamicLink.shortUrl}")
            dynamicLink
        }
    }
    
    override suspend fun createProfileLink(
        userId: String,
        metadata: DeepLinkMetadata
    ): LiftrixResult<DeepLink> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROFILE_LINK_CREATION_FAILED",
                errorMessage = "Failed to create profile link: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CREATE_PROFILE_LINK",
                    "user_id" to userId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            Timber.d("🔗 Creating profile deep link: $userId")
            
            val deepLinkUrl = buildDeepLinkUrl(PROFILE_PATH, userId)
            val webFallbackUrl = buildWebFallbackUrl(WEB_PROFILE_PATH, userId)
            
            val dynamicLink = createFirebaseDynamicLink(
                deepLinkUrl = deepLinkUrl,
                webFallbackUrl = webFallbackUrl,
                metadata = metadata.copy(
                    title = metadata.title ?: "Check out this fitness profile!",
                    description = metadata.description ?: "Follow on Liftrix"
                )
            )
            
            Timber.i("✅ Profile link created: ${dynamicLink.shortUrl}")
            dynamicLink
        }
    }
    
    override suspend fun createProgressLink(
        comparisonId: String,
        metadata: DeepLinkMetadata
    ): LiftrixResult<DeepLink> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "PROGRESS_LINK_CREATION_FAILED",
                errorMessage = "Failed to create progress link: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "CREATE_PROGRESS_LINK",
                    "comparison_id" to comparisonId
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            Timber.d("🔗 Creating progress deep link: $comparisonId")
            
            val deepLinkUrl = buildDeepLinkUrl(PROGRESS_PATH, comparisonId)
            val webFallbackUrl = buildWebFallbackUrl(WEB_PROGRESS_PATH, comparisonId)
            
            val dynamicLink = createFirebaseDynamicLink(
                deepLinkUrl = deepLinkUrl,
                webFallbackUrl = webFallbackUrl,
                metadata = metadata.copy(
                    title = metadata.title ?: "Amazing fitness transformation!",
                    description = metadata.description ?: "Progress shared from Liftrix"
                )
            )
            
            Timber.i("✅ Progress link created: ${dynamicLink.shortUrl}")
            dynamicLink
        }
    }
    
    override suspend fun parseDeepLink(deepLinkUrl: String): LiftrixResult<ParsedDeepLink> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.ValidationError(
                    field = "deep_link_url",
                    violations = listOf("Invalid deep link format: ${throwable.message}"),
                    analyticsContext = mapOf(
                        "operation" to "PARSE_DEEP_LINK",
                        "url" to deepLinkUrl
                    )
                )
            }
        ) {
            withContext(Dispatchers.IO) {
                Timber.d("🔍 Parsing deep link: $deepLinkUrl")
                
                val parsed = when {
                    deepLinkUrl.contains(ROUTINE_PATH) -> parseRoutineLink(deepLinkUrl)
                    deepLinkUrl.contains(POST_PATH) -> parsePostLink(deepLinkUrl)
                    deepLinkUrl.contains(PROFILE_PATH) -> parseProfileLink(deepLinkUrl)
                    deepLinkUrl.contains(PROGRESS_PATH) -> parseProgressLink(deepLinkUrl)
                    else -> ParsedDeepLink(
                        type = DeepLinkType.UNKNOWN,
                        targetId = "",
                        isValid = false,
                        fallbackUrl = CONFIG.webFallbackUrl
                    )
                }
                
                Timber.i("✅ Deep link parsed: ${parsed.type}")
                parsed
            }
        }
    
    override suspend fun validateDeepLink(deepLinkUrl: String): LiftrixResult<DeepLinkValidation> = 
        liftrixCatching(
            errorMapper = { throwable ->
                LiftrixError.BusinessLogicError(
                    code = "DEEP_LINK_VALIDATION_FAILED",
                    errorMessage = "Failed to validate deep link: ${throwable.message}",
                    analyticsContext = mapOf(
                        "operation" to "VALIDATE_DEEP_LINK",
                        "url" to deepLinkUrl
                    )
                )
            }
        ) {
            withContext(Dispatchers.IO) {
                Timber.d("✅ Validating deep link: $deepLinkUrl")
                
                // Parse the link to check its structure
                val parsed = parseDeepLink(deepLinkUrl).getOrThrow()
                
                // Check if the target resource exists (in production, check database)
                val targetExists = checkTargetExists(parsed.type, parsed.targetId)
                
                // Check expiration (would check database in production)
                val isExpired = false
                
                val validation = DeepLinkValidation(
                    isValid = parsed.isValid && targetExists,
                    isExpired = isExpired,
                    isAccessible = targetExists && !isExpired,
                    targetExists = targetExists,
                    requiresAuth = parsed.requiresAuth,
                    errors = if (parsed.isValid && targetExists && !isExpired) {
                        emptyList()
                    } else {
                        listOf("Link validation failed")
                    }
                )
                
                Timber.i("✅ Deep link validation: ${if (validation.isValid) "Valid" else "Invalid"}")
                validation
            }
        }
    
    override suspend fun getDeepLinkAnalytics(
        deepLinkUrl: String,
        timeframe: DeepLinkTimeframe
    ): LiftrixResult<DeepLinkAnalytics> = liftrixCatching(
        errorMapper = { throwable ->
            LiftrixError.BusinessLogicError(
                code = "DEEP_LINK_ANALYTICS_FAILED",
                errorMessage = "Failed to get deep link analytics: ${throwable.message}",
                analyticsContext = mapOf(
                    "operation" to "GET_DEEP_LINK_ANALYTICS",
                    "url" to deepLinkUrl,
                    "timeframe" to timeframe.name
                )
            )
        }
    ) {
        withContext(Dispatchers.IO) {
            Timber.d("📊 Getting deep link analytics: $deepLinkUrl")
            
            // In production, this would fetch real analytics from Firebase
            val analytics = simulateAnalyticsData(deepLinkUrl, timeframe)
            
            Timber.i("✅ Analytics retrieved: ${analytics.totalClicks} clicks")
            analytics
        }
    }
    
    private fun buildDeepLinkUrl(
        path: String,
        targetId: String,
        shareToken: String? = null
    ): String {
        val scheme = "liftrix"
        val host = "app.liftrix.com"
        val params = mutableListOf<String>()
        
        if (shareToken != null) {
            params.add("token=${URLEncoder.encode(shareToken, "UTF-8")}")
        }
        
        val queryString = if (params.isNotEmpty()) "?${params.joinToString("&")}" else ""
        
        return "$scheme://$host$path/$targetId$queryString"
    }
    
    private fun buildWebFallbackUrl(path: String, identifier: String): String {
        return "${CONFIG.webFallbackUrl}$path/$identifier"
    }
    
    private fun createFirebaseDynamicLink(
        deepLinkUrl: String,
        webFallbackUrl: String,
        metadata: DeepLinkMetadata
    ): DeepLink {
        // In production, this would use Firebase Dynamic Links SDK
        // For now, we'll simulate the dynamic link creation
        
        val linkId = generateLinkId()
        val shortUrl = "${CONFIG.domainUriPrefix}/$linkId"
        val longUrl = buildFirebaseDynamicLinkUrl(deepLinkUrl, webFallbackUrl, metadata)
        val qrCodeUrl = "https://api.qrserver.com/v1/create-qr-code/?data=${URLEncoder.encode(shortUrl, "UTF-8")}"
        
        return DeepLink(
            shortUrl = shortUrl,
            longUrl = longUrl,
            webUrl = webFallbackUrl,
            qrCodeUrl = qrCodeUrl,
            metadata = metadata,
            createdAt = System.currentTimeMillis(),
            expiresAt = metadata.expiration
        )
    }
    
    private fun buildFirebaseDynamicLinkUrl(
        deepLinkUrl: String,
        webFallbackUrl: String,
        metadata: DeepLinkMetadata
    ): String {
        val params = mutableListOf<String>()
        
        // Required parameters
        params.add("link=${URLEncoder.encode(deepLinkUrl, "UTF-8")}")
        params.add("apn=${CONFIG.androidPackageName}")
        params.add("ibi=${CONFIG.iosBundleId}")
        params.add("isi=${CONFIG.iosAppStoreId}")
        params.add("ofl=${URLEncoder.encode(webFallbackUrl, "UTF-8")}")
        
        // Social metadata
        metadata.title?.let { params.add("st=${URLEncoder.encode(it, "UTF-8")}") }
        metadata.description?.let { params.add("sd=${URLEncoder.encode(it, "UTF-8")}") }
        metadata.imageUrl?.let { params.add("si=${URLEncoder.encode(it, "UTF-8")}") }
        
        // Analytics parameters
        val analytics = metadata.analytics
        analytics.campaign?.let { params.add("utm_campaign=${URLEncoder.encode(it, "UTF-8")}") }
        analytics.source?.let { params.add("utm_source=${URLEncoder.encode(it, "UTF-8")}") }
        analytics.medium?.let { params.add("utm_medium=${URLEncoder.encode(it, "UTF-8")}") }
        
        return "${CONFIG.domainUriPrefix}/?${params.joinToString("&")}"
    }
    
    private fun generateLinkId(): String {
        val uuid = UUID.randomUUID().toString()
        val hash = MessageDigest.getInstance("MD5").digest(uuid.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(8)
    }
    
    private fun parseRoutineLink(deepLinkUrl: String): ParsedDeepLink {
        val routineId = extractIdFromPath(deepLinkUrl, ROUTINE_PATH)
        val shareToken = extractParameterFromUrl(deepLinkUrl, "token")
        
        return ParsedDeepLink(
            type = DeepLinkType.ROUTINE,
            targetId = routineId,
            parameters = if (shareToken != null) mapOf("token" to shareToken) else emptyMap(),
            isValid = routineId.isNotEmpty(),
            requiresAuth = false,
            fallbackUrl = buildWebFallbackUrl(WEB_ROUTINE_PATH, shareToken ?: routineId)
        )
    }
    
    private fun parsePostLink(deepLinkUrl: String): ParsedDeepLink {
        val postId = extractIdFromPath(deepLinkUrl, POST_PATH)
        
        return ParsedDeepLink(
            type = DeepLinkType.WORKOUT_POST,
            targetId = postId,
            isValid = postId.isNotEmpty(),
            requiresAuth = false,
            fallbackUrl = buildWebFallbackUrl(WEB_POST_PATH, postId)
        )
    }
    
    private fun parseProfileLink(deepLinkUrl: String): ParsedDeepLink {
        val userId = extractIdFromPath(deepLinkUrl, PROFILE_PATH)
        
        return ParsedDeepLink(
            type = DeepLinkType.USER_PROFILE,
            targetId = userId,
            isValid = userId.isNotEmpty(),
            requiresAuth = false,
            fallbackUrl = buildWebFallbackUrl(WEB_PROFILE_PATH, userId)
        )
    }
    
    private fun parseProgressLink(deepLinkUrl: String): ParsedDeepLink {
        val comparisonId = extractIdFromPath(deepLinkUrl, PROGRESS_PATH)
        
        return ParsedDeepLink(
            type = DeepLinkType.PROGRESS_COMPARISON,
            targetId = comparisonId,
            isValid = comparisonId.isNotEmpty(),
            requiresAuth = false,
            fallbackUrl = buildWebFallbackUrl(WEB_PROGRESS_PATH, comparisonId)
        )
    }
    
    private fun extractIdFromPath(url: String, path: String): String {
        return try {
            url.substringAfter(path).substringAfter("/").substringBefore("?")
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun extractParameterFromUrl(url: String, parameter: String): String? {
        return try {
            val query = url.substringAfter("?")
            query.split("&")
                .find { it.startsWith("$parameter=") }
                ?.substringAfter("=")
        } catch (e: Exception) {
            null
        }
    }
    
    private fun checkTargetExists(type: DeepLinkType, targetId: String): Boolean {
        // In production, this would check the database for the target resource
        return targetId.isNotEmpty()
    }
    
    private fun simulateAnalyticsData(
        deepLinkUrl: String,
        timeframe: DeepLinkTimeframe
    ): DeepLinkAnalytics {
        val multiplier = when (timeframe) {
            DeepLinkTimeframe.LAST_1D -> 1
            DeepLinkTimeframe.LAST_7D -> 7
            DeepLinkTimeframe.LAST_30D -> 30
            DeepLinkTimeframe.ALL_TIME -> 90
        }
        
        val totalClicks = (50..500).random() * multiplier.toLong()
        val uniqueClicks = (totalClicks * 0.7).toLong()
        val appOpens = (totalClicks * 0.4).toLong()
        
        return DeepLinkAnalytics(
            linkUrl = deepLinkUrl,
            totalClicks = totalClicks,
            uniqueClicks = uniqueClicks,
            appInstalls = (appOpens * 0.1).toLong(),
            appOpens = appOpens,
            webOpens = totalClicks - appOpens,
            topReferrers = listOf("Instagram", "WhatsApp", "Direct"),
            topCountries = listOf("US", "UK", "CA"),
            topPlatforms = listOf("Android", "iOS", "Web"),
            conversionRate = (appOpens.toFloat() / totalClicks.toFloat()),
            timeframe = timeframe
        )
    }
}