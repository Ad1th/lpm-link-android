package cx.lpm.link.ui.terminal

import android.webkit.JavascriptInterface
import org.json.JSONObject

/**
 * JavaScript interface bound to window.AndroidBridge in terminal.html.
 */
class TerminalBridge(
    private val onInputReceived: (String) -> Unit,
    private val onResizeReceived: (cols: Int, rows: Int) -> Unit,
    private val onClaimRequested: () -> Unit,
) {
    @JavascriptInterface
    fun onInput(data: String) {
        onInputReceived(data)
    }

    @JavascriptInterface
    fun onResize(geomJson: String) {
        try {
            val json = JSONObject(geomJson)
            val cols = json.optInt("cols", 80)
            val rows = json.optInt("rows", 24)
            onResizeReceived(cols, rows)
        } catch (_: Exception) {}
    }

    @JavascriptInterface
    fun onClaim() {
        onClaimRequested()
    }
}
