package cx.lpm.link.network

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes inbound WebSocket messages by their "t" discriminator field to
 * feature-specific flows. Each feature module subscribes to its own flow
 * rather than filtering the global message stream.
 */
@Singleton
class MessageRouter @Inject constructor(
    private val client: LpmClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Feature-specific flows
    private val _projectEvents = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val projectEvents: SharedFlow<JsonObject> = _projectEvents

    private val _terminalEvents = MutableSharedFlow<JsonObject>(extraBufferCapacity = 256)
    val terminalEvents: SharedFlow<JsonObject> = _terminalEvents

    private val _statusEvents = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val statusEvents: SharedFlow<JsonObject> = _statusEvents

    private val _gitEvents = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val gitEvents: SharedFlow<JsonObject> = _gitEvents

    private val _jobEvents = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val jobEvents: SharedFlow<JsonObject> = _jobEvents

    private val _historyEvents = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val historyEvents: SharedFlow<JsonObject> = _historyEvents

    private val _memoryEvents = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val memoryEvents: SharedFlow<JsonObject> = _memoryEvents

    private val _notesEvents = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val notesEvents: SharedFlow<JsonObject> = _notesEvents

    private val _broadcastEvents = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
    val broadcastEvents: SharedFlow<JsonObject> = _broadcastEvents

    private val _pairingEvents = MutableSharedFlow<JsonObject>(extraBufferCapacity = 16)
    val pairingEvents: SharedFlow<JsonObject> = _pairingEvents

    fun start() {
        scope.launch {
            client.messages.collect { msg ->
                route(msg)
            }
        }
    }

    private suspend fun route(msg: JsonObject) {
        val type = msg["t"]?.jsonPrimitive?.content ?: return

        when (type) {
            // Pairing
            "paired", "pairPending", "pairDenied", "error" ->
                _pairingEvents.emit(msg)

            // Projects
            "projects", "start", "stop", "toggleService",
            "sidebar", "sidebarCreateFolder", "sidebarRenameFolder",
            "sidebarDeleteFolder", "sidebarMoveProject",
            "duplicate", "duplicateProgress", "duplicateDefaults",
            "remove", "renameProject", "createProject",
            "createSshProject", "cloneProject" ->
                _projectEvents.emit(msg)

            // Terminals & PTY
            "terminals", "seed", "o", "exit", "control",
            "newTerminal", "closeTerminal", "renameTerminal",
            "pinTerminal", "reorderTerminals",
            "slash", "upload", "mentions",
            "composerDraft", "composerActions",
            "transform", "transformDone" ->
                _terminalEvents.emit(msg)

            // Agent status
            "status", "stats", "limits", "limits-changed" ->
                _statusEvents.emit(msg)

            // Git
            "git", "gitDiff", "gitDiffs",
            "gitCommit", "gitPush", "gitPull", "gitFetch",
            "gitBranches", "gitCheckout", "gitCreateBranch",
            "gitDiscardAll", "gitWatch", "gitUnwatch",
            "gitGenMessage", "gitGenPr", "gitCreatePr" ->
                _gitEvents.emit(msg)

            // Automations
            "jobs", "jobHistory", "jobLiveOutput",
            "runJob", "stopJob", "setJobEnabled",
            "markJobSeen", "markAllJobsSeen",
            "sendJobFollowup", "jobConfig", "saveJob", "deleteJob" ->
                _jobEvents.emit(msg)

            // History
            "history", "historyQuery", "historySaveDraft",
            "historyToggleFavorite", "historySetFolder",
            "historyDelete", "historyFolders",
            "historyCreateFolder", "historyDeleteFolder" ->
                _historyEvents.emit(msg)

            // Memory
            "memory", "memorySession", "memorySave", "memoryDelete" ->
                _memoryEvents.emit(msg)

            // Notes
            "notesChats", "notesCreateChat", "notesRenameChat",
            "notesDeleteChat", "notesMessages", "notesAddMessage",
            "notesEditMessage", "notesDeleteMessage",
            "notesSearch", "notesAttachment" ->
                _notesEvents.emit(msg)

            // Services & config
            "services", "serviceLogs",
            "readFile", "readConfig", "saveConfig",
            "serviceBody", "saveService", "deleteService",
            "actionBody", "saveAction", "deleteAction",
            "saveProfile", "deleteProfile",
            "listDirs", "listSshHosts",
            "ttsSpeak", "apnsToken",
            "backgroundRuns", "runActionBackground",
            "actionBgOutput", "cancelActionBackground",
            "runAction" ->
                _projectEvents.emit(msg)

            // Server broadcast events
            "projects-changed" -> _broadcastEvents.emit(msg)
            "status-changed" -> {
                _broadcastEvents.emit(msg)
                _statusEvents.emit(msg)
            }
            "jobs-changed" -> {
                _broadcastEvents.emit(msg)
                _jobEvents.emit(msg)
            }
            "git-changed" -> {
                _broadcastEvents.emit(msg)
                _gitEvents.emit(msg)
            }
            "memory-changed" -> {
                _broadcastEvents.emit(msg)
                _memoryEvents.emit(msg)
            }

            // Ready is handled by LpmClient directly, but also emit
            "ready" -> _broadcastEvents.emit(msg)
        }
    }
}
