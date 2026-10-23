package com.yohanvelasquez.recordatorios.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Informa si hay red y avisa cuando cambia (por ejemplo, al salir del modo avión). */
class MonitorRed(context: Context) : ConsultaRed {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    override fun estaConectado(): Boolean {
        val capacidades = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val conectado: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(estaConectado()) }
            override fun onLost(network: Network) { trySend(estaConectado()) }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(estaConectado())
            }
        }
        trySend(estaConectado())
        cm.registerDefaultNetworkCallback(callback)
        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
