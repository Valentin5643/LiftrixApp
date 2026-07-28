package com.example.liftrix.domain.qr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Canonical encoder and strict parser for Gym Buddy QR invitations.
 *
 * Version 1 covers the unversioned URL and Base64-JSON formats emitted by older
 * releases. Version 2 adds an explicit protocol version while retaining the
 * same required security fields, so version 1 scanners that ignore unknown
 * query parameters can continue to read newly generated codes.
 */
object GymBuddyQrCodec {
    const val CURRENT_VERSION = 2

    private const val MAX_QR_DATA_LENGTH = 1024
    private const val MAX_USER_ID_LENGTH = 128
    private const val MIN_USER_ID_LENGTH = 10
    private const val MAX_TOKEN_LENGTH = 128
    private const val MIN_SECURE_TOKEN_LENGTH = 20
    private const val MAX_INVITATION_LIFETIME_MS = 5 * 60 * 1000L
    private const val CLOCK_SKEW_TOLERANCE_MS = 30 * 1000L

    private val secureTokenRegex = Regex("^[A-Za-z0-9_-]{$MIN_SECURE_TOKEN_LENGTH,$MAX_TOKEN_LENGTH}$")
    private val legacyTimestampTokenRegex = Regex("^[0-9]{13}$")
    private val allowedQueryKeys = setOf("userId", "token", "expiresAt", "expires", "v", "version")
    private val allowedJsonKeys = setOf("userId", "token", "expiresAt", "expires", "format", "v", "version")
    private val json = Json { ignoreUnknownKeys = false }

    fun encode(userId: String, token: String, expiresAt: Long): String {
        require(isValidUserId(userId)) { "Invalid Gym Buddy user ID" }
        require(isValidToken(token, allowLegacyTimestamp = false)) { "Invalid Gym Buddy token" }
        require(expiresAt > 0) { "Invalid Gym Buddy expiration" }

        return buildString {
            append("liftrix://gym-buddy?")
            append("userId=").append(encodeQueryValue(userId))
            append("&token=").append(encodeQueryValue(token))
            append("&expiresAt=").append(expiresAt)
            append("&v=").append(CURRENT_VERSION)
        }
    }

    fun parse(data: String, nowMillis: Long = System.currentTimeMillis()): GymBuddyQrParseResult {
        if (data.length > MAX_QR_DATA_LENGTH) {
            return GymBuddyQrParseResult.Invalid
        }

        val normalizedData = data.trim()
            .removePrefix("\uFEFF")
            .trim()
        if (normalizedData.isBlank() || normalizedData.any { it.isISOControl() }) {
            return GymBuddyQrParseResult.Invalid
        }

        return when {
            normalizedData.startsWith("liftrix://gym-buddy", ignoreCase = true) ->
                parseUrl(normalizedData, nowMillis)
            normalizedData.startsWith("eyJ") -> parseLegacyJson(normalizedData, nowMillis)
            UUID_REGEX.matches(normalizedData) -> GymBuddyQrParseResult.InsecureLegacy
            else -> GymBuddyQrParseResult.NotGymBuddyCode
        }
    }

    private fun parseUrl(data: String, nowMillis: Long): GymBuddyQrParseResult {
        val uri = runCatching { URI(data) }.getOrNull() ?: return GymBuddyQrParseResult.Invalid
        if (!uri.scheme.equals("liftrix", ignoreCase = true) ||
            !uri.host.equals("gym-buddy", ignoreCase = true) ||
            uri.userInfo != null ||
            uri.port != -1 || uri.fragment != null
        ) {
            return GymBuddyQrParseResult.Invalid
        }

        val pathSegments = uri.rawPath.orEmpty()
            .split('/')
            .filter { it.isNotEmpty() }
        if (pathSegments.size > 1) return GymBuddyQrParseResult.Invalid

        val query = parseQuery(uri.rawQuery ?: return incompleteLegacyResult(pathSegments))
            ?: return GymBuddyQrParseResult.Invalid
        if (query.keys.any { it !in allowedQueryKeys }) return GymBuddyQrParseResult.Invalid

        val version = parseVersion(query) ?: return GymBuddyQrParseResult.Invalid
        if (version > CURRENT_VERSION) return GymBuddyQrParseResult.UnsupportedVersion(version)
        if (version < 1) return GymBuddyQrParseResult.Invalid

        val pathUserId = pathSegments.singleOrNull()?.let(::decodeQueryValue)
            ?: if (pathSegments.isEmpty()) null else return GymBuddyQrParseResult.Invalid
        val queryUserId = query["userId"]
        if (pathUserId != null && queryUserId != null && pathUserId != queryUserId) {
            return GymBuddyQrParseResult.Invalid
        }

        val userId = queryUserId ?: pathUserId ?: return GymBuddyQrParseResult.InsecureLegacy
        val token = query["token"] ?: return GymBuddyQrParseResult.InsecureLegacy
        val expiresAt = parseExpiration(query) ?: return GymBuddyQrParseResult.InsecureLegacy
        val format = if (pathUserId == null) GymBuddyQrFormat.URL_QUERY else GymBuddyQrFormat.URL_PATH_LEGACY

        return validatePayload(
            payload = GymBuddyQrPayload(userId, token, expiresAt, version, format),
            nowMillis = nowMillis,
            allowLegacyTimestampToken = format == GymBuddyQrFormat.URL_PATH_LEGACY && version == 1
        )
    }

    private fun parseLegacyJson(data: String, nowMillis: Long): GymBuddyQrParseResult {
        val decoded = decodeBase64Json(data) ?: return GymBuddyQrParseResult.Invalid
        val jsonObject = runCatching { json.parseToJsonElement(decoded) as? JsonObject }.getOrNull()
            ?: return GymBuddyQrParseResult.Invalid
        if (jsonObject.keys.any { it !in allowedJsonKeys }) return GymBuddyQrParseResult.Invalid

        fun stringValue(key: String): String? = runCatching {
            jsonObject[key]?.jsonPrimitive?.content
        }.getOrNull()

        val versionText = stringValue("v") ?: stringValue("version")
        val version = versionText?.toIntOrNull() ?: if (versionText == null) 1 else return GymBuddyQrParseResult.Invalid
        if (version > CURRENT_VERSION) return GymBuddyQrParseResult.UnsupportedVersion(version)
        if (version < 1) return GymBuddyQrParseResult.Invalid

        val userId = stringValue("userId") ?: return GymBuddyQrParseResult.InsecureLegacy
        val token = stringValue("token") ?: return GymBuddyQrParseResult.InsecureLegacy
        val expiresAt = (stringValue("expiresAt") ?: stringValue("expires"))?.toLongOrNull()
            ?: return GymBuddyQrParseResult.InsecureLegacy

        return validatePayload(
            GymBuddyQrPayload(userId, token, expiresAt, version, GymBuddyQrFormat.BASE64_JSON_LEGACY),
            nowMillis,
            allowLegacyTimestampToken = true
        )
    }

    private fun validatePayload(
        payload: GymBuddyQrPayload,
        nowMillis: Long,
        allowLegacyTimestampToken: Boolean
    ): GymBuddyQrParseResult {
        if (!isValidUserId(payload.userId) ||
            !isValidToken(payload.token, allowLegacyTimestampToken) ||
            payload.expiresAt <= 0
        ) {
            return GymBuddyQrParseResult.Invalid
        }
        if (payload.expiresAt <= nowMillis) return GymBuddyQrParseResult.Expired
        if (payload.expiresAt - nowMillis > MAX_INVITATION_LIFETIME_MS + CLOCK_SKEW_TOLERANCE_MS) {
            return GymBuddyQrParseResult.Invalid
        }
        return GymBuddyQrParseResult.Valid(payload)
    }

    private fun parseQuery(rawQuery: String): Map<String, String>? {
        if (rawQuery.isEmpty()) return emptyMap()
        val values = linkedMapOf<String, String>()
        for (part in rawQuery.split('&')) {
            if (part.isEmpty()) return null
            val separatorIndex = part.indexOf('=')
            if (separatorIndex <= 0) return null
            val key = decodeQueryValue(part.substring(0, separatorIndex)) ?: return null
            val value = decodeQueryValue(part.substring(separatorIndex + 1)) ?: return null
            if (key in values) return null
            values[key] = value
        }
        return values
    }

    private fun parseVersion(query: Map<String, String>): Int? {
        val shortVersion = query["v"]
        val longVersion = query["version"]
        if (shortVersion != null && longVersion != null && shortVersion != longVersion) return null
        return (shortVersion ?: longVersion)?.toIntOrNull() ?: if (shortVersion == null && longVersion == null) 1 else null
    }

    private fun parseExpiration(query: Map<String, String>): Long? {
        val current = query["expiresAt"]
        val legacy = query["expires"]
        if (current != null && legacy != null && current != legacy) return null
        return (current ?: legacy)?.toLongOrNull()
    }

    private fun incompleteLegacyResult(pathSegments: List<String>): GymBuddyQrParseResult =
        if (pathSegments.size <= 1) GymBuddyQrParseResult.InsecureLegacy else GymBuddyQrParseResult.Invalid

    private fun decodeBase64Json(data: String): String? {
        val normalized = data.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        return runCatching {
            String(Base64.getDecoder().decode(padded), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun isValidUserId(userId: String): Boolean =
        userId.length in MIN_USER_ID_LENGTH..MAX_USER_ID_LENGTH &&
            userId == userId.trim() &&
            '/' !in userId &&
            userId.none { it.isISOControl() }

    private fun isValidToken(token: String, allowLegacyTimestamp: Boolean): Boolean =
        secureTokenRegex.matches(token) || (allowLegacyTimestamp && legacyTimestampTokenRegex.matches(token))

    private fun encodeQueryValue(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun decodeQueryValue(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()

    private val UUID_REGEX = Regex(
        "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
    )
}

data class GymBuddyQrPayload(
    val userId: String,
    val token: String,
    val expiresAt: Long,
    val version: Int,
    val format: GymBuddyQrFormat
)

enum class GymBuddyQrFormat {
    URL_QUERY,
    URL_PATH_LEGACY,
    BASE64_JSON_LEGACY
}

sealed interface GymBuddyQrParseResult {
    data class Valid(val payload: GymBuddyQrPayload) : GymBuddyQrParseResult
    data class UnsupportedVersion(val version: Int) : GymBuddyQrParseResult
    data object Expired : GymBuddyQrParseResult
    data object InsecureLegacy : GymBuddyQrParseResult
    data object Invalid : GymBuddyQrParseResult
    data object NotGymBuddyCode : GymBuddyQrParseResult
}
