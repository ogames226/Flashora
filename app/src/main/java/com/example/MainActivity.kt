package com.example

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Base64
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import kotlin.math.abs
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
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
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

class MainActivity : ComponentActivity() {

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null
    private var pendingSaveBytes: ByteArray? = null
    private var mainWebViewRef: WebView? = null
    private var siteWebViewRef: WebView? = null

    private var isPageLoaded by mutableStateOf(false)
    private var isFullscreenMode by mutableStateOf(false)
    private var isPlayerActive by mutableStateOf(false)
    private var isControlTesterActive by mutableStateOf(false)
    private var activeBrowserUrl by mutableStateOf<String?>(null)
    private var browserCurrentUrl by mutableStateOf("")
    private var browserPageLoading by mutableStateOf(false)
    private var isDownloadingGame by mutableStateOf(false)
    private var detectedFlashUrls by mutableStateOf<List<String>>(emptyList())
    private var capturedPageFlashUrls by mutableStateOf<List<String>>(emptyList())

    private data class NativePadState(
        val deviceId: Int,
        val name: String,
        val keys: MutableMap<String, Boolean> = mutableMapOf(),
        var lx: Float = 0f,
        var ly: Float = 0f,
        var rx: Float = 0f,
        var ry: Float = 0f,
        var l2: Float = 0f,
        var r2: Float = 0f,
        var hatX: Float = 0f,
        var hatY: Float = 0f,
    )

    private var inputManager: InputManager? = null
    private val nativeGamepads = java.util.concurrent.ConcurrentHashMap<Int, NativePadState>()
    private var lastSentGamepadPayload = ""

    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = notifyJsConnectedGamepads()
        override fun onInputDeviceRemoved(deviceId: Int) {
            nativeGamepads.remove(deviceId)
            notifyJsConnectedGamepads()
            pushNativeGamepadState(force = true)
        }
        override fun onInputDeviceChanged(deviceId: Int) = notifyJsConnectedGamepads()
    }

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

    private val photoPickerLauncher =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val cb = filePathCallback
            filePathCallback = null
            if (cb != null) {
                if (uri != null) {
                    cb.onReceiveValue(arrayOf(uri))
                } else {
                    cb.onReceiveValue(null)
                }
            }
        }

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            val cb = filePathCallback
            val cameraUri = pendingCameraUri
            filePathCallback = null
            pendingCameraUri = null
            if (cb != null) {
                if (success && cameraUri != null) {
                    cb.onReceiveValue(arrayOf(cameraUri))
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
        inputManager = (getSystemService(Context.INPUT_SERVICE) as? InputManager)?.also { mgr ->
            mgr.registerInputDeviceListener(inputDeviceListener, Handler(Looper.getMainLooper()))
        }

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
                        onPageFinished = {
                            isPageLoaded = true
                            notifyJsConnectedGamepads()
                        },
                        onShowFileChooser = { cb, params ->
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = cb
                            pendingCameraUri = null
                            val acceptsImage = params?.acceptTypes?.any {
                                it.contains("image", ignoreCase = true)
                            } == true
                            when {
                                params?.isCaptureEnabled == true && acceptsImage -> {
                                    try {
                                        val camDir = File(cacheDir, "camera").apply { mkdirs() }
                                        camDir.listFiles()?.forEach { old ->
                                            if (System.currentTimeMillis() - old.lastModified() > 60 * 60 * 1000L) {
                                                old.delete()
                                            }
                                        }
                                        val outFile = File(camDir, "cover_${UUID.randomUUID()}.jpg")
                                        val photoUri = FileProvider.getUriForFile(
                                            this@MainActivity,
                                            "$packageName.fileprovider",
                                            outFile,
                                        )
                                        pendingCameraUri = photoUri
                                        takePictureLauncher.launch(photoUri)
                                    } catch (_: Exception) {
                                        pendingCameraUri = null
                                        filePathCallback?.onReceiveValue(null)
                                        filePathCallback = null
                                        Toast.makeText(
                                            this@MainActivity,
                                            getString(R.string.file_save_error),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                                acceptsImage -> {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                }
                                else -> {
                                    openFilesLauncher.launch(arrayOf("*/*"))
                                }
                            }
                        },
                        bridge = FlashoraBridge(),
                    )

                    AnimatedVisibility(
                        visible = activeBrowserUrl != null,
                        enter = slideInVertically(initialOffsetY = { it / 6 }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it / 6 }) + fadeOut(),
                    ) {
                        activeBrowserUrl?.let { startUrl ->
                            val referer = browserCurrentUrl.ifBlank { startUrl }
                            InAppFlashBrowserOverlay(
                                initialUrl = startUrl,
                                currentUrl = referer,
                                isPageLoading = browserPageLoading,
                                isDownloadingGame = isDownloadingGame,
                                detectedUrls = detectedFlashUrls,
                                capturedCount = capturedPageFlashUrls.size,
                                onDismissDetected = { detectedFlashUrls = emptyList() },
                                onWebViewCreated = { siteWebViewRef = it },
                                onUrlChanged = { browserCurrentUrl = it },
                                onPageNavigationStarted = { capturedPageFlashUrls = emptyList() },
                                onPassiveFlashUrlCaptured = { assetUrl ->
                                    runOnUiThread {
                                        if (assetUrl !in capturedPageFlashUrls && capturedPageFlashUrls.size < 40) {
                                            capturedPageFlashUrls = capturedPageFlashUrls + assetUrl
                                        }
                                    }
                                },
                                onLoadingChanged = { browserPageLoading = it },
                                onClose = { closeInAppBrowser() },
                                onSniffSwf = { sniffCurrentPageForFlash() },
                                onPickDetectedUrl = { pickedUrl ->
                                    detectedFlashUrls = emptyList()
                                    val guess = URLUtil.guessFileName(pickedUrl, null, null)
                                    downloadAndDeliverToFlashora(pickedUrl, guess, referer)
                                },
                                onDownloadIntercepted = { url, contentDisposition, mimeType ->
                                    val guess = URLUtil.guessFileName(url, contentDisposition, mimeType)
                                    downloadAndDeliverToFlashora(url, guess, referer)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isFullscreenMode) {
            updateSystemBars(true)
        }
    }

    override fun onDestroy() {
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        siteWebViewRef?.stopLoading()
        siteWebViewRef?.destroy()
        siteWebViewRef = null
        mainWebViewRef?.stopLoading()
        mainWebViewRef?.destroy()
        mainWebViewRef = null
        super.onDestroy()
    }

    private fun isGamepadDevice(device: InputDevice?): Boolean {
        if (device == null) return false
        val src = device.sources
        val hasGamepad = (src and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        val hasJoystick = (src and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        return hasGamepad || hasJoystick
    }

    private fun listConnectedControllers(): List<JSONObject> {
        val list = mutableListOf<JSONObject>()
        for (id in InputDevice.getDeviceIds()) {
            val dev = InputDevice.getDevice(id)
            if (isGamepadDevice(dev) && dev != null && !dev.isVirtual) {
                val label = dev.name?.trim().orEmpty().ifBlank { "Controller #$id" }
                val hasRumble = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try { dev.vibratorManager.vibratorIds.isNotEmpty() } catch (_: Exception) { false }
                } else {
                    false
                }
                list.add(
                    JSONObject().apply {
                        put("id", id)
                        put("name", label)
                        put("vendorId", dev.vendorId)
                        put("productId", dev.productId)
                        put("hasRumble", hasRumble)
                    },
                )
            }
        }
        return list.take(4)
    }

    private fun listConnectedControllerNames(): List<String> =
        listConnectedControllers().map { it.optString("name") }

    private fun notifyJsConnectedGamepads() {
        val wv = mainWebViewRef ?: return
        if (!isPageLoaded) return
        val devices = listConnectedControllers()
        val namesArr = JSONArray(devices.map { it.optString("name") }).toString()
        val devsArr = JSONArray(devices).toString()
        wv.post {
            wv.evaluateJavascript(
                "window.onNativeGamepadConnection && window.onNativeGamepadConnection(${devices.size}, $namesArr, $devsArr);",
                null,
            )
        }
    }

    private fun resetNativeGamepadState() {
        nativeGamepads.clear()
        lastSentGamepadPayload = ""
    }

    private fun getOrCreatePadState(deviceId: Int, device: InputDevice?): NativePadState {
        return nativeGamepads.getOrPut(deviceId) {
            val label = device?.name?.trim().orEmpty().ifBlank { "Controller #$deviceId" }
            NativePadState(deviceId = deviceId, name = label)
        }
    }

    private fun getCenteredAxis(event: MotionEvent, device: InputDevice?, axis: Int): Float {
        val value = event.getAxisValue(axis)
        val range = device?.getMotionRange(axis, event.source)
        if (range != null) {
            val flat = maxOf(range.flat, 0.08f)
            if (abs(value) <= flat) return 0f
        } else if (abs(value) <= 0.08f) {
            return 0f
        }
        return value.coerceIn(-1f, 1f)
    }

    private fun mapKeyCodeToSlot(keyCode: Int): String? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> "a"
        KeyEvent.KEYCODE_BUTTON_B -> "b"
        KeyEvent.KEYCODE_BUTTON_X -> "x"
        KeyEvent.KEYCODE_BUTTON_Y -> "y"
        KeyEvent.KEYCODE_BUTTON_L1 -> "l1"
        KeyEvent.KEYCODE_BUTTON_R1 -> "r1"
        KeyEvent.KEYCODE_BUTTON_L2 -> "l2"
        KeyEvent.KEYCODE_BUTTON_R2 -> "r2"
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "l3"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "r3"
        KeyEvent.KEYCODE_BUTTON_START -> "start"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "select"
        KeyEvent.KEYCODE_BUTTON_MODE -> "menu"
        KeyEvent.KEYCODE_DPAD_LEFT -> "dpad_left"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "dpad_right"
        KeyEvent.KEYCODE_DPAD_UP -> "dpad_up"
        KeyEvent.KEYCODE_DPAD_DOWN -> "dpad_down"
        KeyEvent.KEYCODE_DPAD_CENTER -> "a"
        else -> null
    }

    private fun padStateToJson(pad: NativePadState): JSONObject {
        fun r2(v: Float): Double = (kotlin.math.round(v * 1000f) / 1000f).toDouble()
        val l2Val = maxOf(pad.l2, if (pad.keys["l2"] == true) 1f else 0f)
        val r2Val = maxOf(pad.r2, if (pad.keys["r2"] == true) 1f else 0f)
        return JSONObject().apply {
            put("deviceId", pad.deviceId)
            put("name", pad.name)
            put("connected", true)
            put("lx", r2(pad.lx))
            put("ly", r2(pad.ly))
            put("rx", r2(pad.rx))
            put("ry", r2(pad.ry))
            put("hatX", r2(pad.hatX))
            put("hatY", r2(pad.hatY))
            put("l2Val", r2(l2Val))
            put("r2Val", r2(r2Val))
            put("dpad_up", (pad.keys["dpad_up"] == true) || pad.hatY < -0.45f)
            put("dpad_down", (pad.keys["dpad_down"] == true) || pad.hatY > 0.45f)
            put("dpad_left", (pad.keys["dpad_left"] == true) || pad.hatX < -0.45f)
            put("dpad_right", (pad.keys["dpad_right"] == true) || pad.hatX > 0.45f)
            put("a", pad.keys["a"] == true)
            put("b", pad.keys["b"] == true)
            put("x", pad.keys["x"] == true)
            put("y", pad.keys["y"] == true)
            put("l1", pad.keys["l1"] == true)
            put("r1", pad.keys["r1"] == true)
            put("l2", l2Val > 0.25f)
            put("r2", r2Val > 0.25f)
            put("l3", pad.keys["l3"] == true)
            put("r3", pad.keys["r3"] == true)
            put("start", pad.keys["start"] == true)
            put("select", pad.keys["select"] == true)
            put("menu", pad.keys["menu"] == true)
            put("esc", (pad.keys["select"] == true) || (pad.keys["menu"] == true))
            put("left", (pad.keys["dpad_left"] == true) || pad.hatX < -0.45f || pad.lx < -0.42f)
            put("right", (pad.keys["dpad_right"] == true) || pad.hatX > 0.45f || pad.lx > 0.42f)
            put("up", (pad.keys["dpad_up"] == true) || pad.hatY < -0.45f || pad.ly < -0.42f)
            put("down", (pad.keys["dpad_down"] == true) || pad.hatY > 0.45f || pad.ly > 0.42f)
        }
    }

    private fun pushNativeGamepadState(force: Boolean = false) {
        val wv = mainWebViewRef ?: return
        if (!isPageLoaded || (!isPlayerActive && !isControlTesterActive)) return
        val devicesArr = JSONArray()
        for ((_, pad) in nativeGamepads.entries.sortedBy { it.key }.take(4)) {
            devicesArr.put(padStateToJson(pad))
        }
        val firstObj = if (devicesArr.length() > 0) devicesArr.getJSONObject(0) else JSONObject().put("connected", false)
        val payload = devicesArr.toString()
        if (!force && payload == lastSentGamepadPayload) return
        lastSentGamepadPayload = payload
        val singleStr = firstObj.toString()
        wv.post {
            wv.evaluateJavascript(
                "window.onNativeGamepadsState && window.onNativeGamepadsState($payload); window.onNativeGamepadState && window.onNativeGamepadState($singleStr);",
                null,
            )
        }
    }

    private fun shouldCaptureControllerInput(): Boolean {
        return isPageLoaded && (isPlayerActive || isControlTesterActive) && activeBrowserUrl == null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (shouldCaptureControllerInput()) {
            val isFromController = isGamepadDevice(event.device) ||
                event.isFromSource(InputDevice.SOURCE_DPAD) ||
                KeyEvent.isGamepadButton(event.keyCode)
            if (isFromController) {
                val slot = mapKeyCodeToSlot(event.keyCode)
                if (slot != null) {
                    val devId = event.deviceId
                    val pad = getOrCreatePadState(devId, event.device)
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        if (event.repeatCount == 0) {
                            pad.keys[slot] = true
                            pushNativeGamepadState()
                        }
                        return true
                    } else if (event.action == KeyEvent.ACTION_UP) {
                        pad.keys[slot] = false
                        pushNativeGamepadState()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (shouldCaptureControllerInput()) {
            val isControllerMotion = event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
                event.isFromSource(InputDevice.SOURCE_GAMEPAD) ||
                event.isFromSource(InputDevice.SOURCE_DPAD)
            if (isControllerMotion && event.action == MotionEvent.ACTION_MOVE) {
                val device = event.device
                val pad = getOrCreatePadState(event.deviceId, device)
                pad.lx = getCenteredAxis(event, device, MotionEvent.AXIS_X)
                pad.ly = getCenteredAxis(event, device, MotionEvent.AXIS_Y)
                pad.hatX = getCenteredAxis(event, device, MotionEvent.AXIS_HAT_X)
                pad.hatY = getCenteredAxis(event, device, MotionEvent.AXIS_HAT_Y)
                pad.rx = getCenteredAxis(event, device, MotionEvent.AXIS_Z)
                pad.ry = getCenteredAxis(event, device, MotionEvent.AXIS_RZ)
                pad.l2 = maxOf(
                    event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_BRAKE),
                ).coerceIn(0f, 1f)
                pad.r2 = maxOf(
                    event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
                    event.getAxisValue(MotionEvent.AXIS_GAS),
                ).coerceIn(0f, 1f)
                pushNativeGamepadState()
                return true
            }
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun closeInAppBrowser() {
        detectedFlashUrls = emptyList()
        capturedPageFlashUrls = emptyList()
        siteWebViewRef?.stopLoading()
        siteWebViewRef?.destroy()
        siteWebViewRef = null
        activeBrowserUrl = null
        browserCurrentUrl = ""
    }

    private fun sniffCurrentPageForFlash() {
        val wv = siteWebViewRef ?: return
        val referer = browserCurrentUrl.ifBlank { activeBrowserUrl }
        val script = """
            (function() {
              var found = [];
              function add(u) {
                if (!u || typeof u !== 'string') return;
                var cleaned = u.trim().replace(/\\\//g, '/').replace(/&amp;/g, '&');
                if (!cleaned || /^javascript:/i.test(cleaned)) return;
                try {
                  var abs = new URL(cleaned, location.href).href;
                  if (/\.(swf|exe|zip)(\?|\#|&|$)/i.test(abs) && found.indexOf(abs) === -1) {
                    found.push(abs);
                  }
                } catch(e) {}
              }
              document.querySelectorAll('a[href], embed[src], object[data], iframe[src], source[src], ruffle-player, ruffle-embed, ruffle-object').forEach(function(el) {
                add(el.getAttribute('href') || el.getAttribute('src') || el.getAttribute('data'));
                try { if (el.config && el.config.url) add(el.config.url); } catch(e) {}
              });
              document.querySelectorAll('[data-swf], [data-src], [data-url], [data-game], [data-file]').forEach(function(el) {
                add(el.getAttribute('data-swf') || el.getAttribute('data-src') || el.getAttribute('data-url') || el.getAttribute('data-game') || el.getAttribute('data-file'));
              });
              document.querySelectorAll('param[name="movie"], param[name="src"]').forEach(function(el) {
                add(el.getAttribute('value'));
              });
              if (performance && performance.getEntriesByType) {
                performance.getEntriesByType('resource').forEach(function(r) { add(r.name); });
              }
              try {
                var re = /((?:https?:)?(?:\\?\/)+[^\s"'<>\\)]+?\.(?:swf|exe)(?:\?[^\s"'<>\\)]*)?)|(["'])([^"'\s<>]+\.(?:swf|exe)(?:\?[^"'\s<>]*)?)\2/gi;
                var bodyText = document.documentElement ? document.documentElement.innerHTML : '';
                if (bodyText && bodyText.length < 1500000) {
                  var m;
                  while ((m = re.exec(bodyText)) !== null && found.length < 40) {
                    add(m[1] || m[3]);
                  }
                }
              } catch(e) {}
              return JSON.stringify(found);
            })();
        """.trimIndent()

        wv.evaluateJavascript(script) { raw ->
            try {
                val domUrls = JSONTokenerCompat.parseStringArray(raw)
                val merged = (capturedPageFlashUrls + domUrls)
                    .distinct()
                    .sortedWith(
                        compareBy<String>(
                            {
                                val lower = it.substringBefore('?').substringBefore('#').lowercase()
                                when {
                                    lower.endsWith(".swf") -> 0
                                    lower.endsWith(".exe") -> 1
                                    else -> 2
                                }
                            },
                            { it.lowercase().contains("preloader") || it.lowercase().contains("loader") },
                        ),
                    )
                if (merged.isEmpty()) {
                    Toast.makeText(
                        this,
                        getString(R.string.browser_no_swf_found),
                        Toast.LENGTH_SHORT,
                    ).show()
                } else if (merged.size == 1) {
                    val onlyUrl = merged.first()
                    val guess = URLUtil.guessFileName(onlyUrl, null, null)
                    downloadAndDeliverToFlashora(onlyUrl, guess, referer)
                } else {
                    detectedFlashUrls = merged
                }
            } catch (_: Exception) {
                val fallback = capturedPageFlashUrls.distinct()
                if (fallback.size == 1) {
                    val onlyUrl = fallback.first()
                    downloadAndDeliverToFlashora(onlyUrl, URLUtil.guessFileName(onlyUrl, null, null), referer)
                } else if (fallback.size > 1) {
                    detectedFlashUrls = fallback
                } else {
                    Toast.makeText(this, getString(R.string.browser_no_swf_found), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun downloadAndDeliverToFlashora(
        rawUrl: String,
        suggestedName: String,
        refererUrl: String? = null,
    ) {
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

                    val chromeUa =
                        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                    var currentUrl = rawUrl
                    var conn: HttpURLConnection? = null
                    var redirects = 0
                    while (redirects < 8) {
                        val urlObj = URL(currentUrl)
                        conn = (urlObj.openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = false
                            connectTimeout = 20000
                            readTimeout = 45000
                            setRequestProperty("User-Agent", chromeUa)
                            setRequestProperty("Accept", "*/*")
                            if (!refererUrl.isNullOrBlank() &&
                                (refererUrl.startsWith("http://") || refererUrl.startsWith("https://"))
                            ) {
                                setRequestProperty("Referer", refererUrl)
                            }
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
                    val tempFile = File(incomingGamesDir, "tmp_${UUID.randomUUID()}")
                    try {
                        val code = activeConn.responseCode
                        if (code !in 200..299) {
                            error("HTTP $code")
                        }

                        val disp = activeConn.getHeaderField("Content-Disposition")
                        val mime = activeConn.contentType
                        var finalName = if (
                            suggestedName.endsWith(".swf", true) ||
                            suggestedName.endsWith(".exe", true) ||
                            suggestedName.endsWith(".zip", true)
                        ) {
                            suggestedName
                        } else {
                            URLUtil.guessFileName(currentUrl, disp, mime)
                        }

                        activeConn.inputStream.use { input ->
                            FileOutputStream(tempFile).use { output ->
                                input.copyTo(output, bufferSize = 16384)
                            }
                        }

                        val preparedFile = if (isGzipFile(tempFile)) {
                            val unzipped = File(incomingGamesDir, "gz_${UUID.randomUUID()}")
                            GZIPInputStream(BufferedInputStream(FileInputStream(tempFile))).use { gz ->
                                FileOutputStream(unzipped).use { out -> gz.copyTo(out, bufferSize = 16384) }
                            }
                            tempFile.delete()
                            unzipped
                        } else {
                            tempFile
                        }

                        try {
                            if (looksLikeHtmlPage(preparedFile)) {
                                error(getString(R.string.browser_html_error))
                            }

                            var deliveredFile: File? = null
                            if (isZipFile(preparedFile)) {
                                val extracted = extractPlayableFromZip(preparedFile, incomingGamesDir)
                                    ?: error(getString(R.string.browser_zip_no_swf))
                                finalName = extracted.first
                                deliveredFile = extracted.second
                            } else {
                                finalName = normalizeFlashFilename(finalName, currentUrl, preparedFile)
                                val token = "${UUID.randomUUID()}_${finalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                                val outFile = File(incomingGamesDir, token)
                                if (!preparedFile.renameTo(outFile)) {
                                    preparedFile.copyTo(outFile, overwrite = true)
                                }
                                deliveredFile = outFile
                            }

                            Triple(
                                finalName,
                                "https://appassets.androidplatform.net/cache-games/${deliveredFile.name}",
                                currentUrl,
                            )
                        } finally {
                            if (preparedFile.exists()) preparedFile.delete()
                        }
                    } finally {
                        if (tempFile.exists()) tempFile.delete()
                        activeConn.disconnect()
                    }
                }
            }

            isDownloadingGame = false
            result.onSuccess { (finalName, internalUrl, sourceUrl) ->
                closeInAppBrowser()
                val js =
                    "window.onNativeGameReady && window.onNativeGameReady(" +
                        "${JSONObject.quote(finalName)}, ${JSONObject.quote(internalUrl)}, ${JSONObject.quote(sourceUrl)});"
                mainWebViewRef?.evaluateJavascript(js, null)
            }.onFailure { err ->
                val msg = err.message ?: getString(R.string.browser_download_failed)
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                val js = "window.onNativeGameError && window.onNativeGameError(${JSONObject.quote(msg)});"
                mainWebViewRef?.evaluateJavascript(js, null)
            }
        }
    }

    private fun readMagicBytes(file: File, count: Int = 64): ByteArray {
        if (!file.exists() || file.length() == 0L) return ByteArray(0)
        return FileInputStream(file).use { fis ->
            val buf = ByteArray(count)
            val n = fis.read(buf)
            if (n > 0) buf.copyOf(n) else ByteArray(0)
        }
    }

    private fun isGzipFile(file: File): Boolean {
        val m = readMagicBytes(file, 2)
        return m.size >= 2 && (m[0].toInt() and 0xFF) == 0x1F && (m[1].toInt() and 0xFF) == 0x8B
    }

    private fun isZipFile(file: File): Boolean {
        val m = readMagicBytes(file, 4)
        return m.size >= 4 && m[0] == 0x50.toByte() && m[1] == 0x4B.toByte() &&
            m[2] == 0x03.toByte() && m[3] == 0x04.toByte()
    }

    private fun looksLikeHtmlPage(file: File): Boolean {
        val m = readMagicBytes(file, 256)
        if (m.isEmpty()) return true
        val head = String(m, Charsets.UTF_8)
            .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            .lowercase()
        return head.startsWith("<!doctype") ||
            head.startsWith("<html") ||
            head.startsWith("<head") ||
            head.startsWith("<body") ||
            head.startsWith("<?xml") ||
            head.startsWith("<script")
    }

    private fun normalizeFlashFilename(currentName: String, url: String, file: File): String {
        val m = readMagicBytes(file, 4)
        val isSwfMagic = m.size >= 3 &&
            (m[0] == 'F'.code.toByte() || m[0] == 'C'.code.toByte() || m[0] == 'Z'.code.toByte()) &&
            m[1] == 'W'.code.toByte() && m[2] == 'S'.code.toByte()
        val isExeMagic = m.size >= 2 && m[0] == 'M'.code.toByte() && m[1] == 'Z'.code.toByte()
        val pathLast = Uri.parse(url).lastPathSegment
        val base = when {
            currentName.endsWith(".swf", true) || currentName.endsWith(".exe", true) -> currentName
            !pathLast.isNullOrBlank() && (pathLast.endsWith(".swf", true) || pathLast.endsWith(".exe", true)) -> pathLast
            else -> currentName.substringBeforeLast('.').ifBlank { "flash_game" }
        }
        return when {
            isSwfMagic && !base.endsWith(".swf", true) -> "${base.substringBeforeLast('.')}.swf"
            isExeMagic && !base.endsWith(".exe", true) -> "${base.substringBeforeLast('.')}.exe"
            !base.endsWith(".swf", true) && !base.endsWith(".exe", true) -> "$base.swf"
            else -> base
        }
    }

    private fun extractPlayableFromZip(zipFile: File, targetDir: File): Pair<String, File>? {
        data class Candidate(val entryName: String, val tempOut: File, val priority: Int, val size: Long)
        val candidates = mutableListOf<Candidate>()
        try {
            ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val baseName = entry.name.substringAfterLast('/').ifBlank { entry.name }
                        val lower = baseName.lowercase()
                        val isSwf = lower.endsWith(".swf")
                        val isExe = lower.endsWith(".exe")
                        if (isSwf || isExe) {
                            val isLoader = lower.contains("preloader") || lower.contains("loader") || lower.contains("intro")
                            val priority = when {
                                isSwf && !isLoader -> 0
                                isSwf -> 1
                                else -> 2
                            }
                            val safe = "${UUID.randomUUID()}_${baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}"
                            val dest = File(targetDir, safe)
                            FileOutputStream(dest).use { out ->
                                zis.copyTo(out, bufferSize = 16384)
                            }
                            candidates.add(Candidate(baseName, dest, priority, dest.length()))
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {
            candidates.forEach { it.tempOut.delete() }
            return null
        }

        if (candidates.isEmpty()) return null
        val best = candidates.minWith(compareBy<Candidate>({ it.priority }, { -it.size }))
        candidates.forEach { if (it.tempOut != best.tempOut) it.tempOut.delete() }
        return Pair(best.entryName, best.tempOut)
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
                if (enabled) {
                    mainWebViewRef?.requestFocus()
                }
            }
        }

        @JavascriptInterface
        fun setKeepScreenOn(enabled: Boolean) {
            runOnUiThread {
                if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        @JavascriptInterface
        fun setPlayerActive(active: Boolean) {
            runOnUiThread {
                isPlayerActive = active
                if (active) {
                    mainWebViewRef?.isFocusable = true
                    mainWebViewRef?.isFocusableInTouchMode = true
                    mainWebViewRef?.requestFocus()
                    notifyJsConnectedGamepads()
                } else if (!isControlTesterActive) {
                    resetNativeGamepadState()
                }
            }
        }

        @JavascriptInterface
        fun setControlTesterActive(active: Boolean) {
            runOnUiThread {
                isControlTesterActive = active
                if (active) {
                    mainWebViewRef?.requestFocus()
                    notifyJsConnectedGamepads()
                } else if (!isPlayerActive) {
                    resetNativeGamepadState()
                }
            }
        }

        @JavascriptInterface
        fun focusWebView() {
            runOnUiThread {
                mainWebViewRef?.isFocusable = true
                mainWebViewRef?.isFocusableInTouchMode = true
                mainWebViewRef?.requestFocus()
            }
        }

        @JavascriptInterface
        fun getConnectedGamepadsJson(): String {
            return JSONArray(listConnectedControllerNames()).toString()
        }

        @JavascriptInterface
        fun getConnectedControllersJson(): String {
            return JSONArray(listConnectedControllers()).toString()
        }

        @JavascriptInterface
        fun vibrate(durationMs: Int, amplitude: Int) {
            val ms = durationMs.coerceIn(5, 800).toLong()
            val amp = amplitude.coerceIn(1, 255)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    val v = vm?.defaultVibrator
                    if (v != null && v.hasVibrator()) {
                        v.vibrate(VibrationEffect.createOneShot(ms, amp))
                    }
                } else {
                    @Suppress("DEPRECATION")
                    val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                    if (v != null && v.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot(ms, amp))
                        } else {
                            @Suppress("DEPRECATION")
                            v.vibrate(ms)
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        @JavascriptInterface
        fun vibrateGamepad(deviceId: Int, durationMs: Int, amplitude: Int): Boolean {
            val ms = durationMs.coerceIn(10, 1500).toLong()
            val amp = amplitude.coerceIn(1, 255)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val dev = InputDevice.getDevice(deviceId)
                    val vm = dev?.vibratorManager
                    if (vm != null && vm.vibratorIds.isNotEmpty()) {
                        vm.defaultVibrator.vibrate(VibrationEffect.createOneShot(ms, amp))
                        return true
                    }
                } catch (_: Exception) {}
            }
            return false
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
        fun openExternalUrl(url: String) {
            runOnUiThread {
                openUriInExternalBrowser(url)
            }
        }

        @JavascriptInterface
        fun downloadAndPlayUrl(url: String, suggestedName: String) {
            runOnUiThread {
                val referer = Uri.parse(url).let { u ->
                    if (!u.scheme.isNullOrBlank() && !u.host.isNullOrBlank()) "${u.scheme}://${u.host}/" else null
                }
                downloadAndDeliverToFlashora(url, suggestedName, referer)
            }
        }

        @JavascriptInterface
        fun readClipboardText(): String {
            return try {
                val task = FutureTask {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clip = cm?.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        clip.getItemAt(0).coerceToText(this@MainActivity)?.toString().orEmpty()
                    } else {
                        ""
                    }
                }
                Handler(Looper.getMainLooper()).post(task)
                task.get(600, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun fetchImageUrl(requestId: String, rawUrl: String) {
            lifecycleScope.launch {
                val trimmed = rawUrl.trim()
                if (!trimmed.startsWith("http://", ignoreCase = true) &&
                    !trimmed.startsWith("https://", ignoreCase = true)
                ) {
                    val msg = getString(R.string.cover_url_invalid)
                    mainWebViewRef?.evaluateJavascript(
                        "window.onNativeImageUrlResult && window.onNativeImageUrlResult(${JSONObject.quote(requestId)}, false, \"\", ${JSONObject.quote(msg)});",
                        null,
                    )
                    return@launch
                }

                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val chromeUa =
                            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                        var currentUrl = trimmed
                        var conn: HttpURLConnection? = null
                        var redirects = 0
                        while (redirects < 6) {
                            val urlObj = URL(currentUrl)
                            conn = (urlObj.openConnection() as HttpURLConnection).apply {
                                instanceFollowRedirects = false
                                connectTimeout = 15000
                                readTimeout = 20000
                                setRequestProperty("User-Agent", chromeUa)
                                setRequestProperty("Accept", "image/webp,image/png,image/jpeg,image/*,*/*;q=0.8")
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

                        val activeConn = conn ?: error(getString(R.string.cover_url_fetch_failed))
                        try {
                            val code = activeConn.responseCode
                            if (code !in 200..299) {
                                error(getString(R.string.cover_url_fetch_failed) + " (HTTP $code)")
                            }
                            val maxBytes = 8 * 1024 * 1024
                            val baos = ByteArrayOutputStream()
                            val buf = ByteArray(16384)
                            var total = 0
                            activeConn.inputStream.use { input ->
                                while (true) {
                                    val n = input.read(buf)
                                    if (n <= 0) break
                                    total += n
                                    if (total > maxBytes) {
                                        error(getString(R.string.cover_url_fetch_failed))
                                    }
                                    baos.write(buf, 0, n)
                                }
                            }
                            val bytes = baos.toByteArray()
                            if (bytes.size < 16) {
                                error(getString(R.string.cover_url_not_image))
                            }
                            val detectedMime = detectImageMime(bytes, activeConn.contentType)
                                ?: error(getString(R.string.cover_url_not_image))
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            "data:$detectedMime;base64,$b64"
                        } finally {
                            activeConn.disconnect()
                        }
                    }
                }

                result.onSuccess { dataUrl ->
                    mainWebViewRef?.evaluateJavascript(
                        "window.onNativeImageUrlResult && window.onNativeImageUrlResult(${JSONObject.quote(requestId)}, true, ${JSONObject.quote(dataUrl)}, \"\");",
                        null,
                    )
                }.onFailure { err ->
                    val msg = err.message ?: getString(R.string.cover_url_fetch_failed)
                    mainWebViewRef?.evaluateJavascript(
                        "window.onNativeImageUrlResult && window.onNativeImageUrlResult(${JSONObject.quote(requestId)}, false, \"\", ${JSONObject.quote(msg)});",
                        null,
                    )
                }
            }
        }
    }

    private fun detectImageMime(bytes: ByteArray, contentType: String?): String? {
        if (bytes.size >= 3 &&
            (bytes[0].toInt() and 0xFF) == 0xFF &&
            (bytes[1].toInt() and 0xFF) == 0xD8 &&
            (bytes[2].toInt() and 0xFF) == 0xFF
        ) {
            return "image/jpeg"
        }
        if (bytes.size >= 8 &&
            (bytes[0].toInt() and 0xFF) == 0x89 &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte()
        ) {
            return "image/png"
        }
        if (bytes.size >= 12 &&
            bytes[0] == 'R'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte() &&
            bytes[3] == 'F'.code.toByte() &&
            bytes[8] == 'W'.code.toByte() &&
            bytes[9] == 'E'.code.toByte() &&
            bytes[10] == 'B'.code.toByte() &&
            bytes[11] == 'P'.code.toByte()
        ) {
            return "image/webp"
        }
        if (bytes.size >= 6 &&
            bytes[0] == 'G'.code.toByte() &&
            bytes[1] == 'I'.code.toByte() &&
            bytes[2] == 'F'.code.toByte()
        ) {
            return "image/gif"
        }
        val ct = contentType?.substringBefore(';')?.trim()?.lowercase() ?: ""
        if (ct.startsWith("image/") && !ct.contains("svg")) {
            return ct
        }
        return null
    }

    private fun openUriInExternalBrowser(rawUrl: String) {
        try {
            val uri = Uri.parse(rawUrl.trim())
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(
                this,
                getString(R.string.browser_open_external_error),
                Toast.LENGTH_SHORT,
            ).show()
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
    capturedCount: Int,
    onDismissDetected: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
    onUrlChanged: (String) -> Unit,
    onPageNavigationStarted: () -> Unit,
    onPassiveFlashUrlCaptured: (String) -> Unit,
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
                        text = if (capturedCount > 0) {
                            "${stringResource(R.string.browser_sniff_swf)} ($capturedCount)"
                        } else {
                            stringResource(R.string.browser_sniff_swf)
                        },
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
                            javaScriptCanOpenWindowsAutomatically = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString =
                                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                onLoadingChanged(true)
                                if (url != null) {
                                    onPageNavigationStarted()
                                    onUrlChanged(url)
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                onLoadingChanged(false)
                                if (url != null) onUrlChanged(url)
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): WebResourceResponse? {
                                val target = request?.url?.toString()
                                val path = request?.url?.path?.lowercase() ?: ""
                                if (target != null && (path.endsWith(".swf") || path.endsWith(".exe"))) {
                                    onPassiveFlashUrlCaptured(target)
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean {
                                val target = request?.url?.toString() ?: return false
                                val path = request.url.path?.lowercase() ?: ""
                                if (path.endsWith(".swf") || path.endsWith(".exe") || path.endsWith(".zip")) {
                                    onDownloadIntercepted(target, null, null)
                                    return true
                                }
                                return false
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?,
                            ): Boolean {
                                onLoadingChanged(false)
                                onClose()
                                return true
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
    onShowFileChooser: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams?) -> Unit,
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

    var webViewNonce by remember { mutableStateOf(0) }

    Box(modifier = containerModifier) {
        androidx.compose.runtime.key(webViewNonce) {
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
                    isVerticalScrollBarEnabled = true
                    isHorizontalScrollBarEnabled = false
                    isNestedScrollingEnabled = true
                    isFocusable = true
                    isFocusableInTouchMode = true
                    overScrollMode = android.view.View.OVER_SCROLL_IF_CONTENT_SCROLLS
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
                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)
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

                        override fun onRenderProcessGone(
                            view: WebView?,
                            detail: RenderProcessGoneDetail?,
                        ): Boolean {
                            view?.post {
                                try { view.destroy() } catch (_: Exception) {}
                                webViewNonce++
                            }
                            return true
                        }
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?,
                        ): Boolean {
                            if (filePathCallback == null) return false
                            onShowFileChooser(filePathCallback, fileChooserParams)
                            return true
                        }

                        override fun onCreateWindow(
                            view: WebView?,
                            isDialog: Boolean,
                            isUserGesture: Boolean,
                            resultMsg: Message?,
                        ): Boolean {
                            val ctx = view?.context ?: return false
                            val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                            val tempWebView = WebView(ctx).apply {
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(
                                        popupView: WebView?,
                                        request: WebResourceRequest?,
                                    ): Boolean {
                                        val targetUrl = request?.url?.toString()
                                        if (!targetUrl.isNullOrBlank()) {
                                            bridge.openExternalUrl(targetUrl)
                                        }
                                        popupView?.post { popupView.destroy() }
                                        return true
                                    }
                                }
                            }
                            transport.webView = tempWebView
                            resultMsg.sendToTarget()
                            return true
                        }
                    }
                    onWebViewCreated(this)
                    loadUrl("https://appassets.androidplatform.net/assets/flashora.html")
                }
            },
        )
        }

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
