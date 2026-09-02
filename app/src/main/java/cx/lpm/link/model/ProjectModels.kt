package cx.lpm.link.model

import kotlinx.serialization.Serializable

@Serializable
data class ProjectInfo(
    val name: String,
    val label: String? = null,
    val root: String? = null,
    val running: Boolean = false,
    val parent: String? = null,
    val worktree: String? = null,
    val services: Map<String, ServiceInfo> = emptyMap(),
    val profiles: List<String> = emptyList(),
    val actions: Map<String, ActionInfo> = emptyMap(),
    val ssh: SshInfo? = null
)

@Serializable
data class ServiceInfo(
    val port: Int = 0,
    val running: Boolean = false
)

@Serializable
data class ActionInfo(
    val key: String,
    val label: String? = null,
    val emoji: String? = null,
    val confirm: Boolean = false,
    val display: Boolean = false,
    val primary: Boolean = false,
    val type: String? = null
)

@Serializable
data class SshInfo(
    val host: String? = null,
    val user: String? = null,
    val port: Int? = null,
    val key: String? = null,
    val dir: String? = null
)

@Serializable
data class SidebarData(
    val order: List<String> = emptyList(),
    val groups: List<ProjectFolder> = emptyList()
)

@Serializable
data class ProjectFolder(
    val name: String,
    val projects: List<String> = emptyList()
)
