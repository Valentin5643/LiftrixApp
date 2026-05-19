package com.example.liftrix.core.logging

import android.util.Log
import timber.log.Timber

/**
 * Minimal release Timber tree.
 *
 * Sensitive values must be removed at call sites. This tree only enforces the
 * release severity policy and avoids global message parsing or rewriting.
 */
class ReleaseLoggingTree : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean {
        return accepts(priority)
    }

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (!accepts(priority)) return

        val safeTag = tag ?: DEFAULT_TAG
        if (t == null) {
            Log.println(priority, safeTag, message)
        } else {
            Log.println(priority, safeTag, "$message\n${Log.getStackTraceString(t)}")
        }
    }

    internal fun accepts(priority: Int): Boolean {
        return priority >= Log.WARN
    }

    private companion object {
        private const val DEFAULT_TAG = "Liftrix"
    }
}
