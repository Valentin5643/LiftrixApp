package com.example.liftrix.di

import org.junit.Test

class FirebaseModuleTest {

    @Test(expected = IllegalStateException::class)
    fun `validateFirestorePersistenceConfig throws when room-first enabled and persistence enabled`() {
        FirebaseModule.validateFirestorePersistenceConfig(
            roomFirstEnabled = true,
            disableFirestorePersistence = false
        )
    }

    @Test
    fun `validateFirestorePersistenceConfig allows legacy mode`() {
        FirebaseModule.validateFirestorePersistenceConfig(
            roomFirstEnabled = false,
            disableFirestorePersistence = false
        )
    }

    @Test
    fun `validateFirestorePersistenceConfig allows when persistence disabled`() {
        FirebaseModule.validateFirestorePersistenceConfig(
            roomFirstEnabled = true,
            disableFirestorePersistence = true
        )
    }
}
