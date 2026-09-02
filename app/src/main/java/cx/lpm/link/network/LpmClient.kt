package cx.lpm.link.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Connection state exposed to the UI.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    CONNECTED,
    RECONNECTING,
}

/**
 * Core WebSocket client that manages the single connection to the lpm desktop.
 *
 * Handles:
 * - TLS with certificate pinning
 * - Authentication (pair + auth flows)
 * - Automatic reconnection with exponential backoff
 * - Heartbeat pings
 * - Offline message queuing
 * - Message routing via SharedFlow
 */
@Singleton
class LpmClient @Inject constructor() {

    companion object {
        private const val TAG = "LpmClient"
        private const val DEFAULT_PORT = 8765
        private const val HEARTBEAT_INTERVAL_MS = 20_000L
        private const val PONG_DEADLINE_MS = 10_000L
        private const val MAX_BACKOFF_MS = 20_000.0
        private const val MAX_OFFLINE_QUEUE = 32
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Connection state
    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state

    // Inbound message stream — UI/stores subscribe to this
    private val _messages = MutableSharedFlow<JsonObject>(extraBufferCapacity = 256)
    val messages: SharedFlow<JsonObject> = _messages

    // Connection parameters
    private var hosts: List<String> = emptyList()
    private var port: Int = DEFAULT_PORT
    private var sslSocketFactory: SSLSocketFactory? = null
    private var trustManager: X509TrustManager? = null

    // Auth
    private var deviceId: String? = null
    private var token: String? = null

    // WebSocket
    private var webSocket: WebSocket? = null
    private var reconnectAttempt = 0
    private var lastPongTime = 0L

    // Offline queue for non-live messages
    private val offlineQueue = ConcurrentLinkedQueue<String>()

    /**
     * Configure connection parameters. Call before [connect].
     */
    fun configure(
        hosts: List<String>,
        port: Int = DEFAULT_PORT,
        sslSocketFactory: SSLSocketFactory,
        trustManager: X509TrustManager,
        deviceId: String?,
        token: String?,
    ) {
        this.hosts = hosts
        this.port = port
        this.sslSocketFactory = sslSocketFactory
        this.trustManager = trustManager
        this.deviceId = deviceId
        this.token = token
    }

    /**
     * Initiate connection to the desktop. Tries each host in order.
     */
    fun connect() {
        if (_state.value == ConnectionState.CONNECTING || _state.value == ConnectionState.CONNECTED) return
        _state.value = ConnectionState.CONNECTING
        scope.launch { connectInternal() }
    }

    /**
     * Disconnect and stop reconnection attempts.
     */
    fun disconnect() {
        reconnectAttempt = Int.MAX_VALUE // prevent reconnect
        webSocket?.close(1000, "User disconnect")
        webSocket = null
        _state.value = ConnectionState.DISCONNECTED
    }

    /**
     * Send a JSON message over the WebSocket.
     * If disconnected, queues the message (up to MAX_OFFLINE_QUEUE).
     */
    fun send(type: String, payload: JsonObject = buildJsonObject {}) {
        val msg = buildJsonObject {
            put("t", type)
            payload.forEach { (k, v) -> put(k, v) }
        }.toString()
        sendRaw(msg)
    }

    /**
     * Send a raw JSON string. Queues if offline.
     */
    fun sendRaw(message: String) {
        val ws = webSocket
        if (ws != null && _state.value == ConnectionState.CONNECTED) {
            ws.send(message)
        } else if (offlineQueue.size < MAX_OFFLINE_QUEUE) {
            offlineQueue.add(message)
        }
    }

    /**
     * Send a pairing request (QR code flow).
     */
    fun sendPair(code: String, deviceName: String, replaces: String? = null) {
        val msg = buildJsonObject {
            put("t", "pair")
            put("code", code)
            put("name", deviceName)
            replaces?.let { put("replaces", it) }
        }.toString()
        webSocket?.send(msg)
    }

    /**
     * Send an approval pairing request (mDNS flow).
     */
    fun sendPairRequest(deviceName: String, replaces: String? = null) {
        val msg = buildJsonObject {
            put("t", "pairRequest")
            put("name", deviceName)
            replaces?.let { put("replaces", it) }
        }.toString()
        webSocket?.send(msg)
    }

    // --- Internal ---

    private suspend fun connectInternal() {
        if (hosts.isEmpty()) {
            Log.e(TAG, "Cannot connect: hosts list is empty! Re-pairing or host resolution required.")
            _state.value = ConnectionState.DISCONNECTED
            return
        }

        val factory = sslSocketFactory ?: run {
            Log.e(TAG, "SSL not configured")
            _state.value = ConnectionState.DISCONNECTED
            return
        }
        val tm = trustManager ?: return

        val client = OkHttpClient.Builder()
            .sslSocketFactory(factory, tm)
            .hostnameVerifier { _, _ -> true } // We pin by cert fingerprint, not hostname
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket keeps alive
            .writeTimeout(20, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.SECONDS) // We handle pings at protocol level
            .build()

        // Try each host
        for (host in hosts) {
            val bracketedHost = if (':' in host) "[$host]" else host
            val url = "wss://$bracketedHost:$port/"
            val request = Request.Builder().url(url).build()

            Log.d(TAG, "Connecting to $url")

            try {
                client.newWebSocket(request, createListener())
                return // listener callbacks handle the rest
            } catch (e: Exception) {
                Log.w(TAG, "Failed to connect to $host: ${e.message}")
            }
        }

        // All hosts failed
        scheduleReconnect()
    }

    private fun createListener() = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket opened")
            this@LpmClient.webSocket = webSocket
            reconnectAttempt = 0
            lastPongTime = System.currentTimeMillis()

            // Authenticate if we have credentials, otherwise wait for pairing
            val did = deviceId
            val tok = token
            if (did != null && tok != null) {
                _state.value = ConnectionState.AUTHENTICATING
                val authMsg = buildJsonObject {
                    put("t", "auth")
                    put("deviceId", did)
                    put("token", tok)
                }.toString()
                webSocket.send(authMsg)
            }

            startHeartbeat()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.v(TAG, "onMessage: ${text.take(30)}...")
            lastPongTime = System.currentTimeMillis()
            try {
                val obj = json.parseToJsonElement(text).jsonObject
                val type = obj["t"]?.jsonPrimitive?.content

                when (type) {
                    "ready" -> onReady(obj)
                    "paired" -> onPaired(obj)
                    "pong" -> { /* timestamp updated above */ }
                    else -> scope.launch { _messages.emit(obj) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse message: ${e.message}")
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "WebSocket failure: ${t.message}")
            this@LpmClient.webSocket = null
            scheduleReconnect()
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket closed: $code $reason")
            this@LpmClient.webSocket = null
            if (reconnectAttempt < Int.MAX_VALUE) {
                scheduleReconnect()
            }
        }
    }

    private fun onReady(obj: JsonObject) {
        Log.d(TAG, "Authenticated successfully")
        _state.value = ConnectionState.CONNECTED
        // Update hosts from server response
        obj["hosts"]?.let { hostsArr ->
            // hosts could be a JSON array of strings
        }
        flushOfflineQueue()
        scope.launch { _messages.emit(obj) }
    }

    private fun onPaired(obj: JsonObject) {
        Log.d(TAG, "Paired successfully")
        _state.value = ConnectionState.CONNECTED
        flushOfflineQueue()
        scope.launch { _messages.emit(obj) }
    }

    private fun flushOfflineQueue() {
        val ws = webSocket ?: return
        while (offlineQueue.isNotEmpty()) {
            val msg = offlineQueue.poll() ?: break
            ws.send(msg)
        }
    }

    private fun startHeartbeat() {
        scope.launch {
            while (webSocket != null) {
                delay(HEARTBEAT_INTERVAL_MS)
                val ws = webSocket ?: break

                // Check if pong was received recently
                val elapsed = System.currentTimeMillis() - lastPongTime
                if (elapsed > HEARTBEAT_INTERVAL_MS + PONG_DEADLINE_MS) {
                    Log.w(TAG, "Pong timeout — reconnecting")
                    ws.cancel()
                    this@LpmClient.webSocket = null
                    scheduleReconnect()
                    break
                }

                // Send protocol-level ping
                ws.send("""{"t":"ping"}""")
            }
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempt >= Int.MAX_VALUE) return
        reconnectAttempt++

        val baseDelay = min(1.5 * 2.0.pow(reconnectAttempt - 1), MAX_BACKOFF_MS)
        val jitter = Random.nextDouble(0.85, 1.15)
        val delayMs = (baseDelay * jitter).toLong()

        _state.value = if (reconnectAttempt <= 3) ConnectionState.CONNECTING else ConnectionState.RECONNECTING

        Log.d(TAG, "Reconnecting in ${delayMs}ms (attempt $reconnectAttempt)")
        scope.launch {
            delay(delayMs)
            connectInternal()
        }
    }
}
