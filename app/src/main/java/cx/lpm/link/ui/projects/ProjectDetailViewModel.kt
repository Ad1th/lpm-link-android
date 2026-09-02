package cx.lpm.link.ui.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.link.model.ActionInfo
import cx.lpm.link.model.ProjectInfo
import cx.lpm.link.model.TerminalInfo
import cx.lpm.link.network.LpmClient
import cx.lpm.link.network.MessageRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

data class ProjectDetailUiState(
    val projectName: String = "",
    val project: ProjectInfo? = null,
    val terminals: List<TerminalInfo> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val client: LpmClient,
    private val router: MessageRouter,
    private val json: Json,
) : ViewModel() {

    private val projectName: String = checkNotNull(savedStateHandle["name"])

    private val _uiState = MutableStateFlow(ProjectDetailUiState(projectName = projectName))
    val uiState: StateFlow<ProjectDetailUiState> = _uiState

    init {
        // Collect terminal responses
        viewModelScope.launch {
            router.terminalEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                if (type == "terminals" && msg["project"]?.jsonPrimitive?.content == projectName) {
                    val arr = msg["terminals"]?.jsonArray ?: return@collect
                    val terms = arr.map { json.decodeFromJsonElement<TerminalInfo>(it) }
                    _uiState.value = _uiState.value.copy(terminals = terms)
                }
            }
        }

        // Collect project updates
        viewModelScope.launch {
            router.projectEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                if (type == "projects") {
                    val arr = msg["projects"]?.jsonArray ?: return@collect
                    val found = arr.map { json.decodeFromJsonElement<ProjectInfo>(it) }
                        .find { it.name == projectName }
                    if (found != null) {
                        _uiState.value = _uiState.value.copy(project = found)
                    }
                }
            }
        }

        refresh()
    }

    fun refresh() {
        client.send("projects")
        val payload = buildJsonObject { put("project", projectName) }
        client.send("terminals", payload)
    }

    fun openNewTerminal() {
        val payload = buildJsonObject { put("project", projectName) }
        client.send("newTerminal", payload)
    }

    fun toggleService(serviceName: String) {
        val payload = buildJsonObject {
            put("name", projectName)
            put("service", serviceName)
        }
        client.send("toggleService", payload)
    }

    fun runAction(actionKey: String) {
        val payload = buildJsonObject {
            put("project", projectName)
            put("action", actionKey)
        }
        client.send("runAction", payload)
    }
}
