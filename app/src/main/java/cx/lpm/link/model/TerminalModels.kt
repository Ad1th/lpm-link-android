package cx.lpm.link.model

import kotlinx.serialization.Serializable

@Serializable
data class TerminalInfo(
    val id: String,
    val label: String? = null,
    val pinned: Boolean = false,
    val emoji: String? = null,
    val cli: String? = null
)

@Serializable
data class SeedData(
    val id: String,
    val cols: Int = 80,
    val rows: Int = 24,
    val data: String = "",
    val off: Long = 0,
    val reset: Boolean = false,
    val owner: ControlOwner? = null,
    val draft: DraftData? = null
)

@Serializable
data class ControlOwner(
    val kind: String,
    val id: String,
    val label: String? = null
)

@Serializable
data class DraftData(
    val text: String,
    val rev: Long = 0
)

@Serializable
data class OutputChunk(
    val id: String,
    val d: String,
    val off: Long = 0
)

@Serializable
data class SlashCommand(
    val name: String,
    val description: String? = null
)
