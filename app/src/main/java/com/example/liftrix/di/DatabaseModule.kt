package com.example.liftrix.di

import android.content.Context
import com.example.liftrix.core.security.DatabaseEncryption
import com.example.liftrix.data.local.DatabasePassphraseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseEncryption(
        @ApplicationContext context: Context
    ): DatabaseEncryption = DatabaseEncryption(context)

    @Provides
    @Singleton
    fun provideDatabasePassphraseProvider(
        databaseEncryption: DatabaseEncryption
    ): DatabasePassphraseProvider {
        if (!databaseEncryption.validateEncryptionSetup()) {
            throw SecurityException("Database encryption validation failed")
        }

        return object : DatabasePassphraseProvider {
            override fun getPassphrase(): ByteArray =
                databaseEncryption.getSQLCipherPassphrase().toByteArray()
        }
    }
}
