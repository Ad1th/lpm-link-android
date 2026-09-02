package cx.lpm.link.ui.terminal

import android.util.Log
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
    private val TAG = "TerminalBridge"

    @JavascriptInterface
    fun onInput(data: String) {
        Log.v(TAG, "onInput: ${data.length} chars")
        onInputReceived(data)
    }

    @JavascriptInterface
    fun onResize(geomJson: String) {
        Log.d(TAG, "onResize: $geomJson")
        try {
            val json = JSONObject(geomJson)
            val cols = json.optInt("cols", 80)
            val rows = json.optInt("rows", 24)
            onResizeReceived(cols, rows)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse resize JSON", e)
        }
    }

    @JavascriptInterface
    fun onClaim() {
        Log.d(TAG, "onClaim")
        onClaimRequested()
    }
}
