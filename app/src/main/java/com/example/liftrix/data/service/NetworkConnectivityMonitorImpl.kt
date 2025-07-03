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
 */
@Singleton
class NetworkConnectivityMonitorImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : NetworkConnectivityMonitor {

    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _isConnected = MutableStateFlow(checkInitialConnectivity())
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isMonitoring = false

    init {
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
     * Start monitoring network connectivity changes
     */
    private fun startMonitoring() {
        if (isMonitoring) return
        
        try {
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
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

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
            isMonitoring = true
            Timber.d("NetworkConnectivityMonitor started monitoring")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start network monitoring")
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
     * Stop monitoring (for cleanup if needed)
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        
        try {
            networkCallback?.let { callback ->
                connectivityManager.unregisterNetworkCallback(callback)
                networkCallback = null
                isMonitoring = false
                Timber.d("NetworkConnectivityMonitor stopped monitoring")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to stop network monitoring")
        }
    }
} 