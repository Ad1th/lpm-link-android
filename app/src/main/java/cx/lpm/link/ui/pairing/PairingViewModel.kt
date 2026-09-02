package cx.lpm.link.ui.pairing

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.link.network.DiscoveredMac
import cx.lpm.link.network.HostProbe
import cx.lpm.link.network.LpmClient
import cx.lpm.link.network.MacDiscovery
import cx.lpm.link.network.MessageRouter
import cx.lpm.link.network.PairingQrData
import cx.lpm.link.security.CertPinStore
import cx.lpm.link.security.CredentialStore
import cx.lpm.link.security.MacServer
import cx.lpm.link.security.TlsPinningFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import javax.inject.Inject

enum class PairingState {
    IDLE,
    SCANNING,
    CONNECTING,
    WAITING_APPROVAL,
    PAIRED,
    ERROR,
}

data class PairingUiState(
    val state: PairingState = PairingState.IDLE,
    val matchCode: String? = null,
    val errorMessage: String? = null,
    val nearbyMacs: List<DiscoveredMac> = emptyList(),
    val serverName: String? = null,
)

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val client: LpmClient,
    private val router: MessageRouter,
    private val discovery: MacDiscovery,
    private val hostProbe: HostProbe,
    private val credentialStore: CredentialStore,
    private val certPinStore: CertPinStore,
) : ViewModel() {

    companion object {
        private const val TAG = "PairingVM"
    }

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState

    private var localId: String = UUID.randomUUID().toString()
    private var pendingFingerprint: String? = null

    init {
        // Listen for pairing events
        viewModelScope.launch {
            router.pairingEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                when (type) {
                    "paired" -> onPaired(msg)
                    "pairPending" -> onPairPending(msg)
                    "pairDenied" -> onPairDenied(msg)
                    "error" -> onError(msg)
                }
            }
        }

        // Observe mDNS discoveries
        viewModelScope.launch {
            discovery.discoveredMacs.collect { macs ->
                _uiState.value = _uiState.value.copy(nearbyMacs = macs)
            }
        }

        // Start router
        router.start()
    }

    /**
     * Handle a scanned QR code string.
     */
    fun onQrScanned(raw: String) {
        val data = PairingQrData.parse(raw)
        if (data == null) {
            _uiState.value = _uiState.value.copy(
                state = PairingState.ERROR,
                errorMessage = "Invalid QR code. Make sure you're scanning the lpm pairing code.",
            )
            return
        }

        _uiState.value = _uiState.value.copy(state = PairingState.CONNECTING)
        pendingFingerprint = data.fingerprint

        viewModelScope.launch {
            // Race hosts to find reachable one
            val reachableHost = hostProbe.race(data.hosts, data.port)
            if (reachableHost == null) {
                _uiState.value = _uiState.value.copy(
                    state = PairingState.ERROR,
                    errorMessage = "Could not reach your Mac. Make sure you're on the same network.",
                )
                return@launch
            }

            // Connect with pinned cert
            val (sslFactory, trustManager) = TlsPinningFactory.create(data.fingerprint)
            client.configure(
                hosts = listOf(reachableHost),
                port = data.port,
                sslSocketFactory = sslFactory,
                trustManager = trustManager,
                deviceId = null,
                token = null,
            )
            client.connect()

            // Wait briefly for connection, then send pair
            kotlinx.coroutines.delay(500)
            client.sendPair(
                code = data.code,
                deviceName = android.os.Build.MODEL,
            )
        }
    }

    /**
     * Pair with a discovered Mac via approval flow.
     */
    fun pairWithDiscoveredMac(mac: DiscoveredMac) {
        _uiState.value = _uiState.value.copy(state = PairingState.CONNECTING)
        pendingFingerprint = null // TOFU mode

        viewModelScope.launch {
            val (sslFactory, trustManager) = TlsPinningFactory.create(null)
            client.configure(
                hosts = listOf(mac.host),
                port = mac.port,
                sslSocketFactory = sslFactory,
                trustManager = trustManager,
                deviceId = null,
                token = null,
            )
            client.connect()

            kotlinx.coroutines.delay(500)
            client.sendPairRequest(deviceName = android.os.Build.MODEL)
        }
    }

    /**
     * Start mDNS discovery for nearby Macs.
     */
    fun startDiscovery() {
        discovery.startDiscovery()
    }

    /**
     * Stop mDNS discovery.
     */
    fun stopDiscovery() {
        discovery.stopDiscovery()
    }

    fun resetError() {
        _uiState.value = _uiState.value.copy(state = PairingState.IDLE, errorMessage = null)
    }

    // --- Event handlers ---

    private fun onPaired(msg: kotlinx.serialization.json.JsonObject) {
        val deviceId = msg["deviceId"]?.jsonPrimitive?.content ?: return
        val token = msg["token"]?.jsonPrimitive?.content ?: return
        val serverId = msg["serverId"]?.jsonPrimitive?.content ?: return
        val serverName = msg["serverName"]?.jsonPrimitive?.content ?: "Mac"
        val hosts = msg["hosts"]?.let { hostsArr ->
            try {
                val arr = hostsArr as? kotlinx.serialization.json.JsonArray
                arr?.map { it.jsonPrimitive.content } ?: emptyList()
            } catch (_: Exception) { emptyList() }
        } ?: emptyList()

        Log.d(TAG, "Paired with $serverName (serverId=$serverId)")

        // Save credentials
        credentialStore.saveCredential(localId, CredentialStore.DeviceCredential(deviceId, token))
        credentialStore.saveServer(MacServer(
            localId = localId,
            serverId = serverId,
            serverName = serverName,
            hosts = hosts,
            port = client.let { 8765 }, // TODO: extract actual port
            certFingerprint = pendingFingerprint,
        ))
        credentialStore.setActiveServerId(localId)

        // Pin cert fingerprint if available
        pendingFingerprint?.let { certPinStore.pinFingerprint(localId, it) }

        _uiState.value = _uiState.value.copy(
            state = PairingState.PAIRED,
            serverName = serverName,
        )
    }

    private fun onPairPending(msg: kotlinx.serialization.json.JsonObject) {
        val matchCode = msg["matchCode"]?.jsonPrimitive?.content
        _uiState.value = _uiState.value.copy(
            state = PairingState.WAITING_APPROVAL,
            matchCode = matchCode,
        )
    }

    private fun onPairDenied(msg: kotlinx.serialization.json.JsonObject) {
        val reason = msg["reason"]?.jsonPrimitive?.content ?: "unknown"
        val message = msg["message"]?.jsonPrimitive?.content
        _uiState.value = _uiState.value.copy(
            state = PairingState.ERROR,
            errorMessage = message ?: when (reason) {
                "declined" -> "Pairing was declined on the Mac."
                "timeout" -> "Pairing timed out. Try again."
                "busy" -> "Mac is busy with another pairing request."
                else -> "Pairing failed: $reason"
            },
        )
    }

    private fun onError(msg: kotlinx.serialization.json.JsonObject) {
        val error = msg["error"]?.jsonPrimitive?.content ?: "Unknown error"
        _uiState.value = _uiState.value.copy(
            state = PairingState.ERROR,
            errorMessage = when (error) {
                "pairing rejected" -> "Invalid pairing code. Scan a fresh QR code."
                "pairing unavailable" -> "Pairing is not available on this Mac."
                else -> error
            },
        )
    }

    override fun onCleared() {
        discovery.stopDiscovery()
    }
}
