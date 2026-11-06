package com.avi.peek.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(context: Context) {

    private val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val _online = MutableStateFlow(isCurrentlyOnline())
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _wifi = MutableStateFlow(isCurrentlyWifi())
    val wifi: StateFlow<Boolean> = _wifi.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refresh()
        override fun onLost(network: Network) = refresh()
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refresh()
    }

    init {
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    fun refresh() {
        _online.value = isCurrentlyOnline()
        _wifi.value = isCurrentlyWifi()
    }

    private fun isCurrentlyOnline(): Boolean {
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isCurrentlyWifi(): Boolean {
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }
}
