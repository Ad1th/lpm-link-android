package cx.lpm.link.ui.projects

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.lpm.link.model.ProjectFolder
import cx.lpm.link.model.ProjectInfo
import cx.lpm.link.model.SidebarData
import cx.lpm.link.network.ConnectionState
import cx.lpm.link.network.LpmClient
import cx.lpm.link.network.MessageRouter
import cx.lpm.link.security.CertPinStore
import cx.lpm.link.security.CredentialStore
import cx.lpm.link.security.MacServer
import cx.lpm.link.security.TlsPinningFactory
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

data class ProjectsUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val currentServer: MacServer? = null,
    val projects: List<ProjectInfo> = emptyList(),
    val sidebar: SidebarData? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val client: LpmClient,
    private val router: MessageRouter,
    private val credentialStore: CredentialStore,
    private val certPinStore: CertPinStore,
    private val json: Json,
) : ViewModel() {

    companion object {
        private const val TAG = "ProjectsVM"
    }

    private val _uiState = MutableStateFlow(ProjectsUiState())
    val uiState: StateFlow<ProjectsUiState> = _uiState

    init {
        // Collect connection state
        viewModelScope.launch {
            client.state.collect { state ->
                _uiState.value = _uiState.value.copy(connectionState = state)
                if (state == ConnectionState.CONNECTED) {
                    refresh()
                }
            }
        }

        // Collect project events from WebSocket
        viewModelScope.launch {
            router.projectEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                when (type) {
                    "projects" -> handleProjectsResponse(msg)
                    "sidebar" -> handleSidebarResponse(msg)
                }
            }
        }

        // Collect server-push broadcasts
        viewModelScope.launch {
            router.broadcastEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                if (type == "projects-changed" || type == "ready") {
                    refresh()
                }
            }
        }

        router.start()
        autoConnect()
    }

    /**
     * Connect to the active paired Mac server if not connected.
     */
    fun autoConnect() {
        val activeId = credentialStore.getActiveServerId() ?: return
        val server = credentialStore.getServer(activeId) ?: return
        val credential = credentialStore.getCredential(activeId) ?: return

        _uiState.value = _uiState.value.copy(currentServer = server)

        if (client.state.value == ConnectionState.DISCONNECTED) {
            val (sslFactory, trustManager) = TlsPinningFactory.create(server.certFingerprint)
            client.configure(
                hosts = server.hosts,
                port = server.port,
                sslSocketFactory = sslFactory,
                trustManager = trustManager,
                deviceId = credential.deviceId,
                token = credential.token,
            )
            client.connect()
        }
    }

    /**
     * Request fresh project list and sidebar structure.
     */
    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        client.send("projects")
        client.send("sidebar")
    }

    /**
     * Start all services in a project (or a specific profile).
     */
    fun startProject(projectName: String, profile: String? = null) {
        val payload = buildJsonObject {
            put("name", projectName)
            profile?.let { put("profile", it) }
        }
        client.send("start", payload)
    }

    /**
     * Stop all services in a project.
     */
    fun stopProject(projectName: String) {
        val payload = buildJsonObject {
            put("name", projectName)
        }
        client.send("stop", payload)
    }

    /**
     * Toggle a single service in a project.
     */
    fun toggleService(projectName: String, serviceName: String) {
        val payload = buildJsonObject {
            put("name", projectName)
            put("service", serviceName)
        }
        client.send("toggleService", payload)
    }

    private fun handleProjectsResponse(msg: kotlinx.serialization.json.JsonObject) {
        try {
            val projectsArray = msg["projects"]?.jsonArray ?: return
            val list = projectsArray.map { element ->
                json.decodeFromJsonElement<ProjectInfo>(element)
            }
            _uiState.value = _uiState.value.copy(
                projects = list,
                isLoading = false,
                error = null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode projects", e)
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }
    }

    private fun handleSidebarResponse(msg: kotlinx.serialization.json.JsonObject) {
        try {
            val sidebar = json.decodeFromJsonElement<SidebarData>(msg)
            _uiState.value = _uiState.value.copy(sidebar = sidebar)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode sidebar", e)
        }
    }
}
