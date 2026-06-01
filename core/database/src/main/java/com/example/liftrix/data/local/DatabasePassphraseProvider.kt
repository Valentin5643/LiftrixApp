package com.example.liftrix.data.local

/**
 * App-owned source for the SQLCipher database passphrase.
 *
 * The database module owns how the passphrase is applied to Room, while the
 * application module owns where the secret comes from.
 */
interface DatabasePassphraseProvider {
    fun getPassphrase(): ByteArray
}
