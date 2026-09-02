package cx.lpm.link.model

import kotlinx.serialization.Serializable

@Serializable
data class GitStatus(
    val project: String,
    val ok: Boolean = false,
    val isRepo: Boolean = false,
    val branch: String? = null,
    val detached: Boolean = false,
    val hasUpstream: Boolean = false,
    val ahead: Int = 0,
    val behind: Int = 0,
    val defaultBranch: String? = null,
    val ghCli: Boolean = false,
    val files: List<GitFile> = emptyList()
)

@Serializable
data class GitFile(
    val path: String,
    val status: String,
    val staged: Boolean = false,
    val stamp: Long = 0
)

@Serializable
data class GitDiff(
    val project: String,
    val path: String,
    val ok: Boolean = false,
    val diff: String? = null,
    val binary: Boolean = false,
    val truncated: Boolean = false,
    val error: String? = null
)

@Serializable
data class BranchInfo(
    val name: String,
    val current: Boolean = false,
    val remote: Boolean = false
)
