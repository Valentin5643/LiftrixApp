package com.example.liftrix.data.service

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.firestore.ListenerRegistration
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized manager for Firestore real-time listeners.
 *
 * Prevents memory leaks by:
 * - Tracking all active listener registrations
 * - Automatic cleanup on lifecycle events
 * - Preventing duplicate listeners for same path
 * - Force cleanup on app backgrounding
 *
 * Usage:
 * ```kotlin
 * // Register listener with lifecycle awareness
 * val registration = firestore.collection("posts")
 *     .document(postId)
 *     .addSnapshotListener { snapshot, error ->
 *         // Handle updates
 *     }
 * listenerManager.registerListener("post_$postId", registration, lifecycleOwner)
 *
 * // Automatic cleanup on lifecycle destroy
 * // Or manual cleanup: listenerManager.removeListener("post_$postId")
 * ```
 *
 * IMPORTANT: Always register listeners with this manager to prevent leaks.
 * LeakCanary will detect leaked listeners if not properly removed.
 */
@Singleton
class FirestoreListenerManager @Inject constructor() {

    private val activeListeners = ConcurrentHashMap<String, ListenerRegistration>()
    private val lifecycleObservers = ConcurrentHashMap<String, LifecycleObserver>()

    /**
     * Register a Firestore listener with automatic lifecycle cleanup.
     *
     * @param listenerId Unique identifier for this listener (e.g., "post_123", "engagement_456")
     * @param registration Firestore ListenerRegistration from addSnapshotListener()
     * @param lifecycleOwner Optional LifecycleOwner for automatic cleanup (recommended)
     */
    fun registerListener(
        listenerId: String,
        registration: ListenerRegistration,
        lifecycleOwner: LifecycleOwner? = null
    ) {
        // Remove existing listener with same ID to prevent duplicates
        removeListener(listenerId)

        activeListeners[listenerId] = registration

        lifecycleOwner?.let { owner ->
            val observer = LifecycleObserver(listenerId, this)
            owner.lifecycle.addObserver(observer)
            lifecycleObservers[listenerId] = observer
        }

        Timber.d("Registered Firestore listener: $listenerId (active: ${activeListeners.size})")
    }

    /**
     * Remove specific listener by ID.
     * Automatically called when lifecycle owner is destroyed.
     */
    fun removeListener(listenerId: String) {
        activeListeners.remove(listenerId)?.let { registration ->
            try {
                registration.remove()
                Timber.d("Removed Firestore listener: $listenerId (remaining: ${activeListeners.size})")
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove listener: $listenerId")
            }
        }

        lifecycleObservers.remove(listenerId)?.let { observer ->
            // Observer cleanup is automatic via lifecycle
        }
    }

    /**
     * Remove all listeners (use when app is backgrounding or user logs out).
     */
    fun removeAllListeners() {
        Timber.d("Removing all Firestore listeners (count: ${activeListeners.size})")

        activeListeners.keys.toList().forEach { listenerId ->
            removeListener(listenerId)
        }

        activeListeners.clear()
        lifecycleObservers.clear()
    }

    /**
     * Check if listener is registered.
     */
    fun isListenerActive(listenerId: String): Boolean {
        return activeListeners.containsKey(listenerId)
    }

    /**
     * Get count of active listeners (for debugging/metrics).
     */
    fun getActiveListenerCount(): Int {
        return activeListeners.size
    }

    /**
     * Get all active listener IDs (for debugging).
     */
    fun getActiveListenerIds(): List<String> {
        return activeListeners.keys.toList()
    }

    /**
     * Lifecycle observer for automatic listener cleanup.
     */
    private class LifecycleObserver(
        private val listenerId: String,
        private val manager: FirestoreListenerManager
    ) : DefaultLifecycleObserver {

        override fun onDestroy(owner: LifecycleOwner) {
            Timber.d("Lifecycle destroyed for listener: $listenerId")
            manager.removeListener(listenerId)
            owner.lifecycle.removeObserver(this)
        }

        override fun onPause(owner: LifecycleOwner) {
            // Optional: Remove listener on pause to save resources
            // Uncomment if aggressive memory management is needed
            // manager.removeListener(listenerId)
        }
    }
}
