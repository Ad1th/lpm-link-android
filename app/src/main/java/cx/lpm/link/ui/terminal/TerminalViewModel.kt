package cx.lpm.link.ui.terminal

import android.util.Base64
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.link.model.ControlOwner
import cx.lpm.link.network.LpmClient
import cx.lpm.link.network.MessageRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import javax.inject.Inject

sealed interface TerminalCommand {
    data class Feed(val base64: String) : TerminalCommand
    data class Seed(val base64: String) : TerminalCommand
    data class Submit(val base64: String) : TerminalCommand
}

data class TerminalUiState(
    val projectName: String = "",
    val terminalId: String = "",
    val terminalLabel: String = "",
    val owner: ControlOwner? = null,
    val isOwner: Boolean = true,
    val isReady: Boolean = false,
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val client: LpmClient,
    private val router: MessageRouter,
    private val json: Json,
) : ViewModel() {

    companion object {
        private const val TAG = "TerminalVM"
    }

    val projectName: String = checkNotNull(savedStateHandle["projectName"])
    val terminalId: String = checkNotNull(savedStateHandle["terminalId"])

    private val _uiState = MutableStateFlow(
        TerminalUiState(projectName = projectName, terminalId = terminalId)
    )
    val uiState: StateFlow<TerminalUiState> = _uiState

    // Commands to be executed by the WebView
    private val _commands = MutableSharedFlow<TerminalCommand>(extraBufferCapacity = 256)
    val commands: SharedFlow<TerminalCommand> = _commands

    private var streamOffset: Long? = null

    init {
        viewModelScope.launch {
            router.terminalEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                val id = msg["id"]?.jsonPrimitive?.content

                if (id == terminalId) {
                    when (type) {
                        "seed" -> handleSeed(msg)
                        "o" -> handleOutput(msg)
                        "control" -> handleControl(msg)
                    }
                }
            }
        }

        subscribe()
    }

    fun subscribe() {
        Log.d(TAG, "Subscribing to terminal: $terminalId")
        val payload = buildJsonObject {
            put("id", terminalId)
            streamOffset?.let { put("from", it) }
        }
        client.send("sub", payload)
    }

    fun unsubscribe() {
        val payload = buildJsonObject {
            put("id", terminalId)
        }
        client.send("unsub", payload)
    }

    fun claimControl() {
        val payload = buildJsonObject {
            put("id", terminalId)
        }
        client.send("claim", payload)
    }

    fun onTerminalInput(data: String) {
        val payload = buildJsonObject {
            put("id", terminalId)
            put("d", data)
        }
        client.send("in", payload)
    }

    fun onTerminalResize(cols: Int, rows: Int) {
        val payload = buildJsonObject {
            put("id", terminalId)
            put("cols", cols)
            put("rows", rows)
        }
        client.send("resize", payload)
    }

    fun submitPrompt(text: String) {
        if (text.isBlank()) return
        Log.d(TAG, "Submitting prompt: ${text.take(20)}...")
        viewModelScope.launch {
            val b64 = Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            _commands.emit(TerminalCommand.Submit(b64))
        }
    }

    private fun handleSeed(msg: kotlinx.serialization.json.JsonObject) {
        val data = msg["data"]?.jsonPrimitive?.content ?: ""
        val off = msg["off"]?.jsonPrimitive?.longOrNull
        val reset = msg["reset"]?.jsonPrimitive?.booleanOrNull ?: true

        streamOffset = off

        // Decode or forward owner
        val ownerObj = msg["owner"]?.jsonObject
        val owner = if (ownerObj != null) {
            try { json.decodeFromJsonElement<ControlOwner>(ownerObj) } catch (_: Exception) { null }
        } else null

        _uiState.value = _uiState.value.copy(
            owner = owner,
            isOwner = owner == null || owner.kind == "mobile",
            isReady = true
        )

        val b64 = Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        viewModelScope.launch {
            if (reset) {
                _commands.emit(TerminalCommand.Seed(b64))
            } else {
                _commands.emit(TerminalCommand.Feed(b64))
            }
        }
    }

    private fun handleOutput(msg: kotlinx.serialization.json.JsonObject) {
        val data = msg["d"]?.jsonPrimitive?.content ?: return
        val off = msg["off"]?.jsonPrimitive?.longOrNull
        if (off != null) {
            streamOffset = off
        }

        val b64 = Base64.encodeToString(data.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        viewModelScope.launch {
            _commands.emit(TerminalCommand.Feed(b64))
        }
    }

    private fun handleControl(msg: kotlinx.serialization.json.JsonObject) {
        val ownerObj = msg["owner"]?.jsonObject
        val owner = if (ownerObj != null) {
            try { json.decodeFromJsonElement<ControlOwner>(ownerObj) } catch (_: Exception) { null }
        } else null

        _uiState.value = _uiState.value.copy(
            owner = owner,
            isOwner = owner == null || owner.kind == "mobile"
        )
    }

    override fun onCleared() {
        unsubscribe()
    }
}
