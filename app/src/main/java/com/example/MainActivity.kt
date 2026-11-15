package com.example

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import com.example.ui.theme.FlashoraBg
import com.example.ui.theme.FlashoraCyan
import com.example.ui.theme.FlashoraTheme

class MainActivity : ComponentActivity() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingSaveBytes: ByteArray? = null
    private var webViewRef: WebView? = null
    private var isPageLoaded by mutableStateOf(false)
    private var isFullscreenMode by mutableStateOf(false)

    private val openFilesLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            val cb = filePathCallback
            filePathCallback = null
            if (cb != null) {
                if (uris.isNotEmpty()) {
                    cb.onReceiveValue(uris.toTypedArray())
                } else {
                    cb.onReceiveValue(null)
                }
            }
        }

    private val createDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val bytes = pendingSaveBytes
            pendingSaveBytes = null
            if (uri != null && bytes != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    Toast.makeText(this, getString(R.string.file_saved_success), Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(this, getString(R.string.file_save_error), Toast.LENGTH_SHORT).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            FlashoraTheme {
                BackHandler {
                    val wv = webViewRef
                    if (wv != null) {
                        wv.evaluateJavascript("(window.flashoraOnBack ? window.flashoraOnBack() : false)") { result ->
                            if (result != "true") {
                                finish()
                            }
                        }
                    } else {
                        finish()
                    }
                }

                FlashoraHostScreen(
                    isPageLoaded = isPageLoaded,
                    isFullscreen = isFullscreenMode,
                    onWebViewCreated = { webViewRef = it },
                    onPageFinished = { isPageLoaded = true },
                    onShowFileChooser = { cb ->
                        filePathCallback?.onReceiveValue(null)
                        filePathCallback = cb
                        openFilesLauncher.launch(arrayOf("*/*"))
                    },
                    bridge = FlashoraBridge(),
                )
            }
        }
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        webViewRef?.destroy()
        webViewRef = null
        super.onDestroy()
    }

    private fun updateSystemBars(fullscreen: Boolean) {
        isFullscreenMode = fullscreen
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    inner class FlashoraBridge {
        @JavascriptInterface
        fun saveFile(filename: String, mimeType: String, base64Data: String) {
            runOnUiThread {
                try {
                    pendingSaveBytes = Base64.decode(base64Data, Base64.DEFAULT)
                    createDocumentLauncher.launch(filename)
                } catch (_: Exception) {
                    pendingSaveBytes = null
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.file_save_error),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }

        @JavascriptInterface
        fun setOrientation(mode: String) {
            runOnUiThread {
                requestedOrientation = when (mode) {
                    "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }

        @JavascriptInterface
        fun setFullscreen(enabled: Boolean) {
            runOnUiThread {
                updateSystemBars(enabled)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FlashoraHostScreen(
    isPageLoaded: Boolean,
    isFullscreen: Boolean,
    onWebViewCreated: (WebView) -> Unit,
    onPageFinished: () -> Unit,
    onShowFileChooser: (ValueCallback<Array<Uri>>) -> Unit,
    bridge: MainActivity.FlashoraBridge,
) {
    val containerModifier = if (isFullscreen) {
        Modifier
            .fillMaxSize()
            .background(FlashoraBg)
    } else {
        Modifier
            .fillMaxSize()
            .background(FlashoraBg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    }

    Box(modifier = containerModifier) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("flashora_webview"),
            factory = { context ->
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .addPathHandler("/res/", WebViewAssetLoader.ResourcesPathHandler(context))
                    .build()

                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#0B0F1E"))
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        allowFileAccess = false
                        allowContentAccess = true
                    }
                    addJavascriptInterface(bridge, "AndroidBridge")
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            return assetLoader.shouldInterceptRequest(request.url)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            onPageFinished()
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?,
                        ): Boolean {
                            if (filePathCallback == null) return false
                            onShowFileChooser(filePathCallback)
                            return true
                        }
                    }
                    onWebViewCreated(this)
                    loadUrl("https://appassets.androidplatform.net/assets/flashora.html")
                }
            },
        )

        AnimatedVisibility(
            visible = !isPageLoaded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FlashoraBg)
                    .testTag("flashora_loading"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.flashora_icon),
                    contentDescription = stringResource(id = R.string.app_name),
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(24.dp)),
                )
                Spacer(modifier = Modifier.height(20.dp))
                CircularProgressIndicator(
                    color = FlashoraCyan,
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = stringResource(id = R.string.loading_flashora),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }
    }
}
