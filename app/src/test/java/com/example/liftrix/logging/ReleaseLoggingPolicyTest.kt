package com.example.liftrix.logging

import android.util.Log
import com.example.liftrix.core.logging.ReleaseLoggingTree
import org.junit.Assert.assertFalse
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
}
