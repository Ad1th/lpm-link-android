package cx.lpm.link.ui.memory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.link.network.LpmClient
import cx.lpm.link.network.MessageRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

@Serializable
data class MemorySessionItem(
    val name: String,
    val preview: String? = null,
    val modifiedAt: Long? = null,
)

data class MemoryUiState(
    val projectName: String = "",
    val sessions: List<MemorySessionItem> = emptyList(),
    val activeSessionContent: String? = null,
    val activeSessionName: String? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class MemoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val client: LpmClient,
    private val router: MessageRouter,
    private val json: Json,
) : ViewModel() {

    val projectName: String = checkNotNull(savedStateHandle["name"])

    private val _uiState = MutableStateFlow(MemoryUiState(projectName = projectName))
    val uiState: StateFlow<MemoryUiState> = _uiState

    init {
        viewModelScope.launch {
            router.memoryEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                when (type) {
                    "memory" -> {
                        val arr = msg["sessions"]?.jsonArray ?: return@collect
                        val items = arr.map { json.decodeFromJsonElement<MemorySessionItem>(it) }
                        _uiState.value = _uiState.value.copy(sessions = items, isLoading = false)
                    }
                    "memorySession" -> {
                        val name = msg["name"]?.jsonPrimitive?.content
                        val content = msg["content"]?.jsonPrimitive?.content
                        _uiState.value = _uiState.value.copy(
                            activeSessionName = name,
                            activeSessionContent = content
                        )
                    }
                    "memory-changed" -> {
                        refresh()
                    }
                }
            }
        }

        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        val payload = buildJsonObject { put("project", projectName) }
        client.send("memory", payload)
    }

    fun openSession(sessionName: String) {
        val payload = buildJsonObject {
            put("project", projectName)
            put("name", sessionName)
        }
        client.send("memorySession", payload)
    }

    fun closeSession() {
        _uiState.value = _uiState.value.copy(activeSessionName = null, activeSessionContent = null)
    }

    fun saveSession(sessionName: String, newContent: String) {
        val payload = buildJsonObject {
            put("project", projectName)
            put("name", sessionName)
            put("content", newContent)
        }
        client.send("memorySave", payload)
    }
}
