package com.example

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewAssetLoader
import com.example.ui.theme.FlashoraBg
import com.example.ui.theme.FlashoraCyan
import com.example.ui.theme.FlashoraOrange
import com.example.ui.theme.FlashoraSurface
import com.example.ui.theme.FlashoraSurfaceElevated
import com.example.ui.theme.FlashoraTextSecondary
import com.example.ui.theme.FlashoraTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class MainActivity : ComponentActivity() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingSaveBytes: ByteArray? = null
    private var mainWebViewRef: WebView? = null
    private var siteWebViewRef: WebView? = null

    private var isPageLoaded by mutableStateOf(false)
    private var isFullscreenMode by mutableStateOf(false)
    private var activeBrowserUrl by mutableStateOf<String?>(null)
    private var browserCurrentUrl by mutableStateOf("")
    private var browserPageLoading by mutableStateOf(false)
    private var isDownloadingGame by mutableStateOf(false)
    private var detectedFlashUrls by mutableStateOf<List<String>>(emptyList())

    private val incomingGamesDir: File by lazy {
        File(cacheDir, "incoming_games").apply { mkdirs() }
    }

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
                    val siteWv = siteWebViewRef
                    if (activeBrowserUrl != null) {
                        if (siteWv != null && siteWv.canGoBack()) {
                            siteWv.goBack()
                        } else {
                            closeInAppBrowser()
                        }
                        return@BackHandler
                    }

                    val wv = mainWebViewRef
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

                Box(modifier = Modifier.fillMaxSize()) {
                    FlashoraHostScreen(
                        isPageLoaded = isPageLoaded,
                        isFullscreen = isFullscreenMode,
                        incomingGamesDir = incomingGamesDir,
                        onWebViewCreated = { mainWebViewRef = it },
                        onPageFinished = { isPageLoaded = true },
                        onShowFileChooser = { cb ->
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = cb
                            openFilesLauncher.launch(arrayOf("*/*"))
                        },
                        bridge = FlashoraBridge(),
                    )

                    AnimatedVisibility(
                        visible = activeBrowserUrl != null,
                        enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(),
                    ) {
                        activeBrowserUrl?.let { startUrl ->
                            InAppFlashBrowserOverlay(
                                initialUrl = startUrl,
                                currentUrl = browserCurrentUrl.ifBlank { startUrl },
                                isPageLoading = browserPageLoading,
                                isDownloadingGame = isDownloadingGame,
                                detectedUrls = detectedFlashUrls,
                                onDismissDetected = { detectedFlashUrls = emptyList() },
                                onWebViewCreated = { siteWebViewRef = it },
                                onUrlChanged = { browserCurrentUrl = it },
                                onLoadingChanged = { browserPageLoading = it },
                                onClose = { closeInAppBrowser() },
                                onSniffSwf = { sniffCurrentPageForFlash() },
                                onPickDetectedUrl = { pickedUrl ->
                                    detectedFlashUrls = emptyList()
                                    val guess = URLUtil.guessFileName(pickedUrl, null, null)
                                    downloadAndDeliverToFlashora(pickedUrl, guess)
                                },
                                onDownloadIntercepted = { url, contentDisposition, mimeType ->
                                    val guess = URLUtil.guessFileName(url, contentDisposition, mimeType)
                                    downloadAndDeliverToFlashora(url, guess)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        siteWebViewRef?.destroy()
        siteWebViewRef = null
        mainWebViewRef?.destroy()
        mainWebViewRef = null
        super.onDestroy()
    }

    private fun closeInAppBrowser() {
        detectedFlashUrls = emptyList()
        siteWebViewRef?.stopLoading()
        siteWebViewRef?.destroy()
        siteWebViewRef = null
        activeBrowserUrl = null
        browserCurrentUrl = ""
    }

    private fun sniffCurrentPageForFlash() {
        val wv = siteWebViewRef ?: return
        val script = """
            (function() {
              var found = [];
              function add(u) {
                if (!u || typeof u !== 'string') return;
                try {
                  var abs = new URL(u, location.href).href;
                  if (/\.(swf|exe)(\?|$|#)/i.test(abs) && found.indexOf(abs) === -1) {
                    found.push(abs);
                  }
                } catch(e) {}
              }
              document.querySelectorAll('a[href], embed[src], object[data], iframe[src], source[src]').forEach(function(el) {
                add(el.getAttribute('href') || el.getAttribute('src') || el.getAttribute('data'));
              });
              document.querySelectorAll('param[name="movie"], param[name="src"]').forEach(function(el) {
                add(el.getAttribute('value'));
              });
              if (performance && performance.getEntriesByType) {
                performance.getEntriesByType('resource').forEach(function(r) { add(r.name); });
              }
              return JSON.stringify(found);
            })();
        """.trimIndent()

        wv.evaluateJavascript(script) { raw ->
            try {
                val decoded = JSONTokenerCompat.parseStringArray(raw)
                if (decoded.isEmpty()) {
                    Toast.makeText(
                        this,
                        getString(R.string.browser_no_swf_found),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else if (decoded.size == 1) {
                    val onlyUrl = decoded.first()
                    val guess = URLUtil.guessFileName(onlyUrl, null, null)
                    downloadAndDeliverToFlashora(onlyUrl, guess)
                } else {
                    detectedFlashUrls = decoded
                }
            } catch (_: Exception) {
                Toast.makeText(this, getString(R.string.browser_no_swf_found), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun downloadAndDeliverToFlashora(rawUrl: String, suggestedName: String) {
        if (isDownloadingGame) return
        isDownloadingGame = true

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    // Clean up older temporary incoming files to keep cache tidy
                    incomingGamesDir.listFiles()?.forEach { old ->
                        if (System.currentTimeMillis() - old.lastModified() > 5 * 60 * 1000L) {
                            old.delete()
                        }
                    }

                    var currentUrl = rawUrl
                    var conn: HttpURLConnection? = null
                    var redirects = 0
                    while (redirects < 6) {
                        val urlObj = URL(currentUrl)
                        conn = (urlObj.openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = false
                            connectTimeout = 20000
                            readTimeout = 30000
                            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) Flashora/1.0")
                            val cookies = CookieManager.getInstance().getCookie(currentUrl)
                            if (!cookies.isNullOrBlank()) {
                                setRequestProperty("Cookie", cookies)
                            }
                        }
                        val code = conn.responseCode
                        if (code in 300..399) {
                            val loc = conn.getHeaderField("Location") ?: break
                            currentUrl = URL(urlObj, loc).toString()
                            conn.disconnect()
                            redirects++
                        } else {
                            break
                        }
                    }

                    val activeConn = conn ?: error("Connection failed")
                    try {
                        val code = activeConn.responseCode
                        if (code !in 200..299) {
                            error("HTTP $code")
                        }

                        val disp = activeConn.getHeaderField("Content-Disposition")
                        val mime = activeConn.contentType
                        var finalName = if (suggestedName.endsWith(".swf", true) || suggestedName.endsWith(".exe", true)) {
                            suggestedName
                        } else {
                            URLUtil.guessFileName(currentUrl, disp, mime)
                        }
                        if (!finalName.endsWith(".swf", true) && !finalName.endsWith(".exe", true)) {
                            val pathPart = Uri.parse(currentUrl).lastPathSegment
                            finalName = if (!pathPart.isNullOrBlank() && (pathPart.endsWith(".swf", true) || pathPart.endsWith(".exe", true))) {
                                pathPart
                            } else {
                                "${finalName.substringBeforeLast('.')}.swf"
                            }
                        }

                        val token = "${UUID.randomUUID()}_${finalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                        val outFile = File(incomingGamesDir, token)
                        activeConn.inputStream.use { input ->
                            FileOutputStream(outFile).use { output ->
                                input.copyTo(output, bufferSize = 16384)
                            }
                        }
                        Pair(finalName, "https://appassets.androidplatform.net/cache-games/$token")
                    } finally {
                        activeConn.disconnect()
                    }
                }
            }

            isDownloadingGame = false
            result.onSuccess { (finalName, internalUrl) ->
                closeInAppBrowser()
                val js = "window.onNativeGameReady && window.onNativeGameReady(${JSONObject.quote(finalName)}, ${JSONObject.quote(internalUrl)});"
                mainWebViewRef?.evaluateJavascript(js, null)
            }.onFailure { err ->
                val msg = err.message ?: getString(R.string.browser_download_failed)
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                val js = "window.onNativeGameError && window.onNativeGameError(${JSONObject.quote(msg)});"
                mainWebViewRef?.evaluateJavascript(js, null)
            }
        }
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

        @JavascriptInterface
        fun openInAppBrowser(url: String) {
            runOnUiThread {
                browserCurrentUrl = url
                detectedFlashUrls = emptyList()
                activeBrowserUrl = url
            }
        }

        @JavascriptInterface
        fun downloadAndPlayUrl(url: String, suggestedName: String) {
            runOnUiThread {
                downloadAndDeliverToFlashora(url, suggestedName)
            }
        }
    }
}

private object JSONTokenerCompat {
    fun parseStringArray(evaluateJsResult: String?): List<String> {
        if (evaluateJsResult.isNullOrBlank() || evaluateJsResult == "null") return emptyList()
        val unescaped = if (evaluateJsResult.startsWith("\"")) {
            JSONObject("{\"v\":$evaluateJsResult}").getString("v")
        } else {
            evaluateJsResult
        }
        val arr = JSONArray(unescaped)
        return List(arr.length()) { idx -> arr.getString(idx) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppFlashBrowserOverlay(
    initialUrl: String,
    currentUrl: String,
    isPageLoading: Boolean,
    isDownloadingGame: Boolean,
    detectedUrls: List<String>,
    onDismissDetected: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    onSniffSwf: () -> Unit,
    onPickDetectedUrl: (String) -> Unit,
    onDownloadIntercepted: (String, String?, String?) -> Unit,
) {
    var localWebView by remember { mutableStateOf<WebView?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FlashoraBg)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Neon Glass Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FlashoraSurface)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("browser_close_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.browser_close),
                        tint = Color.White,
                    )
                }

                IconButton(
                    onClick = { localWebView?.let { if (it.canGoBack()) it.goBack() } },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.browser_back),
                        tint = Color.White,
                    )
                }

                IconButton(
                    onClick = { localWebView?.let { if (it.canGoForward()) it.goForward() } },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.browser_forward),
                        tint = Color.White,
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(FlashoraSurfaceElevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = Uri.parse(currentUrl).host ?: currentUrl,
                        color = FlashoraCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = currentUrl,
                        color = FlashoraTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = { localWebView?.reload() },
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.browser_reload),
                        tint = Color.White,
                    )
                }

                Button(
                    onClick = onSniffSwf,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FlashoraOrange,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("browser_sniff_btn"),
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.browser_sniff_swf),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (isPageLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = FlashoraCyan,
                    trackColor = FlashoraSurface,
                )
            }

            AndroidView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("in_app_browser_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                onLoadingChanged(true)
                                if (url != null) onUrlChanged(url)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                onLoadingChanged(false)
                                if (url != null) onUrlChanged(url)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val target = request?.url?.toString() ?: return false
                                val path = request.url.path?.lowercase() ?: ""
                                if (path.endsWith(".swf") || path.endsWith(".exe")) {
                                    onDownloadIntercepted(target, null, null)
                                    return true
                                }
                                return false
                            }
                        }
                        setDownloadListener { url, _, contentDisposition, mimeType, _ ->
                            onDownloadIntercepted(url, contentDisposition, mimeType)
                        }
                        localWebView = this
                        onWebViewCreated(this)
                        loadUrl(initialUrl)
                    }
                },
            )
        }

        if (isDownloadingGame) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(FlashoraBg.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = FlashoraSurface,
                    modifier = Modifier
                        .padding(24.dp)
                        .border(1.dp, FlashoraCyan.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = FlashoraCyan)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = stringResource(R.string.browser_downloading_game),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        if (detectedUrls.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = onDismissDetected,
                containerColor = FlashoraSurface,
                title = {
                    Text(
                        text = stringResource(R.string.browser_detected_files),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                },
                text = {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(detectedUrls) { url ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(FlashoraSurfaceElevated)
                                    .clickable { onPickDetectedUrl(url) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Download,
                                    contentDescription = null,
                                    tint = FlashoraCyan,
                                )
                                Text(
                                    text = Uri.parse(url).lastPathSegment ?: url,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismissDetected) {
                        Text(text = stringResource(R.string.cancel), color = FlashoraCyan)
                    }
                },
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FlashoraHostScreen(
    isPageLoaded: Boolean,
    isFullscreen: Boolean,
    incomingGamesDir: File,
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
                    .addPathHandler(
                        "/cache-games/",
                        WebViewAssetLoader.InternalStoragePathHandler(context, incomingGamesDir),
                    )
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
