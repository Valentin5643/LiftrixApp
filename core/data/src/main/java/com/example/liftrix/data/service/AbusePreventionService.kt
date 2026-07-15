package com.example.liftrix.data.service

import com.example.liftrix.data.remote.config.RemoteConfigManager
import com.example.liftrix.domain.service.AbuseAction
import com.example.liftrix.domain.service.AbuseDetection
import com.example.liftrix.domain.service.AbusePreventionServiceContract
import com.example.liftrix.domain.service.AbuseType
import com.example.liftrix.domain.service.AnalyticsTracker
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/** Content-policy detection for paid AI requests. Request-rate enforcement belongs to RateLimitingService. */
@Singleton
class AbusePreventionService @Inject constructor(
    private val remoteConfig: RemoteConfigManager,
    private val analyticsTracker: AnalyticsTracker
) : AbusePreventionServiceContract {

    override suspend fun detectAbuse(userId: String, message: String): AbuseDetection {
        val normalized = message.lowercase()
        val threshold = remoteConfig.getAiJailbreakThreshold().getOrThrow().toFloat()
        val fitnessWeight = remoteConfig.getAiFitnessContextWeight().getOrThrow().toFloat()
        require(threshold in 0f..1f) { "AI jailbreak threshold must be between 0 and 1" }
        require(fitnessWeight in 0f..1f) { "AI fitness context weight must be between 0 and 1" }

        val jailbreakScore = calculateJailbreakScore(normalized, fitnessWeight)
        if (jailbreakScore >= threshold) {
            val hasFitnessContext = containsFitnessContext(normalized)
            val action = if (jailbreakScore >= HIGH_CONFIDENCE_THRESHOLD && !hasFitnessContext) {
                AbuseAction.BLOCK
            } else {
                AbuseAction.REVIEW
            }
            analyticsTracker.trackAbuseDetection(
                userId = userId,
                abuseType = AbuseType.JAILBREAK.name,
                action = action.name,
                confidence = jailbreakScore,
                messageLength = message.length,
                additionalProperties = mapOf("has_fitness_context" to hasFitnessContext)
            )
            Timber.w(
                "AI content policy detected jailbreak action=%s confidence=%.2f messageChars=%d",
                action.name,
                jailbreakScore,
                message.length
            )
            return AbuseDetection(
                isAbusive = true,
                type = AbuseType.JAILBREAK,
                action = action,
                confidence = jailbreakScore,
                warning = "This request appears to violate AI usage guidelines"
            )
        }

        if (message.length > MAX_MESSAGE_LENGTH) {
            return AbuseDetection(
                isAbusive = true,
                type = AbuseType.EXCESSIVE_INPUT,
                action = AbuseAction.TRUNCATE,
                confidence = 1f,
                warning = "Message too long. Please keep messages under $MAX_MESSAGE_LENGTH characters."
            )
        }

        if (detectNonsensePattern(normalized)) {
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

    private fun calculateJailbreakScore(message: String, fitnessWeight: Float): Float {
        val matches = jailbreakIndicators.filterKeys(message::contains).values
        if (matches.isEmpty()) return 0f

        val baseScore = matches.average().toFloat()
        val fitnessMatches = fitnessKeywords.count(message::contains)
        val reduction = (fitnessMatches * 0.1f).coerceAtMost(fitnessWeight)
        return (baseScore * (1f - reduction)).coerceIn(0f, 1f)
    }

    private fun containsFitnessContext(message: String): Boolean =
        fitnessKeywords.count(message::contains) >= 2

    private fun detectNonsensePattern(message: String): Boolean {
        val repeatedChars = Regex("(.)\\1{9,}")
        val keyboardMash = Regex("[asdfjkl;]{15,}|[qwerty]{15,}|[zxcvbnm]{15,}")
        val randomChars = Regex("[^a-zA-Z0-9\\s]{20,}")
        return repeatedChars.containsMatchIn(message) ||
            keyboardMash.containsMatchIn(message) ||
            randomChars.containsMatchIn(message)
    }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 2_000
        const val HIGH_CONFIDENCE_THRESHOLD = 0.9f

        val jailbreakIndicators = mapOf(
            "ignore previous instructions" to 0.9f,
            "forget your rules" to 0.9f,
            "bypass safety" to 0.95f,
            "act as dan" to 0.95f,
            "you are now" to 0.6f,
            "pretend to be" to 0.5f,
            "system:" to 0.8f,
            "assistant:" to 0.8f,
            "roleplay as" to 0.7f,
            "disregard all" to 0.9f,
            "override your" to 0.85f,
            "jailbreak" to 0.95f,
            "prompt injection" to 0.95f
        )

        val fitnessKeywords = setOf(
            "workout", "exercise", "reps", "sets", "muscle", "squat", "bench", "deadlift",
            "cardio", "stretch", "form", "weight", "training", "injury", "pain", "recovery",
            "rest", "protein", "calories", "barbell", "dumbbell", "pushup", "pullup", "plank",
            "burpee", "lunge", "bicep", "tricep", "chest", "shoulder", "back", "legs", "core",
            "warmup", "cooldown", "flexibility", "strength", "endurance", "antrenament",
            "exercițiu", "repetări", "seturi", "mușchi", "genuflexiuni", "împins", "îndreptări",
            "întindere", "formă", "greutate", "accidentare", "durere", "recuperare", "odihnă",
            "proteină", "calorii", "bară", "ganteră", "flotări", "tracțiuni", "planșă", "fandări",
            "biceps", "triceps", "piept", "umăr", "spate", "picioare", "abdomen"
        )
    }
}
