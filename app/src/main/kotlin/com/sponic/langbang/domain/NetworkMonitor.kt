package com.sponic.langbang.domain

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether the device currently has a validated internet connection so that callers
 * can short-circuit Azure / Gemini requests when offline. Survives the entire app lifetime.
 */
class NetworkMonitor(context: Context) {

    private val cm = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(probeOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    init {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { refreshOnline() }
            override fun onLost(network: Network) { refreshOnline() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                refreshOnline()
            }
        })
    }

    fun isOnline(): Boolean {
        refreshOnline()
        return _online.value
    }

    private fun refreshOnline() {
        _online.value = probeOnline()
    }

    @Suppress("DEPRECATION")
    private fun probeOnline(): Boolean {
        val manager = cm ?: return false
        val active = manager.activeNetwork
        if (active != null && manager.getNetworkCapabilities(active).hasValidatedInternet()) {
            return true
        }
        return manager.allNetworks.any { network ->
            manager.getNetworkCapabilities(network).hasValidatedInternet()
        }
    }
}

private fun NetworkCapabilities?.hasValidatedInternet(): Boolean =
    this != null &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
