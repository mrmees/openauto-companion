package org.openauto.companion.ui

import android.annotation.SuppressLint
import android.net.ConnectivityManager
import android.net.Network
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.openauto.companion.net.AndroidProcessNetworkBinder
import org.openauto.companion.net.ProcessNetworkBinding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebConfigScreen(
    vehicleName: String,
    url: String,
    wifiNetwork: Network?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val connectivityManager = remember(context) {
        context.getSystemService(ConnectivityManager::class.java)
    }
    val processNetworkBinding = remember(connectivityManager) {
        ProcessNetworkBinding(AndroidProcessNetworkBinder(connectivityManager))
    }

    var bindingWarning by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(processNetworkBinding, wifiNetwork) {
        val result = processNetworkBinding.bindForScope(wifiNetwork)
        bindingWarning = WebConfigBindingWarning.messageFor(result)
        onDispose {
            val scopedBinding = (result as? ProcessNetworkBinding.Result.Bound)?.binding
            try {
                scopedBinding?.restore()
            } catch (_: RuntimeException) {
                // Nothing useful can be shown after the screen has left composition.
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("$vehicleName Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            bindingWarning?.let { warning ->
                InlineMessage(text = warning)
            }
            loadError?.let { error ->
                InlineMessage(text = error)
            }
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Box(modifier = Modifier.fillMaxSize()) {
                WebConfigView(
                    url = url,
                    onLoadingChanged = { loading = it },
                    onMainFrameError = { loadError = it }
                )
            }
        }
    }
}

object WebConfigBindingWarning {
    fun messageFor(result: ProcessNetworkBinding.Result): String? = when (result) {
        is ProcessNetworkBinding.Result.Bound -> null
        ProcessNetworkBinding.Result.NoNetwork ->
            "Head-unit Wi-Fi route is not available. Android may route this page over cellular."
        is ProcessNetworkBinding.Result.Failed ->
            "Could not bind web config to head-unit Wi-Fi. Android may route this page over cellular."
    }
}

@Composable
private fun InlineMessage(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebConfigView(
    url: String,
    onLoadingChanged: (Boolean) -> Unit,
    onMainFrameError: (String) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(
                        view: WebView?,
                        url: String?,
                        favicon: android.graphics.Bitmap?
                    ) {
                        onLoadingChanged(true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoadingChanged(false)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (request?.isForMainFrame != true) return
                        onLoadingChanged(false)
                        val failingUrl = request.url?.toString() ?: url
                        val description = error?.description?.toString() ?: "unknown error"
                        onMainFrameError("Could not load $failingUrl: $description")
                    }
                }
            }
        },
        update = { webView ->
            if (webView.getTag(WEB_CONFIG_URL_TAG) != url) {
                webView.setTag(WEB_CONFIG_URL_TAG, url)
                onLoadingChanged(true)
                webView.loadUrl(url)
            }
        }
    )
}

private const val WEB_CONFIG_URL_TAG = 0x77ebc0f
