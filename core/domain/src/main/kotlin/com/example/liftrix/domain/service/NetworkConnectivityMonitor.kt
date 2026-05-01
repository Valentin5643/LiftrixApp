package com.example.liftrix.domain.service

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for monitoring network connectivity status.
 * Provides reactive connectivity updates for offline/online detection.
 */
interface NetworkConnectivityMonitor {
    
    /**
     * Reactive flow of network connectivity status.
     * Emits true when network is available, false when offline.
     */
    val isConnected: StateFlow<Boolean>
    
    /**
     * Get current connectivity status synchronously.
     * @return true if connected, false if offline
     */
    fun isCurrentlyConnected(): Boolean
} 