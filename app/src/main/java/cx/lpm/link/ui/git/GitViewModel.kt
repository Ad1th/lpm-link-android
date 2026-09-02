package cx.lpm.link.ui.git

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.link.model.GitDiff
import cx.lpm.link.model.GitFile
import cx.lpm.link.model.GitStatus
import cx.lpm.link.network.LpmClient
import cx.lpm.link.network.MessageRouter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

data class GitUiState(
    val projectName: String = "",
    val gitStatus: GitStatus? = null,
    val selectedDiff: GitDiff? = null,
    val isGeneratingMessage: Boolean = false,
    val generatedCommitMessage: String? = null,
    val isOperating: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class GitViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val client: LpmClient,
    private val router: MessageRouter,
    private val json: Json,
) : ViewModel() {

    val projectName: String = checkNotNull(savedStateHandle["name"])

    private val _uiState = MutableStateFlow(GitUiState(projectName = projectName))
    val uiState: StateFlow<GitUiState> = _uiState

    init {
        viewModelScope.launch {
            router.gitEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                when (type) {
                    "git" -> {
                        try {
                            val status = json.decodeFromJsonElement<GitStatus>(msg)
                            _uiState.value = _uiState.value.copy(gitStatus = status, isOperating = false)
                        } catch (_: Exception) {}
                    }
                    "gitDiff" -> {
                        try {
                            val diff = json.decodeFromJsonElement<GitDiff>(msg)
                            _uiState.value = _uiState.value.copy(selectedDiff = diff)
                        } catch (_: Exception) {}
                    }
                    "gitGenMessage" -> {
                        val message = msg["message"]?.jsonPrimitive?.content
                        _uiState.value = _uiState.value.copy(
                            generatedCommitMessage = message,
                            isGeneratingMessage = false
                        )
                    }
                    "gitCommit", "gitPush", "gitPull" -> {
                        refresh()
                    }
                }
            }
        }

        refresh()
    }

    fun refresh() {
        val payload = buildJsonObject { put("project", projectName) }
        client.send("git", payload)
    }

    fun viewDiff(path: String) {
        val payload = buildJsonObject {
            put("project", projectName)
            put("path", path)
        }
        client.send("gitDiff", payload)
    }

    fun closeDiff() {
        _uiState.value = _uiState.value.copy(selectedDiff = null)
    }

    fun generateCommitMessage(files: List<String>) {
        _uiState.value = _uiState.value.copy(isGeneratingMessage = true)
        val payload = buildJsonObject {
            put("project", projectName)
            put("files", buildJsonArray { files.forEach { add(it) } })
        }
        client.send("gitGenMessage", payload)
    }

    fun commit(message: String, files: List<String>) {
        _uiState.value = _uiState.value.copy(isOperating = true)
        val payload = buildJsonObject {
            put("project", projectName)
            put("message", message)
            put("files", buildJsonArray { files.forEach { add(it) } })
        }
        client.send("gitCommit", payload)
    }

    fun push() {
        _uiState.value = _uiState.value.copy(isOperating = true)
        val payload = buildJsonObject { put("project", projectName) }
        client.send("gitPush", payload)
    }

    fun pull() {
        _uiState.value = _uiState.value.copy(isOperating = true)
        val payload = buildJsonObject { put("project", projectName) }
        client.send("gitPull", payload)
    }
}
