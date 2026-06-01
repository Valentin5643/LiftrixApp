package com.example.liftrix.logging

import android.util.Log
import com.example.liftrix.core.logging.LiftrixLogger
import com.example.liftrix.core.logging.ReleaseLoggingTree
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseLoggingPolicyTest {

    private val tree = ReleaseLoggingTree()

    @Test
    fun releaseTreeDropsVerboseAndDebugLogs() {
        assertFalse(tree.accepts(Log.VERBOSE))
        assertFalse(tree.accepts(Log.DEBUG))
    }

    @Test
    fun releaseTreeAllowsWarningAndAbove() {
        assertFalse(tree.accepts(Log.INFO))
        assertTrue(tree.accepts(Log.WARN))
        assertTrue(tree.accepts(Log.ERROR))
        assertTrue(tree.accepts(Log.ASSERT))
    }

    @Test
    fun safeIdDoesNotExposeRawIdentifier() {
        val rawId = "firebase-user-123"

        val safeId = LiftrixLogger.safeId(rawId)

        assertTrue(safeId.startsWith("id-"))
        assertFalse(safeId.contains(rawId))
        assertEquals(safeId, LiftrixLogger.safeId(rawId))
        assertNotEquals(safeId, LiftrixLogger.safeId("other-user"))
    }

    @Test
    fun redactSensitiveTokensRemovesKnownIdentifierValues() {
        val message = "operation=start userId=raw-123 token=secret-value email=user@example.com"

        val redacted = LiftrixLogger.redactSensitiveTokens(message)

        assertFalse(redacted.contains("raw-123"))
        assertFalse(redacted.contains("secret-value"))
        assertFalse(redacted.contains("user@example.com"))
        assertTrue(redacted.contains("userId=[redacted]"))
        assertTrue(redacted.contains("token=[redacted]"))
        assertTrue(redacted.contains("email=[redacted]"))
    }

    @Test
    fun safeKeyNormalizesCrashlyticsKeyNames() {
        assertEquals("current_screen_id", LiftrixLogger.safeKey(" Current Screen ID! "))
        assertEquals("unknown", LiftrixLogger.safeKey("!!!"))
    }
}
