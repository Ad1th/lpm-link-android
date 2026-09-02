package cx.lpm.link.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.link.model.ProjectInfo
import cx.lpm.link.model.StatusEntry
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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject

data class ProjectStatusGroup(
    val projectName: String,
    val statuses: List<StatusEntry>,
)

data class ActivityUiState(
    val groups: List<ProjectStatusGroup> = emptyList(),
    val totalRunning: Int = 0,
    val totalWaiting: Int = 0,
    val isLoading: Boolean = false,
)

@HiltViewModel
class ActivityViewModel @Inject constructor(
    private val client: LpmClient,
    private val router: MessageRouter,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState

    // Track known projects
    private val knownProjects = mutableSetOf<String>()

    init {
        // Collect project list to know which projects to fetch status for
        viewModelScope.launch {
            router.projectEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                if (type == "projects") {
                    val arr = msg["projects"]?.jsonArray ?: return@collect
                    val projects = arr.map { json.decodeFromJsonElement<ProjectInfo>(it) }
                    knownProjects.clear()
                    knownProjects.addAll(projects.map { it.name })
                    refreshAllStatuses()
                }
            }
        }

        // Collect status updates
        viewModelScope.launch {
            router.statusEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                val project = msg["project"]?.jsonPrimitive?.content ?: return@collect

                when (type) {
                    "status" -> {
                        val arr = msg["status"]?.jsonArray ?: return@collect
                        val entries = arr.map { json.decodeFromJsonElement<StatusEntry>(it) }
                        updateProjectStatus(project, entries)
                    }
                    "status-changed" -> {
                        fetchProjectStatus(project)
                    }
                }
            }
        }

        client.send("projects")
    }

    fun refreshAllStatuses() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        knownProjects.forEach { fetchProjectStatus(it) }
    }

    fun fetchProjectStatus(projectName: String) {
        val payload = buildJsonObject { put("project", projectName) }
        client.send("status", payload)
    }

    fun clearStatus(projectName: String, paneId: String, value: String) {
        val payload = buildJsonObject {
            put("project", projectName)
            put("paneId", paneId)
            put("value", value)
        }
        client.send("clearStatus", payload)
    }

    private fun updateProjectStatus(projectName: String, statuses: List<StatusEntry>) {
        val currentGroups = _uiState.value.groups.toMutableList()
        currentGroups.removeAll { it.projectName == projectName }

        if (statuses.isNotEmpty()) {
            currentGroups.add(ProjectStatusGroup(projectName, statuses))
        }

        val totalRunning = currentGroups.sumOf { g -> g.statuses.count { it.value == "Running" } }
        val totalWaiting = currentGroups.sumOf { g -> g.statuses.count { it.value == "Waiting" } }

        _uiState.value = _uiState.value.copy(
            groups = currentGroups,
            totalRunning = totalRunning,
            totalWaiting = totalWaiting,
            isLoading = false
        )
    }
}
