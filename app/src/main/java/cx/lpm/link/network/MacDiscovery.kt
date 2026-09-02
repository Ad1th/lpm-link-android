package cx.lpm.link.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a discovered lpm desktop instance on the local network.
 */
data class DiscoveredMac(
    val serviceName: String,
    val serverId: String,
    val displayName: String,
    val host: String,
    val port: Int,
    val protocolVersion: String,
    val supportsApprovalPairing: Boolean,
    val isDev: Boolean,
)

/**
 * Discovers nearby lpm desktop instances via mDNS/Bonjour.
 *
 * The desktop advertises:
 *   Service type: _lpm._tcp.local.
 *   TXT records: id, name, v, rp, dev
 */
@Singleton
class MacDiscovery @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "MacDiscovery"
        private const val SERVICE_TYPE = "_lpm._tcp."
    }

    private val nsdManager by lazy { context.getSystemService(Context.NSD_SERVICE) as NsdManager }

    private val _discoveredMacs = MutableStateFlow<List<DiscoveredMac>>(emptyList())
    val discoveredMacs: StateFlow<List<DiscoveredMac>> = _discoveredMacs

    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val pending = mutableMapOf<String, NsdServiceInfo>()

    fun startDiscovery() {
        if (discoveryListener != null) return

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "mDNS discovery started for $serviceType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Found service: ${serviceInfo.serviceName}")
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Lost service: ${serviceInfo.serviceName}")
                _discoveredMacs.value = _discoveredMacs.value.filter {
                    it.serviceName != serviceInfo.serviceName
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "mDNS discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
                discoveryListener = null
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: $errorCode")
            }
        }

        discoveryListener = listener
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery: ${e.message}")
            discoveryListener = null
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try {
                nsdManager.stopServiceDiscovery(it)
            } catch (_: Exception) {
                // Already stopped
            }
            discoveryListener = null
        }
        _discoveredMacs.value = emptyList()
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${info.serviceName}: $errorCode")
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host?.hostAddress ?: return
                val port = info.port

                // Extract TXT records
                val attributes = info.attributes
                val serverId = attributes["id"]?.decodeToString() ?: return
                val displayName = attributes["name"]?.decodeToString() ?: info.serviceName
                val version = attributes["v"]?.decodeToString() ?: "1"
                val rp = attributes["rp"]?.decodeToString() == "1"
                val isDev = attributes["dev"]?.decodeToString() == "1"

                val mac = DiscoveredMac(
                    serviceName = info.serviceName,
                    serverId = serverId,
                    displayName = displayName,
                    host = host,
                    port = port,
                    protocolVersion = version,
                    supportsApprovalPairing = rp,
                    isDev = isDev,
                )

                Log.d(TAG, "Resolved: $displayName at $host:$port (id=$serverId)")

                // Add or update in the list
                val current = _discoveredMacs.value.toMutableList()
                current.removeAll { it.serverId == serverId }
                current.add(mac)
                _discoveredMacs.value = current
            }
        })
    }
}
