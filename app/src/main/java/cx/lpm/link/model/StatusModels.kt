package cx.lpm.link.model

import kotlinx.serialization.Serializable

@Serializable
data class StatusEntry(
    val key: String,
    val value: String, // "Running"|"Waiting"|"Done"|"Error"
    val icon: String? = null,
    val color: String? = null,
    val priority: Int = 0,
    val timestamp: Long = 0,
    val agentPID: Int? = null,
    val paneID: String? = null
)

@Serializable
data class PushPayload(
    val serverId: String,
    val project: String? = null,
    val target: String? = null,
    val terminal: String? = null,
    val terminalId: String? = null,
    val automationId: String? = null,
    val status: String? = null,
    val ts: Long = 0,
    val key: String? = null
)

@Serializable
data class ClearPayload(
    val serverId: String,
    val clear: List<ClearEntry> = emptyList()
)

@Serializable
data class ClearEntry(
    val project: String,
    val key: String
)
