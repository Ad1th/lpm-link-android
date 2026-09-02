package cx.lpm.link.ui.terminal

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TerminalScreen(
    onNavigateBack: () -> Unit,
    viewModel: TerminalViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false

            val bridge = TerminalBridge(
                onInputReceived = { data -> viewModel.onTerminalInput(data) },
                onResizeReceived = { cols, rows -> viewModel.onTerminalResize(cols, rows) },
                onClaimRequested = { viewModel.claimControl() }
            )
            addJavascriptInterface(bridge, "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.postDelayed({
                        view.evaluateJavascript("window.lpmRevive?.();", null)
                        view.evaluateJavascript("window.lpmSetFontSize?.(13);", null)
                    }, 100)
                }
            }
            webChromeClient = WebChromeClient()

            loadUrl("file:///android_asset/web/terminal.html")
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Log.d("TerminalScreen", "Disposing WebView")
            webView.destroy()
        }
    }

    // Collect and dispatch commands to WebView
    LaunchedEffect(Unit) {
        viewModel.commands.collect { cmd ->
            when (cmd) {
                is TerminalCommand.Feed -> {
                    Log.d("TerminalScreen", "JS: lpmFeed called (${cmd.base64.length} bytes)")
                    webView.evaluateJavascript("window.lpmFeed?.('${cmd.base64}');", null)
                }
                is TerminalCommand.Seed -> {
                    Log.d("TerminalScreen", "JS: lpmSeed called (${cmd.base64.length} bytes)")
                    webView.evaluateJavascript("window.lpmSeed?.('${cmd.base64}');", null)
                }
                is TerminalCommand.Submit -> {
                    Log.d("TerminalScreen", "JS: lpmSubmit called")
                    webView.evaluateJavascript("window.lpmSubmit?.('${cmd.base64}');", null)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.projectName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = uiState.terminalId,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        webView.evaluateJavascript("window.term?.reset?.();", null)
                    }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Terminal")
                    }
                    IconButton(onClick = {
                        webView.evaluateJavascript("window.lpmRevive?.();", null)
                        webView.evaluateJavascript("window.refit?.();", null)
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Layout")
                    }
                }
            )
        }
    ) { padding ->
        LaunchedEffect(uiState) {
            Log.d("TerminalScreen", "UI State update: $uiState")
        }
        LaunchedEffect(padding) {
            Log.d("TerminalScreen", "Scaffold padding: $padding")
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
                .imePadding()
                .background(Color(0xFF2B2B2B)) // Match xterm dark background
        ) {
            Spacer(modifier = Modifier.height(padding.calculateTopPadding()))

            // "Take Control" Banner if another device owns the terminal
            if (!uiState.isOwner && uiState.owner != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PanTool,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Controlled by ${uiState.owner?.label ?: "desktop"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = { viewModel.claimControl() },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Take Control", fontSize = 12.sp)
                        }
                    }
                }
            }

            // xterm.js WebView container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1E1E1E)) // Dark grey background for the container
                    .border(1.dp, Color.Red.copy(alpha = 0.3f))
            ) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxSize()
                )

                if (!uiState.isReady) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2B2B2B)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Composer with keyboard helpers
            TerminalComposer(
                onSubmit = { prompt -> viewModel.submitPrompt(prompt) },
                onSpecialKey = { key -> viewModel.onTerminalInput(key) }
            )
        }
    }
}
