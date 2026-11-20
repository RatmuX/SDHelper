package io.github.ratmux.sdhelper.data.connection

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import io.github.ratmux.sdhelper.core.model.ComputerDiscovery
import io.github.ratmux.sdhelper.core.model.DiscoveredComputer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Finds agents announced as `_sdhelper._tcp` with the platform's NsdManager. Needs the local network permission on
 * Android 17+. Best effort: some networks block multicast, pairing by QR code or IP address does not depend on it.
 */
@Suppress("DEPRECATION") // resolveService is the only resolver before Android 14 and still works afterwards
class NsdComputerDiscovery(
    context: Context,
) : ComputerDiscovery {
    private val nsd = context.getSystemService(NsdManager::class.java)

    override fun discover(): Flow<List<DiscoveredComputer>> =
        callbackFlow {
            val found = linkedMapOf<String, DiscoveredComputer>()
            val toResolve = Channel<NsdServiceInfo>(Channel.UNLIMITED)

            // NsdManager resolves one service at a time, so resolution is queued.
            launch {
                for (service in toResolve) {
                    val resolved = resolve(service) ?: continue
                    toComputer(resolved)?.let { computer ->
                        synchronized(found) {
                            found[resolved.serviceName] = computer
                            trySend(found.values.toList())
                        }
                    }
                }
            }

            val listener =
                object : NsdManager.DiscoveryListener {
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        toResolve.trySend(serviceInfo)
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                        synchronized(found) {
                            found.remove(serviceInfo.serviceName)
                            trySend(found.values.toList())
                        }
                    }

                    override fun onDiscoveryStarted(serviceType: String) = Unit

                    override fun onDiscoveryStopped(serviceType: String) = Unit

                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) {
                        close()
                    }

                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int,
                    ) = Unit
                }
            trySend(emptyList())
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            awaitClose {
                toResolve.close()
                runCatching { nsd.stopServiceDiscovery(listener) }
            }
        }

    private suspend fun resolve(service: NsdServiceInfo): NsdServiceInfo? =
        suspendCancellableCoroutine { continuation ->
            nsd.resolveService(
                service,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(
                        serviceInfo: NsdServiceInfo,
                        errorCode: Int,
                    ) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        if (continuation.isActive) continuation.resume(serviceInfo)
                    }
                },
            )
        }

    /** IPv4 only: link-local IPv6 addresses need a scope id and are not worth the trouble for a LAN helper. */
    private fun toComputer(info: NsdServiceInfo): DiscoveredComputer? {
        val host = info.host?.hostAddress?.takeIf { ':' !in it } ?: return null
        val id = info.attributes["id"]?.toString(Charsets.UTF_8)?.takeIf { it.isNotBlank() } ?: return null
        return DiscoveredComputer(agentId = id, name = info.serviceName, host = host, port = info.port)
    }

    private companion object {
        const val SERVICE_TYPE = "_sdhelper._tcp"
    }
}
