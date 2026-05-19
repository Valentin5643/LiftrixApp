package com.example.liftrix.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiftrixFirebaseMessagingServiceTest {

    @Test
    fun serviceUsesInjectedScopeInsteadOfGlobalScope() {
        val source = File(
            "src/main/java/com/example/liftrix/service/LiftrixFirebaseMessagingService.kt"
        ).readText()

        assertTrue(source.contains("applicationScope.launch"))
        assertFalse(source.contains("GlobalScope"))
    }
}
