package com.example.liftrix.core.logging

import timber.log.Timber
import java.security.MessageDigest

object LiftrixLogger {
    enum class Context(private val label: String) {
        App("app"),
        Screen("screen"),
        Repository("repository"),
        Network("network"),
        Database("database"),
        Sync("sync"),
        Performance("performance");

        fun prefix(message: String): String = "[$label] $message"
    }

    fun verbose(context: Context, message: String, throwable: Throwable? = null) {
        Timber.v(throwable, context.prefix(message))
    }

    fun debug(context: Context, message: String, throwable: Throwable? = null) {
        Timber.d(throwable, context.prefix(message))
    }

    fun info(context: Context, message: String, throwable: Throwable? = null) {
        Timber.i(throwable, context.prefix(message))
    }

    fun warn(context: Context, message: String, throwable: Throwable? = null) {
        Timber.w(throwable, context.prefix(message))
    }

    fun error(context: Context, message: String, throwable: Throwable? = null) {
        Timber.e(throwable, context.prefix(message))
    }

    fun safeId(value: String?): String {
        if (value.isNullOrBlank()) return "none"

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }

        return "id-${digest.take(12)}"
    }

    fun redact(value: String?): String {
        return if (value.isNullOrBlank()) "none" else REDACTED
    }

    fun redactSensitiveTokens(message: String): String {
        return SENSITIVE_PATTERNS.fold(message) { sanitized, pattern ->
            sanitized.replace(pattern, "$1=$REDACTED")
        }
    }

    fun safeKey(key: String): String {
        return key
            .lowercase()
            .replace(Regex("[^a-z0-9_]+"), "_")
            .trim('_')
            .ifBlank { "unknown" }
            .take(MAX_KEY_LENGTH)
    }

    private const val REDACTED = "[redacted]"
    private const val MAX_KEY_LENGTH = 40
    private val SENSITIVE_PATTERNS = listOf(
        Regex("(?i)\\b(uid|userId|user_id|firebaseCurrentUserId|token|secret)=([^\\s,]+)"),
        Regex("(?i)\\b(email)=([^\\s,]+)")
    )
}
