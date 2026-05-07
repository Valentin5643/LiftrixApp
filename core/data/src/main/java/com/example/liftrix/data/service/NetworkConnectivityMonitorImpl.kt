package com.example.liftrix.data.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.liftrix.domain.service.NetworkConnectivityMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of NetworkConnectivityMonitor using Android's ConnectivityManager.
 * Provides reactive network connectivity status with automatic registration/unregistration.
 * Thread-safe implementation with proper lifecycle management.
 */
@Singleton
class NetworkConnectivityMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkConnectivityMonitor {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isConnected = MutableStateFlow(checkInitialConnectivity())
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // Thread-safe monitoring flag using @Volatile
    @Volatile
    private var isMonitoring = false
    
    // Synchronization lock for thread safety
    private val monitoringLock = Any()

    init {
        // Start monitoring with proper thread safety
        startMonitoring()
    }

    override fun isCurrentlyConnected(): Boolean {
        return _isConnected.value
    }

    /**
     * Check initial connectivity status on service creation
     */
    private fun checkInitialConnectivity(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (e: Exception) {
            Timber.e(e, "Failed to check initial connectivity")
            false
        }
    }

    /**
     * Start monitoring network connectivity changes with thread safety
     */
    private fun startMonitoring() {
        synchronized(monitoringLock) {
            // Double-check to prevent multiple registrations
            if (isMonitoring) {
                Timber.d("NetworkConnectivityMonitor already monitoring, skipping registration")
                return
            }
            
            // Clean up any existing callback before registering new one
            networkCallback?.let { existingCallback ->
                try {
                    connectivityManager.unregisterNetworkCallback(existingCallback)
                    Timber.d("Cleaned up existing network callback before re-registration")
                } catch (e: Exception) {
                    // Ignore - callback might not be registered
                }
                networkCallback = null
            }
            
            try {
                val networkRequest = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .build()

                val callback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        updateConnectivityStatus(true)
                        Timber.d("Network available: Connected")
                    }

                    override fun onLost(network: Network) {
                        super.onLost(network)
                        updateConnectivityStatus(false)
                        Timber.d("Network lost: Disconnected")
                    }

                    override fun onCapabilitiesChanged(
                        network: Network,
                        networkCapabilities: NetworkCapabilities
                    ) {
                        super.onCapabilitiesChanged(network, networkCapabilities)
                        val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                        updateConnectivityStatus(hasInternet && isValidated)
                    }
                }
                
                // Register the callback
                connectivityManager.registerNetworkCallback(networkRequest, callback)
                
                // Only set the callback reference and flag after successful registration
                networkCallback = callback
                isMonitoring = true
                
                Timber.i("NetworkConnectivityMonitor successfully started monitoring")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to start network monitoring")
                isMonitoring = false
                networkCallback = null
            }
        }
    }

    /**
     * Update connectivity status and emit to StateFlow
     */
    private fun updateConnectivityStatus(connected: Boolean) {
        val previousState = _isConnected.value
        _isConnected.value = connected
        
        if (previousState != connected) {
            Timber.i("Network connectivity changed: ${if (connected) "ONLINE" else "OFFLINE"}")
        }
    }

    /**
     * Stop monitoring with proper cleanup and thread safety
     */
    fun stopMonitoring() {
        synchronized(monitoringLock) {
            if (!isMonitoring) {
                Timber.d("NetworkConnectivityMonitor not monitoring, nothing to stop")
                return
            }
            
            try {
                networkCallback?.let { callback ->
                    connectivityManager.unregisterNetworkCallback(callback)
                    Timber.d("NetworkConnectivityMonitor successfully stopped monitoring")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop network monitoring, but continuing cleanup")
            } finally {
                // Always clean up state, even if unregister fails
                networkCallback = null
                isMonitoring = false
            }
        }
    }
    
    /**
     * Restart monitoring - useful for recovering from network system changes
     */
    fun restartMonitoring() {
        synchronized(monitoringLock) {
            Timber.d("Restarting network connectivity monitoring")
            stopMonitoring()
            // Small delay to ensure cleanup completes
            Thread.sleep(100)
            startMonitoring()
        }
    }
} 