package cx.lpm.link.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Outbound messages (client -> server)

@Serializable
data class PairMsg(val code: String, val name: String, val replaces: String? = null)

@Serializable
data class PairRequestMsg(val name: String, val replaces: String? = null)

@Serializable
data class AuthMsg(val deviceId: String, val token: String)

@Serializable
class ProjectsRequestMsg

@Serializable
data class StartMsg(val name: String, val profile: String? = null)

@Serializable
data class StopMsg(val name: String)

@Serializable
data class ToggleServiceMsg(val name: String, val service: String)

@Serializable
data class TerminalsRequestMsg(val project: String)

@Serializable
data class SubMsg(val id: String, val from: Long? = null)

@Serializable
data class UnsubMsg(val id: String)

@Serializable
data class ClaimMsg(val id: String)

@Serializable
data class InputMsg(val id: String, val d: String)

@Serializable
data class ResizeMsg(val id: String, val cols: Int, val rows: Int)

@Serializable
data class StatusRequestMsg(val project: String)

@Serializable
data class ClearStatusMsg(val project: String, val paneId: String, val value: String)

@Serializable
data class GitRequestMsg(val project: String)

@Serializable
data class GitDiffMsg(val project: String, val path: String)

@Serializable
data class ApnsTokenMsg(
    val token: String,
    val env: String,
    val key: String,
    val notify: NotifyPrefs
)

@Serializable
data class NotifyPrefs(
    val waiting: Boolean = true,
    val done: Boolean = true,
    val error: Boolean = true,
    val automationStarted: Boolean = false,
    val automationDone: Boolean = true,
    val automationError: Boolean = true
)

fun buildOutbound(type: String, payload: JsonObject = buildJsonObject {}): String {
    val finalJson = buildJsonObject {
        put("t", type)
        payload.forEach { key, value ->
            put(key, value)
        }
    }
    return finalJson.toString()
}
