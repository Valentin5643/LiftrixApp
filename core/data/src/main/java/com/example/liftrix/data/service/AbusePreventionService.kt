package com.example.liftrix.data.service

import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.domain.model.chat.ChatMessage
import com.example.liftrix.domain.model.chat.MessageType
import com.example.liftrix.domain.repository.ChatRepository
import com.example.liftrix.domain.service.AbuseAction
import com.example.liftrix.domain.service.AbuseDetection
import com.example.liftrix.domain.service.AbusePreventionServiceContract
import com.example.liftrix.domain.service.AbuseType
import com.example.liftrix.domain.service.AnalyticsTracker
import com.example.liftrix.domain.model.common.LiftrixResult
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for detecting and preventing abuse of the AI chat system.
 * Implements multiple layers of protection including jailbreak detection,
 * rate anomaly detection, and content validation.
 */
@Singleton
class AbusePreventionService @Inject constructor(
    private val chatRepository: ChatRepository,
    private val remoteConfig: RemoteConfigManager,
    private val analyticsTracker: AnalyticsTracker
) : AbusePreventionServiceContract {
    
    companion object {
        private const val HOURLY_BASELINE = 10
        private const val RATE_ANOMALY_MULTIPLIER = 10
        private const val MAX_MESSAGE_LENGTH = 2000
        private const val JAILBREAK_THRESHOLD = 0.8f
        private const val HIGH_CONFIDENCE_THRESHOLD = 0.9f
        private const val MESSAGE_SIMILARITY_THRESHOLD = 0.8f
    }
    
    // High-confidence jailbreak patterns with context checking
    private val jailbreakIndicators = mapOf(
        "ignore previous instructions" to 0.9f,
        "forget your rules" to 0.9f,
        "bypass safety" to 0.95f,
        "act as DAN" to 0.95f,
        "you are now" to 0.6f, // Lower confidence, needs context
        "pretend to be" to 0.5f, // Could be legitimate ("pretend to be doing squats")
        "system:" to 0.8f,
        "assistant:" to 0.8f,
        "roleplay as" to 0.7f,
        "disregard all" to 0.9f,
        "override your" to 0.85f,
        "jailbreak" to 0.95f,
        "prompt injection" to 0.95f
    )
    
    // Fitness-related keywords that reduce jailbreak probability (English + Romanian)
    private val fitnessKeywords = setOf(
        // English
        "workout", "exercise", "reps", "sets", "muscle", "squat", "bench",
        "deadlift", "cardio", "stretch", "form", "weight", "training",
        "injury", "pain", "sore", "recovery", "rest", "protein", "calories",
        "barbell", "dumbbell", "pushup", "pullup", "plank", "burpee", "lunge",
        "bicep", "tricep", "chest", "shoulder", "back", "legs", "core",
        "warmup", "cooldown", "flexibility", "strength", "endurance",
        // Romanian
        "antrenament", "exercițiu", "repetări", "seturi", "mușchi", "genuflexiuni",
        "împins", "îndreptări", "cardio", "întindere", "formă", "greutate",
        "accidentare", "durere", "recuperare", "odihnă", "proteină", "calorii",
        "bară", "ganteră", "flotări", "tracțiuni", "planșă", "fandări",
        "biceps", "triceps", "piept", "umăr", "spate", "picioare", "abdomen"
    )
    
    private val userActivityTracker = mutableMapOf<String, UserActivity>()
    
    /**
     * Detects potential abuse in a user message.
     *
     * @param userId The user sending the message
     * @param message The message content to analyze
     * @return Detection result with recommended action
     */
    override suspend fun detectAbuse(userId: String, message: String): AbuseDetection {
        val lowercaseMessage = message.lowercase()
        
        // 1. Context-aware jailbreak detection
        val jailbreakScore = calculateJailbreakScore(lowercaseMessage)
        if (jailbreakScore > JAILBREAK_THRESHOLD) {
            // Log for analysis but don't immediately block
            analyticsTracker.trackAbuseDetection(
                userId = userId,
                abuseType = "POTENTIAL_JAILBREAK",
                action = "LOG",
                confidence = jailbreakScore,
                messageLength = message.length,
                additionalProperties = mapOf("has_fitness_context" to containsFitnessContext(lowercaseMessage))
            )
            
            // Only block if very high confidence and no fitness context
            if (jailbreakScore > HIGH_CONFIDENCE_THRESHOLD && !containsFitnessContext(lowercaseMessage)) {
                Timber.w("High confidence jailbreak attempt detected for user $userId")
                return AbuseDetection(
                    isAbusive = true,
                    type = AbuseType.JAILBREAK,
                    action = AbuseAction.REVIEW, // Send for review instead of immediate block
                    confidence = jailbreakScore,
                    warning = "This message appears to violate usage guidelines"
                )
            }
        }
        
        // 2. Check for rate anomalies with smart detection
        val activity = userActivityTracker.getOrPut(userId) { UserActivity() }
        activity.recordRequest()
        
        if (activity.isAnomalous()) {
            // Check if it's a burst of similar messages (copy-paste attack)
            val recentMessages = try {
                chatRepository.getRecentMessages(userId, limit = 5).fold(
                    onSuccess = { messages -> messages },
                    onFailure = { error ->
                        Timber.e("Failed to fetch recent messages for abuse detection: $error")
                        emptyList()
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error fetching recent messages")
                emptyList()
            }
            
            val similarity = calculateMessageSimilarity(message, recentMessages)
            
            if (similarity > MESSAGE_SIMILARITY_THRESHOLD) {
                Timber.w("Spam detected for user $userId - similar messages")
                return AbuseDetection(
                    isAbusive = true,
                    type = AbuseType.SPAM,
                    action = AbuseAction.COOLDOWN,
                    confidence = similarity,
                    warning = "Please avoid sending duplicate messages"
                )
            }
            
            // Regular rate anomaly
            Timber.w("Rate anomaly detected for user $userId")
            return AbuseDetection(
                isAbusive = true,
                type = AbuseType.RATE_ANOMALY,
                action = AbuseAction.THROTTLE, // Slow down instead of block
                confidence = 0.7f,
                warning = "You're sending messages too quickly. Please slow down."
            )
        }
        
        // 3. Check message length with context
        if (message.length > MAX_MESSAGE_LENGTH) {
            // Check if it's a workout plan paste (legitimate long input)
            if (containsWorkoutPlanIndicators(message)) {
                // Likely a workout plan, allow but suggest truncation
                return AbuseDetection(
                    isAbusive = false,
                    warning = "Long input detected. Consider breaking your message into smaller parts for better responses."
                )
            }
            
            Timber.w("Excessive input detected for user $userId")
            return AbuseDetection(
                isAbusive = true,
                type = AbuseType.EXCESSIVE_INPUT,
                action = AbuseAction.TRUNCATE,
                confidence = 0.6f,
                warning = "Message too long. Please keep messages under 2000 characters."
            )
        }
        
        // 4. Check for repeated nonsense patterns
        if (detectNonsensePattern(lowercaseMessage)) {
            Timber.w("Nonsense pattern detected for user $userId")
            return AbuseDetection(
                isAbusive = true,
                type = AbuseType.NONSENSE,
                action = AbuseAction.REJECT,
                confidence = 0.7f,
                warning = "Please provide a meaningful message"
            )
        }
        
        return AbuseDetection(isAbusive = false)
    }
    
    private fun calculateJailbreakScore(message: String): Float {
        var score = 0f
        var matchCount = 0
        
        for ((pattern, weight) in jailbreakIndicators) {
            if (message.contains(pattern)) {
                score += weight
                matchCount++
            }
        }
        
        // Reduce score if fitness context is present
        val fitnessCount = fitnessKeywords.count { message.contains(it) }
        if (fitnessCount > 0) {
            // Each fitness keyword reduces the score by 10%, up to 50% reduction
            val reduction = (fitnessCount * 0.1f).coerceAtMost(0.5f)
            score *= (1f - reduction)
        }
        
        // Normalize by match count
        return if (matchCount > 0) score / matchCount else 0f
    }
    
    private fun containsFitnessContext(message: String): Boolean {
        // Check if at least 2 fitness keywords are present for stronger context
        return fitnessKeywords.count { message.contains(it) } >= 2
    }
    
    private fun containsWorkoutPlanIndicators(message: String): Boolean {
        val planIndicators = listOf(
            "day", "week", "monday", "tuesday", "wednesday", "thursday", "friday",
            "exercise", "sets", "reps", "rest", "workout", "routine", "plan",
            "phase", "cycle", "progression", "deload"
        )
        
        // Check if message contains multiple plan indicators
        val indicatorCount = planIndicators.count { message.lowercase().contains(it) }
        return indicatorCount >= 3
    }
    
    private suspend fun calculateMessageSimilarity(
        current: String,
        recent: List<ChatMessage>
    ): Float {
        if (recent.isEmpty()) return 0f
        
        val similarities = recent
            .filter { it.type == MessageType.USER } // Only compare with user messages
            .map { msg ->
                levenshteinSimilarity(current, msg.content)
            }
        
        return similarities.maxOrNull() ?: 0f
    }
    
    private fun detectNonsensePattern(message: String): Boolean {
        // Detect keyboard mashing or repeated characters
        val repeatedChars = Regex("(.)\\1{9,}") // Same char 10+ times
        val keyboardMash = Regex("[asdfjkl;]{15,}|[qwerty]{15,}|[zxcvbnm]{15,}") // Keyboard patterns
        val randomChars = Regex("[^a-zA-Z0-9\\s]{20,}") // Too many special characters
        
        return repeatedChars.containsMatchIn(message) || 
               keyboardMash.containsMatchIn(message) ||
               randomChars.containsMatchIn(message)
    }
    
    private fun levenshteinSimilarity(s1: String, s2: String): Float {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1f
        val distance = levenshteinDistance(s1, s2)
        return 1f - (distance.toFloat() / maxLen)
    }
    
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        
        // Create a 2D array for dynamic programming
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        // Initialize base cases
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        
        // Fill the dp table
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[m][n]
    }
    
    /**
     * Tracks user activity for rate anomaly detection.
     */
    private class UserActivity {
        private val requestTimestamps = mutableListOf<Long>()
        private val windowMs = 3600000L // 1 hour window
        
        fun recordRequest() {
            val now = System.currentTimeMillis()
            requestTimestamps.add(now)
            // Clean old timestamps (older than window)
            requestTimestamps.removeAll { it < now - windowMs }
        }
        
        fun isAnomalous(): Boolean {
            // Check if user is making significantly more requests than baseline
            return requestTimestamps.size > HOURLY_BASELINE * RATE_ANOMALY_MULTIPLIER
        }
        
        fun getRequestCount(): Int = requestTimestamps.size
        
        fun getOldestTimestamp(): Long? = requestTimestamps.minOrNull()
    }
}
