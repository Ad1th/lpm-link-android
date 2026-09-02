package cx.lpm.link.ui.automations

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
data class JobInfo(
    val id: String,
    val project: String? = null,
    val label: String? = null,
    val schedule: String? = null,
    val enabled: Boolean = true,
    val lastResult: String? = null,
    val nextFireAt: Long? = null,
    val running: Boolean = false,
)

data class AutomationsUiState(
    val jobs: List<JobInfo> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class AutomationsViewModel @Inject constructor(
    private val client: LpmClient,
    private val router: MessageRouter,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AutomationsUiState())
    val uiState: StateFlow<AutomationsUiState> = _uiState

    init {
        viewModelScope.launch {
            router.jobEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                if (type == "jobs") {
                    val arr = msg["jobs"]?.jsonArray ?: return@collect
                    val list = arr.map { json.decodeFromJsonElement<JobInfo>(it) }
                    _uiState.value = _uiState.value.copy(jobs = list, isLoading = false)
                } else if (type == "jobs-changed") {
                    refresh()
                }
            }
        }

        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        client.send("jobs")
    }

    fun runJob(project: String, jobId: String) {
        val payload = buildJsonObject {
            put("project", project)
            put("jobId", jobId)
        }
        client.send("runJob", payload)
    }

    fun stopJob(project: String, jobId: String) {
        val payload = buildJsonObject {
            put("project", project)
            put("jobId", jobId)
        }
        client.send("stopJob", payload)
    }

    fun toggleJobEnabled(project: String, jobId: String, enabled: Boolean) {
        val payload = buildJsonObject {
            put("project", project)
            put("jobId", jobId)
            put("enabled", enabled)
        }
        client.send("setJobEnabled", payload)
    }
}
