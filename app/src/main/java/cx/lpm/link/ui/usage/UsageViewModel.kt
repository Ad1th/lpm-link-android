package cx.lpm.link.ui.usage

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
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import javax.inject.Inject

@Serializable
data class UsageMeter(
    val percentage: Float = 0f,
    val resetAt: Long? = null,
    val label: String? = null,
)

data class UsageUiState(
    val claudeFiveHour: Float = 0f,
    val claudeWeekly: Float = 0f,
    val claudeResetFiveHour: Long? = null,
    val claudeResetWeekly: Long? = null,
    val claudeEnabled: Boolean = true,
    val totalInputTokens: Long = 0L,
    val totalOutputTokens: Long = 0L,
    val totalCacheReadTokens: Long = 0L,
    val isLoading: Boolean = false,
)

@HiltViewModel
class UsageViewModel @Inject constructor(
    private val client: LpmClient,
    private val router: MessageRouter,
    private val json: Json,
) : ViewModel() {

    private val _uiState = MutableStateFlow(UsageUiState())
    val uiState: StateFlow<UsageUiState> = _uiState

    init {
        viewModelScope.launch {
            router.statusEvents.collect { msg ->
                val type = msg["t"]?.jsonPrimitive?.content
                when (type) {
                    "limits", "limits-changed" -> handleLimits(msg)
                    "stats" -> handleStats(msg)
                }
            }
        }

        refresh()
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isLoading = true)
        client.send("limits")
        client.send("stats")
    }

    private fun handleLimits(msg: kotlinx.serialization.json.JsonObject) {
        val claudeEnabled = msg["claudeEnabled"]?.jsonPrimitive?.booleanOrNull ?: true
        val limitsObj = msg["limits"]?.jsonObject

        var fiveHour = 0f
        var weekly = 0f
        var resetFiveHour: Long? = null
        var resetWeekly: Long? = null

        if (limitsObj != null) {
            val claude = limitsObj["claude"]?.jsonObject
            if (claude != null) {
                fiveHour = claude["fiveHour"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                weekly = claude["weekly"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                resetFiveHour = claude["fiveHourReset"]?.jsonPrimitive?.longOrNull
                resetWeekly = claude["weeklyReset"]?.jsonPrimitive?.longOrNull
            }
        }

        _uiState.value = _uiState.value.copy(
            claudeFiveHour = fiveHour,
            claudeWeekly = weekly,
            claudeResetFiveHour = resetFiveHour,
            claudeResetWeekly = resetWeekly,
            claudeEnabled = claudeEnabled,
            isLoading = false
        )
    }

    private fun handleStats(msg: kotlinx.serialization.json.JsonObject) {
        val statsObj = msg["stats"]?.jsonObject ?: return
        val input = statsObj["inputTokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val output = statsObj["outputTokens"]?.jsonPrimitive?.longOrNull ?: 0L
        val cache = statsObj["cacheReadTokens"]?.jsonPrimitive?.longOrNull ?: 0L

        _uiState.value = _uiState.value.copy(
            totalInputTokens = input,
            totalOutputTokens = output,
            totalCacheReadTokens = cache
        )
    }
}
