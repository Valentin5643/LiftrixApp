package com.example.liftrix.data.service

import android.util.Base64
import timber.log.Timber
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for HMAC-SHA256 signature generation and verification.
 *
 * Provides cryptographic integrity verification for remote data sync operations.
 * Prevents data tampering attacks (SEC-001, SEC-004) by validating that remote
 * data has not been modified in transit or by malicious actors.
 *
 * Security:
 * - Uses HMAC-SHA256 for strong cryptographic signatures
 * - Per-user secret keys prevent cross-user signature replay
 * - Constant-time comparison prevents timing attacks
 * - Failed verification is logged for security auditing
 *
 * Usage:
 * ```
 * // Firestore write (server-side)
 * val signature = hmacService.generateSignature(userId, entityData)
 * firestoreDoc.set(mapOf("data" to entityData, "hmac" to signature))
 *
 * // Client-side verification
 * if (hmacService.verifySignature(userId, remoteData, remoteSignature)) {
 *     dao.upsertFromRemote(remoteData)
 * }
 * ```
 */
@Singleton
class HmacSignatureService @Inject constructor() {

    companion object {
        private const val ALGORITHM = "HmacSHA256"
        private const val TAG = "HmacSignatureService"
    }

    /**
     * Generates HMAC-SHA256 signature for given data.
     *
     * @param userId The user ID (used for key derivation)
     * @param data The data to sign (typically JSON string)
     * @return Base64-encoded HMAC signature
     */
    fun generateSignature(userId: String, data: String): String {
        return try {
            val secretKey = deriveSecretKey(userId)
            val mac = Mac.getInstance(ALGORITHM)
            mac.init(SecretKeySpec(secretKey.toByteArray(), ALGORITHM))
            val signatureBytes = mac.doFinal(data.toByteArray())
            Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate HMAC signature for user: $userId")
            throw SecurityException("HMAC signature generation failed", e)
        }
    }

    /**
     * Verifies HMAC-SHA256 signature for given data.
     *
     * Uses constant-time comparison to prevent timing attacks.
     *
     * @param userId The user ID (used for key derivation)
     * @param data The data to verify
     * @param signature The Base64-encoded signature to check against
     * @return true if signature is valid, false otherwise
     */
    fun verifySignature(userId: String, data: String, signature: String?): Boolean {
        if (signature == null) {
            Timber.w("[SEC-001] Missing HMAC signature for user: $userId")
            return false
        }

        return try {
            val expectedSignature = generateSignature(userId, data)
            val isValid = constantTimeEquals(expectedSignature, signature)

            if (!isValid) {
                Timber.e("[SEC-001] HMAC signature verification failed for user: $userId")
            }

            isValid
        } catch (e: Exception) {
            Timber.e(e, "[SEC-001] HMAC verification error for user: $userId")
            false
        }
    }

    /**
     * Derives a secret key for the given user ID.
     *
     * TODO: In production, this should:
     * 1. Retrieve user-specific key from secure storage (Android Keystore)
     * 2. Use proper key derivation function (PBKDF2, Argon2)
     * 3. Implement key rotation mechanism
     *
     * Current implementation is a placeholder for development.
     */
    private fun deriveSecretKey(userId: String): String {
        // DEVELOPMENT ONLY: Simple hash-based key derivation
        // PRODUCTION TODO: Use Android Keystore + PBKDF2/Argon2
        val baseSecret = "liftrix_hmac_secret_v1" // Should be from secure config
        return "$baseSecret:$userId".hashCode().toString()
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     *
     * Compares two strings in constant time regardless of where differences occur.
     * This prevents attackers from using timing analysis to gradually determine
     * the correct signature.
     */
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) {
            return false
        }

        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
