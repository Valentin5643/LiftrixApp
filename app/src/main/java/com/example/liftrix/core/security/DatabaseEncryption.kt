package com.example.liftrix.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import timber.log.Timber
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Database encryption utility for SQLCipher integration.
 * Generates and manages secure encryption keys using Android Keystore.
 */
@Singleton
class DatabaseEncryption @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val KEYSTORE_ALIAS = "liftrix_db_key"
        private const val ENCRYPTED_PREFS_NAME = "liftrix_secure_prefs"
        private const val DB_KEY_PREFERENCE = "database_encryption_key"
        
        // SQLCipher requires 256-bit (32 byte) keys
        private const val KEY_SIZE_BITS = 256
        private const val KEY_SIZE_BYTES = 32
    }
    
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }
    
    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    /**
     * Gets or creates the database encryption key.
     * Uses Android Keystore for secure key generation and storage.
     * 
     * @return ByteArray representing the 256-bit encryption key
     * @throws SecurityException if key generation fails
     */
    fun getDatabaseKey(): ByteArray {
        return try {
            // First try to get existing key from encrypted preferences
            val existingKey = encryptedPrefs.getString(DB_KEY_PREFERENCE, null)
            if (existingKey != null) {
                Timber.d("Retrieved existing database key from secure storage")
                android.util.Base64.decode(existingKey, android.util.Base64.DEFAULT)
            } else {
                // Generate new key if none exists
                generateNewDatabaseKey()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve database key, generating new one")
            generateNewDatabaseKey()
        }
    }
    
    /**
     * Generates a new secure database encryption key.
     * Uses SecureRandom for cryptographically secure key generation.
     */
    private fun generateNewDatabaseKey(): ByteArray {
        return try {
            Timber.d("Generating new database encryption key")
            
            // Generate secure random key
            val key = ByteArray(KEY_SIZE_BYTES)
            SecureRandom().nextBytes(key)
            
            // Store encrypted key in preferences
            val encodedKey = android.util.Base64.encodeToString(key, android.util.Base64.DEFAULT)
            encryptedPrefs.edit()
                .putString(DB_KEY_PREFERENCE, encodedKey)
                .apply()
            
            Timber.i("Database encryption key generated and stored securely")
            key
        } catch (e: Exception) {
            Timber.e(e, "Critical error: Failed to generate database encryption key")
            throw SecurityException("Database encryption key generation failed", e)
        }
    }
    
    /**
     * Generates SQLCipher-compatible passphrase from the encryption key.
     * SQLCipher accepts raw keys or passphrases - using raw key for security.
     */
    fun getSQLCipherPassphrase(): String {
        val key = getDatabaseKey()
        return "x'${key.joinToString("") { "%02x".format(it) }}'"
    }
    
    /**
     * Validates that the encryption key meets SQLCipher requirements.
     */
    fun validateEncryptionSetup(): Boolean {
        return try {
            val key = getDatabaseKey()
            val isValid = key.size == KEY_SIZE_BYTES && key.any { it != 0.toByte() }
            
            if (isValid) {
                Timber.d("Database encryption setup validation successful")
            } else {
                Timber.e("Database encryption validation failed: invalid key")
            }
            
            isValid
        } catch (e: Exception) {
            Timber.e(e, "Database encryption validation failed with exception")
            false
        }
    }
    
    /**
     * Clears stored encryption key (for testing or reset scenarios).
     * WARNING: This will make existing encrypted data inaccessible.
     */
    fun clearEncryptionKey() {
        try {
            encryptedPrefs.edit()
                .remove(DB_KEY_PREFERENCE)
                .apply()
            Timber.w("Database encryption key cleared - existing data will be inaccessible")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear database encryption key")
        }
    }
}